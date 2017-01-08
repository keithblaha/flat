// Copyright 2016 flat authors

package flat

import flat.utils.HttpClient
import org.apache.http.HttpVersion
import org.scalatest._
import scala.util.{Failure, Success}

class ServerSpec extends FlatSpec with Matchers with BeforeAndAfter with FlatApp {
  val port = 9001
  val rootPath = "/"
  val rootUrl = s"http://localhost:$port$rootPath"
  val rootResponse = Ok("cool")

  after {
    app.stop
    app.clear
  }

  "Server" should "respond to GET routes using the handler" in {
    app.get(rootPath) { request =>
      rootResponse
    }
    app.start(port)

    HttpClient.getSync(rootUrl) match {
      case Success(response) =>
        response.version shouldEqual HttpVersion.HTTP_1_1
        response.code shouldEqual rootResponse.code
        response.reason shouldEqual rootResponse.reason
        response.headers should contain ("Content-Type" -> "text/plain; charset=utf-8")
        response.bodyOpt shouldEqual rootResponse.bodyOpt
      case Failure(e) =>
        throw e
    }
  }

  it should "respond to HEAD with GET routes using the handler but without sending body" in {
    app.get(rootPath) { request =>
      rootResponse
    }
    app.start(port)

    HttpClient.headSync(rootUrl) match {
      case Success(response) =>
        response.version shouldEqual HttpVersion.HTTP_1_1
        response.code shouldEqual rootResponse.code
        response.reason shouldEqual rootResponse.reason
        response.headers should contain ("Content-Type" -> "text/plain; charset=utf-8")
        response.bodyOpt shouldEqual None
      case Failure(e) =>
        throw e
    }
  }

  it should "respond to TRACE with method not allowed" in {
    app.get(rootPath) { request =>
      rootResponse
    }
    app.start(port)

    val expectedResponse = MethodNotAllowed("")

    HttpClient.traceSync(rootUrl) match {
      case Success(response) =>
        response.version shouldEqual HttpVersion.HTTP_1_1
        response.code shouldEqual expectedResponse.code
        response.reason shouldEqual expectedResponse.reason
        response.headers should contain ("Content-Type" -> "text/plain; charset=utf-8")
      case Failure(e) =>
        throw e
    }
  }
}

