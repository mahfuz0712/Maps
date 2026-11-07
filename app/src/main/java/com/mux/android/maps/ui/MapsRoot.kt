package com.mux.android.maps.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Cam
import com.mux.android.maps.data.Loc
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Nav
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.Overpass
import com.mux.android.maps.data.Page
import com.mux.android.maps.data.UiMode
import com.mux.android.maps.map.MapCtl
import com.mux.android.maps.util.toast
import kotlin.math.abs

@Composable
fun MapsRoot() {
    val ctx = LocalContext.current
    HudTheme(if (AppSettings.amber) AmberPalette else CyanPalette) {
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { if (isOnline(ctx)) update = checkUpdate(ctx).second }

        // location (+ notification) permission, asked once on first launch
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Nav.permTick++ }
        val permList = remember {
            val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
            l
        }
        LaunchedEffect(Unit) {
            if (!AppSettings.askedPerms) {
                AppSettings.askedPerms = true
                if (!Loc.hasPermission()) permLauncher.launch(permList.toTypedArray())
            }
        }
        LaunchedEffect(Nav.permTick, Nav.resumeTick) {
            Loc.start()
            // first time we know where the user is: show it
            val f = Loc.fix
            if (f != null && AppSettings.lastLat == "23.8103" && AppSettings.lastLon == "90.4125") {
                MapState.camera = Cam(f.latitude, f.longitude, 15.0)
                AppSettings.lastLat = f.latitude.toString()
                AppSettings.lastLon = f.longitude.toString()
            }
        }

        // screen stays on while navigating
        LaunchedEffect(Navigator.active, AppSettings.keepOn) {
            val w = (ctx as? Activity)?.window
            if (w != null) {
                if (Navigator.active && AppSettings.keepOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tablet = maxWidth >= 600.dp
            val panelW = if (tablet) Modifier.widthIn(max = 440.dp).fillMaxWidth() else Modifier.fillMaxWidth()

            MapHost(Modifier.fillMaxSize())

            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)) {
                // ---- top
                Column(Modifier.align(Alignment.TopStart).then(panelW)) {
                    when (MapState.ui) {
                        UiMode.NAV -> NavBanner()
                        UiMode.ROUTE -> RouteTop()
                        else -> if (!MapState.measuring) {
                            SearchBar()
                            if (MapState.pin == null) CategoryChips()
                        }
                    }
                }

                // ---- right side buttons
                SideControls(Modifier.align(Alignment.CenterEnd), permLauncher = { permLauncher.launch(permList.toTypedArray()) })

                // ---- bottom
                Column(Modifier.align(Alignment.BottomStart).then(panelW)) {
                    when {
                        MapState.measuring -> MeasurePanel()
                        MapState.ui == UiMode.NAV -> NavBottom()
                        MapState.ui == UiMode.ROUTE -> RoutePanel()
                        MapState.ui == UiMode.PLACE -> MapState.pin?.let { PlaceCard(it, MapState.pinLoading) }
                        MapState.poiCat != null -> PoiPanel()
                    }
                }
            }

            // ---- pages and dialogs
            when (Nav.page) {
                Page.SAVED -> SavedScreen()
                Page.RECENT -> RecentScreen()
                Page.OFFLINE -> OfflineScreen()
                Page.SYS -> SystemScreen(tablet) { update = it }
                Page.NONE -> {}
            }
            if (Nav.searching) SearchScreen()
        }

        if (Nav.menu) MenuDialog()
        if (Nav.layers) LayersDialog()
        if (Nav.gps) GpsDialog()
        if (Navigator.arrived) ArrivalDialog()
        if (!AppSettings.introSeen) IntroDialog()
        update?.let { u ->
            UpdateDialog(
                u, appVersion(ctx),
                onUpdate = { openUrl(ctx, u.apkUrl); update = null },
                onCancel = { update = null }
            )
        }
    }
}

@Composable
private fun MapHost(modifier: Modifier) {
    AndroidView(
        factory = { c -> MapCtl.create(c) },
        modifier = modifier,
        onRelease = { MapCtl.release() },
        update = { MapCtl.sync() }
    )
}

@Composable
private fun SearchBar() {
    val c = Hud.colors
    val shape = HudShape(8.dp)
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        HudButton("☰", Modifier.width(50.dp).fillMaxHeight(), KeyKind.OP, fontSize = 20.sp) { Nav.menu = true }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(2.dp)
                .background(c.bg.copy(alpha = 0.88f), shape)
                .border(1.dp, c.primary.copy(alpha = 0.6f), shape)
                .clickable {
                    Nav.pickStart = false
                    Nav.searching = true
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            HudText("⌕  SEARCH PLACES", color = c.dim, size = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryChips() {
    val c = Hud.colors
    Box(Modifier.fillMaxWidth().background(c.bg.copy(alpha = 0.55f))) {
        ChipRow(
            Overpass.cats.map { it.key to "${it.icon} ${it.label}" },
            MapState.poiCat ?: ""
        ) { key -> Overpass.cat(key)?.let { MapActions.showCategory(it) } }
    }
}

@Composable
private fun SideControls(modifier: Modifier, permLauncher: () -> Unit) {
    val ctx = LocalContext.current
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (abs(MapState.rotation) > 1f) {
            HudButton("N", Modifier.size(46.dp), KeyKind.ACCENT, fontSize = 15.sp, sub = "${MapState.rotation.toInt()}°") { MapCtl.resetNorth() }
        }
        if (MapState.ui != UiMode.NAV) {
            HudButton("▤", Modifier.size(46.dp), KeyKind.OP, fontSize = 18.sp) { Nav.layers = true }
        }
        HudButton(
            when (MapState.follow) { 1 -> "◉"; 2 -> "➤"; else -> "◎" },
            Modifier.size(46.dp), KeyKind.GOOD, selected = MapState.follow > 0, fontSize = 20.sp
        ) { locate(ctx, permLauncher) }
        HudButton("+", Modifier.size(46.dp), KeyKind.OP, fontSize = 22.sp) { MapCtl.zoomIn() }
        HudButton("−", Modifier.size(46.dp), KeyKind.OP, fontSize = 22.sp) { MapCtl.zoomOut() }
    }
}

/** ◎ -> center on me -> follow with heading -> back to north-up follow. */
private fun locate(ctx: Context, request: () -> Unit) {
    if (!Loc.hasPermission()) {
        request()
        return
    }
    Loc.start()
    val f = Loc.fix
    if (f == null) {
        toast(ctx, if (!Loc.gpsEnabled()) "TURN ON LOCATION" else "WAITING FOR GPS...")
        return
    }
    when (MapState.follow) {
        0 -> {
            MapState.follow = 1
            MapState.camera = Cam(f.latitude, f.longitude, maxOf(MapState.zoom, 16.0))
        }
        1 -> MapState.follow = 2
        else -> MapCtl.resetNorth()
    }
}
