package app.organicmaps.routing.ev

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EVRoutePlannerTest {

  private class MockEVDataProvider(
    private val stations: List<EVChargingStation>
  ) : EVDataProvider {
    override suspend fun fetchChargingStations(
      sampledPoints: List<RoutePoint>,
      corridorMeters: Double
    ): List<EVChargingStation> = stations
  }

  @Test
  fun testPlanChargingStops() = runBlocking {
    val polyline = listOf(
      50.9375 to 6.9603,   // Köln (0 km)
      50.1109 to 8.6821,   // Frankfurt (~150 km)
      49.4521 to 11.0767,  // Nürnberg (~370 km)
      48.1351 to 11.5820   // München (~540 km)
    )

    val mockStations = listOf(
      EVChargingStation(
        id = 1,
        name = "Fastned Limburg (A3)",
        lat = 50.38,
        lon = 8.05,
        operator = "Fastned",
        maxPowerKw = 300.0,
        plugTypes = listOf(PlugType.CCS),
        isRestArea = true
      ),
      EVChargingStation(
        id = 2,
        name = "Ionity Würzburg (A3)",
        lat = 49.78,
        lon = 9.93,
        operator = "Ionity",
        maxPowerKw = 350.0,
        plugTypes = listOf(PlugType.CCS),
        isRestArea = true
      )
    )

    val options = EVCorridorOptions(
      maxRangeKm = 300.0,
      minArrivalBatteryPercent = 15.0,
      minChargingPowerKw = 50.0
    )

    val planner = EVRoutePlanner(MockEVDataProvider(mockStations))
    val stops = planner.planChargingStops(polyline, options)

    assertNotNull(stops)
    assertTrue("Should suggest at least one charging stop for ~540km route with 300km range", stops.isNotEmpty())
    assertEquals("First stop should be a fast charger", "Fastned Limburg (A3)", stops.first().station.name)
  }
}
