package service
import CityUtil.{CityEntry, readCities}
import service.geoService._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

case class GeoService() extends GeoServiceGrpc.GeoService {

  private val worldCities: List[CityEntry] = readCities()

  private val cachedIp: mutable.Map[String, (String, String)] = mutable.Map()

  override def getCountries(
      request: GetCountriesRequest
  ): Future[GetCountriesReply] =
    Future {
      val countries = worldCities.map(_.country).distinct
      GetCountriesReply(countries)
    }

  override def getStatesOfCountry(
      request: GetStatesOfCountryRequest
  ): Future[GetStatesOfCountryReply] =
    Future {
      val states =
        worldCities.filter(_.country == request.country).map(_.state).distinct
      GetStatesOfCountryReply(states)
    }

  override def getCitiesOfState(
      request: GetCitiesOfStateRequest
  ): Future[GetCitiesOfStateReply] =
    Future {
      val cities = worldCities
        .filter(e => e.country == request.country && e.state == request.state)
        .map(_.city)
        .distinct
      GetCitiesOfStateReply(cities)
    }

  override def getLocationByIp(
      request: GetLocationByIpRequest
  ): Future[GetLocationByIpReply] =
    Future {
      val ip = request.ip
      val (country, state) = cachedIp.getOrElse(
        ip, {
          val url = s"http://ipwhois.app/json/$ip"
          val source = Source.fromURL(url)
          val str = source.mkString
          source.close
          val json = ujson.read(str)
          val country = json("country").str
          val state = json("region").str
          val tuple = (country, state)
          cachedIp.put(ip, tuple)
          println(s"Cached IP: $ip with country $country and state $state")
          tuple
        }
      )
      GetLocationByIpReply(country, state)
    }

  override def healthCheck(request: HealthCheckReq): Future[HealthCheckRes] = {
    println("healthCheck")
    Future.successful(HealthCheckRes())
  }
}
