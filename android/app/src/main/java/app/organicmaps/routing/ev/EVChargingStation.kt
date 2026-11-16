package app.organicmaps.routing.ev

data class EVChargingStation(
  val id: Long,
  val name: String,
  val lat: Double,
  val lon: Double,
  val operator: String? = null,
  val maxPowerKw: Double = 0.0,
  val plugTypes: List<PlugType> = emptyList(),
  val isRestArea: Boolean = false,
  val address: String? = null
) {
  val isFastCharger: Boolean
    get() = maxPowerKw >= 50.0 || plugTypes.contains(PlugType.CCS) || plugTypes.contains(PlugType.TESLA_SUPERCHARGER)
}
