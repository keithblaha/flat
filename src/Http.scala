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

case class HttpRequest(method: HttpMethod, uri: String, headers: List[(String,String)], body: Option[String] = None)

abstract class HttpResponse(val code: Int, val reason: String, val finalHeaders: List[(String,String)], val bodyOpt: Option[String] = None)

final case class OK(body: String, headers: List[(String,String)] = List()) extends HttpResponse(200, "OK", headers, Some(body))
final case class BadRequest(body: String, headers: List[(String,String)] = List()) extends HttpResponse(400, "Bad Request", headers, Some(body))
final case class NotFound(body: String, headers: List[(String,String)] = List()) extends HttpResponse(404, "Not Found", headers, Some(body))
final case class InternalServerError(body: String, headers: List[(String,String)] = List()) extends HttpResponse(500, "Internal Service Error", headers, Some(body))

