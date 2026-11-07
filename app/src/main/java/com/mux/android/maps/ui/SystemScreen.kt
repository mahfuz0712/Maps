package com.mux.android.maps.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.maps.AppInfo
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Nav
import com.mux.android.maps.data.Page
import com.mux.android.maps.map.MapCtl
import com.mux.android.maps.map.Styles
import com.mux.android.maps.util.openAppSettings
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

/** SYS: settings, data sources, permissions, updates, developer info. */
@Composable
fun SystemScreen(tablet: Boolean, onUpdateFound: (UpdateInfo) -> Unit) {
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { link -> runCatching { uri.openUri(link) } }
    Overlay("SYSTEM", { Nav.page = Page.NONE }) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (tablet) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        AppPanel(); Gap(); MapPanel(); Gap(); NavPanel(); Gap(); ServersPanel()
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        DataPanel(); Gap(); PermissionsPanel(); Gap(); UpdatePanel(onUpdateFound); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
                    }
                }
            } else {
                AppPanel(); Gap(); MapPanel(); Gap(); NavPanel(); Gap(); ServersPanel(); Gap(); DataPanel(); Gap()
                PermissionsPanel(); Gap(); UpdatePanel(onUpdateFound); Gap(); AuthorPanel(open); Gap(); MoreAppsPanel(open)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Gap() = Spacer(Modifier.height(8.dp))

@Composable
private fun SettingRow(label: String, hint: String?, value: String, kind: KeyKind = KeyKind.OP, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            HudText(label, color = Hud.colors.text, size = 12.sp)
            if (hint != null) HudText(hint, color = Hud.colors.dim, size = 9.sp)
        }
        HudButton(value, Modifier.width(118.dp).height(38.dp), kind, fontSize = 11.sp, onClick = onClick)
    }
}

@Composable
private fun ToggleRow(label: String, hint: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(label, hint, if (value) "ON" else "OFF", if (value) KeyKind.GOOD else KeyKind.FN) { onChange(!value) }
}

@Composable
private fun AppPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "SYSTEM") {
        HudText("◈ ${AppInfo.NAME.uppercase()}", color = c.primary, size = 28.sp, bold = true)
        HudText("VERSION ${appVersion(LocalContext.current)}", color = c.accent, size = 12.sp)
        Spacer(Modifier.height(8.dp))
        HudText("A free map app built on OpenStreetMap. No Google services, no account, no ads, no tracking.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        SettingRow("COLOR THEME", null, if (AppSettings.amber) "AMBER" else "CYAN", KeyKind.ACCENT) { AppSettings.amber = !AppSettings.amber }
    }
}

@Composable
private fun MapPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "MAP") {
        val st = Styles.byKey(AppSettings.style)
        SettingRow("MAP STYLE", st.note.uppercase(), st.label, KeyKind.ACCENT) {
            val i = Styles.all.indexOfFirst { it.key == AppSettings.style }
            AppSettings.style = Styles.all[(i + 1) % Styles.all.size].key
        }
        HudText("CUSTOM TILE SERVER  (https://host/{z}/{x}/{y}.png)", color = c.dim, size = 10.sp)
        HudField(AppSettings.customTiles, "https://", Modifier.fillMaxWidth().height(44.dp)) { AppSettings.customTiles = it.trim() }
        Spacer(Modifier.height(4.dp))
        ToggleRow("SCALE BAR", null, AppSettings.showScale) { AppSettings.showScale = it }
        ToggleRow("ROTATE WITH TWO FINGERS", null, AppSettings.rotateGestures) { AppSettings.rotateGestures = it }
        ToggleRow("OFFLINE MODE", "USE ONLY TILES SAVED ON THE PHONE", AppSettings.offlineOnly) { AppSettings.offlineOnly = it }
    }
}

@Composable
private fun NavPanel() {
    HudPanel(Modifier.fillMaxWidth(), "NAVIGATION") {
        val modes = listOf("car" to "CAR", "bike" to "BIKE", "walk" to "WALK")
        SettingRow("DEFAULT TRAVEL MODE", null, modes.first { it.first == AppSettings.mode }.second) {
            val i = modes.indexOfFirst { it.first == AppSettings.mode }
            AppSettings.mode = modes[(i + 1) % modes.size].first
        }
        SettingRow("UNITS", null, if (AppSettings.units == "KM") "KILOMETERS" else "MILES") {
            AppSettings.units = if (AppSettings.units == "KM") "MI" else "KM"
        }
        ToggleRow("VOICE GUIDANCE", "ENGLISH, ANDROID'S OWN TEXT-TO-SPEECH", AppSettings.voice) { AppSettings.voice = it }
        ToggleRow("MAP TURNS WITH YOU", "HEADING-UP WHILE NAVIGATING", AppSettings.headingUp) { AppSettings.headingUp = it }
        ToggleRow("KEEP SCREEN ON", "WHILE NAVIGATING", AppSettings.keepOn) { AppSettings.keepOn = it }
    }
}

