// Copyright 2016 flat authors

package flat

import java.net.{ServerSocket, Socket, SocketException}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import org.apache.http.HttpRequest
import org.apache.http.impl.io.{DefaultHttpRequestParser, HttpTransportMetricsImpl, SessionInputBufferImpl}
import org.slf4j.LoggerFactory
import scala.io.StdIn

object Main extends App {
  val logger = LoggerFactory.getLogger("flat")

  val port = 9000
  val parallelism = Math.ceil(Runtime.getRuntime.availableProcessors / 2.0).toInt

  val serverSocket = new ServerSocket(port)

  val okResponder = (httpRequest: HttpRequest) => {
    s"HTTP/1.1 200 OK\r\n\r\n<html><body><h1>hi</h1></body></html>"
  }

  val serverTask = Observable
    .repeatEval {
      try Some(serverSocket.accept)
      catch {
        case se: SocketException if Set("Socket is closed", "Socket closed").contains(se.getMessage) =>
          None
        case t: Throwable =>
          logger.error("Uncaught error in socket source", t)
          None
      }
    }
    .filter(_.isDefined)
    .map(_.get)
    .runWith(Consumer.foreachParallelAsync[Socket](parallelism) { socket =>
      Task {
        try {
          val buffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 1024)
          buffer.bind(socket.getInputStream)
          val parser = new DefaultHttpRequestParser(buffer)
          val request = parser.parse
          logger.debug(s"Received request:\n$request")

          val response = okResponder(request)
          socket.getOutputStream.write(response.getBytes("UTF-8"))
          socket.shutdownOutput
        }
        catch { case t: Throwable =>
          logger.error("Uncaught error in socket consumer", t)
        }
        finally socket.close
      }
    })
    .onErrorRestartIf { t =>
      logger.error("Uncaught error in server task", t)
      false
    }
    .runAsync

  logger.info(s"Running on port ${Console.CYAN}$port${Console.RESET}, press ${Console.YELLOW}enter${Console.RESET} to stop")
  StdIn.readLine()
  serverTask.cancel
  serverSocket.close
  logger.info(s"Stopped running")
}

