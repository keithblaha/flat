package flat

import java.net.{ServerSocket, Socket, SocketException}
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import org.apache.http.impl.io.{DefaultHttpRequestParser, HttpTransportMetricsImpl, SessionInputBufferImpl}
import org.apache.http.util.CharArrayBuffer
import org.slf4j.LoggerFactory
import scala.concurrent.duration._
import scala.io.{BufferedSource, Source, StdIn}

object Main extends App {
  val logger = LoggerFactory.getLogger("flat")

  val port = 9000
  val consumerParallelism = 10

  val server = new ServerSocket(port)

  val socketClosedMessages = Set("Socket is closed", "Socket closed")

  val cancelable = Observable
    .repeatEval {
      try Some(server.accept)
      catch {
        case se: SocketException if socketClosedMessages.contains(se.getMessage) =>
          None
        case t: Throwable =>
          logger.error("Uncaught error in server socket source", t)
          None
      }
    }
    .filter(_.isDefined)
    .map(_.get)
    .runWith(Consumer.loadBalance(consumerParallelism, Consumer.foreach[Socket] { socket =>
      try {
        // TODO - define consumers so parsers are reused between requests
        val buffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 1024) 
        buffer.bind(socket.getInputStream)
        val parser = new DefaultHttpRequestParser(buffer)
        val request = parser.parse
        logger.debug(s"Received request:\n$request")
        // TODO - should pass along requests and sockets to be answered by requests, implemented via handlers
        socket.getOutputStream.write(s"HTTP/1.1 200 OK\r\n\r\n<html><body><h1>hi</h1></body></html>".getBytes("UTF-8"))
        socket.shutdownOutput
      }
      catch { case t: Throwable =>
        logger.error("Uncaught error in socket handler", t)
      }
      finally socket.close
    }))
    .onErrorRestartIf { t =>
      logger.error("Uncaught error in server socket consumer, terminating", t)
      false
    }
    .runAsync

  logger.info(s"Running on port ${Console.CYAN}$port${Console.RESET}, press ${Console.YELLOW}enter${Console.RESET} to stop")
  StdIn.readLine()
  cancelable.cancel
  server.close
  logger.info(s"Stopped running")
}

