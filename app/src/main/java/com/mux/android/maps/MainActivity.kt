package com.mux.android.maps

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Fmt
import com.mux.android.maps.data.Geocoder
import com.mux.android.maps.data.Loc
import com.mux.android.maps.data.MapActions
import com.mux.android.maps.data.MapState
import com.mux.android.maps.data.Nav
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.Page
import com.mux.android.maps.data.Place
import com.mux.android.maps.map.MapCtl
import com.mux.android.maps.ui.MapsRoot
import com.mux.android.maps.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handleGeo(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    Nav.searching -> {
                        Nav.searching = false
                        Nav.pickStart = false
                    }
                    Nav.layers -> Nav.layers = false
                    Nav.menu -> Nav.menu = false
                    Nav.gps -> Nav.gps = false
                    Nav.page != Page.NONE -> Nav.page = Page.NONE
                    MapState.measuring -> {
                        MapState.measuring = false
                        MapState.measure = emptyList()
                    }
                    Navigator.active -> moveTaskToBack(true)
                    MapState.routeTo != null -> MapActions.clearRoute()
                    MapState.pin != null -> MapActions.clearPin()
                    MapState.poiCat != null -> MapActions.clearPois()
                    else -> finish()
                }
            }
        })
        setContent { MapsRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGeo(intent)
    }

    /** geo:23.78,90.41   geo:0,0?q=cafe   geo:0,0?q=23.78,90.41(Label) */
    private fun handleGeo(i: Intent?) {
        val uri = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW || uri.scheme != "geo") return
        i.data = null
        val ssp = uri.schemeSpecificPart ?: return
        val coord = ssp.substringBefore('?').split(',')
        var lat = coord.getOrNull(0)?.trim()?.toDoubleOrNull()
        var lon = coord.getOrNull(1)?.trim()?.toDoubleOrNull()
        var q = ssp.substringAfter('?', "").split('&').firstOrNull { it.startsWith("q=") }?.substring(2)?.let { Uri.decode(it) }
        var label: String? = null
        if (q != null) {
            val m = Regex("^(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)(?:\\((.*)\\))?$").find(q.trim())
            if (m != null) {
                lat = m.groupValues[1].toDouble()
                lon = m.groupValues[2].toDouble()
                label = m.groupValues[3].ifEmpty { null }
                q = null
            }
        }
        if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) {
            if (label != null) MapActions.select(Place(label, Fmt.coord(lat, lon), lat, lon), 16.0)
            else {
                MapActions.dropPin(lat, lon)
                MapActions.fly(lat, lon, 16.0)
            }
        } else if (!q.isNullOrBlank()) {
            val query = q
            scope.launch {
                val r = Geocoder.search(query, MapState.centerLat, MapState.centerLon, 1).firstOrNull()
                if (r != null) MapActions.select(r) else toast(this@MainActivity, "NOTHING FOUND FOR \"$query\"")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Loc.start()
    }

    override fun onStop() {
        if (!Navigator.active) Loc.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        MapCtl.onResume()
        Nav.resumeTick++
    }

    override fun onPause() {
        MapCtl.onPause()
        super.onPause()
    }
}
