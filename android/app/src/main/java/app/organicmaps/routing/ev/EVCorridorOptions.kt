package app.organicmaps.routing.ev

data class EVCorridorOptions(
  val maxRangeKm: Double = 350.0,
  val minArrivalBatteryPercent: Double = 15.0,
  val minChargingPowerKw: Double = 50.0,
  val requiredPlugTypes: Set<PlugType> = setOf(PlugType.CCS),
  val searchCorridorMeters: Double = 2000.0,
  val preferentialRestAreasOnly: Boolean = true
)
