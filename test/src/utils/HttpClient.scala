// Copyright 2016 flat authors

package flat.utils

import flat._
import java.nio.charset.StandardCharsets
import org.apache.http.HttpEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.client.methods.{
  HttpDelete, HttpGet, HttpHead, HttpOptions,
  HttpPatch, HttpPost, HttpPut, HttpTrace
}
import org.apache.http.util.EntityUtils

object HttpClient {
  private val client = HttpClients.createDefault

  private def doRequest(method: HttpMethod, url: String): HttpResponse = {
    val request = method match {
      case DELETE => new HttpDelete(url)
      case GET => new HttpGet(url)
      case HEAD => new HttpHead(url)
      case OPTIONS => new HttpOptions(url)
      case PATCH => new HttpPatch(url)
      case POST => new HttpPost(url)
      case PUT => new HttpPut(url)
      case TRACE => new HttpTrace(url)
    }

    val response = client.execute(request)

    val version = response.getStatusLine.getProtocolVersion
    val code = response.getStatusLine.getStatusCode
    val reason = response.getStatusLine.getReasonPhrase
    val headers = response.getAllHeaders.toList.map(h => (h.getName, h.getValue))
    val content = response.getEntity match {
      case entity: HttpEntity if entity != null =>
        Some(EntityUtils.toString(entity, StandardCharsets.UTF_8))
      case _ =>
        None
    }

    response.close

    new HttpResponse(version, code, reason, headers, content)
  }

  def delete(url: String) = doRequest(DELETE, url)
  def get(url: String) = doRequest(GET, url)
  def head(url: String) = doRequest(HEAD, url)
  def options(url: String) = doRequest(OPTIONS, url)
  def patch(url: String) = doRequest(PATCH, url)
  def post(url: String) = doRequest(POST, url)
  def put(url: String) = doRequest(PUT, url)
  def trace(url: String) = doRequest(TRACE, url)
}

