name := "chunk-request-spray-example"

version := "0.1"

scalaVersion := "2.11.12"
val akkaVersion = "2.4.20"
val akkaHttpVersion = "10.0.13"
val scalaTestVersion = "3.0.8"
val sprayVersion = "1.3.4"
val sprayJsonVersion = "1.3.5"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"           % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-stream"         % akkaVersion,
  "io.spray"          %% "spray-client"        % sprayVersion,
  "io.spray"          %% "spray-routing"       % sprayVersion,
  "io.spray"          %% "spray-can"           % sprayVersion,
  "com.typesafe.akka" %% "akka-slf4j"          % akkaVersion,
  "io.spray"          %% "spray-json"          % sprayJsonVersion,
  "io.netty"          %  "netty-all"           % "4.1.43.Final",
  "ch.qos.logback"    %  "logback-classic"     % "1.2.3",
  "ch.qos.logback"    %  "logback-core"        % "1.2.3",
  "org.slf4j"         %  "slf4j-api"           % "1.7.22",
  "com.typesafe.akka" %% "akka-testkit"        % akkaVersion           % Test,
  "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpVersion       % Test,
  "org.scalatest"     %% "scalatest"           % scalaTestVersion      % Test
)

mainClass in (Compile, run) := Some("com.seeta.akka.http.server.HttpServer")