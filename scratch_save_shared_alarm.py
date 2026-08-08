import re

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_func = """    fun saveSharedAlarm(
        existingAlarm: SharedAlarmMirror?,
        connectionId: String,
        title: String,
        description: String?,
        dueDate: Long,
        recurrencePattern: RecurrencePattern?,
        customIntervalMillis: Long?,
        startH: Int?,
        startM: Int?,
        endH: Int?,
        endM: Int?
    ) {"""

new_func = """    fun saveSharedAlarm(
        existingAlarm: SharedAlarmMirror?,
        connectionId: String,
        title: String,
        description: String?,
        dueDate: Long,
        recurrencePattern: RecurrencePattern?,
        customIntervalMillis: Long?,
        startH: Int?,
        startM: Int?,
        endH: Int?,
        endM: Int?,
        emoji: String = "??"
    ) {"""
content = content.replace(old_func, new_func)

old_body = """            val newAlarm = SharedAlarmMirror(
                alarmId = actualAlarmId,
                connectionId = connectionId,
                title = title,
                note = description,
                dueAt = dueDate,
                recurrencePattern = recurrencePattern?.name,
                customIntervalMillis = customIntervalMillis,
                repeatStartHour = startH,
                repeatStartMinute = startM,
                repeatEndHour = endH,
                repeatEndMinute = endM,
                ownerMemberId = syncManager.getMemberId(),
                ownerDisplayName = "You",
                isActive = true
            )"""

new_body = """            val newAlarm = SharedAlarmMirror(
                alarmId = actualAlarmId,
                connectionId = connectionId,
                title = title,
                note = description,
                dueAt = dueDate,
                recurrencePattern = recurrencePattern?.name,
                customIntervalMillis = customIntervalMillis,
                repeatStartHour = startH,
                repeatStartMinute = startM,
                repeatEndHour = endH,
                repeatEndMinute = endM,
                ownerMemberId = syncManager.getMemberId(),
                ownerDisplayName = "You",
                isActive = true,
                emoji = emoji
            )"""
content = content.replace(old_body, new_body)

with open('app/src/main/java/com/aman/gigi/ui/Reminders.kt', 'w', encoding='utf-8') as f:
    f.write(content)
