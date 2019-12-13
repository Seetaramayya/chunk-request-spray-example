package com.seeta.akka.http.client

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URLEncoder

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status}
import akka.util.Timeout
import com.seeta.akka.http.server.Configuration.{AKKA_HTTP_SERVER_PORT, SPRAY_SERVER_PORT, HOST}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import spray.http.{ChunkedMessageEnd, HttpCharsets, MessageChunk}

import scala.util.{Failure, Success}
import akka.io.{IO, Tcp}
import akka.pattern.ask
import com.seeta.akka.http.client.SprayHttpChunkedExample.StartSending
import spray.can.Http
import spray.can.Http._
import spray.http.HttpHeaders._
import spray.http.{HttpRequest, HttpResponse}
import spray.httpx.RequestBuilding

import scala.concurrent.Future
import scala.concurrent.duration._

object SprayHttpChunkedExample {
  def props(data: Array[Byte], request: HttpRequest): Props = Props(new SprayHttpChunkedExample(data, request))
  case object NextChunk extends Tcp.Event

  case class StartSending(host: String, port: Int)
}
class SprayHttpChunkedExample(val inMemoryData: Array[Byte], val request: HttpRequest) extends Actor with ActorLogging {
  import SprayHttpChunkedExample.NextChunk
  private val chunkSize = 1024
  private val input: InputStream = new ByteArrayInputStream(inMemoryData)
  private val buffer = new Array[Byte](chunkSize)
  private implicit val system = context.system
  private var counter = 1

  private def start: Receive = {
    case StartSending(host, port) =>
      IO(Http) ! Http.Connect(host, port)
      context.become(waitingForConnection(sender()))
  }

  private def waitingForConnection(respondTo: ActorRef): Receive = {
    case c @ Http.Connected(remote, local) =>
      log.info("Connected to {}", remote)
      val connection = sender()
      log.info("Connection is {}", connection)
      connection ! Register(self)
      log.debug("Connected. About to send first data.")
      sender ! request.chunkedMessageStart.withAck(NextChunk)
      context.become(dataCommunication(connection, respondTo))
  }

  override def receive: Receive = start

  private def nextChunk(connection: ActorRef): Unit = {
    val size = readNextChunk
    if (size > 0) {
      val chunk = buffer.take(size)
//      log.info("Sending data chunk {}", counter)
      connection ! MessageChunk(chunk).withAck(NextChunk)
      counter += 1
    } else {
      log.info("Nothing to send")
      connection ! ChunkedMessageEnd
    }
  }



  private def readNextChunk: Int = input.read(buffer)

  private def dataCommunication(connection: ActorRef, respondTo: ActorRef): Receive = connectionClose(respondTo) orElse {
    case NextChunk              => nextChunk(sender())
    case response: HttpResponse =>
      val responseBody = response.entity.data.asString(HttpCharsets.`UTF-8`)
      log.info("Overall processed chunks are {}", counter)
      log.info("Raw response '{}'", responseBody)
      respondTo ! responseBody
      context.stop(self)
    case c @ Http.CommandFailed(Http.Connect(address, _, _, _, _)) =>
      log.error("Http.CommandFailed failed {}", c)
    case "close" => connection ! Close
  }

  private def connectionClose(respondTo: ActorRef): Receive = {
    case Closed     =>
      respondTo ! "Done"
      context.stop(self)
    case PeerClosed =>
      respondTo ! "Done"
      context.stop(self)
    case Aborted =>
      respondTo ! "Done"
      context.stop(self)
    case ConfirmedClosed =>
      respondTo ! "Done"
      context.stop(self)
    case e @ ErrorClosed(cause) =>
      log.error("*" * 50)
      log.error("Connection ErrorClosed('{}')", cause)
      log.error("*" * 50)
      respondTo ! Status.Failure(new IllegalStateException(s"connection closed with reason '$cause'"))
      context.stop(self)
  }
}

object HttpClientApp extends App {
  //------------- CHANGE FOLLOWING PORT ---------------
  private val host = HOST
//  private val port = AKKA_HTTP_SERVER_PORT
  private val port = SPRAY_SERVER_PORT
  //---------------------------------------------------

  private val fileName = "seeta.png"
  private val log = LoggerFactory.getLogger(getClass)
  implicit private val system = ActorSystem("spray-test-client", ConfigFactory.load())
  implicit private val ec = system.dispatcher
  implicit val timeout = Timeout(10.second)
  private val inputStream: InputStream = getClass.getClassLoader.getResourceAsStream(fileName)
  log.info(s"Started...")

  if (inputStream == null) {
    log.info("Input stream is null")
    system.terminate()
  } else {
    val data = Stream.continually(inputStream.read).takeWhile(_ != -1).map(_.toByte).toArray

    log.info(s"Data length is '{}', size is '{}'", data.length, data.size)

    val headers = List(
      `Content-Disposition`("inline", Map("filename*" -> URLEncoder.encode(fileName, "UTF-8"))),
      Connection("close"),
      RawHeader("X-Content-Length", "10000000")
    )
    val request = RequestBuilding.Put("/example").withHeaders(headers)
    val futureResponses = (1 to 50).map(_ => system.actorOf(SprayHttpChunkedExample.props(data, request)) ? StartSending(host, port))

    Future.sequence(futureResponses).onComplete {
      case Success(_) => system.terminate()
      case Failure(t) =>
        t.printStackTrace()
        log.info("&" * 40)
        log.info("ERROR ", t)
        log.info("&" * 40)
        system.terminate()
    }
  }
}
