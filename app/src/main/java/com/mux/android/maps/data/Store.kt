package com.mux.android.maps.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Saved places and recent searches, kept in small JSON files. */
object Store {
    private var dir: File? = null
    private val io = Executors.newSingleThreadExecutor()

    val saved = mutableStateListOf<Saved>()
    val recents = mutableStateListOf<Place>()

    fun init(ctx: Context) {
        if (dir != null) return
        dir = ctx.applicationContext.filesDir
        try {
            val a = JSONArray(read("saved.json") ?: "[]")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                saved.add(Saved(o.getLong("i"), o.getString("n"), o.getDouble("a"), o.getDouble("o"), o.optString("l"), o.optString("d")))
            }
            val r = JSONArray(read("recents.json") ?: "[]")
            for (i in 0 until r.length()) {
                val o = r.getJSONObject(i)
                recents.add(Place(o.getString("n"), o.optString("d"), o.getDouble("a"), o.getDouble("o"), o.optString("k")))
            }
        } catch (e: Exception) {
        }
    }

    private fun read(name: String): String? = try {
        File(dir, name).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    private fun write(name: String, text: String) {
        val d = dir ?: return
        io.execute {
            try {
                File(d, name).writeText(text)
            } catch (e: Exception) {
            }
        }
    }

    private fun saveSaved() {
        val a = JSONArray()
        saved.toList().forEach {
            a.put(JSONObject().put("i", it.id).put("n", it.name).put("a", it.lat).put("o", it.lon).put("l", it.label).put("d", it.address))
        }
        write("saved.json", a.toString())
    }

    private fun saveRecents() {
        val a = JSONArray()
        recents.toList().forEach {
            a.put(JSONObject().put("n", it.name).put("d", it.address).put("a", it.lat).put("o", it.lon).put("k", it.kind))
        }
        write("recents.json", a.toString())
    }

    // ---- saved places -------------------------------------------------------------------------

    fun isSaved(lat: Double, lon: Double): Saved? = saved.firstOrNull { Geo.dist(it.lat, it.lon, lat, lon) < 15.0 }

    fun home(): Saved? = saved.firstOrNull { it.label == "HOME" }
    fun work(): Saved? = saved.firstOrNull { it.label == "WORK" }

    /** [label] HOME / WORK are unique: setting one replaces the old. */
    fun save(p: Place, label: String = "STAR", name: String = p.name) {
        if (label == "HOME" || label == "WORK") saved.removeAll { it.label == label }
        saved.removeAll { Geo.dist(it.lat, it.lon, p.lat, p.lon) < 15.0 }
        saved.add(0, Saved(System.currentTimeMillis(), name, p.lat, p.lon, label, p.address))
        saveSaved()
    }

    fun unsave(s: Saved) {
        saved.remove(s)
        saveSaved()
    }

    // ---- recents ------------------------------------------------------------------------------

    fun addRecent(p: Place) {
        recents.removeAll { Geo.dist(it.lat, it.lon, p.lat, p.lon) < 15.0 }
        recents.add(0, p)
        while (recents.size > 30) recents.removeAt(recents.size - 1)
        saveRecents()
    }

    fun clearRecents() {
        recents.clear()
        saveRecents()
    }
}
