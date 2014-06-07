package akka.scalajs.wsserver

import akka.actor._
import akka.scalajs.wscommon.AbstractProxy

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent.Akka

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._

object ActorWebSocket {
  def apply(f: RequestHeader => Future[Any]) = {
    WebSocket.async[JsValue] { request =>
      f(request).map(_.asInstanceOf[(Iteratee[JsValue, Unit], Enumerator[JsValue])])
    }
  }

  def actorForWebSocketHandler(entryPointRef: ActorRef)(
      implicit context: ActorRefFactory): (Iteratee[JsValue, Unit], Enumerator[JsValue]) = {

    val (out, channel) = Concurrent.broadcast[JsValue]
    val serverProxy = context.actorOf(
        Props(classOf[ServerProxy], channel, entryPointRef))

    // Forward incoming messages as messages to the proxy
    val in = Iteratee.foreach[JsValue] {
      msg => serverProxy ! AbstractProxy.IncomingMessage(msg)
    }.map {
      _ => serverProxy ! AbstractProxy.ConnectionClosed
    }

    (in, out)
  }
}
