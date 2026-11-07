package com.mux.android.maps

import android.app.Application
import com.mux.android.maps.data.AppSettings
import com.mux.android.maps.data.Loc
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.Store
import org.osmdroid.config.Configuration
import java.io.File

class MapsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Store.init(this)
        Loc.init(this)
        Navigator.init(this)

        // osmdroid: keep the tile cache in the app's own folder (no storage permission) and identify ourselves
        val c = Configuration.getInstance()
        c.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        c.userAgentValue = AppInfo.USER_AGENT
        val base = File(getExternalFilesDir(null) ?: filesDir, "osmdroid")
        base.mkdirs()
        c.osmdroidBasePath = base
        c.osmdroidTileCache = File(base, "tiles")
        c.tileFileSystemCacheMaxBytes = 600L * 1024 * 1024
        c.tileFileSystemCacheTrimBytes = 500L * 1024 * 1024
    }
}
