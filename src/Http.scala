// Copyright 2016 flat authors

package flat

import org.apache.http.{HttpVersion, ProtocolVersion}
import io.circe.Json

sealed trait HttpMethod
final case object GET extends HttpMethod
final case object POST extends HttpMethod
final case object PUT extends HttpMethod
final case object HEAD extends HttpMethod
final case object DELETE extends HttpMethod
final case object TRACE extends HttpMethod
final case object OPTIONS extends HttpMethod
final case object PATCH extends HttpMethod

case class HttpRequest(
  version: ProtocolVersion,
  method: HttpMethod,
  uri: String,
  headers: List[(String,String)],
  bodyOpt: Option[String] = None
)

class HttpResponse(
  val version: ProtocolVersion,
  val code: Int,
  val reason: String,
  val headers: List[(String,String)],
  val bodyOpt: Option[String] = None
)

object HttpResponse {
  def create(code: Int, reason: String, contentOpt: Option[Any], extraHeaders: List[(String,String)]): HttpResponse = {
    val (body, contentHeader) = contentOpt match {
      case Some(json: Json) =>
        (Some(json.noSpaces), Map("Content-Type" -> "application/json; charset=utf-8"))
      case Some(text: String) =>
        (Some(text), Map("Content-Type" -> "text/plain; charset=utf-8"))
      case None =>
        (None, Map.empty[String,String])
      case _ =>
        throw new RuntimeException("Encountered unknown content type")
    }
    new HttpResponse(HttpVersion.HTTP_1_1, code, reason, extraHeaders ++ contentHeader.toList, body)
  }
}

object OK {
  def apply(content: Any, extraHeaders: List[(String,String)] = List()) = HttpResponse.create(200, "OK", Some(content), extraHeaders)
}
object BadRequest {
  def apply(content: Any, extraHeaders: List[(String,String)] = List()) = HttpResponse.create(400, "Bad Request", Some(content), extraHeaders)
}
object NotFound {
  def apply(content: Any, extraHeaders: List[(String,String)] = List()) = HttpResponse.create(404, "Not Found", Some(content), extraHeaders)
}
object InternalServerError {
  def apply(content: Any, extraHeaders: List[(String,String)] = List()) = HttpResponse.create(500, "Internal Server Error", Some(content), extraHeaders)
}

