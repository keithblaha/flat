// Copyright 2016 flat authors

package flat

import flat.logging.Logger
import java.net.{ServerSocket, Socket, SocketException}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import org.apache.http.HttpRequest
import org.apache.http.impl.io.{
  DefaultHttpRequestParser, HttpTransportMetricsImpl,
  SessionInputBufferImpl, SessionOutputBufferImpl
}
import org.slf4j.LoggerFactory
import scala.io.StdIn

object Server {
  val port = 9000
  val parallelism = Math.ceil(Runtime.getRuntime.availableProcessors / 2.0).toInt

  val serverSocket = new ServerSocket(port)

  val okResponder = (httpRequest: HttpRequest) => {
    HttpResponse(HttpStatus.OK, "<html><body><h1>hi</h1></body></html>")
  }

  def start = {
    val serverCancelable = Observable
      .repeatEval {
        try Some(serverSocket.accept)
        catch {
          case se: SocketException if Set("Socket is closed", "Socket closed").contains(se.getMessage) =>
            None
          case t: Throwable =>
            Logger.error("Uncaught error in socket source", t)
            None
        }
      }
      .collect {
        case Some(s) => s
      }
      .runWith(Consumer.foreachParallelAsync[Socket](parallelism) { socket =>
        Task {
          val inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl, 1024)
          inputBuffer.bind(socket.getInputStream)
          val requestParser = new DefaultHttpRequestParser(inputBuffer)
          val request = requestParser.parse
          Logger.debug(s"Received request:\n$request")
          request
        }
        .map { request =>
          okResponder(request)
        }
        .onErrorHandleWith { error =>
          Task.now(HttpResponse(HttpStatus.InternalServerError, "<html><body><h1>error!</h1></body></html>"))
        }
        .map { response =>
          val outputBuffer = new SessionOutputBufferImpl(new HttpTransportMetricsImpl, 1024)
          outputBuffer.bind(socket.getOutputStream)
          outputBuffer.write(response.getBytes)
          outputBuffer.flush
          socket.shutdownOutput
        }
        .doOnFinish { _ =>
          Task.now(socket.close)
        }
      })
      .onErrorRestartIf { t =>
        Logger.error("Uncaught error in server task", t)
        false
      }
      .delayExecutionWith(Task.now {
        Logger.info(s"Running on port ${Console.CYAN}$port${Console.RESET}, press ${Console.YELLOW}enter${Console.RESET} to stop")
      })
      .runAsync

    StdIn.readLine
    serverCancelable.cancel
    serverSocket.close
    Logger.info(s"Stopped running")
  }
}

