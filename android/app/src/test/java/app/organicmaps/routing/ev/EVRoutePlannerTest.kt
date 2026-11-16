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
  fun testPlanChargingStopsHessenThuringen() = runBlocking {
    val polyline = listOf(
      50.1109 to 8.6821,   // Frankfurt am Main (Hessen, 0 km)
      50.5516 to 9.6752,   // Fulda (Hessen, ~100 km)
      50.8682 to 9.7077,   // Bad Hersfeld / Kirchheim (Hessen, ~140 km)
      50.9746 to 10.3244,  // Eisenach (Thüringen, ~190 km)
      50.9848 to 11.0299,  // Erfurt (Thüringen, ~245 km)
      50.9803 to 11.3290   // Weimar (Thüringen, ~265 km)
    )

    val mockStations = listOf(
      EVChargingStation(
        id = 101,
        name = "Ionity Kirchheimer Dreieck (Hessen)",
        lat = 50.84,
        lon = 9.57,
        operator = "Ionity",
        maxPowerKw = 350.0,
        plugTypes = listOf(PlugType.CCS),
        isRestArea = true
      ),
      EVChargingStation(
        id = 102,
        name = "EnBW Fastned Eisenach (Thüringen)",
        lat = 50.96,
        lon = 10.35,
        operator = "EnBW",
        maxPowerKw = 300.0,
        plugTypes = listOf(PlugType.CCS),
        isRestArea = true
      )
    )

    val options = EVCorridorOptions(
      maxRangeKm = 150.0,
      minArrivalBatteryPercent = 15.0,
      minChargingPowerKw = 50.0
    )

    val planner = EVRoutePlanner(MockEVDataProvider(mockStations))
    val stops = planner.planChargingStops(polyline, options)

    assertNotNull(stops)
    assertTrue("Should suggest charging stop along Hessen/Thüringen corridor", stops.isNotEmpty())
    assertEquals("First stop should be Ionity Kirchheimer Dreieck (Hessen)", "Ionity Kirchheimer Dreieck (Hessen)", stops.first().station.name)
  }
}
