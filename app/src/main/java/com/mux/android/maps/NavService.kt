package com.mux.android.maps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mux.android.maps.data.Fmt
import com.mux.android.maps.data.Navigator
import com.mux.android.maps.data.Routing

/** Keeps guidance (GPS + voice) running with a notification when the screen is off. */
class NavService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            if (!Navigator.active) {
                stopForeground(true)
                stopSelf()
                return
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, build())
            handler.postDelayed(this, 1500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW))
        }
        try {
            ServiceCompat.startForeground(this, ID, build(), if (Build.VERSION.SDK_INT >= 29) 8 else 0)  // type: location
        } catch (e: Exception) {
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun build(): Notification {
        val next = Navigator.next
        val title = if (next != null) "${Fmt.dist(Navigator.distToNext)} · ${Routing.text(next)}" else "Navigating"
        val text = "${Fmt.dist(Navigator.remaining)} left · ${Fmt.dur(Navigator.remainingTime)}"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_nav)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ID = 4001
        private const val CHANNEL = "maps_nav"

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, NavService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
