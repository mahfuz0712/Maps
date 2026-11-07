package com.mux.android.maps.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Place search. Photon (autocomplete friendly) first, Nominatim as backup. No Google. */
object Geocoder {
    private val coordRe = Regex("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,; ]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$")

    /** "23.78, 90.41" typed in the search box. */
    fun parseCoords(q: String): Place? {
        val m = coordRe.matchEntire(q) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Place("PIN", Fmt.coord(lat, lon), lat, lon, "coordinates")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun search(q: String, nearLat: Double, nearLon: Double, limit: Int = 8): List<Place> = withContext(Dispatchers.IO) {
        parseCoords(q)?.let { return@withContext listOf(it) }
        try {
            val o = Net.json("https://photon.komoot.io/api/?q=${enc(q)}&limit=$limit&lang=en&lat=$nearLat&lon=$nearLon")
            val list = photonList(o.optJSONArray("features"))
            if (list.isNotEmpty()) return@withContext list
        } catch (e: Exception) {
        }
        try {
            val a = JSONArray(Net.get("https://nominatim.openstreetmap.org/search?q=${enc(q)}&format=jsonv2&limit=$limit&addressdetails=0"))
            val out = ArrayList<Place>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val dn = o.optString("display_name")
                out.add(
                    Place(
                        o.optString("name").ifEmpty { dn.substringBefore(',') },
                        dn.substringAfter(',', "").trim(),
                        o.getString("lat").toDouble(), o.getString("lon").toDouble(), o.optString("type")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** What is at this spot? Falls back to the coordinates. */
    suspend fun reverse(lat: Double, lon: Double): Place = withContext(Dispatchers.IO) {
        try {
            val o = Net.json("https://photon.komoot.io/reverse?lat=$lat&lon=$lon&lang=en")
            val p = photonList(o.optJSONArray("features")).firstOrNull()
            if (p != null) return@withContext p.copy(lat = lat, lon = lon)
        } catch (e: Exception) {
        }
        try {
            val o = Net.json("https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=jsonv2")
            val dn = o.optString("display_name")
            if (dn.isNotEmpty()) {
                return@withContext Place(o.optString("name").ifEmpty { dn.substringBefore(',') }, dn.substringAfter(',', "").trim(), lat, lon, o.optString("type"))
            }
        } catch (e: Exception) {
        }
        Place("DROPPED PIN", Fmt.coord(lat, lon), lat, lon)
    }

    private fun photonList(a: JSONArray?): List<Place> {
        val out = ArrayList<Place>()
        if (a == null) return out
        for (i in 0 until a.length()) {
            val f = a.optJSONObject(i) ?: continue
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val p = f.optJSONObject("properties") ?: JSONObject()
            val name = p.optString("name")
            val street = (p.optString("street") + " " + p.optString("housenumber")).trim()
            val city = p.optString("city").ifEmpty { p.optString("district") }.ifEmpty { p.optString("county") }
            val title = name.ifEmpty { street }.ifEmpty { city }.ifEmpty { p.optString("state") }
            if (title.isEmpty()) continue
            val addr = listOf(if (name.isNotEmpty()) street else "", city, p.optString("state"), p.optString("country"))
                .filter { it.isNotBlank() && it != title }.distinct().joinToString(", ")
            out.add(Place(title, addr, c.getDouble(1), c.getDouble(0), p.optString("osm_value")))
        }
        return out
    }
}
