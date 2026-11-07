package com.mux.android.maps.map

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Cam
import com.mux.android.maps.data.Bounds
import com.mux.android.maps.data.Loc
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.UiMode
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

private const val CYAN = 0xFF19E3FF.toInt()
private const val AMBER = 0xFFFFB000.toInt()
private const val GREEN = 0xFF3DDC84.toInt()
private const val DARK = 0xFF040A12.toInt()

/** Drives the osmdroid MapView from [MapState]. */
object MapCtl {
    var map: MapView? = null
        private set
    private var hub: Hub? = null

    fun create(ctx: Context): MapView {
        val m = MapView(ctx)
        map = m
        hub = Hub(ctx, m)
        return m
    }

    fun sync() {
        hub?.sync()
    }

    fun release() {
        hub?.release()
        hub = null
        map = null
    }

    fun zoomIn() {
        map?.controller?.zoomIn()
    }

    fun zoomOut() {
        map?.controller?.zoomOut()
    }

    fun resetNorth() {
        map?.mapOrientation = 0f
        MapState.rotation = 0f
        if (MapState.follow == 2) MapState.follow = 1
    }

    fun applyStyle() {
        hub?.applyStyle()
    }

    fun onResume() {
        map?.onResume()
    }

    fun onPause() {
        map?.onPause()
        hub?.saveCamera()
    }

    private class Hub(val ctx: Context, val m: MapView) {
        private val main = Handler(Looper.getMainLooper())
        private val density = ctx.resources.displayMetrics.density

        private val dynamic = ArrayList<Overlay>()       // route, pois, pin, ruler
        private val meOverlays = ArrayList<Overlay>()
        private val top = ArrayList<Overlay>()           // copyright + scale bar
        private val base = ArrayList<Overlay>()          // gestures

        private var lastPin: Any? = null
        private var lastPois: Any? = null
        private var lastRoutes: Any? = null
        private var lastIndex = -1
        private var lastMeasure: Any? = null
        private var lastRev = -1
        private var lastStyle = ""
        private var lastCustom = ""
        private var lastOffline: Boolean? = null
        private var lastAmber: Boolean? = null
        private var lastScale: Boolean? = null
        private var lastRotateGest: Boolean? = null
        private var lastCamId = 0L
        private var lastFixTime = 0L
        private var lastNavMode = false
        private var reportPosted = false
        private val fixListener: (Location) -> Unit = { main.post { sync() } }

        private val me = Marker(m)
        private val accuracy = Polygon()
        private var meIcons: Pair<Boolean, Boolean>? = null
        private val rotation = RotationGestureOverlay(m)
        private val scale = ScaleBarOverlay(m)
        private val copyright = CopyrightOverlay(ctx)

