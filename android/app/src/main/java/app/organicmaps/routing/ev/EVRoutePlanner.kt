package app.organicmaps.routing.ev

class EVRoutePlanner(
  private val dataProvider: EVDataProvider
) {

  /**
   * Plans EV charging stops along a route polyline.
   */
  suspend fun planChargingStops(
    routePolyline: List<Pair<Double, Double>>,
    options: EVCorridorOptions = EVCorridorOptions()
  ): List<EVRouteStop> {
    if (routePolyline.size < 2) return emptyList()

    // 1. Compute cumulative distances and sample equidistant route points
    val fullRoute = PolylineSampler.computeCumulativeDistances(routePolyline)
    val totalRouteDistanceKm = fullRoute.last().distanceFromStartKm
    val sampledPoints = PolylineSampler.sampleEquidistantPoints(routePolyline, intervalKm = 5.0)

    // 2. Fetch charging stations along the route corridor
    val rawStations = dataProvider.fetchChargingStations(
      sampledPoints = sampledPoints,
      corridorMeters = options.searchCorridorMeters
    )

    // 3. Filter stations based on options (power, plug types, rest area preference)
    val eligibleStations = rawStations.filter { station ->
      val meetsPower = station.maxPowerKw == 0.0 || station.maxPowerKw >= options.minChargingPowerKw
      val meetsPlugs = options.requiredPlugTypes.isEmpty() || station.plugTypes.any { options.requiredPlugTypes.contains(it) }
      meetsPower && meetsPlugs
    }

    if (eligibleStations.isEmpty()) return emptyList()

    // 4. Project each station onto the route polyline to get its distanceFromStartKm
    val projectedStations = eligibleStations.map { station ->
      val distFromStart = projectStationOnRoute(station, fullRoute)
      station to distFromStart
    }.sortedBy { it.second }

    // 5. Calculate EV charging stops based on battery SOC simulation
    return computeSuggestedStops(projectedStations, totalRouteDistanceKm, options)
  }

  /**
   * Projects a charging station onto the route to estimate its distance from start in km.
   */
  private fun projectStationOnRoute(
    station: EVChargingStation,
    fullRoute: List<RoutePoint>
  ): Double {
    var minDistanceToRouteKm = Double.MAX_VALUE
    var closestPointDistanceKm = 0.0

    for (pt in fullRoute) {
      val d = PolylineSampler.distanceKm(station.lat, station.lon, pt.lat, pt.lon)
      if (d < minDistanceToRouteKm) {
        minDistanceToRouteKm = d
        closestPointDistanceKm = pt.distanceFromStartKm
      }
    }

    return closestPointDistanceKm
  }

  /**
   * Computes recommended charging stops along the route distance.
   */
  private fun computeSuggestedStops(
    projectedStations: List<Pair<EVChargingStation, Double>>,
    totalDistanceKm: Double,
    options: EVCorridorOptions
  ): List<EVRouteStop> {
    val stops = mutableListOf<EVRouteStop>()
    var currentPositionKm = 0.0
    var currentSocPercent = 100.0

    val usableRangeKm = options.maxRangeKm * (1.0 - options.minArrivalBatteryPercent / 100.0)

    while (currentPositionKm + usableRangeKm < totalDistanceKm) {
      val targetDistKm = currentPositionKm + usableRangeKm

      // Look for candidate chargers in the window around targetDistKm (e.g. targetDistKm - 50km to targetDistKm)
      val candidates = projectedStations.filter { (station, dist) ->
        dist in (currentPositionKm + 10.0)..targetDistKm &&
            (!options.preferentialRestAreasOnly || station.isRestArea || station.isFastCharger)
      }

      val chosen = if (candidates.isNotEmpty()) {
        // Prefer rest areas or highest power station closest to targetDistKm
        candidates.maxByOrNull { (station, dist) ->
          var score = dist
          if (station.isRestArea) score += 50.0
          if (station.maxPowerKw >= 150.0) score += 30.0
          score
        }
      } else {
        // Fallback: any candidate within range
        projectedStations.filter { (_, dist) -> dist > currentPositionKm && dist <= targetDistKm }
          .maxByOrNull { it.second }
      }

      if (chosen == null) {
        // No charging station found in range for this segment
        break
      }

      val (station, distFromStart) = chosen
      val distanceDrivenSegment = distFromStart - currentPositionKm
      val arrivalSoc = currentSocPercent - (distanceDrivenSegment / options.maxRangeKm) * 100.0

      stops.add(
        EVRouteStop(
          station = station,
          distanceFromStartKm = distFromStart,
          estimatedArrivalBatteryPercent = arrivalSoc.coerceAtLeast(0.0),
          suggestedChargeToPercent = 80.0
        )
      )

      currentPositionKm = distFromStart
      currentSocPercent = 80.0 // Recharge to 80%
    }

    return stops
  }
}
