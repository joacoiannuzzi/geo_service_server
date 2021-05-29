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
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.util.Random

object Server extends App {
  private val port = 50_004

  val randomKey: String = Random.nextString(5)
  val localhost: String = InetAddress.getLocalHost.getHostAddress

  private val url = s"$localhost:$port"

  val client: Client =
    Client.builder().endpoints("http://127.0.0.1:2379").build()
  val kvClient: KV = client.getKVClient

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
    .getOrElse(5L)

  val keepAliveTime = kvClient
    .get(
      ByteSequence.from(
        "config/services/geo/lease/keepAliveTime",
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
    .getOrElse(4L)

  val leaseClient = client.getLeaseClient

  val leaseId = leaseClient.grant(ttl).get.getID
  println("leaseId " + leaseId)

  val ex = new ScheduledThreadPoolExecutor(1)
  val task = new Runnable {
    def run() = {
      leaseClient.keepAliveOnce(leaseId)
//      println("Renewed lease")
    }
  }
  val f =
    ex.scheduleAtFixedRate(task, keepAliveTime, keepAliveTime, TimeUnit.SECONDS)
//  f.cancel(false)

  val key: ByteSequence =
    ByteSequence.from(s"service/geo/$randomKey".getBytes())

  val value: ByteSequence = ByteSequence.from(url.getBytes())

  kvClient
    .put(key, value, PutOption.newBuilder().withLeaseId(leaseId).build())
    .get()

  private val builder = ServerBuilder
    .forPort(port)
  builder.addService(
    GeoServiceGrpc.bindService(
      GeoService(url, leaseId),
      ExecutionContext.global
    )
  )
  private val server = builder.build()

  server.start()

  println(s"Listening on $url")
  server.awaitTermination()
}
