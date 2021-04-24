package controller

import service.GeoService
import service.geoService._

import scala.concurrent.Future

case class GeoController() extends GeoServiceGrpc.GeoService {

  val geoService: GeoService = GeoService()

  override def getCountries(
      request: GetCountriesRequest
  ): Future[GetCountriesReply] = geoService.getCountries

  override def getStatesOfCountry(
      request: GetStatesOfCountryRequest
  ): Future[GetStatesOfCountryReply] =
    geoService.getStatesOfCountry(request.country)

  override def getCitiesOfState(
      request: GetCitiesOfStateRequest
  ): Future[GetCitiesOfStateReply] =
    geoService.getCitiesOfState(request.country, request.state)

  override def getLocationByIp(
      request: GetLocationByIpRequest
  ): Future[GetLocationByIpReply] = geoService.getLocationByIp(request.ip)

  override def healthCheck(request: HealthCheckReq): Future[HealthCheckRes] =
    geoService.healthCheck
}
