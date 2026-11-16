package app.organicmaps.routing.ev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineSamplerTest {

  @Test
  fun testDistanceCalculation() {
    // Distance from Köln (50.9375, 6.9603) to Frankfurt (50.1109, 8.6821) is ~152 km
    val distKm = PolylineSampler.distanceKm(50.9375, 6.9603, 50.1109, 8.6821)
    assertTrue("Distance should be between 140 and 160 km", distKm in 140.0..160.0)
  }

  @Test
  fun testPolylineDownsampling() {
    val polyline = listOf(
      50.9375 to 6.9603, // Köln
      50.6857 to 7.1537, // Bonn
      50.3569 to 7.5890, // Koblenz
      50.1109 to 8.6821, // Frankfurt
      49.4521 to 11.0767, // Nürnberg
      48.1351 to 11.5820  // München
    )

    val sampled = PolylineSampler.sampleEquidistantPoints(polyline, intervalKm = 100.0)

    assertTrue("Sampled points should include start and end", sampled.size >= 2)
    assertEquals("First point should be Köln", 50.9375, sampled.first().lat, 0.0001)
    assertEquals("Last point should be München", 48.1351, sampled.last().lat, 0.0001)
  }
}
