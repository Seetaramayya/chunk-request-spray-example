package com.seeta.akka.http.server


import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.seeta.akka.http.server.Configuration.MAX_ALLOWED
import spray.http.StatusCodes._
import spray.routing._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object SprayHttpServer {
  def props() = Props(new SprayHttpServer())
}

class SprayHttpServer extends Actor with ActorLogging with Directives with HttpServiceBase {
  implicit private val ec = context.dispatcher
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
        log.warning("Rejecting the request because of the error", exception)
        reject(ValidationRejection(s"Converting content length string to long failed", Some(exception)))
    }
  }

  private val rejectionHandler = RejectionHandler.apply {
    case ValidationRejection(message, _) :: _ =>
      log.warning("Rejecting the request with {}", message)
      complete(BadRequest -> message)
  }

  private val route = path("example") {
    put {
      handleRejections(rejectionHandler) {
        validateInputSize { _ =>
          log.info("SHOULD NOT SEE THIS IN LOGS, IN COMING REQUEST SHOULD BE REJECTED")
          complete(OK -> """ {"status": "ok"} """)
        }
      }
    } ~ get {
      complete(OK -> "YES")
    }
  }

  override def receive: Receive = runRoute(route)
}
