package com.aman.gigi.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aman.gigi.R
import com.aman.gigi.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor() {

    companion object {
        const val LOVE_CARD_CHANNEL_ID = "gigi_love_cards_channel"
        const val NOW_PLAYING_CHANNEL_ID = "gigi_now_playing_channel"
        private var notifIdCounter = 9100
    }

    fun showLoveCardNotification(
        context: Context,
        partnerName: String,
        deckTitle: String = "a new Love Card deck"
    ) {
        ensureLoveCardChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_OPEN_SWEET_CORNER"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifIdCounter,
            intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, LOVE_CARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("💌 New Love Card from $partnerName")
            .setContentText("$partnerName sent you $deckTitle")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifIdCounter++, notification)
    }

    fun showNowPlayingReceivedNotification(
        context: Context,
        partnerName: String,
        songTitle: String,
        artist: String
    ) {
        ensureNowPlayingChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_OPEN_MUSIC_PLAYER"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifIdCounter,
            intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, NOW_PLAYING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("🎵 $partnerName is listening to")
            .setContentText("$songTitle — $artist")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifIdCounter++, notification)
    }

    private fun ensureLoveCardChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOVE_CARD_CHANNEL_ID,
                "Love Cards",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when your partner sends a Love Card deck"
                enableVibration(true)
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun ensureNowPlayingChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOW_PLAYING_CHANNEL_ID,
                "Now Playing Shares",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when your partner shares what they're listening to"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
