package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shared_alarm_mirrors")
data class SharedAlarmMirror(
    @PrimaryKey
    val alarmId: String,
    val connectionId: String,
    val title: String,
    val note: String? = null,
    val dueAt: Long,
    val recurrencePattern: String? = null,
    val customIntervalMillis: Long? = null,
    val repeatStartHour: Int? = null,
    val repeatStartMinute: Int? = null,
    val repeatEndHour: Int? = null,
    val repeatEndMinute: Int? = null,
    val emoji: String? = null,
    val ownerMemberId: String? = null,
    val ownerDisplayName: String? = null,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun asReminderStub(partnerName: String?): Reminder {
        val descriptionParts = buildList {
            note?.takeIf { it.isNotBlank() }?.let(::add)
            partnerName?.takeIf { it.isNotBlank() }?.let { add("Shared with $it") }
            ownerDisplayName?.takeIf { it.isNotBlank() }?.let { add("By $it") }
        }

        return Reminder(
            _id = stableReminderId(),
            title = title,
            description = descriptionParts.joinToString(" • ").takeIf { it.isNotBlank() },
            dueDate = dueAt,
            recurrencePattern = recurrencePattern
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { RecurrencePattern.valueOf(it) }.getOrNull() },
            customIntervalMillis = customIntervalMillis,
            repeatStartHour = repeatStartHour,
            repeatStartMinute = repeatStartMinute,
            repeatEndHour = repeatEndHour,
            repeatEndMinute = repeatEndMinute,
            emoji = emoji
        )
    }

    fun stableReminderId(): Long {
        return (alarmId.hashCode().toLong() and 0x7FFFFFFF)
            .takeIf { it > 0L }
            ?: 1L
    }
}
