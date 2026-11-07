package com.mux.android.maps.data

import org.osmdroid.util.GeoPoint

data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val kind: String = "",
    val phone: String = "",
    val website: String = "",
    val hours: String = ""
) {
    val point: GeoPoint get() = GeoPoint(lat, lon)
}

/** One maneuver of a route (OSRM style). The maneuver happens at [lat],[lon]; [distance] runs to the next one. */
data class Step(
    val type: String,
    val modifier: String,
    val name: String,
    val distance: Double,
    val duration: Double,
    val lat: Double,
    val lon: Double,
    val exit: Int,
    val bearingAfter: Int
)

class Route(val points: List<GeoPoint>, val distance: Double, val duration: Double, val steps: List<Step>) {
    /** Meters from the start to every point. */
    val cum: DoubleArray = DoubleArray(points.size).also { a ->
        for (i in 1 until points.size) a[i] = a[i - 1] + Geo.dist(points[i - 1].latitude, points[i - 1].longitude, points[i].latitude, points[i].longitude)
    }

    /** Meters from the start to the END of every step (= where the next maneuver is). */
    val stepEnd: DoubleArray = DoubleArray(steps.size).also { a ->
        var s = 0.0
        for (i in steps.indices) {
            s += steps[i].distance
            a[i] = s
        }
    }

    val length: Double get() = if (cum.isEmpty()) distance else cum[cum.size - 1]
}

data class Saved(val id: Long, val name: String, val lat: Double, val lon: Double, val label: String, val address: String)
