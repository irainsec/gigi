package com.aman.gigi.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aman.gigi.db.ReminderDAO
import com.aman.gigi.db.SharedAlarmDao
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderDAO: ReminderDAO

    @Inject
    lateinit var sharedAlarmDao: SharedAlarmDao

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart Screensaver Sync Service
            val syncIntent = Intent(context, com.aman.gigi.service.ScreensaverSyncService::class.java).apply {
                action = com.aman.gigi.service.ScreensaverSyncService.ACTION_START_SYNC
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(syncIntent)
            } else {
                context.startService(syncIntent)
            }

            CoroutineScope(Dispatchers.IO).launch {
                val reminders = reminderDAO.reminders()

                reminders.forEach {
                    if (it.dueDate >= System.currentTimeMillis())
                        AlarmUtils.scheduleAlarm(context, it)
                    else if (it.recurrencePattern != null)
                        AlarmUtils.rescheduleAlarm(context, it)
                }

                val sharedAlarms = sharedAlarmDao.getAllActiveAlarmsOnce()
                sharedAlarms.forEach {
                    val reminder = it.asReminderStub(partnerName = null)
                    if (reminder.dueDate >= System.currentTimeMillis()) {
                        AlarmUtils.scheduleAlarm(context, reminder)
                    } else if (reminder.recurrencePattern != null) {
                        AlarmUtils.rescheduleAlarm(context, reminder)
                    }
                }
            }
        }
    }
}
