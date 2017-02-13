// Copyright 2016 flat authors

package flat.utils

import flat._
import java.nio.charset.StandardCharsets
import monix.execution.Scheduler.Implicits.global
import monix.eval.Task
import org.apache.http.HttpEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{
  HttpDelete, HttpGet, HttpHead, HttpOptions,
  HttpPatch, HttpPost, HttpPut, HttpTrace
}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import scala.collection.JavaConversions._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object HttpClient {
  private val client = HttpClients.createDefault

  private def doRequest(method: HttpMethod, url: String, formValues: List[(String, String)] = List()): HttpResponse = {
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

    if (request.isInstanceOf[HttpPost] && formValues.size > 0) {
      val nvps = formValues.map { case (n, v) =>
        new BasicNameValuePair(n, v)
      }
      request.asInstanceOf[HttpPost].setEntity(new UrlEncodedFormEntity(nvps, StandardCharsets.UTF_8));
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

  def delete(url: String): Task[Try[HttpResponse]] = Task { doRequest(DELETE, url) }.materialize
  def get(url: String): Task[Try[HttpResponse]] = Task { doRequest(GET, url) }.materialize
  def head(url: String): Task[Try[HttpResponse]] = Task { doRequest(HEAD, url) }.materialize
  def options(url: String): Task[Try[HttpResponse]] = Task { doRequest(OPTIONS, url) }.materialize
  def patch(url: String): Task[Try[HttpResponse]] = Task { doRequest(PATCH, url) }.materialize
  def post(url: String, formValues: List[(String, String)] = List()): Task[Try[HttpResponse]] = Task { doRequest(POST, url, formValues) }.materialize
  def put(url: String): Task[Try[HttpResponse]] = Task { doRequest(PUT, url) }.materialize
  def trace(url: String): Task[Try[HttpResponse]] = Task { doRequest(TRACE, url) }.materialize

  def deleteSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] = Await.result(delete(url).runAsync, timeout)
  def getSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(get(url).runAsync, timeout)
  def headSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(head(url).runAsync, timeout)
  def optionsSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(options(url).runAsync, timeout)
  def patchSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(patch(url).runAsync, timeout)
  def postSync(url: String, formValues: List[(String, String)] = List(), timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(post(url, formValues).runAsync, timeout)
  def putSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(put(url).runAsync, timeout)
  def traceSync(url: String, timeout: Duration = 10.seconds): Try[HttpResponse] =  Await.result(trace(url).runAsync, timeout)
}
