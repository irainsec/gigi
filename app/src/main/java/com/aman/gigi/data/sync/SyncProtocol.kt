package com.aman.gigi.data.sync

import org.json.JSONObject
import java.util.UUID

object SyncProtocol {
    const val VERSION = 2

    const val ACTION_PRESENCE_SNAPSHOT = "presence_snapshot"
    const val ACTION_PARTNER_STATUS_CHANGED = "partner_status_changed"
    const val ACTION_RESUME_SESSION = "resume_session"
    const val ACTION_RESUME_RESULT = "resume_result"
    const val ACTION_SERVER_STATUS = "server_status"
    const val ACTION_ACTION_ACCEPTED = "action_accepted"
    const val ACTION_ACTION_DELIVERED = "action_delivered"
    const val ACTION_ACTION_DISPLAYED = "action_displayed"
    const val ACTION_ACTION_FAILED = "action_failed"
    const val ACTION_REMOTE_COMMAND = "remote_command"
    const val ACTION_SCRIBBLE = "scribble"
    const val ACTION_SHARED_ALARM_UPSERTED = "shared_alarm_upserted"
    const val ACTION_SHARED_ALARM_DELETED = "shared_alarm_deleted"
    const val ACTION_QUOTE_SENT = "quote_sent"
    const val ACTION_CARD_STACK_SENT = "card_stack_sent"
    const val ACTION_CARD_STACK_ANSWERED = "card_stack_answered"
    const val ACTION_CARD_STACK_OPENED = "card_stack_opened"
    const val ACTION_PROFILE_UPDATED = "profile_updated"
    const val ACTION_PARTNER_PROFILE_UPDATED = "partner_profile_updated"
    const val ACTION_WEBRTC_SIGNAL = "webrtc_signal"

    data class Envelope(
        val protocolVersion: Int,
        val messageId: String,
        val connectionId: String?,
        val senderDeviceId: String?,
        val recipientDeviceId: String?,
        val actionType: String,
        val payload: JSONObject,
        val createdAt: Long,
        val requiresDisplayReceipt: Boolean
    )

    fun buildEnvelope(
        connectionId: String?,
        senderDeviceId: String?,
        actionType: String,
        payload: JSONObject = JSONObject(),
        recipientDeviceId: String? = null,
        messageId: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
        requiresDisplayReceipt: Boolean = false
    ): JSONObject {
        return JSONObject().apply {
            put("protocolVersion", VERSION)
            put("messageId", messageId)
            put("connectionId", connectionId)
            put("senderDeviceId", senderDeviceId)
            if (!recipientDeviceId.isNullOrBlank()) put("recipientDeviceId", recipientDeviceId)
            put("actionType", actionType)
            put("payload", payload)
            put("createdAt", createdAt)
            put("requiresDisplayReceipt", requiresDisplayReceipt)
        }
    }

    fun parse(text: String): Envelope? {
        return try {
            val json = JSONObject(text)
            if (!json.has("protocolVersion") && !json.has("actionType")) {
                null
            } else {
                Envelope(
                    protocolVersion = json.optInt("protocolVersion", 1),
                    messageId = json.optString("messageId"),
                    connectionId = json.optString("connectionId").ifBlank { null },
                    senderDeviceId = json.optString("senderDeviceId").ifBlank { null },
                    recipientDeviceId = json.optString("recipientDeviceId").ifBlank { null },
                    actionType = json.optString("actionType"),
                    payload = json.optJSONObject("payload") ?: JSONObject(),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    requiresDisplayReceipt = json.optBoolean("requiresDisplayReceipt", false)
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
