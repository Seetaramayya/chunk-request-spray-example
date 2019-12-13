package com.seeta.akka.http.server

import akka.actor.ActorSystem
import akka.io.IO
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

object Configuration {
  val AKKA_HTTP_SERVER_PORT = 9595
  val SPRAY_SERVER_PORT = 9191
  val HOST = "localhost"
  val MAX_ALLOWED = 150000 // 179904
}

trait SLF4JLogger {
  val log: Logger = LoggerFactory.getLogger(getClass)
}

object HttpServer extends App {
  import Configuration._
  implicit private val system = ActorSystem("http-server", ConfigFactory.load())
  implicit private val materializer = ActorMaterializer()
  implicit val timeout = Timeout(5.seconds)

  //Akka Http Server Starting ...
  AkkaHttpServer(HOST, AKKA_HTTP_SERVER_PORT).start

  //Spray Http Server Starting ...
  val service = system.actorOf(SprayHttpServer.props(), "spray-server")
  IO(Http) ? Http.Bind(service, interface = HOST, port = SPRAY_SERVER_PORT)
}