package service
import CityUtil.{CityEntry, readCities}
import io.etcd.jetcd.{ByteSequence, Client}
import io.grpc.ManagedChannelBuilder
import scalacache._
import scalacache.memcached._
import scalacache.modes.try_._
import service.StubUtil.createStub
import service.geoService.GeoServiceGrpc.GeoServiceStub
import service.geoService._
import scalacache.serialization.binary._
import service.cache.CachedEntry

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.io.Source
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.language.postfixOps

//case class CachedEntry(
//    country: String,
//    state: String
//)

case class GeoService(url: String, leaseId: Long)
    extends GeoServiceGrpc.GeoService {

  val worldCities: List[CityEntry] = readCities()

  private val client: Client = Client
    .builder()
    .endpoints("http://localhost:2379")
    .build

  val kvClient = client.getKVClient

  val electionClient = client.getElectionClient

  var isMaster = false

  Future {
    electionClient
      .campaign(
        ByteSequence.from("service/geo/election".getBytes()),
        leaseId,
        ByteSequence.from(url.getBytes)
      )
      .thenRun { () =>
        isMaster = true
      }
  }

  def getCountries(
      getCountriesRequest: GetCountriesRequest
  ): Future[GetCountriesReply] =
    Future {
      val countries = worldCities.map(_.country).distinct
      GetCountriesReply(countries)
    }

  def getStatesOfCountry(
      getStatesOfCountryRequest: GetStatesOfCountryRequest
  ): Future[GetStatesOfCountryReply] =
    Future {
      val states =
        worldCities
          .filter(_.country == getStatesOfCountryRequest.country)
          .map(_.state)
          .distinct
      GetStatesOfCountryReply(states)
    }

  def getCitiesOfState(
      getCitiesOfStateRequest: GetCitiesOfStateRequest
  ): Future[GetCitiesOfStateReply] =
    Future {
      val cities = worldCities
        .filter(e =>
          e.country == getCitiesOfStateRequest.country && e.state == getCitiesOfStateRequest.state
        )
        .map(_.city)
        .distinct
      GetCitiesOfStateReply(cities)
    }

  def getLocationByIp(
      getLocationByIpRequest: GetLocationByIpRequest
  ): Future[GetLocationByIpReply] = {

    Future {
      val ip = getLocationByIpRequest.ip

      val cacheTtl = kvClient
        .get(
          ByteSequence.from(
            "config/services/geo/cache/ttl",
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

      val cacheUrl = kvClient
        .get(
          ByteSequence.from(
            "config/services/geo/cache/url",
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
        }
        .getOrElse("localhost:11211")

      implicit val memcachedCache: Cache[Array[Byte]] = MemcachedCache(
        cacheUrl
      )

      if (get(ip).get.isDefined || isMaster) {
        val bytes =
          caching(ip)(ttl = Option(Duration(cacheTtl, TimeUnit.SECONDS))) {

            val url = s"http://ipwhois.app/json/$ip"
            val source = Source.fromURL(url)
            val json = ujson.read(source.mkString)
            source.close
            val country = json("country").str
            val state = json("region").str

            println(s"Cached IP: $ip with country $country and state $state")

            CachedEntry(country, state).toByteArray
          }.get
        val CachedEntry(country, state, _) = CachedEntry.parseFrom(bytes)
        GetLocationByIpReply(country, state)
      } else {
        println("searching master")

        val Array(masterIp, masterPort) = electionClient
          .leader(
            ByteSequence.from("service/geo/election".getBytes())
          )
          .get
          .getKv
          .getValue
          .toString(Charset.defaultCharset())
          .split(":")

        println(s"masterPort $masterPort")

        val future = createStub(masterIp, masterPort.toInt).getLocationByIp(
          getLocationByIpRequest
        )
        Await.result(future, 2 seconds)
      }
    }
  }

  def healthCheck(healthCheckReq: HealthCheckReq): Future[HealthCheckRes] = {
    println("healthCheck")
    Future.successful(HealthCheckRes())
  }

}

object StubUtil {

  def createStub(ip: String, port: Int): GeoServiceStub = {
    val builder = ManagedChannelBuilder.forAddress(ip, port)
    builder.usePlaintext()
    val channel = builder.build()
    GeoServiceGrpc.stub(channel)
  }
}
