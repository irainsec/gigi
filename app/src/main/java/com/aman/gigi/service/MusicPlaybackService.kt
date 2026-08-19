package com.aman.gigi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.aman.gigi.ui.MainActivity
import com.aman.gigi.model.LocalSong
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.aman.gigi.ui.screensaver.LockscreenPlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : Service() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val currentSong = playbackManager.currentSong.value
                val isPlaying = playbackManager.isPlaying.value
                if (currentSong != null && isPlaying) {
                    val lockIntent = Intent(context, LockscreenPlayerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(lockIntent)
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "gigi_music_playback_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_PLAY = "com.aman.gigi.action.PLAY"
        const val ACTION_PAUSE = "com.aman.gigi.action.PAUSE"
        const val ACTION_NEXT = "com.aman.gigi.action.NEXT"
        const val ACTION_PREVIOUS = "com.aman.gigi.action.PREVIOUS"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenStateReceiver, filter)
        }

        playbackManager.onPlaybackStateChangedListener = {
            updateNotification()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> playbackManager.togglePlayback(PlaybackSource.NOTIFICATION)
                ACTION_PAUSE -> playbackManager.togglePlayback(PlaybackSource.NOTIFICATION)
                ACTION_NEXT -> playbackManager.playNext()
                ACTION_PREVIOUS -> playbackManager.playPrevious()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "GigiMusicSession").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playbackManager.togglePlayback(PlaybackSource.MEDIA_SESSION)
                }

                override fun onPause() {
                    playbackManager.togglePlayback(PlaybackSource.MEDIA_SESSION)
                }

                override fun onSkipToNext() {
                    playbackManager.playNext()
                }

                override fun onSkipToPrevious() {
                    playbackManager.playPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    playbackManager.seekTo(pos)
                }
            })
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback controls for Gigi Music"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val song = playbackManager.currentSong.value
        val isPlaying = playbackManager.isPlaying.value

        if (song == null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            return
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                playbackManager.progressMs.value,
                1.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        serviceScope.launch(Dispatchers.IO) {
            val rawArt = loadRawArt(song.albumArtUri)
            val vinylArt = rawArt?.let { getCircularVinylBitmap(it) } ?: getPlaceholderVinylBitmap()

            serviceScope.launch(Dispatchers.Main) {
                val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, playbackManager.durationMs.value)
                    .putBitmap(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART, vinylArt)
                    .build()
                mediaSession?.setMetadata(metadata)

                val notification = buildForegroundNotification(song, isPlaying, vinylArt)
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildForegroundNotification(
        song: LocalSong,
        isPlaying: Boolean,
        vinylArt: Bitmap
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                action = "ACTION_OPEN_MUSIC_PLAYER"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREVIOUS }
        val prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(song.album)
            .setSmallIcon(com.aman.gigi.R.drawable.ic_launcher_monochrome)
            .setLargeIcon(vinylArt)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", playPausePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playPausePendingIntent)
        }
        builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)

        return builder.build()
    }

    private fun loadRawArt(artUri: android.net.Uri?): Bitmap? {
        if (artUri == null) return null
        return try {
            contentResolver.openInputStream(artUri).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getCircularVinylBitmap(src: Bitmap): Bitmap {
        val size = src.width.coerceAtMost(src.height).coerceAtLeast(200)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = 0xFF121212.toInt()
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        val artRadius = radius * 0.7f
        val artOutput = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val artCanvas = Canvas(artOutput)
        val artPaint = Paint().apply { isAntiAlias = true }
        artCanvas.drawCircle(radius, radius, artRadius, artPaint)
        artPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        
        val srcRect = Rect(0, 0, src.width, src.height)
        val destRect = Rect(
            (radius - artRadius).toInt(),
            (radius - artRadius).toInt(),
            (radius + artRadius).toInt(),
            (radius + artRadius).toInt()
        )
        artCanvas.drawBitmap(src, srcRect, destRect, artPaint)

        paint.xfermode = null
        canvas.drawBitmap(artOutput, 0f, 0f, paint)

        paint.color = 0xFF000000.toInt()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawCircle(radius, radius, radius * 0.08f, paint)

        return output
    }

    private fun getPlaceholderVinylBitmap(): Bitmap {
        val size = 256
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = 0xFF1E1E1E.toInt()
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        paint.color = 0xFF4E3D30.toInt()
        canvas.drawCircle(radius, radius, radius * 0.4f, paint)

        paint.color = 0xFF000000.toInt()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        canvas.drawCircle(radius, radius, radius * 0.08f, paint)

        return output
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenStateReceiver) }
        playbackManager.onPlaybackStateChangedListener = null
        mediaSession?.release()
        super.onDestroy()
    }
}
