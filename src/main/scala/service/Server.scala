package service

import io.grpc.ServerBuilder
import service.geoService.GeoServiceGrpc
import controller.GeoController
import io.etcd.jetcd.{ByteSequence, Client, KV}

import java.net.InetAddress
import java.sql.SQLClientInfoException
import scala.concurrent.ExecutionContext
import scala.util.Random

object Server extends App {
  private val port = 50_003
  private val builder = ServerBuilder
    .forPort(port)
  builder.addService(
    GeoServiceGrpc.bindService(GeoController(), ExecutionContext.global)
  )
  private val server = builder.build()

  server.start()
  val client: Client = Client.builder().build()
  val kvClient: KV = client.getKVClient
  val randomKey: String = Random.nextString(5)
  val localhost: String = InetAddress.getLocalHost.getHostAddress

  val key: ByteSequence = ByteSequence.from(s"service/geo/$randomKey".getBytes());
  val value: ByteSequence = ByteSequence.from(s"$localhost:$port".getBytes());

  kvClient.put(key, value).get();

  println(s"Listening on port $port")
  server.awaitTermination()

}
