package com.mux.android.maps.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

/** Directions from OSRM (open source routing on OpenStreetMap data). No Google. */
object Routing {
    class RouteException(msg: String) : Exception(msg)

    private fun base(mode: String): String {
        val custom = AppSettings.routeServer.trim().trimEnd('/')
        if (custom.isNotEmpty()) return "$custom/route/v1/driving/"
        return when (mode) {
            "bike" -> "https://routing.openstreetmap.de/routed-bike/route/v1/driving/"
            "walk" -> "https://routing.openstreetmap.de/routed-foot/route/v1/driving/"
            else -> "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
        }
    }

    suspend fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, mode: String): List<Route> =
        withContext(Dispatchers.IO) {
            val q = "$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline6&steps=true&alternatives=true"
            val urls = ArrayList<String>()
            urls.add(base(mode) + q)
            if (mode == "car" && AppSettings.routeServer.isBlank()) urls.add("https://router.project-osrm.org/route/v1/driving/$q")
            var last: Exception? = null
            for (u in urls) {
                try {
                    val o = Net.json(u, 25_000)
                    val list = parse(o)
                    if (list.isNotEmpty()) return@withContext list
                    last = RouteException("NO ROUTE FOUND BETWEEN THESE POINTS")
                } catch (e: Exception) {
                    last = e
                }
            }
            throw last ?: RouteException("ROUTING FAILED")
        }

    private fun parse(o: JSONObject): List<Route> {
        if (o.optString("code") != "Ok") return emptyList()
        val arr = o.optJSONArray("routes") ?: return emptyList()
        val out = ArrayList<Route>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val pts = decode(r.getString("geometry"), 1e6)
            val steps = ArrayList<Step>()
            val legs = r.optJSONArray("legs")
            if (legs != null) {
                for (l in 0 until legs.length()) {
                    val st = legs.getJSONObject(l).optJSONArray("steps") ?: continue
                    for (k in 0 until st.length()) {
                        val s = st.getJSONObject(k)
                        val m = s.getJSONObject("maneuver")
                        val loc = m.getJSONArray("location")
                        steps.add(
                            Step(
                                m.optString("type"), m.optString("modifier"), s.optString("name"),
                                s.optDouble("distance"), s.optDouble("duration"),
                                loc.getDouble(1), loc.getDouble(0), m.optInt("exit"), m.optInt("bearing_after")
                            )
                        )
                    }
                }
            }
            if (pts.size >= 2) out.add(Route(pts, r.optDouble("distance"), r.optDouble("duration"), steps))
        }
        return out
    }

    /** Google-style encoded polyline (precision 1e5 or 1e6). The format is an open algorithm. */
    fun decode(s: String, factor: Double): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(s.length / 4)
        var i = 0
        var lat = 0
        var lon = 0
        while (i < s.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = s[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            shift = 0
            result = 0
            do {
                b = s[i++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out.add(GeoPoint(lat / factor, lon / factor))
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // words + arrows for a step
    // ---------------------------------------------------------------------------------------------

    private fun compass(b: Int): String {
        val names = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        return names[(((b % 360) + 360) % 360 + 22) / 45 % 8]
    }

    private fun dirWord(mod: String): String = when (mod) {
        "left" -> "left"
        "right" -> "right"
        "slight left" -> "slightly left"
        "slight right" -> "slightly right"
        "sharp left" -> "sharp left"
        "sharp right" -> "sharp right"
        "uturn" -> "around"
        else -> "ahead"
    }

    /** English instruction, e.g. "Turn left onto Mirpur Road". */
    fun text(s: Step): String {
        val onto = if (s.name.isNotEmpty()) " onto ${s.name}" else ""
        val on = if (s.name.isNotEmpty()) " on ${s.name}" else ""
        val m = s.modifier
        return when (s.type) {
            "depart" -> "Head ${compass(s.bearingAfter)}$on"
            "arrive" -> when (m) {
                "left", "slight left", "sharp left" -> "You have arrived. Your destination is on the left"
                "right", "slight right", "sharp right" -> "You have arrived. Your destination is on the right"
                else -> "You have arrived at your destination"
            }
            "turn", "continue", "end of road" -> when (m) {
                "uturn" -> "Make a U-turn$on"
                "straight", "" -> if (s.type == "end of road") "Continue$onto" else "Continue straight$on"
                "slight left", "slight right" -> "Bear ${dirWord(m).removePrefix("slightly ")}$onto"
                else -> (if (s.type == "end of road") "At the end of the road, turn " else "Turn ") + dirWord(m) + onto
            }
            "new name" -> "Continue$onto"
            "merge" -> "Merge ${dirWord(m)}$onto"
            "on ramp" -> "Take the ramp ${dirWord(m)}$onto"
            "off ramp" -> "Take the exit ${dirWord(m)}$onto"
            "fork" -> "Keep ${if (m.contains("left")) "left" else if (m.contains("right")) "right" else "straight"} at the fork$onto"
            "roundabout", "rotary", "exit roundabout", "exit rotary" ->
                if (s.exit > 0) "At the roundabout, take the ${ordinal(s.exit)} exit$onto" else "Enter the roundabout$onto"
            "roundabout turn" -> "At the roundabout, turn ${dirWord(m)}$onto"
            else -> "Continue$on"
        }
    }

    private fun ordinal(n: Int) = when (n) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"; else -> "${n}th"
    }

    /** Big arrow glyph for the HUD. */
    fun arrow(s: Step): String = when (s.type) {
        "arrive" -> "⚑"
        "depart" -> "↑"
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> "↻"
        else -> when (s.modifier) {
            "left" -> "←"
            "right" -> "→"
            "slight left" -> "↖"
            "slight right" -> "↗"
            "sharp left" -> "↙"
            "sharp right" -> "↘"
            "uturn" -> "↶"
            else -> "↑"
        }
    }
}
