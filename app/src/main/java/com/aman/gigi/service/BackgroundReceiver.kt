package com.aman.gigi.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BackgroundReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_KEEP_ALIVE = "com.aman.gigi.action.KEEP_ALIVE"
        const val KEEP_ALIVE_INTERVAL = 15 * 60 * 1000L // 15 minutes
        
        fun scheduleKeepAlive(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, BackgroundReceiver::class.java).apply {
                action = ACTION_KEEP_ALIVE
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val triggerTime = System.currentTimeMillis() + KEEP_ALIVE_INTERVAL
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                    }
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                Log.d("BackgroundReceiver", "⏰ Keep-alive alarm scheduled for ${KEEP_ALIVE_INTERVAL / 60000} mins")
            } catch (e: Exception) {
                Log.e("BackgroundReceiver", "❌ Failed to schedule keep-alive alarm", e)
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_KEEP_ALIVE) {
            Log.i("BackgroundReceiver", "💓 Keep-alive alarm received. Checking service...")
            
            // Start the sync service to ensure it's running
            val serviceIntent = Intent(context, ScreensaverSyncService::class.java).apply {
                action = ScreensaverSyncService.ACTION_START_SYNC
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Re-schedule for next interval
            scheduleKeepAlive(context)
        }
    }
}
