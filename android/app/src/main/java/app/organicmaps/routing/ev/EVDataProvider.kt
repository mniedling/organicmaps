package app.organicmaps.routing.ev

interface EVDataProvider {
  suspend fun fetchChargingStations(
    sampledPoints: List<RoutePoint>,
    corridorMeters: Double
  ): List<EVChargingStation>
}
