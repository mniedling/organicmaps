package app.organicmaps.routing.ev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min

class OverpassEVProvider(
  private val endpointUrl: String = "https://overpass-api.de/api/interpreter",
  private val timeoutMs: Int = 15000
) : EVDataProvider {

  override suspend fun fetchChargingStations(
    sampledPoints: List<RoutePoint>,
    corridorMeters: Double
  ): List<EVChargingStation> = withContext(Dispatchers.IO) {
    if (sampledPoints.isEmpty()) return@withContext emptyList()

    // Divide sampled points into bounding box chunks of ~50km to avoid Overpass timeouts
    val chunks = chunkSampledPoints(sampledPoints, chunkSize = 15)
    val allStations = mutableMapOf<Long, EVChargingStation>()

    for (chunk in chunks) {
      val bbox = computeBoundingBox(chunk, corridorMeters)
      val stations = queryOverpassBbox(bbox)
      for (station in stations) {
        allStations[station.id] = station
      }
    }

    allStations.values.toList()
  }

  private fun chunkSampledPoints(points: List<RoutePoint>, chunkSize: Int): List<List<RoutePoint>> {
    return points.chunked(chunkSize)
  }

  private data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)

  private fun computeBoundingBox(points: List<RoutePoint>, corridorMeters: Double): BoundingBox {
    var minLat = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE

    for (p in points) {
      minLat = min(minLat, p.lat)
      maxLat = max(maxLat, p.lat)
      minLon = min(minLon, p.lon)
      maxLon = max(maxLon, p.lon)
    }

    // Convert meters to approximate degree delta
    val deltaDeg = (corridorMeters / 1000.0) / 111.0
    return BoundingBox(
      south = minLat - deltaDeg,
      west = minLon - deltaDeg,
      north = maxLat + deltaDeg,
      east = maxLon + deltaDeg
    )
  }

  private fun queryOverpassBbox(bbox: BoundingBox): List<EVChargingStation> {
    val overpassQl = """
      [out:json][timeout:15];
      (
        node["amenity"="charging_station"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
        way["amenity"="charging_station"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
        node["highway"~"services|rest_area"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
        way["highway"~"services|rest_area"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
      );
      out center tags 100;
    """.trimIndent()

    return try {
      val url = URL(endpointUrl)
      val conn = url.openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.connectTimeout = timeoutMs
      conn.readTimeout = timeoutMs
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      conn.setRequestProperty("User-Agent", "CoMaps-EV-Fork/1.0")

      val postData = "data=" + URLEncoder.encode(overpassQl, "UTF-8")
      OutputStreamWriter(conn.outputStream).use { writer ->
        writer.write(postData)
        writer.flush()
      }

      if (conn.responseCode == 200) {
        val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
        parseOverpassJson(jsonText)
      } else {
        emptyList()
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun parseOverpassJson(jsonText: String): List<EVChargingStation> {
    val root = JSONObject(jsonText)
    val elements = root.optJSONArray("elements") ?: return emptyList()

    val result = mutableListOf<EVChargingStation>()
    val restAreaBboxes = mutableListOf<BoundingBox>()

    // First pass: collect rest areas
    for (i in 0 until elements.length()) {
      val el = elements.getJSONObject(i)
      val tags = el.optJSONObject("tags") ?: continue
      val highway = tags.optString("highway", "")
      if (highway == "services" || highway == "rest_area") {
        val lat = el.optDouble("lat", el.optJSONObject("center")?.optDouble("lat") ?: Double.NaN)
        val lon = el.optDouble("lon", el.optJSONObject("center")?.optDouble("lon") ?: Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
          restAreaBboxes.add(BoundingBox(lat - 0.003, lon - 0.003, lat + 0.003, lon + 0.003))
        }
      }
    }

    // Second pass: collect charging stations
    for (i in 0 until elements.length()) {
      val el = elements.getJSONObject(i)
      val tags = el.optJSONObject("tags") ?: continue
      if (tags.optString("amenity") == "charging_station") {
        val id = el.optLong("id")
        val lat = el.optDouble("lat", el.optJSONObject("center")?.optDouble("lat") ?: Double.NaN)
        val lon = el.optDouble("lon", el.optJSONObject("center")?.optDouble("lon") ?: Double.NaN)
        if (lat.isNaN() || lon.isNaN()) continue

        val name = tags.optString("name", if (tags.has("operator")) tags.getString("operator") else "Ladesäule")
        val operator = if (tags.has("operator")) tags.getString("operator") else if (tags.has("brand")) tags.getString("brand") else null
        val maxPower = parsePowerKw(tags)
        val plugTypes = parsePlugTypes(tags)
        val isRestArea = restAreaBboxes.any { lat >= it.south && lat <= it.north && lon >= it.west && lon <= it.east }

        result.add(
          EVChargingStation(
            id = id,
            name = name,
            lat = lat,
            lon = lon,
            operator = operator,
            maxPowerKw = maxPower,
            plugTypes = plugTypes,
            isRestArea = isRestArea
          )
        )
      }
    }

    return result
  }

  private fun parsePowerKw(tags: JSONObject): Double {
    val rawOutput = tags.optString("charging_station:output", tags.optString("capacity", ""))
    val digitsOnly = rawOutput.replace(Regex("[^0-9.]"), "")
    return digitsOnly.toDoubleOrNull() ?: 0.0
  }

  private fun parsePlugTypes(tags: JSONObject): List<PlugType> {
    val list = mutableListOf<PlugType>()
    val keys = tags.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      if (key.startsWith("socket:")) {
        val plug = PlugType.fromOsmString(key)
        if (!list.contains(plug)) {
          list.add(plug)
        }
      }
    }
    if (list.isEmpty()) {
      list.add(PlugType.CCS) // Default assumption for highway corridor search if untagged
    }
    return list
  }
}
