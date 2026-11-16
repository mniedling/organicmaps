package app.organicmaps.routing.ev

data class EVRouteStop(
  val station: EVChargingStation,
  val distanceFromStartKm: Double,
  val estimatedArrivalBatteryPercent: Double,
  val suggestedChargeToPercent: Double = 80.0
)
