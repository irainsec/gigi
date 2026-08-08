package com.aman.gigi.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aman.gigi.model.Reminder
import com.aman.gigi.model.RecurrencePattern
import com.aman.gigi.utils.Constants
import java.util.Date

class AlarmUtils {
    companion object {
        fun scheduleAlarm(context: Context, reminder: Reminder) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.dueDate,
                getPendingIntent(
                    context,
                    reminder
                )
            )
        }

        /** reschedules an alarm for the nearest (dueDate + intervalMillis) in the future. only for recurring alarms. */
        fun rescheduleAlarm(context: Context, reminder: Reminder) {

            if (reminder.recurrencePattern == null)
                return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            var dueDate = reminder.dueDate
            val interval = if (reminder.recurrencePattern == RecurrencePattern.CUSTOM) {
                reminder.customIntervalMillis ?: 0L
            } else {
                reminder.recurrencePattern.intervalMillis
            }

            if (interval <= 0) return

            while (Date(dueDate).before(Date())) {
                dueDate += interval
                
                // Check time frame window
                if (reminder.repeatStartHour != null && reminder.repeatEndHour != null) {
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = dueDate }
                    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val currentMinute = calendar.get(java.util.Calendar.MINUTE)
                    
                    val startTimeMinutes = reminder.repeatStartHour * 60 + (reminder.repeatStartMinute ?: 0)
                    val endTimeMinutes = reminder.repeatEndHour * 60 + (reminder.repeatEndMinute ?: 0)
                    val currentTimeMinutes = currentHour * 60 + currentMinute
                    
                    if (currentTimeMinutes < startTimeMinutes) {
                        // Move to start time of same day
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, reminder.repeatStartHour)
                        calendar.set(java.util.Calendar.MINUTE, reminder.repeatStartMinute ?: 0)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        dueDate = calendar.timeInMillis
                    } else if (currentTimeMinutes > endTimeMinutes) {
                        // Move to start time of NEXT day
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, reminder.repeatStartHour)
                        calendar.set(java.util.Calendar.MINUTE, reminder.repeatStartMinute ?: 0)
                        calendar.set(java.util.Calendar.SECOND, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        dueDate = calendar.timeInMillis
                    }
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                dueDate,
                getPendingIntent(
                    context,
                    reminder
                )
            )
        }

        fun cancelAlarm(context: Context, reminder: Reminder) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            alarmManager.cancel(
                getPendingIntent(
                    context,
                    reminder
                )
            )
        }

        private fun getPendingIntent(context: Context, reminder: Reminder): PendingIntent {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(Constants.REMINDER_ITEM_KEY, reminder)
            }

            return PendingIntent.getBroadcast(
                context,
                reminder._id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}