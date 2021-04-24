import com.github.tototoshi.csv.CSVReader

package object CityUtil {
  case class CityEntry(city: String, country: String, state: String)

  def readCities(): List[CityEntry] = {
    CSVReader
      .open(
        "./src/main/resources/world-cities_csv.csv"
      )
      .allWithHeaders
      .map(_.toList)
      .map { case List(city, country, state, _) =>
        CityEntry(city._2, country._2, state._2)
      }

  }

}
