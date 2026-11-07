package com.mux.android.maps.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class Page { NONE, SAVED, RECENT, OFFLINE, SYS }

/** Which page / dialog is open. */
object Nav {
    var page by mutableStateOf(Page.NONE)
    var searching by mutableStateOf(false)
    var pickStart by mutableStateOf(false)         // search is choosing the route START point
    var menu by mutableStateOf(false)
    var layers by mutableStateOf(false)
    var gps by mutableStateOf(false)
    var resumeTick by mutableIntStateOf(0)
    var permTick by mutableIntStateOf(0)
}

/** Every property is Compose state AND persisted in SharedPreferences. */
object AppSettings {
    private var sp: SharedPreferences? = null
    private val registry = ArrayList<Pref<*>>()

    fun init(ctx: Context) {
        if (sp != null) return
        sp = ctx.applicationContext.getSharedPreferences("maps", Context.MODE_PRIVATE)
        registry.forEach { it.load() }
    }

    class Pref<T : Any>(private val key: String, private val def: T) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(def)

        init {
            registry.add(this)
        }

        @Suppress("UNCHECKED_CAST")
        fun load() {
            val s = sp ?: return
            val v: Any = when (def) {
                is Boolean -> s.getBoolean(key, def)
                is Int -> s.getInt(key, def)
                else -> s.getString(key, def as String) ?: def
            }
            state.value = v as T
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            val e = sp?.edit() ?: return
            when (value) {
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
                else -> e.putString(key, value as String)
            }
            e.apply()
        }
    }

    var amber by Pref("amber", false)
    var introSeen by Pref("introSeen", false)
    var askedPerms by Pref("askedPerms", false)

    // map
    var style by Pref("style", "hud")              // hud, osm, dark, light, topo, sat, custom
    var customTiles by Pref("customTiles", "")     // https://host/{z}/{x}/{y}.png
    var showScale by Pref("showScale", true)
    var rotateGestures by Pref("rotateGestures", true)
    var offlineOnly by Pref("offlineOnly", false)  // use only saved tiles
    var lastLat by Pref("lastLat", "23.8103")
    var lastLon by Pref("lastLon", "90.4125")
    var lastZoom by Pref("lastZoom", 11)

    // routing / navigation
    var mode by Pref("mode", "car")                // car, bike, walk
    var units by Pref("units", "KM")               // KM, MI
    var voice by Pref("voice", true)
    var keepOn by Pref("keepOn", true)
    var headingUp by Pref("headingUp", true)       // rotate the map with the heading while navigating
    var routeServer by Pref("routeServer", "")     // custom OSRM base url, "" = public default
}
