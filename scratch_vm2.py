import re

with open('app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

old_save = """    fun saveSharedAlarm(
        alarmId: String? = null,
        connectionId: String,
        title: String,
        note: String?,
        dueAt: Long,
        recurrencePattern: String?,
        customIntervalMillis: Long?,
        repeatStartHour: Int?,
        repeatStartMinute: Int?,
        repeatEndHour: Int?,
        repeatEndMinute: Int?
    ) {"""

new_save = """    fun saveSharedAlarm(
        alarmId: String? = null,
        connectionId: String,
        title: String,
        note: String?,
        dueAt: Long,
        recurrencePattern: String?,
        customIntervalMillis: Long?,
        repeatStartHour: Int?,
        repeatStartMinute: Int?,
        repeatEndHour: Int?,
        repeatEndMinute: Int?,
        emoji: String = "??"
    ) {"""

content = content.replace(old_save, new_save)

old_mirror = """            val mirror = SharedAlarmMirror(
                alarmId = actualAlarmId,
                connectionId = connectionId,
                title = title,
                note = note,
                dueAt = dueAt,
                recurrencePattern = recurrencePattern,
                customIntervalMillis = customIntervalMillis,
                repeatStartHour = repeatStartHour,
                repeatStartMinute = repeatStartMinute,
                repeatEndHour = repeatEndHour,
                repeatEndMinute = repeatEndMinute,
                ownerMemberId = syncManager.getMemberId(),
                ownerDisplayName = "You",
                isActive = true
            )"""

new_mirror = """            val mirror = SharedAlarmMirror(
                alarmId = actualAlarmId,
                connectionId = connectionId,
                title = title,
                note = note,
                dueAt = dueAt,
                recurrencePattern = recurrencePattern,
                customIntervalMillis = customIntervalMillis,
                repeatStartHour = repeatStartHour,
                repeatStartMinute = repeatStartMinute,
                repeatEndHour = repeatEndHour,
                repeatEndMinute = repeatEndMinute,
                ownerMemberId = syncManager.getMemberId(),
                ownerDisplayName = "You",
                isActive = true,
                emoji = emoji
            )"""
content = content.replace(old_mirror, new_mirror)

with open('app/src/main/java/com/aman/gigi/viewmodel/ScreensaverViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)
