package com.aman.gigi.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.AlarmManager
import com.aman.gigi.R
import com.aman.gigi.ui.MainActivity
import com.aman.gigi.receiver.TimeCapsuleReceiver

object NotificationHelper {

    private const val CHANNEL_ID = "love_cards_channel"
    private const val CHANNEL_NAME = "Love Cards"
    private const val CHANNEL_DESC = "Notifications for received Love Cards"

    fun showLoveCardNotification(context: Context, partnerName: String, stackId: String, isTimeCapsuleUnlock: Boolean = false) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Deep link to the Sweet Corner/Love Cards tab in MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "ACTION_VIEW_LOVE_CARDS"
            putExtra("EXTRA_STACK_ID", stackId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            stackId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // Fallback icon
            .setContentTitle(if (isTimeCapsuleUnlock) "⏳ Time Capsule Unlocked!" else "💌 Love Card Received")
            .setContentText(if (isTimeCapsuleUnlock) "${if (partnerName.isNotBlank()) partnerName else "Your partner"}'s time capsule is now ready to open." else "${if (partnerName.isNotBlank()) partnerName else "Your partner"} sent you a new Love Card deck!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(stackId.hashCode(), notification)
    }

    fun scheduleTimeCapsuleUnlock(context: Context, partnerName: String, stackId: String, unlockDate: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TimeCapsuleReceiver::class.java).apply {
            putExtra("EXTRA_STACK_ID", stackId)
            putExtra("EXTRA_PARTNER_NAME", partnerName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            stackId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule exact alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unlockDate, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unlockDate, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unlockDate, pendingIntent)
            }
            android.util.Log.i("NotificationHelper", "⏰ Scheduled Time Capsule unlock for $stackId at $unlockDate")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Failed to schedule exact alarm: ${e.message}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, unlockDate, pendingIntent)
        }
    }
}
