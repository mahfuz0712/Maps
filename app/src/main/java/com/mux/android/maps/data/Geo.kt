package com.mux.android.maps.data

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val R = 6371000.0

    /** Great-circle distance in meters. */
    fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1 * PI / 180
        val p2 = lat2 * PI / 180
        val dp = (lat2 - lat1) * PI / 180
        val dl = (lon2 - lon1) * PI / 180
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing 0..360. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1 * PI / 180
        val p2 = lat2 * PI / 180
        val dl = (lon2 - lon1) * PI / 180
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x) * 180 / PI + 360) % 360
    }

    class Proj(val index: Int, val along: Double, val dist: Double)

    /** Closest point of the route to (lat, lon). Searches near [from] first (fast + avoids jumping to a far loop). */
    fun project(r: Route, lat: Double, lon: Double, from: Int): Proj {
        val n = r.points.size
        if (n < 2) return Proj(0, 0.0, Double.MAX_VALUE)
        val lo = (from - 10).coerceAtLeast(0)
        val hi = (from + 400).coerceAtMost(n - 2)
        var best = scan(r, lat, lon, lo, hi)
        if (best.dist > 80.0) {
            val all = scan(r, lat, lon, 0, n - 2)
            if (all.dist < best.dist) best = all
        }
        return best
    }

    private fun scan(r: Route, lat: Double, lon: Double, lo: Int, hi: Int): Proj {
        val kx = cos(lat * PI / 180) * 111320.0
        val ky = 110540.0
        var bestD = Double.MAX_VALUE
        var bestI = lo
        var bestT = 0.0
        for (i in lo..hi) {
            val a = r.points[i]
            val b = r.points[i + 1]
            val ax = (a.longitude - lon) * kx
            val ay = (a.latitude - lat) * ky
            val bx = (b.longitude - lon) * kx
            val by = (b.latitude - lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            var t = if (len2 > 0) (-(ax * dx + ay * dy)) / len2 else 0.0
            t = t.coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (d < bestD) {
                bestD = d
                bestI = i
                bestT = t
            }
        }
        val seg = if (bestI + 1 < r.cum.size) r.cum[bestI + 1] - r.cum[bestI] else 0.0
        return Proj(bestI, r.cum[bestI] + seg * bestT, bestD)
    }
}

/** Distance / time / coordinates as text. */
object Fmt {
    fun dist(m: Double): String {
        val imperial = AppSettings.units == "MI"
        return if (!imperial) {
            if (m < 950) "${(Math.round(m / 10.0) * 10).coerceAtLeast(10)} m"
            else if (m < 10_000) "%.1f km".format(m / 1000.0)
            else "${Math.round(m / 1000.0)} km"
        } else {
            val ft = m * 3.28084
            if (ft < 500) "${(Math.round(ft / 10.0) * 10).coerceAtLeast(10)} ft"
            else "%.1f mi".format(m / 1609.344)
        }
    }

    fun speed(mps: Float): String {
        val imperial = AppSettings.units == "MI"
        val v = if (imperial) mps * 2.23694 else mps * 3.6
        return "${Math.round(v)}"
    }

    fun speedUnit(): String = if (AppSettings.units == "MI") "MPH" else "KM/H"

    fun dur(sec: Double): String {
        val m = Math.round(sec / 60.0).toInt()
        return if (m < 1) "< 1 min" else if (m < 60) "$m min" else "${m / 60} h ${"%02d".format(m % 60)} min"
    }

    fun coord(lat: Double, lon: Double): String =
        "%.5f%s, %.5f%s".format(Math.abs(lat), if (lat >= 0) "N" else "S", Math.abs(lon), if (lon >= 0) "E" else "W")

    fun plain(lat: Double, lon: Double): String = "%.6f, %.6f".format(lat, lon)

    /** Distance for the voice: "500 meters" / "1500 feet". */
    fun speak(m: Double): String {
        val imperial = AppSettings.units == "MI"
        return if (!imperial) {
            if (m < 950) "${(Math.round(m / 50.0) * 50).coerceAtLeast(50)} meters" else "%.1f kilometers".format(m / 1000.0)
        } else {
            val ft = m * 3.28084
            if (ft < 1000) "${(Math.round(ft / 50.0) * 50).coerceAtLeast(50)} feet" else "%.1f miles".format(m / 1609.344)
        }
    }
}
