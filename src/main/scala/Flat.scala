// Copyright 2016 flat authors

package flat

import flat.logging.Logger
import java.net.{ServerSocket, Socket, SocketException}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Consumer, Observable}
import org.apache.http.{HttpRequest => ApacheHttpRequest, HttpVersion}
import org.apache.http.impl.io.{
  DefaultHttpRequestParser, HttpTransportMetricsImpl,
  SessionInputBufferImpl, SessionOutputBufferImpl
}
import scala.collection.mutable.HashMap
import scala.io.StdIn

object app {
  val parallelism = Math.ceil(Runtime.getRuntime.availableProcessors / 2.0).toInt

  val routes = HashMap.empty[(String,HttpMethod),(HttpRequest) => Task[HttpResponse]]
  def route(uri: String, method: HttpMethod, handler: (HttpRequest) => Task[HttpResponse]): Unit = {
    routes += (uri, method) -> handler
  }

  def start: Unit = start(9000)
  def start(port: Int): Unit = {
    val serverSocket = new ServerSocket(port)
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
          if (request.getRequestLine.getProtocolVersion != HttpVersion.HTTP_1_1)
            throw new RuntimeException("Encountered unhandled http version")
          val method = request.getRequestLine.getMethod match {
            case "GET" => GET
            case "POST" => POST
            case _ => throw new RuntimeException("Encountered unhandled http method")
          }
          HttpRequest(
            method,
            request.getRequestLine.getUri,
            request.getAllHeaders.toList.map(h => (h.getName, h.getValue))
          )
        }
        .flatMap { request =>
          routes.get((request.uri, request.method)) match {
            case Some(handler) =>
              handler(request)
            case _ =>
              Task.now(NotFound(""))
          }
        }
        .onErrorHandleWith { t =>
          Logger.error("Uncaught error from handler", t)
          Task.now(InternalServerError("<html><body><h1>error!</h1></body></html>"))
        }
        .map { response =>
          val outputBuffer = new SessionOutputBufferImpl(new HttpTransportMetricsImpl, 1024)
          outputBuffer.bind(socket.getOutputStream)
          outputBuffer.write(response.getBytes)
          outputBuffer.flush
          socket.shutdownOutput
          socket.close
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

