// Copyright 2016 flat authors

package flat

import flat.logging.Logger
import java.io.{ByteArrayInputStream, IOException}
import java.net.{ServerSocket, Socket, SocketException}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.Executors
import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.{Consumer, Observable}
import monix.execution.schedulers.ExecutionModel
import org.apache.http.{ConnectionClosedException, HttpRequest => ApacheHttpRequest, HttpVersion}
import org.apache.http.entity.{BasicHttpEntity, ContentLengthStrategy}
import org.apache.http.impl.entity.StrictContentLengthStrategy
import org.apache.http.impl.io.{
  ChunkedInputStream, ChunkedOutputStream, ContentLengthInputStream, ContentLengthOutputStream,
  DefaultHttpResponseWriter, DefaultHttpRequestParser, HttpTransportMetricsImpl, IdentityInputStream,
  IdentityOutputStream, SessionInputBufferImpl, SessionOutputBufferImpl
}
import org.apache.http.message.{BasicHeader, BasicHttpEntityEnclosingRequest, BasicHttpResponse}
import org.apache.http.util.EntityUtils
import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

trait FlatApp {
  import Ws._
  implicit def handlerSync2Async(response: HttpResponse): Task[HttpResponse] = Task.now(response)
  implicit def unitSync2Async(work: Unit): Task[Unit] = Task.now(work)
  implicit def wsConnectAsync(connect: WsOnConnectAsync): WsOnConnect = Left(connect)
  implicit def wsConnectSync(connect: WsOnConnectSync): WsOnConnect = Right(connect)
  implicit def wsMessageAsync(message: WsOnMessageAsync): WsOnMessage = Left(message)
  implicit def wsMessageSync(message: WsOnMessageSync): WsOnMessage = Right(message)
}

abstract class FlatException(m: String, t: Option[Throwable]) extends RuntimeException(m, t.getOrElse(null))
case class UnsupportedMethodException(m: String, t: Option[Throwable] = None) extends FlatException(m, t)

object app {
  private val parallelism = Math.ceil(Runtime.getRuntime.availableProcessors / 2.0).toInt
  private val executor = Executors.newScheduledThreadPool(parallelism * 2)
  implicit val scheduler = Scheduler(executor, ExecutionModel.Default)

  private type Handler = (HttpRequest) => Task[HttpResponse]

  private val routes = HashMap.empty[(String, HttpMethod), Handler]
  private def addRoute(uri: String, methods: List[HttpMethod], handler: Handler) = {
    if (methods.contains(TRACE)) throw new RuntimeException("TRACE requests are not supported")
    methods.foreach(method => routes += (uri, method) -> handler)
  }

  def route(uri: String, methods: List[HttpMethod])(handler: Handler): Unit = {
    addRoute(uri, methods, handler)
  }

