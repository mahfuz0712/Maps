package com.mux.android.maps.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Fmt
import com.mux.android.maps.data.Geo
import com.mux.android.maps.data.Loc
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.Overpass
import com.mux.android.maps.data.Place
import com.mux.android.maps.data.Routing
import com.mux.android.maps.data.Store
import com.mux.android.maps.util.copyText
import com.mux.android.maps.util.dial
import com.mux.android.maps.util.openInOtherApp
import com.mux.android.maps.util.sharePlace
import com.mux.android.maps.util.toast
import com.mux.android.maps.data.Nav
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Meters from me (or from the map center when there is no GPS fix). */
fun distanceText(lat: Double, lon: Double): String {
    val f = Loc.fix
    val d = if (f != null) Geo.dist(f.latitude, f.longitude, lat, lon) else Geo.dist(MapState.centerLat, MapState.centerLon, lat, lon)
    return Fmt.dist(d) + if (f == null) " FROM CENTER" else " AWAY"
}

// -------------------------------------------------------------------------------------------------
// selected place
// -------------------------------------------------------------------------------------------------

@Composable
fun PlaceCard(p: Place, loading: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = Hud.colors
    val saved = Store.isSaved(p.lat, p.lon)
    HudPanel(modifier.fillMaxWidth(), if (loading) "PLACE · LOOKING UP..." else "PLACE") {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                HudText(p.name, color = c.primary, size = 22.sp, bold = true, maxLines = 2)
                if (p.address.isNotEmpty()) HudText(p.address, color = c.text, size = 12.sp, maxLines = 3)
            }
            HudButton("✕", Modifier.width(40.dp).height(36.dp), KeyKind.FN, fontSize = 14.sp) { MapActions.clearPin() }
        }
        Spacer(Modifier.height(4.dp))
        Row {
            if (p.kind.isNotEmpty()) Tag(p.kind.replace('_', ' ').uppercase(), c.accent)
            Tag(distanceText(p.lat, p.lon), c.good)
        }
        HudText(Fmt.coord(p.lat, p.lon), color = c.dim, size = 10.sp)
        if (p.hours.isNotEmpty()) HudText("HOURS: " + p.hours, color = c.dim, size = 10.sp, maxLines = 2)
        if (p.phone.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            HudButton("CALL  ${p.phone}", Modifier.fillMaxWidth().height(36.dp), KeyKind.FN, fontSize = 11.sp) { dial(ctx, p.phone) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(46.dp)) {
            HudButton("DIRECTIONS", Modifier.weight(1.6f).fillMaxHeight(), KeyKind.GOOD, fontSize = 13.sp) { MapActions.directions(p) }
            HudButton(
                if (saved != null) "★ SAVED" else "☆ SAVE", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT,
                selected = saved != null, fontSize = 11.sp
            ) {
                if (saved != null) Store.unsave(saved) else Store.save(p)
            }
        }
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            HudButton("SHARE", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 10.sp) { sharePlace(ctx, p) }
            HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 10.sp) { copyText(ctx, Fmt.plain(p.lat, p.lon)) }
            HudButton("HOME", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 10.sp) {
                Store.save(p, "HOME")
                toast(ctx, "SET AS HOME")
            }
            HudButton("WORK", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 10.sp) {
                Store.save(p, "WORK")
                toast(ctx, "SET AS WORK")
            }
            HudButton("OTHER APP", Modifier.weight(1.2f).fillMaxHeight(), KeyKind.FN, fontSize = 10.sp) { openInOtherApp(ctx, p) }
        }
    }
}

