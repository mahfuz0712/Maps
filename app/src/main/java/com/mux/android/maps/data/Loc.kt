package com.mux.android.maps.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * "Where am I?" using only Android's own LocationManager (GPS + network) and the compass sensor.
 * No Google Play services / fused location.
 */
object Loc {
    var fix by mutableStateOf<Location?>(null)
        private set
    var heading by mutableFloatStateOf(0f)
        private set
    var hasCompass by mutableStateOf(false)
        private set

    private lateinit var app: Context
    private var started = false
    private var lastGps = 0L
    private val listeners = ArrayList<(Location) -> Unit>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(): Boolean = try {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) {
        false
    }

    fun addListener(l: (Location) -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: (Location) -> Unit) {
        listeners.remove(l)
    }

    private fun accept(l: Location): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (l.provider == LocationManager.GPS_PROVIDER) {
            lastGps = now
            return true
        }
        return now - lastGps > 15_000          // network fixes only while GPS is silent
    }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!accept(location)) return
            fix = location
            for (l in listeners.toList()) l(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
            restart()
        }

        override fun onProviderDisabled(provider: String) {
            restart()
        }
    }

    fun start() {
        if (started || !hasPermission()) return
        started = true
        try {
            val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 0f, locListener, Looper.getMainLooper())
            }
            if (fix == null) {
                val last = listOfNotNull(
                    lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                    lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ).maxByOrNull { it.time }
                if (last != null) fix = last
            }
        } catch (e: SecurityException) {
            started = false
        } catch (e: Exception) {
        }
        startCompass()
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            (app.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(locListener)
        } catch (e: Exception) {
        }
        stopCompass()
    }

    fun restart() {
        stop()
        start()
    }

    // ---------------------------------------------------------------------------------------------
    // compass
    // ---------------------------------------------------------------------------------------------

    private var sensors: SensorManager? = null
    private var curHeading = 0f
    private var lastPush = 0L

    private val sensorListener = object : SensorEventListener {
        private val rot = FloatArray(9)
        private val out = FloatArray(9)
        private val ori = FloatArray(3)

        override fun onSensorChanged(ev: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rot, ev.values)
            val r = try {
                @Suppress("DEPRECATION")
                (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
            when (r) {
                Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, out)
                Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, out)
                Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, out)
                else -> System.arraycopy(rot, 0, out, 0, 9)
            }
            SensorManager.getOrientation(out, ori)
            var az = Math.toDegrees(ori[0].toDouble()).toFloat()
            val f = fix
            if (f != null) {
                az += GeomagneticField(f.latitude.toFloat(), f.longitude.toFloat(), f.altitude.toFloat(), System.currentTimeMillis()).declination
            }
            val d = (((az - curHeading) % 360f) + 540f) % 360f - 180f     // shortest turn, smoothed
            curHeading = (curHeading + d * 0.25f + 360f) % 360f
            val now = SystemClock.elapsedRealtime()
            if (now - lastPush > 100) {
                lastPush = now
                heading = curHeading
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }
    }

    private fun startCompass() {
        try {
            val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensors = sm
            val s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            hasCompass = s != null
            if (s != null) sm.registerListener(sensorListener, s, SensorManager.SENSOR_DELAY_UI)
        } catch (e: Exception) {
        }
    }

    private fun stopCompass() {
        try {
            sensors?.unregisterListener(sensorListener)
        } catch (e: Exception) {
        }
    }
}
