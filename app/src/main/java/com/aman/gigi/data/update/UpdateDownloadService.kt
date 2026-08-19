package com.aman.gigi.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aman.gigi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the update download.
 *
 * Without a foreground service a 50–100 MB transfer is just background work: Android
 * freezes or kills the process as soon as the user locks the screen or switches away,
 * which is why updates appeared to "close the app" partway through. Running here makes
 * the work user-visible, keeps the process alive, and gives people a progress
 * notification they can act on.
 */
class UpdateDownloadService : Service() {

    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A second tap while a download is already running must not start a competing
        // one — two jobs writing the same part files corrupts both.
        if (job?.isActive == true) return START_STICKY

        val versionName = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        val sha256 = intent.getStringExtra(EXTRA_SHA256)
        val requireUnmetered = intent.getBooleanExtra(EXTRA_UNMETERED, false)

        startForeground(NOTIFICATION_ID, buildNotification(versionName, 0, indeterminate = true))
        acquireWakeLock()

        // downloadProgress is a process-wide StateFlow, and a StateFlow replays its
        // CURRENT value to every new collector. After any previous download it still
        // holds COMPLETED (or ERROR) — so the collector below used to fire that branch
        // the instant it subscribed, call stopSelf(), and have onDestroy cancel the
        // download that had only just been launched. The transfer died immediately and
        // the UI sat at 0% forever. Clearing it first makes the replayed value IDLE.
        AppUpdateManager.resetProgress()

        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope

        newScope.launch {
            // Belt and braces alongside the reset above: a terminal state can only be
            // this download's if we have actually seen it doing work first.
            var sawActiveWork = false
            AppUpdateManager.downloadProgress.collectLatest { progress ->
                if (progress.status == DownloadStatus.DOWNLOADING ||
                    progress.status == DownloadStatus.VERIFYING ||
                    progress.status == DownloadStatus.WAITING_FOR_NETWORK
                ) {
                    sawActiveWork = true
                }
                when (progress.status) {
                    DownloadStatus.WAITING_FOR_NETWORK -> notify(
                        waitingNotification(
                            progress.versionName,
                            progress.waitingReason ?: "Waiting for a connection"
                        )
                    )
                    DownloadStatus.DOWNLOADING -> notify(
                        buildNotification(progress.versionName, progress.progressPercent)
                    )
                    DownloadStatus.VERIFYING -> notify(
                        buildNotification(progress.versionName, 100, verifying = true)
                    )
                    DownloadStatus.COMPLETED -> if (sawActiveWork) {
                        notifyFinal(readyNotification(progress.versionName))
                        stopSelf()
                    }
                    DownloadStatus.ERROR -> if (sawActiveWork) {
                        notifyFinal(failedNotification(progress.error))
                        stopSelf()
                    }
                    DownloadStatus.IDLE -> Unit
                }
            }
        }

        job = newScope.launch {
            AppUpdateManager.download(
                applicationContext, url, versionName, sha256, requireUnmetered
            )
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gigi:update-download")
                .apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }
        }
    }

    private fun manager() = getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Progress while a Gigi update downloads." }
            )
        }
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        this, 0,
        packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildNotification(
        versionName: String,
        percent: Int,
        indeterminate: Boolean = false,
        verifying: Boolean = false
    ): android.app.Notification {
        ensureChannel()
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, UpdateDownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                if (verifying) "Checking the download…"
                else "Downloading Gigi ${versionName.ifBlank { "update" }}"
            )
            .setContentText(if (verifying) "Almost there" else "$percent%")
            .setProgress(100, percent, indeterminate || verifying)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancel", stop)
            .build()
    }

    /**
     * Parked, not broken. The service stays in the foreground while it waits so the OS
     * keeps the process alive — the download resumes the instant the network is back,
     * with no input from the user.
     */
    private fun waitingNotification(
        versionName: String,
        reason: String
    ): android.app.Notification {
        ensureChannel()
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, UpdateDownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Gigi ${versionName.ifBlank { "update" }} is paused")
            .setContentText("$reason — it will pick up where it left off")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .addAction(0, "Cancel", stop)
            .build()
    }

    private fun readyNotification(versionName: String): android.app.Notification {
        ensureChannel()
        val install = AppUpdateManager.downloadProgress.value.fileUri?.let { uri ->
            PendingIntent.getActivity(
                this, 2,
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Gigi ${versionName.ifBlank { "update" }} is ready")
            .setContentText("Tap to install")
            .setAutoCancel(true)
            .setContentIntent(install ?: openAppIntent())
            .build()
    }

    private fun failedNotification(error: String?): android.app.Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Update didn't finish")
            .setContentText(error?.take(120) ?: "Something went wrong. Try again later.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
    }

    private fun notify(notification: android.app.Notification) {
        runCatching { manager().notify(NOTIFICATION_ID, notification) }
    }

    /** Posted under a different id so it survives the foreground notification going away. */
    private fun notifyFinal(notification: android.app.Notification) {
        runCatching { manager().notify(DONE_NOTIFICATION_ID, notification) }
    }

    override fun onDestroy() {
        job?.cancel()
        scope?.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "gigi_app_update"
        private const val NOTIFICATION_ID = 8801
        private const val DONE_NOTIFICATION_ID = 8802
        private const val EXTRA_URL = "url"
        private const val EXTRA_VERSION = "versionName"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_UNMETERED = "requireUnmetered"
        const val ACTION_STOP = "com.aman.gigi.UPDATE_DOWNLOAD_STOP"

        fun start(
            context: Context,
            url: String,
            versionName: String,
            sha256: String?,
            requireUnmetered: Boolean = false
        ) {
            val intent = Intent(context, UpdateDownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_VERSION, versionName)
                .putExtra(EXTRA_SHA256, sha256)
                .putExtra(EXTRA_UNMETERED, requireUnmetered)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, UpdateDownloadService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
