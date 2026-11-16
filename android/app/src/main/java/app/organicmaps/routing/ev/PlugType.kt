package app.organicmaps.routing.ev

enum class PlugType(val title: String) {
  CCS("CCS (Combo 2)"),
  TYPE_2("Type 2"),
  CHADEMO("CHAdeMO"),
  TESLA_SUPERCHARGER("Tesla Supercharger"),
  OTHER("Other");

  companion object {
    fun fromOsmString(socketTag: String): PlugType {
      val lower = socketTag.lowercase()
      return when {
        lower.contains("combo") || lower.contains("ccs") || lower.contains("type2_combo") -> CCS
        lower.contains("type2") -> TYPE_2
        lower.contains("chademo") -> CHADEMO
        lower.contains("tesla") || lower.contains("supercharger") -> TESLA_SUPERCHARGER
        else -> OTHER
      }
    }
  }
}
