package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val connectionId: String,
    val senderDeviceId: String = "",
    val senderName: String = "",
    val isMine: Boolean = false,
    val type: String = "text",
    val text: String = "",
    val gifUrl: String = "",
    val sentAt: Long = System.currentTimeMillis(),
    val status: String = "SENT",
    val timestamp: Long = System.currentTimeMillis()
)
