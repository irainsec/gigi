package com.aman.gigi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aman.gigi.ui.LoveCardDeckActivity
import com.aman.gigi.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import com.aman.gigi.data.sync.ScribbleSyncManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service for background scribble sync
 */
@AndroidEntryPoint
class ScreensaverSyncService : LifecycleService() {
    
    @Inject
    lateinit var syncManager: ScribbleSyncManager
    
    private val TAG = "ScreensaverSyncService"
    private val CHANNEL_ID = "ScreensaverSyncChannel"
    private val MIRROR_CHANNEL_ID = "PartnerNotificationMirrorChannel_v2"
    private val PARTNER_EVENT_CHANNEL_ID = "PartnerLoveEventsChannel"
    private val NOTIFICATION_ID = 1002
    private var allowAutoRestart = true
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Gigi:SyncServiceWakeLock").apply {
                acquire() // Hold indefinitely until released on destroy
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire sync wake lock", e)
        }
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service started")

        if (!promoteToForegroundSafely()) {
            Log.w(TAG, "Foreground promotion skipped; stopping sync service to avoid process crash.")
            return START_NOT_STICKY
        }
        
        when (intent?.action) {
            ACTION_START_SYNC -> {
                allowAutoRestart = true
                cancelScheduledRestart(this)
                syncManager.setLifecycleOwner(this)
                startSync()
                BackgroundReceiver.scheduleKeepAlive(this) // Trigger initial keep-alive chain
            }
            ACTION_STOP_SYNC -> {
                allowAutoRestart = false
                cancelScheduledRestart(this)
                stopSync()
                stopSelf()
            }
            null -> {
                // Sticky restart case
                Log.i(TAG, "Service restarted via START_STICKY, resuming sync...")
                allowAutoRestart = true
                cancelScheduledRestart(this)
                syncManager.setLifecycleOwner(this)
                startSync()
            }
        }
        
        return START_STICKY
    }

    private fun promoteToForegroundSafely(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Foreground sync start suppressed safely: ${error.message}")
            false
        }
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release sync wake lock", e)
        }
        syncManager.setLifecycleOwner(null)
        stopSync()
        if (allowAutoRestart) {
            Log.w(TAG, "Service destroyed unexpectedly. Scheduling immediate restart.")
            scheduleServiceRestart(this, 1500L)
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (allowAutoRestart) {
            Log.w(TAG, "App task removed. Scheduling sync service restart.")
            scheduleServiceRestart(this, 1000L)
        }
    }
    
    private fun startSync() {
        Log.d(TAG, "Starting sync manager and event observation")
        syncManager.start()
        
        // Observe sync events for notification mirroring
        lifecycleScope.launch {
            syncManager.events.collect { event ->
                if (event is com.aman.gigi.data.sync.SyncEvent.NotificationReceived) {
                    // Role Check: Only the CREATOR should see mirrored notifications.
                    // The Joinee is the one being monitored and should not see the Creator's notifications.
                    val activeConnections = syncManager.connectionRepository.getAllActiveConnectionsOnce()
                    val connection = activeConnections.find { it.connectionId == event.connectionId }
                    
                    val isCreator = connection?.creatorDeviceId?.equals(syncManager.deviceId, ignoreCase = true) == true
                    if (isCreator) {
                        postMirroredNotification(event.notification)
                        Log.i(TAG, "🔔 Mirrored notification posted to system tray.")
                    } else {
                        Log.i(TAG, "🕵️ Stealth Mode: Suppressing mirrored notification for Joinee device.")
                    }
                } else if (event is com.aman.gigi.data.sync.SyncEvent.QuoteReceived) {
                    postPartnerEventNotification(
                        title = "${event.senderName.ifBlank { "Your partner" }} sent a quote",
                        text = event.text,
                        notificationId = ("quote_" + event.connectionId + "_" + event.text).hashCode(),
                        contentIntent = mainAppPendingIntent("ACTION_OPEN_SWEET_CORNER")
                    )
                } else if (event is com.aman.gigi.data.sync.SyncEvent.PartnerProfileUpdated) {
                    postPartnerEventNotification(
                        title = "${event.partnerName.ifBlank { "Your partner" }} updated their picture",
                        text = "Your partner refreshed their profile photo in Gigi.",
                        notificationId = ("profile_" + event.connectionId).hashCode(),
                        contentIntent = mainAppPendingIntent("ACTION_OPEN_SWEET_CORNER")
                    )
                } else if (event is com.aman.gigi.data.sync.SyncEvent.LoveCardStackReceived) {
                    postLoveCardNotification(event)
                } else if (event is com.aman.gigi.data.sync.SyncEvent.LoveCardStackAnswered) {
                    postLoveCardAnsweredNotification(event)
                }
            }
        }
    }
    
    private fun postMirroredNotification(remote: com.aman.gigi.model.RemoteNotification) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Decode icon if available
        val largeIcon = try {
            if (!remote.iconBase64.isNullOrBlank()) {
                val decodedString = android.util.Base64.decode(remote.iconBase64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode notification icon", e)
            null
        }

        val builder = NotificationCompat.Builder(this, MIRROR_CHANNEL_ID)
            .setContentTitle(remote.title)
            .setContentText(remote.text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setWhen(remote.timestamp ?: 0L)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            
        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(remote.text))
        }

        // Use a unique ID based on the remote notification ID to allow updates
        val notificationId = remote.id.hashCode()
        notificationManager.notify(notificationId, builder.build())
        Log.i(TAG, "🔔 Mirrored notification posted: ${remote.title} (ID: $notificationId)")
    }

    private fun postPartnerEventNotification(
        title: String,
        text: String,
        notificationId: Int,
        contentIntent: PendingIntent? = null
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, PARTNER_EVENT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .apply {
                contentIntent?.let { setContentIntent(it) }
            }
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun postLoveCardNotification(event: com.aman.gigi.data.sync.SyncEvent.LoveCardStackReceived) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            ("love_card_" + event.stackId).hashCode(),
            Intent(this, LoveCardDeckActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LoveCardDeckActivity.EXTRA_STACK_ID, event.stackId)
                putExtra(LoveCardDeckActivity.EXTRA_CONNECTION_ID, event.connectionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, PARTNER_EVENT_CHANNEL_ID)
            .setContentTitle("${event.senderName.ifBlank { "Your partner" }} sent a love card stack")
            .setContentText("${event.title} • ${event.cardCount} sweet cards waiting for you")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${event.title} • ${event.cardCount} sweet cards waiting for you"
                )
            )
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        notificationManager.notify(("love_cards_received_" + event.stackId).hashCode(), notification)
        try {
            startActivity(
                Intent(this, LoveCardDeckActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(LoveCardDeckActivity.EXTRA_STACK_ID, event.stackId)
                    putExtra(LoveCardDeckActivity.EXTRA_CONNECTION_ID, event.connectionId)
                }
            )
        } catch (error: Exception) {
            Log.w(TAG, "Direct Love Card launch blocked, relying on full-screen notification.", error)
        }
    }

    private fun postLoveCardAnsweredNotification(event: com.aman.gigi.data.sync.SyncEvent.LoveCardStackAnswered) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            ("love_card_answer_" + event.stackId).hashCode(),
            Intent(this, LoveCardDeckActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LoveCardDeckActivity.EXTRA_STACK_ID, event.stackId)
                putExtra(LoveCardDeckActivity.EXTRA_CONNECTION_ID, event.connectionId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, PARTNER_EVENT_CHANNEL_ID)
            .setContentTitle("Your love cards came back")
            .setContentText("${event.title} now has ${event.answerCount} replies from your partner")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "${event.title} now has ${event.answerCount} replies from your partner"
                )
            )
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()
        notificationManager.notify(("love_cards_answered_" + event.stackId).hashCode(), notification)
        try {
            startActivity(
                Intent(this, LoveCardDeckActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(LoveCardDeckActivity.EXTRA_STACK_ID, event.stackId)
                    putExtra(LoveCardDeckActivity.EXTRA_CONNECTION_ID, event.connectionId)
                }
            )
        } catch (error: Exception) {
            Log.w(TAG, "Direct answered Love Card launch blocked, relying on full-screen notification.", error)
        }
    }

    private fun mainAppPendingIntent(action: String? = null): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            action?.let { this.action = it }
        }
        return PendingIntent.getActivity(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    private fun stopSync() {
        Log.d(TAG, "Stopping sync manager")
        syncManager.stop()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Channel 1: Foreground Service
            val name = "Screensaver Sync"
            val descriptionText = "Keep screensaver synced between devices"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)

            // Channel 2: Partner Notification Mirror
            val mirrorName = "Partner Notifications"
            val mirrorDesc = "Mirrored notifications from your partner's device"
            val mirrorImportance = NotificationManager.IMPORTANCE_DEFAULT
            val mirrorChannel = NotificationChannel(MIRROR_CHANNEL_ID, mirrorName, mirrorImportance).apply {
                description = mirrorDesc
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(mirrorChannel)

            val partnerEventChannel = NotificationChannel(
                PARTNER_EVENT_CHANNEL_ID,
                "Partner Updates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Quotes, love cards, and profile updates from your partner"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(partnerEventChannel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screensaver Synced")
            .setContentText("Gigi is staying awake so partner scribbles and quotes arrive instantly.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    companion object {
        const val ACTION_START_SYNC = "com.aman.gigi.action.START_SYNC"
        const val ACTION_STOP_SYNC = "com.aman.gigi.action.STOP_SYNC"
        private const val RESTART_REQUEST_CODE = 32041

        fun scheduleServiceRestart(context: Context, delayMs: Long = 1500L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val restartIntent = Intent(context, ScreensaverSyncService::class.java).apply {
                action = ACTION_START_SYNC
            }
            val pendingIntent = PendingIntent.getService(
                context,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + delayMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        private fun cancelScheduledRestart(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val restartIntent = Intent(context, ScreensaverSyncService::class.java).apply {
                action = ACTION_START_SYNC
            }
            val pendingIntent = PendingIntent.getService(
                context,
                RESTART_REQUEST_CODE,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
