package com.mux.android.maps.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** "Nearby" categories, answered by the free Overpass API (OpenStreetMap data). */
object Overpass {
    class Cat(val key: String, val label: String, val icon: String, val filters: List<String>)

    val cats = listOf(
        Cat("restaurant", "FOOD", "🍴", listOf("[\"amenity\"~\"restaurant|fast_food|food_court\"]")),
        Cat("cafe", "CAFE", "☕", listOf("[\"amenity\"=\"cafe\"]")),
        Cat("fuel", "FUEL", "⛽", listOf("[\"amenity\"=\"fuel\"]")),
        Cat("atm", "ATM / BANK", "₿", listOf("[\"amenity\"~\"atm|bank\"]")),
        Cat("pharmacy", "PHARMACY", "✚", listOf("[\"amenity\"=\"pharmacy\"]")),
        Cat("hospital", "HOSPITAL", "H", listOf("[\"amenity\"~\"hospital|clinic|doctors\"]")),
        Cat("hotel", "HOTEL", "▣", listOf("[\"tourism\"~\"hotel|guest_house|hostel|motel\"]")),
        Cat("market", "SHOPS", "◫", listOf("[\"shop\"~\"supermarket|convenience|mall|department_store\"]")),
        Cat("mosque", "MOSQUE", "☪", listOf("[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"]")),
        Cat("parking", "PARKING", "P", listOf("[\"amenity\"=\"parking\"]")),
        Cat("bus", "BUS STOP", "⛟", listOf("[\"highway\"=\"bus_stop\"]")),
        Cat("toilet", "TOILET", "WC", listOf("[\"amenity\"=\"toilets\"]"))
    )

    fun cat(key: String) = cats.firstOrNull { it.key == key }

    /** Places of category [c] inside the box, nearest to the center first. */
    suspend fun search(c: Cat, south: Double, west: Double, north: Double, east: Double, cLat: Double, cLon: Double): List<Place> =
        withContext(Dispatchers.IO) {
            val bb = "($south,$west,$north,$east)"
            val body = StringBuilder("[out:json][timeout:20];(")
            for (f in c.filters) body.append("node").append(f).append(bb).append(";way").append(f).append(bb).append(";")
            body.append(");out center 120;")
            val o = Net.json("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(body.toString(), "UTF-8"), 25_000)
            val arr = o.optJSONArray("elements") ?: return@withContext emptyList()
            val out = ArrayList<Place>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val lat: Double
                val lon: Double
                if (e.has("lat")) {
                    lat = e.getDouble("lat"); lon = e.getDouble("lon")
                } else {
                    val ce = e.optJSONObject("center") ?: continue
                    lat = ce.getDouble("lat"); lon = ce.getDouble("lon")
                }
                val t = e.optJSONObject("tags") ?: continue
                val name = t.optString("name").ifEmpty { t.optString("brand") }.ifEmpty { t.optString("operator") }.ifEmpty { c.label }
                val street = (t.optString("addr:street") + " " + t.optString("addr:housenumber")).trim()
                val addr = listOf(street, t.optString("addr:city")).filter { it.isNotBlank() }.joinToString(", ")
                out.add(
                    Place(
                        name, addr, lat, lon, c.key,
                        phone = t.optString("phone").ifEmpty { t.optString("contact:phone") },
                        website = t.optString("website").ifEmpty { t.optString("contact:website") },
                        hours = t.optString("opening_hours")
                    )
                )
            }
            out.sortedBy { Geo.dist(cLat, cLon, it.lat, it.lon) }.take(60)
        }
}
