// Copyright 2016 flat authors

package flat

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

// TODO - headers
sealed trait HttpResponse {
  val code: Int
  val reason: String
  val body: Any
  def getBytes: Array[Byte] = s"HTTP/1.1 $code $reason\r\n\r\n${body.toString}".map(_.toByte).toArray
}
final case class OK(body: Any, code: Int = 200, reason: String = "OK") extends HttpResponse
final case class NotFound(body: Any, code: Int = 404, reason: String = "Not Found") extends HttpResponse
final case class InternalServerError(body: Any, code: Int = 500, reason: String = "Internal Server Error") extends HttpResponse

// TODO - content
case class HttpRequest(method: HttpMethod, uri: String, headers: List[(String,String)]) {

}

