package com.mux.android.maps.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/** One-shot camera command for the map. */
class Cam(val lat: Double? = null, val lon: Double? = null, val zoom: Double? = null, val box: BoundingBox? = null) {
    val id: Long = System.nanoTime()
}

class Bounds(val south: Double, val west: Double, val north: Double, val east: Double)

enum class UiMode { BROWSE, PLACE, ROUTE, NAV }

/** Everything the map screen shows. The map itself (osmdroid) reads this and draws it. */
object MapState {
    var pin by mutableStateOf<Place?>(null)
    var pinLoading by mutableStateOf(false)

    var pois by mutableStateOf<List<Place>>(emptyList())
    var poiCat by mutableStateOf<String?>(null)
    var poiLoading by mutableStateOf(false)
    var poiError by mutableStateOf("")

    var routeTo by mutableStateOf<Place?>(null)
    var routeFrom by mutableStateOf<Place?>(null)       // null = my location
    var routes by mutableStateOf<List<Route>>(emptyList())
    var routeIndex by mutableIntStateOf(0)
    var routeLoading by mutableStateOf(false)
    var routeError by mutableStateOf("")
    var navRouteChanged by mutableIntStateOf(0)

    var measuring by mutableStateOf(false)
    var measure by mutableStateOf<List<GeoPoint>>(emptyList())

    /** 0 = free, 1 = follow me, 2 = follow + rotate with heading */
    var follow by mutableIntStateOf(0)
    var rotation by mutableFloatStateOf(0f)
    var zoom by mutableDoubleStateOf(11.0)
    var centerLat by mutableDoubleStateOf(23.8103)
    var centerLon by mutableDoubleStateOf(90.4125)
    var bounds by mutableStateOf<Bounds?>(null)
    var camera by mutableStateOf<Cam?>(null)

    val ui: UiMode
        get() = when {
            Navigator.active -> UiMode.NAV
            routeTo != null -> UiMode.ROUTE
            pin != null -> UiMode.PLACE
            else -> UiMode.BROWSE
        }

    fun measureMeters(): Double {
        var d = 0.0
        for (i in 1 until measure.size) d += Geo.dist(measure[i - 1].latitude, measure[i - 1].longitude, measure[i].latitude, measure[i].longitude)
        return d
    }
}

