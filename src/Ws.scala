// Copyright 2016 flat authors

package flat

import java.lang.StringBuilder
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import monix.eval.Task
import scala.collection.mutable.ArrayBuffer

abstract class FlatWsException(m: String, t: Option[Throwable]) extends FlatException(m, t)
case class UnmaskedWsRequestException(m: String, t: Option[Throwable] = None) extends FlatWsException(m, t)
case class UnsupportedWsRequestException(m: String, t: Option[Throwable] = None) extends FlatWsException(m, t)

object Ws {
  type WsOnConnectAsync = Task[Unit]
  type WsOnConnectSync = Unit
  type WsOnConnect = Either[WsOnConnectAsync, WsOnConnectSync]

  type WsOnMessageAsync = String => Task[Unit]
  type WsOnMessageSync = String => Unit
  type WsOnMessage = Either[WsOnMessageAsync, WsOnMessageSync]

  sealed trait WsOpcode { val code: Int }
  final case object Continuation extends WsOpcode { val code = 0 }
  final case object TextData extends WsOpcode  { val code = 1 }

  val supportedOpcodes = Map(
    Continuation.code -> Continuation,
    TextData.code -> TextData
  )

  case class WsFrame(
    fin: Boolean,
    opcode: WsOpcode,
    payload: Array[Byte]
  )

  def getUnsignedIntValue(bytes: Array[Byte]): Long = {
    (bytes.size - 1 to 0 by -1)
      .map { e =>
        Math.pow(2, 8 * e).toLong * (bytes(bytes.size - e - 1) & 255)
      }
      .sum
  }

  def readFrame(socket: Socket): WsFrame = {
    val headByte = socket.getInputStream.read
    val fin = (headByte & 128) > 0
    val rsv1 = (headByte & 64) > 0
    val rsv2 = (headByte & 32) > 0
    val rsv3 = (headByte & 16) > 0
    val rawOpcode = headByte & 15

    flat.logging.Logger.debug(s"""
      reading frame with following
        fin $fin
        rsv1 $rsv1
        rsv2 $rsv2
        rsv3 $rsv3
        opcode $rawOpcode
      """)

    // doesn't currently support extensions
    if (rsv1 || rsv2 || rsv3) {
      throw new UnsupportedWsRequestException("Websocket extensions not supported yet, closing")
    }

    // validate that opcode is supported
    if (!supportedOpcodes.contains(rawOpcode)) {
      throw new UnsupportedWsRequestException(s"Websocket opcode $rawOpcode not supported yet, closing")
    }
    val opcode = supportedOpcodes(rawOpcode)

    val payloadLengthSignalByte = socket.getInputStream.read

    // requests must be masked
    val masked = (payloadLengthSignalByte & 128) > 0
    if (!masked) {
      throw UnmaskedWsRequestException("Received websocket frame without mask, closing")
    }

    // extract payload length
    val payloadLengthSignal = payloadLengthSignalByte & 127
    val payloadLength = payloadLengthSignal match {
      case 127 =>
        val payloadLengthBytes = Array.fill(8)(0.toByte)
        socket.getInputStream.read(payloadLengthBytes)
        if ((payloadLengthBytes(0) & 128) > 0) {
          throw new UnsupportedWsRequestException("Websocket 64 bit length with non-zero most significant bit, closing")
        }
        val size = getUnsignedIntValue(payloadLengthBytes)
        if (size > Int.MaxValue) {
          throw new UnsupportedWsRequestException(s"Websocket doesn't support frame size over ${Int.MaxValue}, closing")
        }
        size.toInt
      case 126 =>
        val payloadLengthBytes = Array.fill(2)(0.toByte)
        socket.getInputStream.read(payloadLengthBytes)
        getUnsignedIntValue(payloadLengthBytes).toInt
      case _ =>
        payloadLengthSignal
    }

    // read masking key
    val maskingKeyBytes = Array.fill(4)(0.toByte)
    socket.getInputStream.read(maskingKeyBytes)

    // read tranformed payload bytes
    val transformedPayloadBytes = Array.fill(payloadLength)(0.toByte)
    socket.getInputStream.read(transformedPayloadBytes)

    // get original bytes
    val payloadBytes = transformedPayloadBytes.zipWithIndex.map { case (transformedByte, i) =>
      (transformedByte ^ maskingKeyBytes(i % 4)).toByte
    }

    WsFrame(fin, opcode, payloadBytes)
  }

  def getPayload(socket: Socket): String = {
    val frames = Iterator
      .continually(readFrame(socket))
      .takeWhile(!_.fin)
      .toList

    frames.head.opcode match {
      case TextData =>
        // TODO - long strings dont seem to fully materialize yet, but it looks like full byte arrays are pulled
        val stringBuilder = new StringBuilder()
        frames.foreach { f =>
          stringBuilder.append(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(f.payload)))
        }
        stringBuilder.toString
      case o : WsOpcode =>
        throw new UnsupportedWsRequestException(s"Websocket doesn't support first frame with opcode ${o.code}, closing")
    }
  }
}
import Ws._

class WsContext(socket: Socket) {
  var wsOnConnectHandlers = ArrayBuffer.empty[WsOnConnect]
  var wsOnMessageHandlers = ArrayBuffer.empty[WsOnMessage]

  def onConnect(wsOnConnect: WsOnConnect): Unit = {
    wsOnConnectHandlers += wsOnConnect
  }
  def triggerOnConnect: Task[Unit] = {
    Task.gather(wsOnConnectHandlers.map {
      case Left(asyncHandler) =>
        asyncHandler
      case Right(syncHandler) =>
        Task.now(syncHandler)
    }).map(_ => ())
  }

  def onMessage(wsOnMessage: WsOnMessage): Unit = {
    wsOnMessageHandlers += wsOnMessage
  }
  def triggerOnMessage(msg: String): Task[Unit] = {
    Task.gather(wsOnMessageHandlers.map {
      case Left(asyncHandler) =>
        asyncHandler(msg)
      case Right(syncHandler) =>
        Task.now(syncHandler(msg))
    }).map(_ => ())
  }

  def send(msg: String): Unit = {
    // this should just queue something to send so no clobbering occurs
    // can probably have a single observable on all things needing to be sent
    // and run that somewhere always sending just like main server loop
    // should do the same thing for listening, run a pool that listens and grabs anything
    // which is ready to be read from
    socket.getOutputStream.write(Array(0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f).map(_.toByte))
    socket.getOutputStream.flush
  }
}
