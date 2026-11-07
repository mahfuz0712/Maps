package com.mux.android.maps.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Fmt
import com.mux.android.maps.data.Loc
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Nav
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.Page
import com.mux.android.maps.map.MapCtl
import com.mux.android.maps.map.Styles
import com.mux.android.maps.util.copyText
import com.mux.android.maps.util.openSettingsAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LayersDialog() {
    val c = Hud.colors
    HudDialog("MAP LAYERS", onDismiss = { Nav.layers = false }) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            for (s in Styles.all) {
                val sel = AppSettings.style == s.key
                HudButton(
                    s.label, Modifier.fillMaxWidth().height(46.dp), if (sel) KeyKind.GOOD else KeyKind.OP, selected = sel,
                    fontSize = 13.sp, sub = s.note.uppercase().take(40)
                ) { AppSettings.style = s.key }
            }
            if (AppSettings.style == "custom" && AppSettings.customTiles.isBlank()) {
                HudText("SET YOUR TILE SERVER ADDRESS IN  SYS > MAP", color = c.accent, size = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText("SCALE BAR", color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
                HudButton(if (AppSettings.showScale) "ON" else "OFF", Modifier.width(80.dp).height(36.dp), if (AppSettings.showScale) KeyKind.GOOD else KeyKind.FN, fontSize = 11.sp) {
                    AppSettings.showScale = !AppSettings.showScale
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText("OFFLINE MODE", color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
                HudButton(if (AppSettings.offlineOnly) "ON" else "OFF", Modifier.width(80.dp).height(36.dp), if (AppSettings.offlineOnly) KeyKind.GOOD else KeyKind.FN, fontSize = 11.sp) {
                    AppSettings.offlineOnly = !AppSettings.offlineOnly
                }
            }
        }
        DialogButtons(Triple("CLOSE", KeyKind.FN) { Nav.layers = false })
    }
}

@Composable
fun MenuDialog() {
    val close = { Nav.menu = false }
    HudDialog("MENU", onDismiss = close) {
        val items = listOf(
            Triple("★ SAVED PLACES", KeyKind.ACCENT) { close(); Nav.page = Page.SAVED },
            Triple("◷ RECENT", KeyKind.OP) { close(); Nav.page = Page.RECENT },
            Triple("⇩ OFFLINE MAPS", KeyKind.OP) { close(); Nav.page = Page.OFFLINE },
            Triple("📏 MEASURE", KeyKind.OP) {
                close()
                MapActions.clearPin()
                MapState.measuring = true
                MapState.measure = emptyList()
            },
            Triple("◎ GPS INFO", KeyKind.OP) { close(); Nav.gps = true },
            Triple("⚙ SETTINGS", KeyKind.FN) { close(); Nav.page = Page.SYS }
        )
        for (row in items.chunked(2)) {
            Row(Modifier.fillMaxWidth().height(54.dp)) {
                for ((label, kind, run) in row) HudButton(label, Modifier.weight(1f).fillMaxHeight(), kind, fontSize = 11.sp, onClick = run)
            }
        }
        DialogButtons(Triple("CLOSE", KeyKind.FN) { close() })
    }
}

@Composable
fun GpsDialog() {
    val ctx = LocalContext.current
    val c = Hud.colors
    val f = Loc.fix
    HudDialog("GPS STATUS", onDismiss = { Nav.gps = false }) {
        if (!Loc.hasPermission()) {
            HudText("LOCATION PERMISSION IS MISSING (SYS > PERMISSIONS)", color = c.danger, size = 12.sp, bold = true)
        } else if (!Loc.gpsEnabled()) {
            HudText("LOCATION IS TURNED OFF", color = c.danger, size = 12.sp, bold = true)
            Spacer(Modifier.height(6.dp))
            HudButton("OPEN LOCATION SETTINGS", Modifier.fillMaxWidth().height(42.dp), KeyKind.ACCENT, fontSize = 11.sp) {
                openSettingsAction(ctx, AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
        } else if (f == null) {
            HudText("WAITING FOR A GPS FIX...\nGo outside or near a window.", color = c.accent, size = 12.sp, bold = true)
        } else {
            Row2("POSITION", Fmt.coord(f.latitude, f.longitude))
            Row2("ACCURACY", if (f.hasAccuracy()) "± ${Fmt.dist(f.accuracy.toDouble())}" else "-")
            Row2("ALTITUDE", if (f.hasAltitude()) "${f.altitude.toInt()} m" else "-")
            Row2("SPEED", if (f.hasSpeed()) "${Fmt.speed(f.speed)} ${Fmt.speedUnit()}" else "-")
            Row2("HEADING", if (Loc.hasCompass) "${Loc.heading.toInt()}°" else "NO COMPASS")
            Row2("SOURCE", (f.provider ?: "-").uppercase())
            Row2("UPDATED", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(f.time)))
        }
        Spacer(Modifier.height(6.dp))
        DialogButtons(
            Triple("COPY", KeyKind.OP) { if (f != null) copyText(ctx, Fmt.plain(f.latitude, f.longitude)) },
            Triple("CLOSE", KeyKind.FN) { Nav.gps = false }
        )
    }
}

@Composable
private fun Row2(label: String, value: String) {
    val c = Hud.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        HudText(label, color = c.dim, size = 11.sp, modifier = Modifier.width(92.dp))
        HudText(value, color = c.text, size = 13.sp, bold = true, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ArrivalDialog() {
    val c = Hud.colors
    HudDialog("YOU HAVE ARRIVED", onDismiss = { MapActions.stopNavigation() }) {
        HudText(Navigator.destination?.name ?: "", color = c.primary, size = 22.sp, bold = true, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        HudText("DISTANCE LEFT: ${Fmt.dist(Navigator.remaining)}", color = c.dim, size = 11.sp)
        DialogButtons(Triple("DONE", KeyKind.GOOD) { MapActions.stopNavigation() })
    }
}

@Composable
fun IntroDialog() {
    val c = Hud.colors
    HudDialog("WELCOME", onDismiss = { AppSettings.introSeen = true }) {
        HudText("◈ MAPS", color = c.primary, size = 22.sp, bold = true)
        Spacer(Modifier.height(8.dp))
        HudText("A map app made only with free, open data (OpenStreetMap). No Google, no account, no ads.", color = c.text, size = 12.sp)
        Spacer(Modifier.height(6.dp))
        HudText("• Long-press the map to drop a pin, then tap DIRECTIONS.", color = c.text, size = 11.sp)
        HudText("• Search places, or use the NEARBY categories (food, fuel, pharmacy, mosque...).", color = c.text, size = 11.sp)
        HudText("• Turn-by-turn voice navigation for car, bike and walking.", color = c.text, size = 11.sp)
        HudText("• Areas you view are kept on the phone and work offline.", color = c.text, size = 11.sp)
        HudText("• Allow Location when asked, so the map can show where you are.", color = c.accent, size = 11.sp)
        DialogButtons(Triple("LET'S GO", KeyKind.GOOD) { AppSettings.introSeen = true })
    }
}
