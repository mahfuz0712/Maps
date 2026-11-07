package com.mux.android.maps.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Nav
import com.mux.android.maps.data.Page
import com.mux.android.maps.data.Place
import com.mux.android.maps.data.Store
import com.mux.android.maps.map.MapCtl
import com.mux.android.maps.util.copyText
import com.mux.android.maps.data.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter

// -------------------------------------------------------------------------------------------------
// saved places + recents
// -------------------------------------------------------------------------------------------------

@Composable
fun SavedScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    var open by remember { mutableStateOf(-1L) }
    val close = { Nav.page = Page.NONE }
    Overlay("SAVED PLACES", close) {
        if (Store.saved.isEmpty()) EmptyNote("NOTHING SAVED YET.\nOPEN A PLACE AND TAP ☆ SAVE, HOME OR WORK.")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(Store.saved.toList(), key = { it.id }) { s ->
                val isOpen = open == s.id
                HudItem(Modifier.fillMaxWidth(), selected = isOpen, onClick = { open = if (isOpen) -1L else s.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudText(if (s.label == "HOME") "⌂" else if (s.label == "WORK") "▣" else "★", color = c.accent, size = 20.sp, bold = true, modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            HudText(s.name, color = c.text, size = 15.sp, bold = true, maxLines = 1)
                            HudText(if (s.address.isNotEmpty()) s.address else Fmt.coord(s.lat, s.lon), color = c.dim, size = 10.sp, maxLines = 1)
                        }
                        if (s.label != "STAR") Tag(s.label, c.good)
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            HudButton("SHOW", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 11.sp) {
                                MapActions.select(s.toPlace())
                                close()
                            }
                            HudButton("GO", Modifier.weight(1f).fillMaxHeight(), KeyKind.GOOD, fontSize = 11.sp) {
                                MapActions.directions(s.toPlace())
                                close()
                            }
                            HudButton("COPY", Modifier.weight(1f).fillMaxHeight(), KeyKind.FN, fontSize = 11.sp) { copyText(ctx, Fmt.plain(s.lat, s.lon)) }
                            HudButton("DEL", Modifier.weight(1f).fillMaxHeight(), KeyKind.DANGER, fontSize = 11.sp) { Store.unsave(s) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentScreen() {
    var sure by remember { mutableStateOf(false) }
    val close = { Nav.page = Page.NONE }
    Overlay("RECENT PLACES", close, actions = {
        HudButton(if (sure) "SURE?" else "CLEAR", Modifier.width(82.dp).height(38.dp), KeyKind.DANGER, selected = sure, enabled = Store.recents.isNotEmpty(), fontSize = 11.sp) {
            if (sure) {
                Store.clearRecents()
                sure = false
            } else sure = true
        }
    }) {
        if (Store.recents.isEmpty()) EmptyNote("NO RECENT PLACES")
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            items(Store.recents.toList(), key = { "r" + it.lat + it.lon }) { p: Place ->
                PlaceRow(p, "◷") {
                    MapActions.select(p)
                    close()
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// offline maps
// -------------------------------------------------------------------------------------------------

private fun fmtBytes(b: Long): String = when {
    b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024L -> "%d KB".format(b / 1024)
    else -> "$b B"
}

@Composable
fun OfflineScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    val close = { Nav.page = Page.NONE }
    var deeper by remember { mutableStateOf(3) }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("") }
    var cacheBytes by remember { mutableStateOf(0L) }
    var cacheTick by remember { mutableStateOf(0) }
    var sure by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<CacheManager.CacheManagerTask?>(null) }

    val map = MapCtl.map
    val zMin = MapState.zoom.toInt().coerceIn(3, 17)
    val zMax = (zMin + deeper).coerceAtMost(17)
    val box = remember(MapState.centerLat, MapState.centerLon, MapState.zoom) { map?.boundingBox }
    val estimate = remember(box, zMin, zMax, AppSettings.style) {
        try {
            if (map != null && box != null) CacheManager(map).possibleTilesInArea(box, zMin, zMax) else 0
        } catch (e: Exception) {
            -1                                  // this tile server does not allow bulk download
        }
    }
    val cap = 4000

    LaunchedEffect(cacheTick) {
        cacheBytes = withContext(Dispatchers.IO) {
            try {
                val w = SqlTileWriter()
                val s = w.size
                w.onDetach()
                s
            } catch (e: Exception) {
                0L
            }
        }
    }

    Overlay("OFFLINE MAPS", close) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            HudPanel(Modifier.fillMaxWidth(), "MAP CACHE") {
                HudText("SAVED ON THIS PHONE: ${fmtBytes(cacheBytes)}", color = c.primary, size = 15.sp, bold = true)
                HudText("Every area you look at is kept automatically, so places you have seen already work without internet.", color = c.dim, size = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudText("USE ONLY SAVED TILES (OFFLINE MODE)", color = c.text, size = 12.sp, modifier = Modifier.weight(1f))
                    HudButton(
                        if (AppSettings.offlineOnly) "ON" else "OFF", Modifier.width(90.dp).height(38.dp),
                        if (AppSettings.offlineOnly) KeyKind.GOOD else KeyKind.FN, fontSize = 12.sp
                    ) { AppSettings.offlineOnly = !AppSettings.offlineOnly }
                }
            }
            Spacer(Modifier.height(8.dp))

            HudPanel(Modifier.fillMaxWidth(), "SAVE THIS AREA") {
                HudText("Downloads the part of the map you see now (zoom $zMin to $zMax) so it works without internet.", color = c.text, size = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(40.dp)) {
                    for (d in listOf(1, 2, 3, 4)) {
                        HudButton("+$d", Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, selected = deeper == d, enabled = !running, fontSize = 12.sp) { deeper = d }
                    }
                }
                HudText("+N = how many zoom levels deeper than the current view", color = c.dim, size = 9.sp)
                Spacer(Modifier.height(6.dp))
                HudText(
                    when {
                        estimate < 0 -> "THIS TILE SERVER DOES NOT ALLOW BULK DOWNLOAD (SEE NOTE). CHOOSE ANOTHER MAP STYLE OR A CUSTOM TILE SERVER."
                        AppSettings.offlineOnly -> "TURN OFFLINE MODE OFF TO DOWNLOAD"
                        estimate > cap -> "$estimate TILES - TOO MANY. ZOOM IN OR USE A SMALLER +N (MAX $cap)."
                        else -> "$estimate TILES (≈ ${fmtBytes(estimate * 20_000L)})"
                    },
                    color = if (estimate in 0..cap) c.primary else c.accent, size = 12.sp, bold = true
                )
                if (running || status.isNotEmpty()) {
                    val pct = if (total > 0) (done * 100 / total) else 0
                    HudText(if (running) "DOWNLOADING $pct%  ($done / $total)" else status, color = c.good, size = 12.sp, bold = true)
                    if (running) HudText("█".repeat((pct / 5).coerceIn(0, 20)) + "░".repeat(20 - (pct / 5).coerceIn(0, 20)), color = c.primary, size = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (!running) {
                    HudButton(
                        "DOWNLOAD", Modifier.fillMaxWidth().height(46.dp), KeyKind.GOOD,
                        enabled = estimate in 1..cap && !AppSettings.offlineOnly && map != null && box != null, fontSize = 13.sp
                    ) {
                        try {
                            running = true
                            status = ""
                            done = 0
                            total = estimate
                            task = CacheManager(map!!).downloadAreaAsyncNoUI(ctx, box!!, zMin, zMax, object : CacheManager.CacheManagerCallback {
                                override fun onTaskComplete() {
                                    running = false
                                    status = "DONE - AREA SAVED"
                                    cacheTick++
                                }

                                override fun onTaskFailed(errors: Int) {
                                    running = false
                                    status = "FINISHED WITH $errors MISSING TILES"
                                    cacheTick++
                                }

                                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                                    done = progress
                                }

                                override fun downloadStarted() {
                                }

                                override fun setPossibleTilesInArea(total: Int) {
                                }
                            })
                        } catch (e: Exception) {
                            running = false
                            status = "NOT ALLOWED FOR THIS TILE SERVER"
                        }
                    }
                } else {
                    HudButton("CANCEL", Modifier.fillMaxWidth().height(46.dp), KeyKind.DANGER, fontSize = 13.sp) {
                        task?.cancel(true)
                        running = false
                        status = "CANCELED"
                        cacheTick++
                    }
                }
                Spacer(Modifier.height(6.dp))
                HudText(
                    "NOTE: the public OpenStreetMap tile server forbids bulk downloads. Use the DARK / LIGHT styles, or your own tile server (SYS > MAP) for big areas. Be considerate with free tile servers.",
                    color = c.dim, size = 9.sp
                )
            }
            Spacer(Modifier.height(8.dp))

            HudPanel(Modifier.fillMaxWidth(), "CLEAN UP") {
                HudButton(
                    if (sure) "TAP AGAIN: DELETE ALL SAVED TILES" else "CLEAR MAP CACHE", Modifier.fillMaxWidth().height(44.dp), KeyKind.DANGER,
                    selected = sure, fontSize = 12.sp
                ) {
                    if (!sure) {
                        sure = true
                    } else {
                        sure = false
                        try {
                            val w = SqlTileWriter()
                            w.purgeCache()
                            w.onDetach()
                        } catch (e: Exception) {
                        }
                        status = "CACHE CLEARED"
                        cacheTick++
                    }
                }
                HudText("Saved places and history are kept.", color = c.dim, size = 9.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
