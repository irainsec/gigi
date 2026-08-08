import re

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Update AddReminder signature
old_sig = "onSave: (String, String?, Long, RecurrencePattern?, Long?, Int?, Int?, Int?, Int?) -> Unit"
new_sig = "onSave: (String, String?, Long, RecurrencePattern?, Long?, Int?, Int?, Int?, Int?, String) -> Unit"
content = content.replace(old_sig, new_sig)

# Update the caller inside Reminders Screen (local reminder)
old_call1 = """                                onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM ->
                                    val reminder = Reminder(
                                        title = title,
                                        description = description,
                                        dueDate = dueDate,
                                        recurrencePattern = recurrencePattern,
                                        customIntervalMillis = customIntervalMillis,
                                        repeatStartHour = startH,
                                        repeatStartMinute = startM,
                                        repeatEndHour = endH,
                                        repeatEndMinute = endM
                                    )"""

new_call1 = """                                onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM, emoji ->
                                    val reminder = Reminder(
                                        title = title,
                                        description = description,
                                        dueDate = dueDate,
                                        recurrencePattern = recurrencePattern,
                                        customIntervalMillis = customIntervalMillis,
                                        repeatStartHour = startH,
                                        repeatStartMinute = startM,
                                        repeatEndHour = endH,
                                        repeatEndMinute = endM,
                                        emoji = emoji
                                    )"""
content = content.replace(old_call1, new_call1)

# Update the caller for shared alarm
old_call2 = """                                onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM ->
                                    resolvedSharedAlarmConnection?.let { connection ->
                                        viewModel.saveSharedAlarm(
                                            alarmId = editingSharedAlarm?.alarmId,
                                            connectionId = connection.connectionId,
                                            title = title,
                                            note = description,
                                            dueAt = dueDate,
                                            recurrencePattern = recurrencePattern?.name,
                                            customIntervalMillis = customIntervalMillis,
                                            repeatStartHour = startH,
                                            repeatStartMinute = startM,
                                            repeatEndHour = endH,
                                            repeatEndMinute = endM
                                        )"""

new_call2 = """                                onSave = { title, description, dueDate, recurrencePattern, customIntervalMillis, startH, startM, endH, endM, emoji ->
                                    resolvedSharedAlarmConnection?.let { connection ->
                                        viewModel.saveSharedAlarm(
                                            alarmId = editingSharedAlarm?.alarmId,
                                            connectionId = connection.connectionId,
                                            title = title,
                                            note = description,
                                            dueAt = dueDate,
                                            recurrencePattern = recurrencePattern?.name,
                                            customIntervalMillis = customIntervalMillis,
                                            repeatStartHour = startH,
                                            repeatStartMinute = startM,
                                            repeatEndHour = endH,
                                            repeatEndMinute = endM,
                                            emoji = emoji
                                        )"""
content = content.replace(old_call2, new_call2)

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'w', encoding='utf-8') as f:
    f.write(content)
