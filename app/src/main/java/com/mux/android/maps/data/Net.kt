package com.mux.android.maps.data

import com.mux.android.maps.AppInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tiny HTTP client for the public OpenStreetMap services. Always sends a proper User-Agent. */
object Net {
    fun get(url: String, timeoutMs: Int = 15_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", AppInfo.USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Accept-Language", "en")
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    fun json(url: String, timeoutMs: Int = 15_000): JSONObject = JSONObject(get(url, timeoutMs))
}