/** Results of a "nearby" category. */
@Composable
fun PoiPanel(modifier: Modifier = Modifier) {
    val c = Hud.colors
    val cat = Overpass.cat(MapState.poiCat ?: return)
    HudPanel(modifier.fillMaxWidth(), "NEARBY · ${cat?.label ?: ""}") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudText(
                when {
                    MapState.poiLoading -> "SEARCHING THIS AREA..."
                    MapState.poiError.isNotEmpty() -> MapState.poiError
                    else -> "${MapState.pois.size} FOUND · NEAREST FIRST"
                },
                color = if (MapState.poiError.isNotEmpty()) c.accent else c.primary, size = 12.sp, bold = true,
                modifier = Modifier.weight(1f)
            )
            HudButton("✕", Modifier.width(40.dp).height(34.dp), KeyKind.FN, fontSize = 14.sp) { MapActions.clearPois() }
        }
        if (MapState.pois.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                items(MapState.pois, key = { it.lat.toString() + it.lon + it.name }) { p ->
                    HudItem(Modifier.fillMaxWidth(), onClick = { MapActions.select(p, maxOf(MapState.zoom, 16.0)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                HudText(p.name, color = c.text, size = 14.sp, bold = true, maxLines = 1)
                                if (p.address.isNotEmpty()) HudText(p.address, color = c.dim, size = 10.sp, maxLines = 1)
                            }
                            HudText(distanceText(p.lat, p.lon).substringBefore(" AWAY").substringBefore(" FROM"), color = c.accent, size = 11.sp, bold = true)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// directions
// -------------------------------------------------------------------------------------------------

/** FROM / TO header while previewing a route. */
@Composable
fun RouteTop(modifier: Modifier = Modifier) {
    val c = Hud.colors
    val to = MapState.routeTo ?: return
    val from = MapState.routeFrom
    HudPanel(modifier.fillMaxWidth(), "DIRECTIONS") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HudText("FROM", color = c.dim, size = 9.sp)
                HudText(from?.name ?: "MY LOCATION", color = c.good, size = 14.sp, bold = true, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                HudText("TO", color = c.dim, size = 9.sp)
                HudText(to.name, color = c.accent, size = 14.sp, bold = true, maxLines = 1)
            }
            Column {
                HudButton("⇅", Modifier.width(44.dp).height(38.dp), KeyKind.OP, fontSize = 16.sp) { MapActions.swapEnds() }
                HudButton("START", Modifier.width(44.dp).height(34.dp), KeyKind.FN, fontSize = 8.sp) {
                    Nav.pickStart = true
                    Nav.searching = true
                }
            }
        }
    }
}

@Composable
fun RoutePanel(modifier: Modifier = Modifier) {
    val c = Hud.colors
    val routes = MapState.routes
    val idx = MapState.routeIndex.coerceIn(0, (routes.size - 1).coerceAtLeast(0))
    HudPanel(modifier.fillMaxWidth(), "ROUTE") {
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            for ((key, label) in listOf("car" to "🚗 CAR", "bike" to "🚲 BIKE", "walk" to "🚶 WALK")) {
                HudButton(label, Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = AppSettings.mode == key, fontSize = 11.sp) { MapActions.setMode(key) }
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            MapState.routeLoading -> HudText("FINDING THE BEST ROUTES...", color = c.accent, size = 13.sp, bold = true)
            MapState.routeError.isNotEmpty() -> {
                HudText(MapState.routeError, color = c.danger, size = 12.sp, bold = true)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(40.dp)) {
                    HudButton("RETRY", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) { MapActions.fetchRoutes() }
                    HudButton("CHOOSE START", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, fontSize = 11.sp) {
                        Nav.pickStart = true
                        Nav.searching = true
                    }
                }
            }
            routes.isNotEmpty() -> {
                if (routes.size > 1) {
                    ChipRow(
                        routes.mapIndexed { i, r -> i.toString() to "${i + 1} · ${Fmt.dur(r.duration)}" },
                        idx.toString()
                    ) { MapState.routeIndex = it.toInt() }
                    Spacer(Modifier.height(4.dp))
                }
                val r = routes[idx]
                Row(verticalAlignment = Alignment.Bottom) {
                    HudText(Fmt.dur(r.duration), color = c.primary, size = 26.sp, bold = true)
                    Spacer(Modifier.width(10.dp))
                    HudText(Fmt.dist(r.distance), color = c.text, size = 16.sp)
                    Spacer(Modifier.weight(1f))
                    HudText("ARRIVE " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (r.duration * 1000).toLong())), color = c.dim, size = 11.sp)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                    items(r.steps.size) { i ->
                        val s = r.steps[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            HudText(Routing.arrow(s), color = c.accent, size = 18.sp, bold = true, modifier = Modifier.width(28.dp))
                            HudText(Routing.text(s), color = c.text, size = 11.sp, modifier = Modifier.weight(1f), maxLines = 2)
                            if (s.distance > 0 && s.type != "arrive") HudText(Fmt.dist(s.distance), color = c.dim, size = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    HudButton("START", Modifier.weight(1.5f).fillMaxHeight(), KeyKind.GOOD, fontSize = 15.sp) { MapActions.startNavigation() }
                    HudButton("CLOSE", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 12.sp) {
                        MapActions.clearRoute()
                    }
                }
            }
        }
        if (routes.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            HudButton("CLOSE", Modifier.fillMaxWidth().height(40.dp), KeyKind.FN, fontSize = 11.sp) { MapActions.clearRoute() }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// navigation HUD
// -------------------------------------------------------------------------------------------------

@Composable
fun NavBanner(modifier: Modifier = Modifier) {
    val c = Hud.colors
    val next = Navigator.next
    HudPanel(modifier.fillMaxWidth(), if (Navigator.rerouting) "REROUTING..." else "NEXT MANEUVER") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudText(if (next != null) Routing.arrow(next) else "⚑", color = c.accent, size = 54.sp, bold = true, modifier = Modifier.width(70.dp))
            Column(Modifier.weight(1f)) {
                HudText(Fmt.dist(Navigator.distToNext), color = c.primary, size = 30.sp, bold = true)
                HudText(if (next != null) Routing.text(next) else "Continue to your destination", color = c.text, size = 14.sp, maxLines = 3)
            }
        }
        if (Navigator.note.isNotEmpty()) HudText(Navigator.note, color = c.accent, size = 11.sp, bold = true)
        if (Navigator.offRoute && !Navigator.rerouting) HudText("YOU LEFT THE ROUTE", color = c.danger, size = 11.sp, bold = true)
    }
}

@Composable
fun NavBottom(modifier: Modifier = Modifier) {
    val c = Hud.colors
    var sure by remember { mutableStateOf(false) }
    val eta = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + (Navigator.remainingTime * 1000).toLong()))
    HudPanel(modifier.fillMaxWidth(), "TO ${Navigator.destination?.name?.uppercase()?.take(24) ?: ""}") {
        Row(Modifier.fillMaxWidth()) {
            Stat("ETA", eta, Modifier.weight(1f))
            Stat("LEFT", Fmt.dist(Navigator.remaining), Modifier.weight(1f))
            Stat("TIME", Fmt.dur(Navigator.remainingTime), Modifier.weight(1.2f))
            Stat(Fmt.speedUnit(), Fmt.speed(Navigator.speed), Modifier.weight(0.9f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(44.dp)) {
            HudButton(if (Navigator.muted) "🔇" else "🔊", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = Navigator.muted, fontSize = 16.sp) {
                Navigator.muted = !Navigator.muted
            }
            HudButton("REPEAT", Modifier.weight(1.2f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) { Navigator.repeatInstruction() }
            HudButton(
                if (sure) "SURE?" else "END", Modifier.weight(1.2f).fillMaxHeight(), KeyKind.DANGER, selected = sure, fontSize = 13.sp
            ) {
                if (sure) MapActions.stopNavigation() else sure = true
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = Hud.colors
    Column(modifier) {
        HudText(label, color = c.dim, size = 9.sp)
        HudText(value, color = c.text, size = 16.sp, bold = true, maxLines = 1)
    }
}

// -------------------------------------------------------------------------------------------------
// measure
// -------------------------------------------------------------------------------------------------

@Composable
fun MeasurePanel(modifier: Modifier = Modifier) {
    val c = Hud.colors
    HudPanel(modifier.fillMaxWidth(), "MEASURE DISTANCE") {
        HudText(Fmt.dist(MapState.measureMeters()), color = c.good, size = 30.sp, bold = true)
        HudText(
            if (MapState.measure.isEmpty()) "TAP THE MAP TO ADD POINTS" else "${MapState.measure.size} POINTS · TAP TO ADD MORE",
            color = c.dim, size = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            HudButton("UNDO", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, enabled = MapState.measure.isNotEmpty(), fontSize = 11.sp) {
                MapState.measure = MapState.measure.dropLast(1)
            }
            HudButton("CLEAR", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, enabled = MapState.measure.isNotEmpty(), fontSize = 11.sp) {
                MapState.measure = emptyList()
            }
            HudButton("DONE", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 11.sp) {
                MapState.measuring = false
                MapState.measure = emptyList()
            }
        }
    }
}
