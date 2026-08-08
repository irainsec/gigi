package com.aman.gigi.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aman.gigi.model.Reminder
import com.aman.gigi.service.AlarmForegroundService
import com.aman.gigi.utils.Constants

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                intent.getParcelableExtra<Reminder>(
                    Constants.REMINDER_ITEM_KEY,
                    Reminder::class.java
                )
            else
                intent.getParcelableExtra<Reminder>(Constants.REMINDER_ITEM_KEY)

        val alarmIntent = Intent(context, AlarmForegroundService::class.java)
        alarmIntent.putExtra(Constants.REMINDER_ITEM_KEY, reminder)

        context.startForegroundService(alarmIntent)
    }
}
