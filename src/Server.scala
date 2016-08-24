// Copyright 2016 flat authors

package flat

import flat.logging.Logger
import java.io.IOException
import java.net.{ServerSocket, Socket}
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.{Consumer, Observable}
import monix.execution.schedulers.ExecutionModel
import org.apache.http.{ConnectionClosedException, HttpRequest => ApacheHttpRequest, HttpVersion}
import org.apache.http.entity.{BasicHttpEntity, ContentLengthStrategy}
import org.apache.http.impl.entity.StrictContentLengthStrategy
import org.apache.http.impl.io.{
  ChunkedInputStream,  ContentLengthInputStream, DefaultHttpRequestParser,
  HttpTransportMetricsImpl, IdentityInputStream, SessionInputBufferImpl,
  SessionOutputBufferImpl
}
import org.apache.http.message.BasicHttpEntityEnclosingRequest
import org.apache.http.util.EntityUtils
import scala.collection.mutable.HashMap

trait FlatApp {
  implicit def sync2Async(response: HttpResponse): Task[HttpResponse] = Task.now(response)
}

object app {
  val parallelism = Math.ceil(Runtime.getRuntime.availableProcessors / 2.0).toInt

  private type Handler = (HttpRequest) => Task[HttpResponse]

  private val routes = HashMap.empty[(String,HttpMethod),Handler]
  private def addRoute(uri: String, methods: List[HttpMethod], handler: Handler) = {
    methods.foreach(method => routes += (uri, method) -> handler)
  }

  def route(uri: String, methods: List[HttpMethod])(handler: Handler): Unit = {
    addRoute(uri, methods, handler)
  }

  def route(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(GET, POST, PUT, HEAD, DELETE, TRACE, OPTIONS, PATCH, CONNECT), handler)
  }

  def get(uri: String)(handler: Handler): Unit = {
    addRoute(uri, List(GET), handler)
  }

  private val executor = Executors.newScheduledThreadPool(parallelism * 2)
  implicit val scheduler = Scheduler(executor, ExecutionModel.Default)

  def start: Unit = start(9000)
  def start(port: Int): Unit = {
    val serverSocket = new ServerSocket(port)
    Observable
      .repeatEval(serverSocket.accept)
      .runWith(Consumer.foreachParallelAsync[Socket](parallelism) { socket =>
        Task {
          val inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl, 1024)
          inputBuffer.bind(socket.getInputStream)
          val requestParser = new DefaultHttpRequestParser(inputBuffer)
          val request = requestParser.parse

          if (request.getRequestLine.getProtocolVersion != HttpVersion.HTTP_1_1)
            throw new RuntimeException("Encountered unhandled http version")

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
            case "TRACE"   => TRACE
            case "OPTIONS" => OPTIONS
            case "PATCH"   => PATCH
            case "CONNECT" => CONNECT
            case _ => throw new RuntimeException("Encountered unhandled http method")
          }

          val flatRequest = HttpRequest(
            method,
            request.getRequestLine.getUri,
            request.getAllHeaders.toList.map(h => (h.getName, h.getValue)),
            body
          )
          Logger.debug(s"Received request:\n$flatRequest")
          flatRequest
        }
        .onErrorRestartIf {
          case ioe: IOException if ioe.getMessage == "Stream closed" =>
            Logger.debug("Caught stream closed exception")
            false
          case cce: ConnectionClosedException if cce.getMessage == "Client closed connection" =>
            Logger.debug("Caught client closed exception")
            false
          case t: Throwable =>
            Logger.error(s"Unknown error parsing request", t)
            true
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
        Logger.info(s"Running on port ${Console.CYAN}$port${Console.RESET}")
      })
      .doOnFinish { _ =>
        Task.now(serverSocket.close)
      }
      .runAsync
  }
}

