package service
import CityUtil.{CityEntry, readCities}
import io.etcd.jetcd.{ByteSequence, Client}
import io.grpc.ManagedChannelBuilder
import scalacache.memcached._
import scalacache.modes.try_.mode
import scalacache.serialization.binary.anyRefBinaryCodec
import scalacache._
import service.StubUtil.createStub
import service.geoService.GeoServiceGrpc.GeoServiceStub
import service.geoService._

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationInt}
import scala.io.Source
import scala.language.postfixOps

case class GeoService(port: Int, leaseId: Long)
    extends GeoServiceGrpc.GeoService {

  val electionClient = Client
    .builder()
    .endpoints("http://localhost:2379")
    .build()
    .getElectionClient

  var isMaster = false

  val worldCities: List[CityEntry] = readCities()

  Future {
    electionClient
      .campaign(
        ByteSequence.from("service/geo/election".getBytes()),
        leaseId,
        ByteSequence.from(port.toString.getBytes())
      )
      .thenAcceptAsync { _ =>
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

  case class CachedEntry(
      country: String,
      state: String
  )

  def getLocationByIp(
      getLocationByIpRequest: GetLocationByIpRequest
  ): Future[GetLocationByIpReply] = {

    Future {

      implicit val memcachedCache: Cache[CachedEntry] = MemcachedCache(
        "localhost:11211"
      )
      println("Enter function")
      val ip = getLocationByIpRequest.ip

      if (get(ip).get.isDefined || isMaster) {
        val CachedEntry(country, state) =
          caching(ip)(ttl = Option(Duration(15.toLong, TimeUnit.SECONDS))) {
            println("ENTER HERE")

            val url = s"http://ipwhois.app/json/$ip"
            val source = Source.fromURL(url)
            println("Before json read")
            val json = ujson.read(source.mkString)
            println("Before close")
            source.close
            val country = json("country").str
            val state = json("region").str

            println(s"Cached IP: $ip with country $country and state $state")

            CachedEntry(country, state)
          }.get
        println("OUTSIDE")
        GetLocationByIpReply(country, state)
      } else {
        println("searching master")
        val masterPort = electionClient
          .leader(
            ByteSequence.from("service/geo/election".getBytes())
          )
          .get
          .getKv
          .getValue
          .toString(Charset.defaultCharset())
          .toInt

        println(s"masterPort $masterPort")

        val future = createStub("localhost", masterPort).getLocationByIp(
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