/** Things the user can do; they update [MapState]. */
object MapActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pinReq = 0L
    private var routeReq = 0L

    fun fly(lat: Double, lon: Double, zoom: Double? = null) {
        MapState.follow = 0
        MapState.camera = Cam(lat, lon, zoom)
    }

    /** Show a found place (search result, saved place, category result). */
    fun select(p: Place, zoom: Double = 16.0) {
        pinReq++
        MapState.pinLoading = false
        MapState.pin = p
        fly(p.lat, p.lon, zoom)
    }

    /** Long-press on the map: drop a pin and find out what is there. */
    fun dropPin(lat: Double, lon: Double) {
        val id = ++pinReq
        MapState.pin = Place("DROPPED PIN", Fmt.coord(lat, lon), lat, lon)
        MapState.pinLoading = true
        scope.launch {
            val p = Geocoder.reverse(lat, lon)
            if (id == pinReq && MapState.pin != null) MapState.pin = p
            if (id == pinReq) MapState.pinLoading = false
        }
    }

    fun clearPin() {
        pinReq++
        MapState.pin = null
        MapState.pinLoading = false
    }

    // ---- nearby categories ------------------------------------------------------------------

    fun showCategory(cat: Overpass.Cat) {
        val b = MapState.bounds
        if (b == null || MapState.zoom < 12.0) {
            MapState.poiError = "ZOOM IN CLOSER TO SEARCH THIS AREA"
            MapState.poiCat = cat.key
            MapState.pois = emptyList()
            return
        }
        MapState.poiCat = cat.key
        MapState.poiLoading = true
        MapState.poiError = ""
        MapState.pois = emptyList()
        val cLat = MapState.centerLat
        val cLon = MapState.centerLon
        scope.launch {
            try {
                val list = Overpass.search(cat, b.south, b.west, b.north, b.east, cLat, cLon)
                if (MapState.poiCat == cat.key) {
                    MapState.pois = list
                    if (list.isEmpty()) MapState.poiError = "NOTHING FOUND IN THIS AREA"
                }
            } catch (e: Exception) {
                MapState.poiError = "SEARCH FAILED - CHECK YOUR INTERNET"
            }
            MapState.poiLoading = false
        }
    }

    fun clearPois() {
        MapState.poiCat = null
        MapState.pois = emptyList()
        MapState.poiError = ""
        MapState.poiLoading = false
    }

    // ---- directions ---------------------------------------------------------------------------

    fun directions(to: Place, from: Place? = null) {
        MapState.pin = to
        MapState.routeTo = to
        MapState.routeFrom = from
        clearPois()
        fetchRoutes()
    }

    fun setFrom(p: Place?) {
        MapState.routeFrom = p
        fetchRoutes()
    }

    fun swapEnds() {
        val to = MapState.routeTo ?: return
        val from = MapState.routeFrom
        val me = Loc.fix
        val start = from ?: if (me != null) Place("MY LOCATION", "", me.latitude, me.longitude) else return
        MapState.pin = start
        MapState.routeTo = start
        MapState.routeFrom = to
        fetchRoutes()
    }

    fun setMode(m: String) {
        AppSettings.mode = m
        if (MapState.routeTo != null) fetchRoutes()
    }

    fun fetchRoutes() {
        val to = MapState.routeTo ?: return
        val from = MapState.routeFrom
        val fLat: Double
        val fLon: Double
        if (from != null) {
            fLat = from.lat
            fLon = from.lon
        } else {
            val f = Loc.fix
            if (f == null) {
                MapState.routes = emptyList()
                MapState.routeLoading = false
                MapState.routeError = "NO GPS FIX YET. TURN ON LOCATION OR CHOOSE A START POINT."
                return
            }
            fLat = f.latitude
            fLon = f.longitude
        }
        val id = ++routeReq
        MapState.routeLoading = true
        MapState.routeError = ""
        scope.launch {
            try {
                val list = Routing.route(fLat, fLon, to.lat, to.lon, AppSettings.mode)
                if (id == routeReq) {
                    MapState.routes = list
                    MapState.routeIndex = 0
                    MapState.follow = 0
                    MapState.camera = Cam(box = BoundingBox.fromGeoPoints(list[0].points))
                }
            } catch (e: Exception) {
                if (id == routeReq) {
                    MapState.routes = emptyList()
                    MapState.routeError = (e.message ?: "ROUTING FAILED").take(90).uppercase()
                }
            }
            if (id == routeReq) MapState.routeLoading = false
        }
    }

    fun clearRoute() {
        routeReq++
        MapState.routes = emptyList()
        MapState.routeTo = null
        MapState.routeFrom = null
        MapState.routeError = ""
        MapState.routeLoading = false
    }

    // ---- navigation ---------------------------------------------------------------------------

    fun startNavigation() {
        val r = MapState.routes.getOrNull(MapState.routeIndex) ?: return
        val d = MapState.routeTo ?: return
        Navigator.start(r, d, AppSettings.mode)
        MapState.follow = if (AppSettings.headingUp) 2 else 1
        Loc.fix?.let { MapState.camera = Cam(it.latitude, it.longitude, 17.0) }
    }

    fun stopNavigation() {
        Navigator.stop()
        MapState.follow = 0
        MapState.rotation = 0f
        clearRoute()
        clearPin()
    }

    // ---- measure ------------------------------------------------------------------------------

    fun addMeasurePoint(p: GeoPoint) {
        MapState.measure = MapState.measure + GeoPoint(p.latitude, p.longitude)
    }
}
