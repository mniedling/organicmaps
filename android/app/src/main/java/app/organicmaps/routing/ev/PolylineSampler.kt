package app.organicmaps.routing.ev

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RoutePoint(
  val lat: Double,
  val lon: Double,
  val distanceFromStartKm: Double = 0.0
)

object PolylineSampler {
  private const val EARTH_RADIUS_KM = 6371.0

  /**
   * Calculates geodesic distance between two coordinates in kilometers using Haversine formula.
   */
  fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * asin(sqrt(a))
    return EARTH_RADIUS_KM * c
  }

  /**
   * Calculates cumulative distances along a list of route points.
   */
  fun computeCumulativeDistances(points: List<Pair<Double, Double>>): List<RoutePoint> {
    if (points.isEmpty()) return emptyList()

    val result = ArrayList<RoutePoint>(points.size)
    var cumulativeDistance = 0.0
    result.add(RoutePoint(points[0].first, points[0].second, 0.0))

    for (i in 1 until points.size) {
      val prev = points[i - 1]
      val curr = points[i]
      val segDist = distanceKm(prev.first, prev.second, curr.first, curr.second)
      cumulativeDistance += segDist
      result.add(RoutePoint(curr.first, curr.second, cumulativeDistance))
    }

    return result
  }

  /**
   * Downsamples a dense polyline of route points into equidistant sampling points.
   * Always includes start and end points.
   */
  fun sampleEquidistantPoints(
    points: List<Pair<Double, Double>>,
    intervalKm: Double
  ): List<RoutePoint> {
    if (points.isEmpty()) return emptyList()
    val routeWithDistances = computeCumulativeDistances(points)
    if (routeWithDistances.size <= 2 || intervalKm <= 0.0) return routeWithDistances

    val sampled = mutableListOf<RoutePoint>()
    sampled.add(routeWithDistances.first())

    var nextTargetDistance = intervalKm
    val totalDistance = routeWithDistances.last().distanceFromStartKm

    for (i in 1 until routeWithDistances.size) {
      val curr = routeWithDistances[i]
      if (curr.distanceFromStartKm >= nextTargetDistance && curr.distanceFromStartKm < totalDistance) {
        sampled.add(curr)
        nextTargetDistance = curr.distanceFromStartKm + intervalKm
      }
    }

    if (sampled.last() != routeWithDistances.last()) {
      sampled.add(routeWithDistances.last())
    }

    return sampled
  }
}
