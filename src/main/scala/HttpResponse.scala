// Copyright 2016 flat authors

package flat

case class HttpStatus(code: Int, reason: String)
case object HttpStatus {
  val OK = HttpStatus(200, "OK")
  val InternalServerError = HttpStatus(500, "Internal Server Error")
}

// TODO - headers
case class HttpResponse(status: HttpStatus, content: Any) {
  def getBytes: Array[Byte] = s"HTTP/1.1 ${status.code} ${status.reason}\r\n\r\n${content.toString}".map(_.toByte).toArray
}

