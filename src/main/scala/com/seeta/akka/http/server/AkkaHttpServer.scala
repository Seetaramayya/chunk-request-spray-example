package com.seeta.akka.http.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError, OK}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, RejectionHandler, ValidationRejection}
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import com.seeta.akka.http.server.Configuration.MAX_ALLOWED
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object AkkaHttpServer {
  def apply(host: String, port: Int)(implicit system: ActorSystem, mat: ActorMaterializer): AkkaHttpServer = new AkkaHttpServer(host, port)
}

class AkkaHttpServer(host: String, port: Int)(implicit system: ActorSystem, mat: ActorMaterializer) {
  private val log = LoggerFactory.getLogger(getClass)
  implicit private val ec = system.dispatcher
  implicit val timeout = Timeout(1.second)
  private def isAllowed(value: Long):Boolean = value <= MAX_ALLOWED && value > 0
  private def validateInputSize: Directive1[Long] = headerValueByName("X-Content-Length").flatMap { headerValue =>
    log.info("Server received 'X-Content-Length' value is '{}'", headerValue)
    Try(headerValue.toLong) match {
      case Success(value) if isAllowed(value) =>
        log.info("Passing the request further")
        provide(value)
      case Success(value)     =>
        reject(ValidationRejection(s"Max allowed: $MAX_ALLOWED, actual: $value"))
      case Failure(exception) =>
        log.info("Rejecting the request because of the error", exception)
        reject(ValidationRejection(s"Converting content length string to long failed", Some(exception)))
    }
  }

  private val rejectionHandler = RejectionHandler.newBuilder().handle {
    case ValidationRejection(message, _) =>
      log.info("Rejecting the request with {}", message)
      complete(BadRequest -> message)
  }.result()

  private val route = path("example") {
    put {
      handleRejections(rejectionHandler) {
        validateInputSize { _ =>
          log.info("SHOULD NOT SEE THIS IN LOGS, IN COMING REQUEST SHOULD BE REJECTED")
          extractRequest { request =>
            val wholeBodyF = request.entity.dataBytes.runFold(ByteString.empty)(_ ++ _)
            onComplete(wholeBodyF) {
              case Success(v) => complete(OK -> """ {"status": "ok"} """)
              case Failure(t) => complete(InternalServerError -> t.getMessage)
            }
          }
        }
      }
    } ~ get {
      complete(OK -> "YES")
    }
  }

  private lazy val serverBindF: Future[Http.ServerBinding] = Http().bindAndHandle(route, host, port)
  serverBindF.onComplete {
    case Success(binding) =>
      log.info("*" * 40)
      log.info("Akka Http {}", binding)
      log.info("*" * 40)
    case Failure(t) => log.error("NOT ABLE TO BIND", t)
      system.terminate()
  }

  def start: Future[Http.ServerBinding] = serverBindF

}
