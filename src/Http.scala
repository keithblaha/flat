// Copyright 2016 flat authors

package flat

import java.nio.charset.StandardCharsets

sealed trait HttpMethod
final case object GET extends HttpMethod
final case object POST extends HttpMethod
final case object PUT extends HttpMethod
final case object HEAD extends HttpMethod
final case object DELETE extends HttpMethod
final case object TRACE extends HttpMethod
final case object OPTIONS extends HttpMethod
final case object PATCH extends HttpMethod
final case object CONNECT extends HttpMethod

case class HttpRequest(method: HttpMethod, uri: String, headers: List[(String,String)], body: Option[String] = None) {
}

// TODO - headers
abstract class HttpResponse(
  code: Int,
  reason: String,
  bodyOpt: Option[Any]
) {
  def getBytes: Array[Byte] = s"HTTP/1.1 $code $reason\r\n\r\n${bodyOpt.map(_.toString).getOrElse("")}".getBytes(StandardCharsets.UTF_8)
}

final case class OK(body: Any) extends HttpResponse(200, "OK", Some(body))
final case class BadRequest(body: Any) extends HttpResponse(400, "Bad Request", Some(body))
final case class NotFound(body: Any) extends HttpResponse(404, "Not Found", Some(body))
final case class InternalServerError(body: Any) extends HttpResponse(500, "Internal Service Error", Some(body))

