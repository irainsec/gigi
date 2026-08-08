package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbound_actions")
data class OutboundAction(
    @PrimaryKey
    val id: String,
    val connectionId: String,
    val actionType: String,
    val payloadJson: String,
    val localAssetPath: String? = null,
    val remoteAssetUrl: String? = null,
    val relatedScribbleId: String? = null,
    val state: String = OutboundActionState.QUEUED.name,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val requiresDisplayReceipt: Boolean = false,
    val targetDeviceId: String? = null
)

enum class OutboundActionState {
    QUEUED,
    SENDING,
    ACCEPTED,
    DELIVERED,
    DISPLAYED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
}