@Composable
private fun ServersPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "SERVERS") {
        HudText("ROUTING SERVER (OSRM)  -  EMPTY = PUBLIC DEFAULT", color = c.dim, size = 10.sp)
        HudField(AppSettings.routeServer, "https://your-osrm-server", Modifier.fillMaxWidth().height(44.dp)) { AppSettings.routeServer = it.trim() }
        Spacer(Modifier.height(6.dp))
        HudText(
            "The public servers are free for light, personal use. For heavy use or your own business, run your own OSRM / tile server and put its address here.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun DataPanel() {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "DATA & PRIVACY") {
        HudText("MAP DATA  © OpenStreetMap contributors (ODbL).", color = c.text, size = 11.sp)
        HudText("TILES  OpenStreetMap, CARTO, OpenTopoMap, Esri (per the style you choose).", color = c.text, size = 11.sp)
        HudText("SEARCH  Photon (komoot) + Nominatim.   ROUTES  OSRM / routing.openstreetmap.de.   PLACES  Overpass API.", color = c.text, size = 11.sp)
        Spacer(Modifier.height(6.dp))
        HudText(
            "YOUR LOCATION STAYS ON THE PHONE. It is only used to draw the blue marker and to guide you. When you search or ask for a route, the words you typed and the start/end coordinates are sent to the servers above - nothing else, and no account or ID.",
            color = c.dim, size = 9.sp
        )
    }
}

@Composable
private fun PermissionsPanel() {
    val c = Hud.colors
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Nav.permTick++ }
    val all = remember {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        l
    }
    val status = remember(Nav.permTick, Nav.resumeTick) {
        all.map { it to (ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED) }
    }
    HudPanel(Modifier.fillMaxWidth(), "PERMISSIONS") {
        for ((p, ok) in status) {
            val label = when (p) {
                Manifest.permission.ACCESS_FINE_LOCATION -> "PRECISE LOCATION (GPS)"
                Manifest.permission.ACCESS_COARSE_LOCATION -> "APPROXIMATE LOCATION"
                else -> "NOTIFICATIONS (NAVIGATION BAR)"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                HudText(label, color = c.text, size = 11.sp, modifier = Modifier.weight(1f))
                HudText(if (ok) "OK" else "MISSING", color = if (ok) c.good else c.danger, size = 11.sp, bold = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HudButton("GRANT ALL", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, enabled = status.any { !it.second }, fontSize = 12.sp) {
                val m = all.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
                if (m.isNotEmpty()) launcher.launch(m.toTypedArray())
            }
            HudButton("APP SETTINGS", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 12.sp) { openAppSettings(ctx) }
        }
    }
}

@Composable
private fun UpdatePanel(onUpdateFound: (UpdateInfo) -> Unit) {
    val c = Hud.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    HudPanel(Modifier.fillMaxWidth(), "UPDATES") {
        HudButton("CHECK FOR UPDATES", Modifier.fillMaxWidth().height(46.dp), KeyKind.OP, fontSize = 13.sp) {
            scope.launch {
                status = "CHECKING..."
                if (!isOnline(ctx)) {
                    status = "NO INTERNET CONNECTION"
                    return@launch
                }
                val (ok, info) = checkUpdate(ctx)
                status = when {
                    !ok -> "CHECK FAILED"
                    info == null -> "UP TO DATE"
                    else -> "NEW VERSION AVAILABLE: v${info.version}"
                }
                if (info != null) onUpdateFound(info)
            }
        }
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HudText(status, color = c.dim, size = 12.sp)
        }
    }
}

@Composable
private fun AuthorPanel(open: (String) -> Unit) {
    HudPanel(Modifier.fillMaxWidth(), "AUTHOR") {
        LinkRow("NAME", AppInfo.AUTHOR, null, open)
        LinkRow("EMAIL", AppInfo.EMAIL, "mailto:${AppInfo.EMAIL}", open)
        LinkRow("GITHUB", "github.com/mahfuz0712", AppInfo.GITHUB, open)
        LinkRow("PORTFOLIO", "portfolio-mahfuz0712.vercel.app", AppInfo.PORTFOLIO, open)
    }
}

@Composable
private fun MoreAppsPanel(open: (String) -> Unit) {
    val c = Hud.colors
    HudPanel(Modifier.fillMaxWidth(), "MORE APPS") {
        HudText("Discover more apps by the developer on zApps Store.", color = c.text, size = 13.sp)
        Spacer(Modifier.height(8.dp))
        HudButton("OPEN zApps STORE ▸", Modifier.fillMaxWidth().height(48.dp), KeyKind.ACCENT, fontSize = 14.sp) { open(AppInfo.MORE_APPS) }
    }
}

@Composable
private fun LinkRow(label: String, value: String, link: String?, open: (String) -> Unit) {
    val c = Hud.colors
    Column(
        Modifier.fillMaxWidth().then(if (link != null) Modifier.clickable { open(link) } else Modifier).padding(vertical = 6.dp)
    ) {
        HudText(label, color = c.dim, size = 10.sp)
        HudText(value, color = if (link != null) c.primary else c.text, size = 16.sp)
    }
}
