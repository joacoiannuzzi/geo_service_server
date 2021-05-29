package service

import com.google.common.base.Charsets.UTF_8
import io.etcd.jetcd.lease.LeaseKeepAliveResponse
import io.etcd.jetcd.options.PutOption
import io.etcd.jetcd.{ByteSequence, Client, KV}
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import service.geoService.GeoServiceGrpc

import java.net.InetAddress
import java.nio.charset.Charset
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Random

object Server extends App {
  private val port = 50_003

  val client: Client =
    Client.builder().endpoints("http://127.0.0.1:2379").build()
  val kvClient: KV = client.getKVClient

  val randomKey: String = Random.nextString(5)
  val localhost: String = InetAddress.getLocalHost.getHostAddress

  val ttl = kvClient
    .get(
      ByteSequence.from(
        "config/services/geo/lease/ttl",
        Charset.defaultCharset()
      )
    )
    .get()
    .getKvs
    .asScala
    .headOption
    .map { o =>
      o.getValue
        .toString(Charset.defaultCharset())
        .toLong
    }
    .getOrElse(2L)

  val leaseClient = client.getLeaseClient
  val leaseId = leaseClient.grant(ttl).get.getID

//  leaseClient.timeToLive()

  leaseClient.keepAlive(
    leaseId,
    new StreamObserver[LeaseKeepAliveResponse] {
      override def onNext(value: LeaseKeepAliveResponse): Unit = {}

      override def onError(t: Throwable): Unit = {}

      override def onCompleted(): Unit = {}
    }
  )

  val key: ByteSequence =
    ByteSequence.from(s"service/geo/$randomKey".getBytes())
  val value: ByteSequence = ByteSequence.from(s"$localhost:$port".getBytes())

  kvClient
    .put(key, value, PutOption.newBuilder().withLeaseId(leaseId).build())
    .get()

  private val builder = ServerBuilder
    .forPort(port)
  builder.addService(
    GeoServiceGrpc.bindService(
      GeoService(port, leaseId),
      ExecutionContext.global
    )
  )
  private val server = builder.build()

  server.start()

  println(s"Listening on port $port")
  server.awaitTermination()

}
