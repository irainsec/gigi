package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class BreakCardConfig(val id: String, val name: String, val animatedSvgUrl: String?)

@Entity(tableName = "break_card_sessions")
data class BreakCardSessionMirror(
    @PrimaryKey val id: String
)

/**
 * A live "let's take a break" call-out. Ephemeral by design — it lives in memory and
 * on the wire only (like a ringing call), so it needs no Room migration.
 */
data class BreakInvite(
    val breakId: String,
    val connectionId: String,
    val cardId: String,
    val fromName: String,
    val fromDeviceId: String,
    val isMine: Boolean,
    val sentAt: Long = System.currentTimeMillis()
)

/** One person's answer to a break invite. */
data class BreakResponse(
    val breakId: String,
    val deviceId: String,
    val name: String,
    val accepted: Boolean
)
