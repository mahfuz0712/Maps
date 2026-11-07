package com.mux.android.maps.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mux.android.maps.data.Geocoder
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Nav
import com.mux.android.maps.data.Overpass
import com.mux.android.maps.data.Place
import com.mux.android.maps.data.Saved
import com.mux.android.maps.data.Store
import com.mux.android.maps.util.toast
import kotlinx.coroutines.delay

fun Saved.toPlace() = Place(name, address, lat, lon, "saved")

@Composable
fun SearchScreen() {
    val ctx = LocalContext.current
    val c = Hud.colors
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val close = {
        Nav.searching = false
        Nav.pickStart = false
    }

    LaunchedEffect(q) {
        results = emptyList()
        val t = q.trim()
        if (t.length < 2) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(400)                      // wait until the user stops typing (be kind to the free server)
        results = Geocoder.search(t, MapState.centerLat, MapState.centerLon)
        loading = false
    }

    fun pick(p: Place) {
        Store.addRecent(p)
        if (Nav.pickStart) MapActions.setFrom(p) else MapActions.select(p)
        close()
    }

    Overlay(if (Nav.pickStart) "CHOOSE START POINT" else "SEARCH", onClose = close) {
        HudField(
            q, "SEARCH PLACES OR ENTER  LAT, LON", Modifier.fillMaxWidth().height(48.dp),
            ime = ImeAction.Search, autoFocus = true
        ) { q = it }
        Spacer(Modifier.height(6.dp))

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (q.trim().length >= 2) {
                if (loading) item { HudText("SEARCHING...", color = c.accent, size = 12.sp, bold = true, modifier = Modifier.padding(8.dp)) }
                if (!loading && results.isEmpty()) item { EmptyNote("NO PLACES FOUND.\nTRY ANOTHER NAME OR ADD THE CITY.") }
                items(results, key = { it.lat.toString() + it.lon + it.name }) { p -> PlaceRow(p) { pick(p) } }
            } else {
                if (Nav.pickStart) {
                    item {
                        HudButton("◎  USE MY LOCATION", Modifier.fillMaxWidth().height(46.dp), KeyKind.GOOD, fontSize = 13.sp) {
                            MapActions.setFrom(null)
                            close()
                        }
                    }
                } else {
                    item {
                        Row(Modifier.fillMaxWidth().height(46.dp)) {
                            val home = Store.home()
                            val work = Store.work()
                            HudButton(if (home != null) "⌂ GO HOME" else "⌂ SET HOME", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, fontSize = 12.sp) {
                                if (home != null) {
                                    MapActions.directions(home.toPlace())
                                    close()
                                } else toast(ctx, "OPEN A PLACE AND TAP \"HOME\"")
                            }
                            HudButton(if (work != null) "▣ GO WORK" else "▣ SET WORK", Modifier.weight(1f).fillMaxHeight(), KeyKind.ACCENT, fontSize = 12.sp) {
                                if (work != null) {
                                    MapActions.directions(work.toPlace())
                                    close()
                                } else toast(ctx, "OPEN A PLACE AND TAP \"WORK\"")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HudText("NEARBY", color = c.dim, size = 10.sp)
                        for (row in Overpass.cats.chunked(3)) {
                            Row(Modifier.fillMaxWidth().height(54.dp)) {
                                for (cat in row) {
                                    HudButton(cat.icon, Modifier.weight(1f).fillMaxHeight(), KeyKind.OP, fontSize = 18.sp, sub = cat.label) {
                                        close()
                                        MapActions.showCategory(cat)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (Store.recents.isNotEmpty()) {
                    item { HudText("RECENT", color = c.dim, size = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
                    items(Store.recents.take(8), key = { "r" + it.lat + it.lon }) { p -> PlaceRow(p, "◷") { pick(p) } }
                }
                if (Store.saved.isNotEmpty() && !Nav.pickStart) {
                    item { HudText("SAVED", color = c.dim, size = 10.sp, modifier = Modifier.padding(top = 8.dp)) }
                    items(Store.saved.toList(), key = { "s" + it.id }) { s ->
                        PlaceRow(s.toPlace(), if (s.label == "HOME") "⌂" else if (s.label == "WORK") "▣" else "★") { pick(s.toPlace()) }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceRow(p: Place, icon: String = "⌖", onClick: () -> Unit) {
    val c = Hud.colors
    HudItem(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudText(icon, color = c.accent, size = 18.sp, bold = true, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                HudText(p.name, color = c.text, size = 15.sp, bold = true, maxLines = 1)
                if (p.address.isNotEmpty()) HudText(p.address, color = c.dim, size = 10.sp, maxLines = 1)
            }
            HudText(distanceText(p.lat, p.lon).substringBefore(" AWAY").substringBefore(" FROM"), color = c.dim, size = 10.sp)
        }
    }
}