        init {
            m.setMultiTouchControls(true)
            m.isTilesScaledToDpi = true
            m.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            m.minZoomLevel = 3.0
            m.maxZoomLevel = 20.0
            m.setHorizontalMapRepetitionEnabled(false)
            m.setVerticalMapRepetitionEnabled(false)
            m.setScrollableAreaLimitLatitude(85.0, -85.0, 0)
            m.controller.setZoom(AppSettings.lastZoom.toDouble())
            m.controller.setCenter(GeoPoint(AppSettings.lastLat.toDoubleOrNull() ?: 23.8103, AppSettings.lastLon.toDoubleOrNull() ?: 90.4125))

            // taps
            m.overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p == null) return false
                        when {
                            MapState.measuring -> MapActions.addMeasurePoint(p)
                            MapState.ui == UiMode.PLACE -> MapActions.clearPin()
                            else -> return false
                        }
                        return true
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean {
                        if (p == null || MapState.measuring || MapState.ui == UiMode.NAV || MapState.ui == UiMode.ROUTE) return false
                        MapActions.dropPin(p.latitude, p.longitude)
                        return true
                    }
                })
            )
            // dragging the map stops "follow me"
            val gesture = object : Overlay() {
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, dx: Float, dy: Float, mv: MapView?): Boolean {
                    if (MapState.follow != 0) MapState.follow = 0
                    return false
                }
            }
            base.add(gesture)
            base.add(rotation)
            m.overlays.addAll(base)

            me.setInfoWindow(null)
            me.isFlat = true
            me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            me.setOnMarkerClickListener { _, _ -> true }
            accuracy.fillPaint.color = 0x2219E3FF
            accuracy.outlinePaint.color = 0x8819E3FF.toInt()
            accuracy.outlinePaint.strokeWidth = 2f
            accuracy.setInfoWindow(null)

            top.add(copyright)
            top.add(scale)
            scale.setAlignBottom(true)
            scale.setLineWidth(2f)
            scale.setScaleBarOffset((12 * density).toInt(), (10 * density).toInt())

            m.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    report()
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    report()
                    return false
                }
            })

            Loc.addListener(fixListener)
            applyStyle()
            relayout()
            report()
        }

        fun release() {
            Loc.removeListener(fixListener)
            try {
                m.onDetach()
            } catch (e: Exception) {
            }
        }

        /** Throttled: tell the UI where the map is (search bias, "nearby" box, compass). */
        private fun report() {
            if (reportPosted) return
            reportPosted = true
            main.postDelayed({
                reportPosted = false
                val c = m.mapCenter
                MapState.centerLat = c.latitude
                MapState.centerLon = c.longitude
                MapState.zoom = m.zoomLevelDouble
                MapState.rotation = m.mapOrientation
                try {
                    val b = m.boundingBox
                    MapState.bounds = Bounds(b.latSouth, b.lonWest, b.latNorth, b.lonEast)
                } catch (e: Exception) {
                }
            }, 120)
        }

        fun saveCamera() {
            val c = m.mapCenter
            AppSettings.lastLat = c.latitude.toString()
            AppSettings.lastLon = c.longitude.toString()
            AppSettings.lastZoom = m.zoomLevelDouble.toInt()
        }

        fun applyStyle() {
            val st = Styles.byKey(AppSettings.style)
            m.setTileSource(st.make())
            m.overlayManager.tilesOverlay.setColorFilter(if (st.invert) TilesOverlay.INVERT_COLORS else null)
            m.invalidate()
            lastStyle = AppSettings.style
            lastCustom = AppSettings.customTiles
        }

        // ---------------------------------------------------------------------------------------
        // sync with state
        // ---------------------------------------------------------------------------------------

        fun sync() {
            val accent = if (AppSettings.amber) CYAN else AMBER
            val primary = if (AppSettings.amber) AMBER else CYAN
            var relayout = false

            if (AppSettings.style != lastStyle || (AppSettings.style == "custom" && AppSettings.customTiles != lastCustom)) applyStyle()
            if (lastOffline != AppSettings.offlineOnly) {
                lastOffline = AppSettings.offlineOnly
                m.setUseDataConnection(!AppSettings.offlineOnly)
            }
            if (lastScale != AppSettings.showScale) {
                lastScale = AppSettings.showScale
                scale.isEnabled = AppSettings.showScale
                relayout = true
            }
            if (lastRotateGest != AppSettings.rotateGestures) {
                lastRotateGest = AppSettings.rotateGestures
                rotation.isEnabled = AppSettings.rotateGestures
                if (!AppSettings.rotateGestures) {
                    m.mapOrientation = 0f
                    MapState.rotation = 0f
                }
            }

            val pin = MapState.pin
            val pois = MapState.pois
            val routes = MapState.routes
            val measure = MapState.measure
            val idx = MapState.routeIndex
            val rev = MapState.navRouteChanged
            val navMode = Navigator.active
            val navRoute = Navigator.route

            if (pin !== lastPin || pois !== lastPois || routes !== lastRoutes || idx != lastIndex ||
                measure !== lastMeasure || rev != lastRev || AppSettings.amber != lastAmber || navMode != lastNavMode
            ) {
                lastPin = pin; lastPois = pois; lastRoutes = routes; lastIndex = idx
                lastMeasure = measure; lastRev = rev; lastAmber = AppSettings.amber; lastNavMode = navMode
                rebuild(pin, pois, if (navMode && navRoute != null) listOf(navRoute) else routes, if (navMode) 0 else idx, measure, primary, accent)
                relayout = true
            }
            if (relayout) relayout()

            updateMe(primary)
            val cam = MapState.camera
            if (cam != null && cam.id != lastCamId) {
                lastCamId = cam.id
                runCamera(cam)
            }
            if (MapState.follow == 0 && !Navigator.active) m.invalidate()
        }

        private fun runCamera(c: Cam) {
            val box = c.box
            if (box != null) {
                m.zoomToBoundingBox(box, true, (90 * density).toInt())
                return
            }
            val lat = c.lat ?: return
            val lon = c.lon ?: return
            val z = c.zoom
            if (z != null) m.controller.animateTo(GeoPoint(lat, lon), z, 600L) else m.controller.animateTo(GeoPoint(lat, lon))
        }

        private fun rebuild(pin: Any?, pois: List<com.mux.android.maps.data.Place>, routes: List<com.mux.android.maps.data.Route>, idx: Int, measure: List<GeoPoint>, primary: Int, accent: Int) {
            dynamic.clear()

            // routes: alternatives first (grey), chosen one on top
            for ((i, r) in routes.withIndex()) {
                if (i == idx) continue
                dynamic.add(line(r.points, 0xAA6A8A94.toInt(), 8f))
            }
            routes.getOrNull(idx)?.let { r ->
                dynamic.add(line(r.points, DARK, 15f))
                dynamic.add(line(r.points, accent, 9f))
            }

            // measure
            if (measure.size >= 2) {
                dynamic.add(line(measure, DARK, 10f))
                dynamic.add(line(measure, GREEN, 5f))
            }
            for (p in measure) {
                val mk = Marker(m)
                mk.position = p
                mk.icon = Icons.ruler(ctx, GREEN)
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mk.setInfoWindow(null)
                mk.setOnMarkerClickListener { _, _ -> true }
                dynamic.add(mk)
            }

            // category results
            for (p in pois) {
                val mk = Marker(m)
                mk.position = GeoPoint(p.lat, p.lon)
                mk.icon = Icons.dot(ctx, accent)
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                mk.setInfoWindow(null)
                mk.setOnMarkerClickListener { _, _ ->
                    MapActions.select(p, maxOf(MapState.zoom, 16.0))
                    true
                }
                dynamic.add(mk)
            }

            // selected place / destination
            val place = MapState.pin
            if (place != null) {
                val mk = Marker(m)
                mk.position = GeoPoint(place.lat, place.lon)
                mk.icon = Icons.pin(ctx, accent)
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mk.setInfoWindow(null)
                mk.setOnMarkerClickListener { _, _ -> true }
                dynamic.add(mk)
            }
            // custom route start
            val from = MapState.routeFrom
            if (from != null && MapState.routeTo != null) {
                val mk = Marker(m)
                mk.position = GeoPoint(from.lat, from.lon)
                mk.icon = Icons.pin(ctx, GREEN)
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                mk.setInfoWindow(null)
                mk.setOnMarkerClickListener { _, _ -> true }
                dynamic.add(mk)
            }
        }

        private fun line(pts: List<GeoPoint>, color: Int, widthDp: Float): Polyline {
            val l = Polyline()
            l.setPoints(pts)
            l.outlinePaint.color = color
            l.outlinePaint.strokeWidth = widthDp * density
            l.outlinePaint.strokeCap = Paint.Cap.ROUND
            l.outlinePaint.strokeJoin = Paint.Join.ROUND
            l.isGeodesic = false
            l.setOnClickListener { _, _, _ -> true }
            return l
        }

        /** Keeps a fixed order: base gestures, route/markers, my location, copyright + scale. */
        private fun relayout() {
            val ov = m.overlays
            val keep = ov.firstOrNull { it is MapEventsOverlay }
            ov.clear()
            if (keep != null) ov.add(keep)
            ov.addAll(base)
            ov.addAll(dynamic)
            ov.addAll(meOverlays)
            ov.add(copyright)
            if (AppSettings.showScale) ov.add(scale)
            m.invalidate()
        }

        private fun updateMe(primary: Int) {
            val f = Loc.fix
            val navigating = Navigator.active
            val icons = Pair(navigating, AppSettings.amber)
            if (meIcons != icons) {
                meIcons = icons
                me.icon = if (navigating) Icons.navArrow(ctx, primary) else Icons.me(ctx, primary, true)
            }
            if (f == null) {
                if (meOverlays.isNotEmpty()) {
                    meOverlays.clear()
                    relayout()
                }
                return
            }
            if (meOverlays.isEmpty()) {
                meOverlays.add(accuracy)
                meOverlays.add(me)
                relayout()
            }
            val p = GeoPoint(f.latitude, f.longitude)
            me.position = p
            // which way am I facing? GPS bearing while moving, compass when still
            val h = if (f.hasBearing() && f.hasSpeed() && f.speed > 1.5f) f.bearing else Loc.heading
            me.rotation = -h
            if (lastFixTime != f.time) {
                lastFixTime = f.time
                if (f.accuracy > 0f && f.accuracy < 400f && !navigating) {
                    accuracy.points = Polygon.pointsAsCircle(p, f.accuracy.toDouble())
                } else {
                    accuracy.points = ArrayList<GeoPoint>()
                }
            }
            // follow modes
            if (MapState.follow >= 1) {
                m.controller.animateTo(p)
                if (MapState.follow == 2) {
                    m.mapOrientation = -h
                    MapState.rotation = -h
                }
            }
            m.invalidate()
        }
    }
}
