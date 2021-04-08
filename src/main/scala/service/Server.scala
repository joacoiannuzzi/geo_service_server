package service

import io.grpc.ServerBuilder
import service.geoService.GeoServiceGrpc

import scala.concurrent.ExecutionContext

object Server extends App {
  private val port = 50_004
  private val builder = ServerBuilder
    .forPort(port)
  builder.addService(
    GeoServiceGrpc.bindService(GeoService(), ExecutionContext.global)
  )
  private val server = builder.build()

  server.start()
  println(s"Listening on port $port")
  server.awaitTermination()
}
