# Spray Chunked Request sender

This is reproduction scenario.

This project 3 things 

1. `com.seeta.akka.http.server.HttpServer` is a main class which starts two servers (`sbt run`)
  - `AkkaHttpServer` starts at port 9595. This port is configured in `com.seeta.akka.http.server.Configuration`
  - `SprayHttpServer` starts at port 9191. This port is configured in `com.seeta.akka.http.server.Configuration`

2. `com.seeta.akka.http.client.HttpClientApp` (line `112- 117` configures which port) start connecting to one of the above server and upload big file, __should be rejected__, 
   but sometimes fails with `ErrorClosed('Broken Pipe')` when it connects to Akka Http Server.