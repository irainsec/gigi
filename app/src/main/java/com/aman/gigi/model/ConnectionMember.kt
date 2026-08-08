package com.aman.gigi.model

import androidx.room.Entity

@Entity(tableName = "connection_members", primaryKeys = ["connectionId", "memberDeviceId"])
data class ConnectionMember(
    val connectionId: String,
    val memberDeviceId: String,
    val memberName: String,
    val memberEmoji: String = "🌻",
    val memberAvatarUrl: String? = null,
    // The member's chosen animated profile emoji (asset/URL), synced from the server.
    val emojiUrl: String? = null,
    val role: String = ConnectionRole.PARTNER.name, // CREATOR, PARTNER
    val joinedAt: Long = System.currentTimeMillis()
)
