package service
import CityUtil.{CityEntry, readCities}
import service.geoService._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

//case class GeoService() extends GeoServiceGrpc.GeoService {
case class GeoService() {

  private val worldCities: List[CityEntry] = readCities()

  private val cachedIp: mutable.Map[String, (String, String)] = mutable.Map()

   def getCountries: Future[GetCountriesReply] =
    Future {
      val countries = worldCities.map(_.country).distinct
      GetCountriesReply(countries)
    }

   def getStatesOfCountry(
      aCountry: String
  ): Future[GetStatesOfCountryReply] =
    Future {
      val states =
        worldCities.filter(_.country == aCountry).map(_.state).distinct
      GetStatesOfCountryReply(states)
    }

   def getCitiesOfState(
      aCountry: String,
      aState: String
  ): Future[GetCitiesOfStateReply] =
    Future {
      val cities = worldCities
        .filter(e => e.country == aCountry && e.state == aState)
        .map(_.city)
        .distinct
      GetCitiesOfStateReply(cities)
    }

   def getLocationByIp(
      ip: String
  ): Future[GetLocationByIpReply] =
    Future {
//      val ip = aIp
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

   def healthCheck: Future[HealthCheckRes] = {
    println("healthCheck")
    Future.successful(HealthCheckRes())
  }
}