  def route(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(GET, POST, PUT, DELETE, PATCH, OPTIONS), handler)
  }

  def get(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(GET), handler)
  }
  def post(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(POST), handler)
  }
  def put(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(PUT), handler)
  }
  def delete(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(DELETE), handler)
  }
  def patch(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(PATCH), handler)
  }
  def options(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(OPTIONS), handler)
  }

  private type WsHandler = (WsContext) => Task[Unit]

  private val wsRoutes = HashMap.empty[String, WsHandler]

  def ws(uri: String)(handler: WsHandler): Unit = {
    wsRoutes += uri -> handler
  }

  private type Prehandler = (HttpRequest) => Option[HttpResponse]
  private val prehandlers = ArrayBuffer.empty[Prehandler]

  def addPrehandler(prehandler: Prehandler) = prehandlers += prehandler

  private var serverSocketOpt = Option.empty[ServerSocket]
  private var serverCancelableOpt = Option.empty[CancelableFuture[Unit]]

  private def sendResponse(
    socket: Socket,
    flatResponse: HttpResponse,
    flatRequestOpt: Option[HttpRequest] = None,
    wsHandlerOpt: Option[WsHandler] = None
  ): Unit = {
    val outputBuffer = new SessionOutputBufferImpl(new HttpTransportMetricsImpl, 1024)
    outputBuffer.bind(socket.getOutputStream)
    val responseWriter = new DefaultHttpResponseWriter(outputBuffer)
    val response = new BasicHttpResponse(
      HttpVersion.HTTP_1_1,
      flatResponse.code,
      flatResponse.reason
    )

    response.setHeaders(flatResponse.headers.map { case (n,v) =>
      new BasicHeader(n, v)
    }.toArray)

    if (flatResponse.bodyOpt.isEmpty) {
      responseWriter.write(response)
    }
    else {
      val body = flatResponse.bodyOpt.get
      val entity = new BasicHttpEntity
      entity.setContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
      response.setEntity(entity)
      val contentLength = StrictContentLengthStrategy.INSTANCE.determineLength(response)
      val contentStream = contentLength match {
        case ContentLengthStrategy.CHUNKED =>
          new ChunkedOutputStream(2048, outputBuffer)
        case ContentLengthStrategy.IDENTITY =>
          new IdentityOutputStream(outputBuffer)
        case _ =>
          new ContentLengthOutputStream(outputBuffer, contentLength)
      }

      response.setHeader("Content-Length", contentLength.toString)
      responseWriter.write(response)
      if (flatRequestOpt.exists(_.method != HEAD)) {
        entity.writeTo(contentStream)
        contentStream.close
      }
    }

    wsHandlerOpt match {
      case Some(wsHandler) =>
        val wsContext = registerWs(socket, wsHandler)
        outputBuffer.flush
        Logger.debug(s"Sent response and keeping alive:\n$response")
        wsContext.triggerOnConnect
      case _ =>
        outputBuffer.flush
        Logger.debug(s"Sent response and closing:\n$response")
        socket.shutdownOutput
        socket.close
    }
  }

  private def registerWs(socket: Socket, wsHandler: WsHandler): WsContext = {
    val wsContext = new WsContext(socket)
    wsHandler(wsContext)

    Observable
      .repeatEval {
        socket.getInputStream.available > 0
      }
      .collect {
        case true => ()
      }
      .foreach { _ =>
        try {
          val payload = Ws.getPayload(socket)
          wsContext.triggerOnMessage(payload)
        }
        catch {
          case fwe: FlatWsException =>
            Logger.debug(fwe.getMessage)
            socket.shutdownOutput
            socket.close
          case e: Exception =>
            Logger.error("Websocket closing due to following error", e)
            socket.shutdownOutput
            socket.close
        }
      }

    wsContext
  }

  def start: Unit = start(9000)
  def start(port: Int): Unit = {
    serverSocketOpt = Some(new ServerSocket(port))

    serverCancelableOpt = Some(Observable
      .repeatEval(serverSocketOpt.filterNot(_.isClosed).map(s => Try(s.accept)))
      .map {
        case Some(Success(socket)) =>
          Some(socket)
        case Some(Failure(t)) if !(t.isInstanceOf[SocketException] && t.getMessage == "Socket closed") =>
          throw t
        case _ =>
          None
      }
      .collect {
        case Some(socket) => socket
      }
      .consumeWith(Consumer.foreachParallelAsync[Socket](parallelism) { socket =>
        Task {
          val inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl, 1024)
          inputBuffer.bind(socket.getInputStream)
          val requestParser = new DefaultHttpRequestParser(inputBuffer)
          val request = requestParser.parse

          if (request.getRequestLine.getProtocolVersion != HttpVersion.HTTP_1_1)
            throw new RuntimeException("Encountered unhandled http version")

          val version = HttpVersion.HTTP_1_1

          val body = {
            if (request.isInstanceOf[BasicHttpEntityEnclosingRequest]) {
              val contentLength = StrictContentLengthStrategy.INSTANCE.determineLength(request)
              val contentStream = contentLength match {
                case ContentLengthStrategy.CHUNKED =>
                  new ChunkedInputStream(inputBuffer)
                case ContentLengthStrategy.IDENTITY =>
                  new IdentityInputStream(inputBuffer)
                case _ =>
                  new ContentLengthInputStream(inputBuffer, contentLength)
              }
              val entity = new BasicHttpEntity
              entity.setContent(contentStream)
              Some(EntityUtils.toString(entity, StandardCharsets.UTF_8))
            }
            else None
          }

          val method = request.getRequestLine.getMethod match {
            case "GET"     => GET
            case "POST"    => POST
            case "PUT"     => PUT
            case "HEAD"    => HEAD
            case "DELETE"  => DELETE
            case "OPTIONS" => OPTIONS
            case "PATCH"   => PATCH
            case _ => throw new UnsupportedMethodException("Encountered unhandled http method")
          }

          val flatRequest = HttpRequest(
            version,
            method,
            request.getRequestLine.getUri,
            request.getAllHeaders.toList.map(h => (h.getName, h.getValue)),
            body
          )
          Logger.debug(s"Received request:\n$request")
          flatRequest
        }
        .onErrorRestartIf {
          case ioe: IOException if ioe.getMessage == "Stream closed" =>
            Logger.debug("Caught stream closed exception")
            false
          case cce: ConnectionClosedException if cce.getMessage == "Client closed connection" =>
            Logger.debug("Caught client closed exception")
            false
          case ume: UnsupportedMethodException =>
            false
          case t: Throwable =>
            Logger.error("Unknown error parsing request", t)
            true
        }
        .flatMap { request =>
          val prehandledOpt = prehandlers.map(prehandler => prehandler(request)).find(_.isDefined)
          prehandledOpt match {
            case Some(Some(prehandledResponse)) =>
              Task.now((request, prehandledResponse, None))
            case _ =>
              val isWsHandshake = request.method == GET &&
                  wsRoutes.contains(request.uri) &&
                  request.headers.contains("Connection" -> "Upgrade") &&
                  request.headers.contains("Upgrade" -> "websocket") &&
                  request.headers.contains("Sec-WebSocket-Version" -> "13") &&
                  request.headers.exists(_._1 == "Sec-WebSocket-Key")

              isWsHandshake match {
                case true =>
                  val wsAccept = request.headers.find(_._1 == "Sec-WebSocket-Key")
                      .map(_._2 + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                      .map(_.getBytes(StandardCharsets.UTF_8))
                      .map(MessageDigest.getInstance("SHA-1").digest(_))
                      .map(Base64.getEncoder.encodeToString(_))
                      .get
                  Task.now((
                    request,
                    SwitchingProtocols(List(
                      "Connection" -> "Upgrade",
                      "Upgrade" -> "websocket",
                      "Sec-WebSocket-Accept" -> wsAccept
                    )),
                    wsRoutes.get(request.uri)
                  ))
                case _ =>
                  val routedMethod = if (request.method == HEAD) GET else request.method
                  routes.get((request.uri, routedMethod)) match {
                    case Some(handler) =>
                      handler(request).map { response =>
                        (request, response, None)
                      }.onErrorHandleWith { t =>
                        Logger.error("Uncaught error from handler", t)
                        Task.now((request, InternalServerError("error"), None))
                      }
                    case _ =>
                      Task.now((request, NotFound("not found"), None))
                  }
              }
          }
        }
        .map { case (flatRequest, flatResponse, wsHandlerOpt) =>
          sendResponse(socket, flatResponse, Some(flatRequest), wsHandlerOpt)
        }
        .onErrorHandle { t =>
          t match {
            case ume: UnsupportedMethodException =>
              sendResponse(socket, MethodNotAllowed("method not allowed"))
            case _ =>
              Logger.error("Unexpected error from request consumer", t)
              sendResponse(socket, InternalServerError("error"))
          }
        }
      })
      .onErrorRestartIf { t =>
        t match {
          case cce: ConnectionClosedException if cce.getMessage == "Client closed connection" => ()
          case se: SocketException if t.getMessage == "Socket is closed" => ()
          case _ =>
            Logger.error("Unexpected error caught in server task", t)
        }

        false
      }
      .delayExecutionWith(Task.now {
        Logger.info(s"Starting server on port ${Console.CYAN}$port${Console.RESET}")
      })
      .runAsync
    )
  }

  def stop: Unit = {
    serverCancelableOpt match {
      case Some(serverCancelable) =>
        Logger.info("Stopping server")
        serverCancelable.cancel
        serverSocketOpt.map(_.close).getOrElse(throw new RuntimeException("Trying to close server socket that wasn't open"))
      case None =>
        throw new RuntimeException("Trying to stop app that is not running")
    }
  }

  def clear: Unit = {
    Logger.info("Clearing server")
    routes.clear
  }
}
