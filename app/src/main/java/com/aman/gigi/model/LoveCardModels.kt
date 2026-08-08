package com.aman.gigi.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class LoveCardType {
    CUTE_NOTE,
    QUESTION,
    MULTIPLE_CHOICE,
    ANIMATED_GIFT,
    PHOTO_MEMORY,
    COUPON,
    MUSIC_DEDICATION,
    COUNTDOWN,
    VOICE_MESSAGE,
    SCRATCH_REVEAL
}

enum class LoveCardStackStatus {
    SENT,
    OPENED,
    ANSWERED
}

enum class LoveCardLocalState {
    SYNCED,
    PENDING_SEND,
    PENDING_ANSWER
}

@Entity(
    tableName = "love_card_stacks",
    indices = [
        Index(value = ["connectionId", "updatedAt"])
    ]
)
data class LoveCardStackMirror(
    @PrimaryKey
    val stackId: String,
    val connectionId: String,
    val title: String,
    val senderMemberId: String? = null,
    val senderDisplayName: String? = null,
    val recipientMemberId: String? = null,
    val status: String = LoveCardStackStatus.SENT.name,
    val localState: String = LoveCardLocalState.SYNCED.name,
    val theme: String? = null,
    val previewText: String? = null,
    val isIncoming: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val openedAt: Long? = null,
    val answeredAt: Long? = null,
    val unlockDate: Long? = null
)

@Entity(
    tableName = "love_card_items",
    indices = [
        Index(value = ["stackId", "sortOrder"])
    ]
)
data class LoveCardItemMirror(
    @PrimaryKey
    val cardId: String,
    val stackId: String,
    val connectionId: String,
    val type: String,
    val prompt: String,
    val choicesJson: String? = null,
    val theme: String? = null,
    val animationStyle: String? = null,
    val decorationsJson: String? = null,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "love_card_responses",
    indices = [
        Index(value = ["stackId", "cardId"], unique = true)
    ]
)
data class LoveCardResponseMirror(
    @PrimaryKey
    val responseId: String,
    val stackId: String,
    val cardId: String,
    val answerText: String? = null,
    val selectedChoice: String? = null,
    val emojiReaction: String? = null,
    val answeredAt: Long = System.currentTimeMillis(),
    val answeredByMemberId: String? = null
)

data class LoveCardStickerPlacement(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.32f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val style: String = "emoji",
    val mediaUrl: String? = null
)

data class LoveCardDraftItem(
    val type: LoveCardType,
    val prompt: String,
    val choices: List<String> = emptyList(),
    val theme: String = "blush",
    val animationStyle: String = "none",
    val decorations: List<LoveCardStickerPlacement> = emptyList()
)

data class LoveCardDraftResponse(
    val cardId: String,
    val answerText: String? = null,
    val selectedChoice: String? = null,
    val emojiReaction: String? = null
)

data class LoveCardDeckItem(
    val card: LoveCardItemMirror,
    val response: LoveCardResponseMirror? = null
) {
    fun choices(): List<String> {
        val raw = card.choicesJson?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun decorations(): List<LoveCardStickerPlacement> = decodeLoveCardDecorations(card.decorationsJson)
}

data class LoveCardDeck(
    val stack: LoveCardStackMirror,
    val items: List<LoveCardDeckItem>
) {
    val answeredCount: Int get() = items.count { it.response != null }
    val totalCount: Int get() = items.size
    val isAnswered: Boolean get() = stack.status == LoveCardStackStatus.ANSWERED.name
}

fun encodeLoveCardDecorations(decorations: List<LoveCardStickerPlacement>): String? {
    if (decorations.isEmpty()) return null
    return JSONArray().apply {
        decorations.take(8).forEach { decoration ->
            put(
                JSONObject().apply {
                    put("id", decoration.id)
                    put("content", decoration.content)
                    put("x", decoration.normalizedX)
                    put("y", decoration.normalizedY)
                    put("scale", decoration.scale)
                    put("rotation", decoration.rotation)
                    put("style", decoration.style)
                    put("mediaUrl", decoration.mediaUrl)
                }
            )
        }
    }.toString()
}

fun decodeLoveCardDecorations(raw: String?): List<LoveCardStickerPlacement> {
    val payload = raw?.trim().orEmpty()
    if (payload.isBlank() || payload.equals("null", ignoreCase = true)) return emptyList()
    return runCatching {
        val array = JSONArray(payload)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val content = json.optString("content").trim()
                if (content.isBlank()) continue
                add(
                    LoveCardStickerPlacement(
                        id = json.optString("id").trim().ifBlank { UUID.randomUUID().toString() },
                        content = content.take(32),
                        normalizedX = json.optDouble("x", 0.5).toFloat().coerceIn(0.05f, 0.88f),
                        normalizedY = json.optDouble("y", 0.32).toFloat().coerceIn(0.05f, 0.88f),
                        scale = json.optDouble("scale", 1.0).toFloat().coerceIn(0.3f, 4.0f),
                        rotation = json.optDouble("rotation", 0.0).toFloat().coerceIn(-30f, 30f),
                        style = json.optString("style").trim().ifBlank { "emoji" }.take(20),
                        mediaUrl = json.optString("mediaUrl").trim().ifBlank { null }
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
