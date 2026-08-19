package com.aman.gigi.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aman.gigi.R
import com.aman.gigi.repository.LiveRepository
import com.aman.gigi.ui.MainActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Streams this device's position to one live meet-up.
 *
 * Foreground-only by design: it exists so the sharing is *visible and interruptible*,
 * and so we never have to ask for ACCESS_BACKGROUND_LOCATION (which Play reviews
 * separately and rejects for "nice to have" uses). It stops itself the moment the
 * server says the meet-up is finished.
 */
@AndroidEntryPoint
class LiveTrackingService : Service() {

    @Inject lateinit var liveRepository: LiveRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var postId: String? = null
    private var peerName: String = "your meet-up"

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val id = postId ?: return
            scope.launch {
                val ok = liveRepository.pushLocation(
                    postId = id,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    heading = loc.bearing.takeIf { loc.hasBearing() },
                    speed = loc.speed.takeIf { loc.hasSpeed() },
                    battery = batteryPercent()
                )
                // 410/403 from the server means "it's over" — don't keep a stale
                // notification (and a stale GPS drain) alive.
                if (!ok) stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        postId = intent?.getStringExtra(EXTRA_POST_ID) ?: run {
            stopSelf(); return START_NOT_STICKY
        }
        peerName = intent.getStringExtra(EXTRA_LABEL) ?: peerName

        startForeground(NOTIFICATION_ID, buildNotification())
        startUpdates()
        return START_STICKY
    }

    private fun startUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) { stopSelf(); return }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS / 2)
            .setMinUpdateDistanceMeters(10f)
            .build()
        runCatching { fused.requestLocationUpdates(request, callback, mainLooper) }
            .onFailure { stopSelf() }
    }

    private fun batteryPercent(): Int? = runCatching {
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()?.takeIf { it in 0..100 }

    private fun buildNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live meet-ups", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while you're sharing your location with a meet-up." }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("openLivePostId", postId)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LiveTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sharing your location")
            .setContentText("With $peerName · tap to open, or stop below")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(0, "Stop sharing", stop)
            .build()
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "gigi_live_tracking"
        private const val NOTIFICATION_ID = 4711
        private const val UPDATE_INTERVAL_MS = 10_000L
        const val EXTRA_POST_ID = "postId"
        const val EXTRA_LABEL = "label"
        const val ACTION_STOP = "com.aman.gigi.LIVE_STOP"

        fun start(context: Context, postId: String, label: String) {
            val intent = Intent(context, LiveTrackingService::class.java)
                .putExtra(EXTRA_POST_ID, postId)
                .putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, LiveTrackingService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
