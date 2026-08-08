package com.aman.gigi.repository

import com.aman.gigi.db.LoveCardDao
import com.aman.gigi.model.LoveCardDeck
import com.aman.gigi.model.LoveCardDeckItem
import com.aman.gigi.model.LoveCardDraftItem
import com.aman.gigi.model.LoveCardDraftResponse
import com.aman.gigi.model.LoveCardItemMirror
import com.aman.gigi.model.LoveCardLocalState
import com.aman.gigi.model.LoveCardResponseMirror
import com.aman.gigi.model.LoveCardStackMirror
import com.aman.gigi.model.LoveCardStackStatus
import com.aman.gigi.model.LoveCardType
import com.aman.gigi.model.encodeLoveCardDecorations
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LoveCardRepository @Inject constructor(
    private val loveCardDao: LoveCardDao
) {
    private fun sanitizeNullable(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    fun observeDecks(): Flow<List<LoveCardDeck>> {
        return combine(
            loveCardDao.observeStacks(),
            loveCardDao.observeItems(),
            loveCardDao.observeResponses()
        ) { stacks, items, responses ->
            buildDecks(stacks, items, responses)
        }
    }

    suspend fun createOutgoingDeck(
        connectionId: String,
        senderMemberId: String?,
        senderDisplayName: String?,
        title: String,
        cards: List<LoveCardDraftItem>,
        unlockDate: Long? = null
    ): LoveCardDeck {
        val stackId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val normalizedCards = cards.mapIndexed { index, draft ->
            LoveCardItemMirror(
                cardId = java.util.UUID.randomUUID().toString(),
                stackId = stackId,
                connectionId = connectionId,
                type = draft.type.name,
                prompt = draft.prompt.trim(),
                choicesJson = draft.choices.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
                theme = draft.theme,
                animationStyle = draft.animationStyle,
                decorationsJson = encodeLoveCardDecorations(draft.decorations),
                sortOrder = index
            )
        }
        val stack = LoveCardStackMirror(
            stackId = stackId,
            connectionId = connectionId,
            title = title.trim().ifBlank { "A sweet little deck" },
            senderMemberId = senderMemberId,
            senderDisplayName = senderDisplayName,
            status = LoveCardStackStatus.SENT.name,
            localState = LoveCardLocalState.PENDING_SEND.name,
            theme = normalizedCards.firstOrNull()?.theme,
            previewText = normalizedCards.firstOrNull()?.prompt,
            isIncoming = false,
            createdAt = now,
            updatedAt = now,
            unlockDate = unlockDate
        )

        loveCardDao.upsertStacks(listOf(stack))
        loveCardDao.upsertItems(normalizedCards)
        return LoveCardDeck(stack = stack, items = normalizedCards.map { LoveCardDeckItem(it, null) })
    }

    suspend fun markDeckQueued(stackId: String) {
        val stack = loveCardDao.getStackById(stackId) ?: return
        loveCardDao.upsertStacks(
            listOf(
                stack.copy(
                    localState = LoveCardLocalState.PENDING_SEND.name,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
    }

    suspend fun markDeckOpened(stackId: String, openedAt: Long = System.currentTimeMillis()) {
        val stack = loveCardDao.getStackById(stackId) ?: return
        loveCardDao.upsertStacks(
            listOf(
                stack.copy(
                    status = LoveCardStackStatus.OPENED.name,
                    openedAt = openedAt,
                    updatedAt = openedAt
                )
            )
        )
    }

    suspend fun answerDeck(
        stackId: String,
        connectionId: String,
        memberId: String?,
        responses: List<LoveCardDraftResponse>
    ): LoveCardDeck? {
        val stack = loveCardDao.getStackById(stackId) ?: return null
        val now = System.currentTimeMillis()
        val responseMirrors = responses.mapNotNull { draft ->
            val hasContent = !draft.answerText.isNullOrBlank() || !draft.selectedChoice.isNullOrBlank() || !draft.emojiReaction.isNullOrBlank()
            if (!hasContent) return@mapNotNull null
            LoveCardResponseMirror(
                responseId = "$stackId:${draft.cardId}",
                stackId = stackId,
                cardId = draft.cardId,
                answerText = draft.answerText?.trim()?.ifBlank { null },
                selectedChoice = draft.selectedChoice?.trim()?.ifBlank { null },
                emojiReaction = draft.emojiReaction?.trim()?.ifBlank { null },
                answeredAt = now,
                answeredByMemberId = memberId
            )
        }
        loveCardDao.upsertResponses(responseMirrors)
        val updatedStack = stack.copy(
            connectionId = connectionId,
            status = LoveCardStackStatus.ANSWERED.name,
            localState = LoveCardLocalState.PENDING_ANSWER.name,
            answeredAt = now,
            updatedAt = now
        )
        loveCardDao.upsertStacks(listOf(updatedStack))
        val items = loveCardDao.getItemsForStack(stackId).sortedBy { it.sortOrder }
        val responseMap = responseMirrors.associateBy { it.cardId }
        return LoveCardDeck(
            stack = updatedStack,
            items = items.map { LoveCardDeckItem(it, responseMap[it.cardId]) }
        )
    }

    suspend fun reconcileWithServer(
        serverStacks: List<LoveCardStackMirror>,
        serverItems: List<LoveCardItemMirror>,
        serverResponses: List<LoveCardResponseMirror>,
        authoritative: Boolean = false
    ) {
        android.util.Log.i("LoveCardRepository", "🔄 reconcileWithServer called. Stacks=${serverStacks.size}, Authoritative=$authoritative")
        if (authoritative) {
            loveCardDao.deleteAllResponses()
            loveCardDao.deleteAllItems()
            loveCardDao.deleteAllStacks()
        }

        val serverStackIds = serverStacks.map { it.stackId }
        val syncedStateName = LoveCardLocalState.SYNCED.name

        // 1. Perform UPSERT first to update existing or add new data
        if (serverStacks.isNotEmpty()) {
            loveCardDao.upsertStacks(serverStacks.map { it.copy(localState = syncedStateName) })
        }
        if (serverItems.isNotEmpty()) {
            loveCardDao.upsertItems(serverItems)
        }
        if (serverResponses.isNotEmpty()) {
            loveCardDao.upsertResponses(serverResponses)
        }

        if (!authoritative) {
            // 2. Perform targeted cleanup of synced items that are no longer on the server
            // GRACE PERIOD: Don't delete stacks that were updated in the last 1 minute.
            val cutoff = System.currentTimeMillis() - 60_000L 
            loveCardDao.deleteSyncedStacksNotInList(serverStackIds, syncedStateName, cutoff)
            
            // Clean up items/responses whose stacks were deleted
            loveCardDao.deleteOrphanedItems()
            loveCardDao.deleteOrphanedResponses()
        }
    }

    /**
     * Applies an incoming love card stack from the sync protocol.
     * Returns true if this is a new stack for this device.
     */
    suspend fun applyIncomingStack(
        stack: LoveCardStackMirror,
        items: List<LoveCardItemMirror>
    ): Boolean {
        val existing = loveCardDao.getStackById(stack.stackId)
        val isNew = existing == null
        
        loveCardDao.upsertStacks(listOf(stack.copy(localState = LoveCardLocalState.SYNCED.name)))
        if (items.isNotEmpty()) loveCardDao.upsertItems(items)
        
        return isNew
    }

    suspend fun applyOpenedEvent(stackId: String, openedAt: Long) {
        val current = loveCardDao.getStackById(stackId) ?: return
        loveCardDao.upsertStacks(
            listOf(
                current.copy(
                    status = LoveCardStackStatus.OPENED.name,
                    openedAt = openedAt,
                    updatedAt = openedAt,
                    localState = LoveCardLocalState.SYNCED.name
                )
            )
        )
    }

    suspend fun applyAnsweredEvent(
        stackId: String,
        answeredAt: Long,
        responses: List<LoveCardResponseMirror>
    ) {
        val current = loveCardDao.getStackById(stackId) ?: return
        loveCardDao.upsertResponses(responses)
        loveCardDao.upsertStacks(
            listOf(
                current.copy(
                    status = LoveCardStackStatus.ANSWERED.name,
                    localState = LoveCardLocalState.SYNCED.name,
                    answeredAt = answeredAt,
                    updatedAt = answeredAt
                )
            )
        )
    }

    suspend fun markSynced(stackId: String) {
        val current = loveCardDao.getStackById(stackId) ?: return
        loveCardDao.upsertStacks(listOf(current.copy(localState = LoveCardLocalState.SYNCED.name, updatedAt = System.currentTimeMillis())))
    }

    suspend fun getDeckById(stackId: String): LoveCardDeck? {
        val stack = loveCardDao.getStackById(stackId) ?: return null
        val items = loveCardDao.getItemsForStack(stackId).sortedBy { it.sortOrder }
        val responses = loveCardDao.getResponsesForStack(stackId).associateBy { it.cardId }
        return LoveCardDeck(
            stack = stack,
            items = items.map { LoveCardDeckItem(it, responses[it.cardId]) }
        )
    }

    fun parseStacks(jsonArray: JSONArray?): List<LoveCardStackMirror> {
        if (jsonArray == null) return emptyList()
        return buildList {
            for (index in 0 until jsonArray.length()) {
                parseStack(jsonArray.optJSONObject(index))?.let(::add)
            }
        }
    }

    fun parseItems(jsonArray: JSONArray?): List<LoveCardItemMirror> {
        if (jsonArray == null) return emptyList()
        return buildList {
            for (index in 0 until jsonArray.length()) {
                parseItem(jsonArray.optJSONObject(index))?.let(::add)
            }
        }
    }

    fun parseResponses(jsonArray: JSONArray?): List<LoveCardResponseMirror> {
        if (jsonArray == null) return emptyList()
        return buildList {
            for (index in 0 until jsonArray.length()) {
                parseResponse(jsonArray.optJSONObject(index))?.let(::add)
            }
        }
    }

    fun parseStack(json: JSONObject?): LoveCardStackMirror? {
        if (json == null) return null
        val stackId = json.optString("stackId").trim()
        val connectionId = json.optString("connectionCode")
            .ifBlank { json.optString("connectionId") }
            .trim()
            .lowercase()
        if (stackId.isBlank() || connectionId.isBlank()) return null
        return LoveCardStackMirror(
            stackId = stackId,
            connectionId = connectionId,
            title = json.optString("title").ifBlank { "A sweet little deck" },
            senderMemberId = sanitizeNullable(json.optString("senderMemberId")),
            senderDisplayName = sanitizeNullable(json.optString("senderDisplayName")),
            recipientMemberId = sanitizeNullable(json.optString("recipientMemberId")),
            status = json.optString("status").takeIf { it.isNotBlank() } ?: LoveCardStackStatus.SENT.name,
            localState = LoveCardLocalState.SYNCED.name,
            theme = sanitizeNullable(json.optString("theme")),
            previewText = sanitizeNullable(json.optString("previewText")),
            isIncoming = json.optBoolean("isIncoming", false),
            createdAt = json.optLong("createdAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
            updatedAt = json.optLong("updatedAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
            openedAt = json.optLong("openedAt").takeIf { it > 0L },
            answeredAt = json.optLong("answeredAt").takeIf { it > 0L },
            unlockDate = json.optLong("unlockDate").takeIf { it > 0L }
        )
    }

    fun parseItem(json: JSONObject?): LoveCardItemMirror? {
        if (json == null) return null
        val cardId = json.optString("cardId").trim()
        val stackId = json.optString("stackId").trim()
        val connectionId = json.optString("connectionCode")
            .ifBlank { json.optString("connectionId") }
            .trim()
            .lowercase()
        if (cardId.isBlank()) return null
        return LoveCardItemMirror(
            cardId = cardId,
            stackId = stackId.ifBlank { "pending" },
            connectionId = connectionId.ifBlank { "pending" },
            type = json.optString("type").ifBlank { LoveCardType.CUTE_NOTE.name },
            prompt = json.optString("prompt").ifBlank { "A little note for you" },
            choicesJson = json.optJSONArray("choices")?.toString()
                ?: sanitizeNullable(json.optString("choicesJson")),
            theme = sanitizeNullable(json.optString("theme")),
            animationStyle = sanitizeNullable(json.optString("animationStyle")),
            decorationsJson = json.optJSONArray("decorations")?.toString()
                ?: sanitizeNullable(json.optString("decorationsJson")),
            sortOrder = json.optInt("sortOrder", 0)
        )
    }

    fun parseResponse(json: JSONObject?): LoveCardResponseMirror? {
        if (json == null) return null
        val stackId = json.optString("stackId").trim()
        val cardId = json.optString("cardId").trim()
        if (stackId.isBlank() || cardId.isBlank()) return null
        return LoveCardResponseMirror(
            responseId = json.optString("responseId").ifBlank { "$stackId:$cardId" },
            stackId = stackId,
            cardId = cardId,
            answerText = sanitizeNullable(json.optString("answerText")),
            selectedChoice = sanitizeNullable(json.optString("selectedChoice")),
            emojiReaction = sanitizeNullable(json.optString("emojiReaction")),
            answeredAt = json.optLong("answeredAt").takeIf { it > 0L } ?: System.currentTimeMillis(),
            answeredByMemberId = sanitizeNullable(json.optString("answeredByMemberId"))
        )
    }

    private fun buildDecks(
        stacks: List<LoveCardStackMirror>,
        items: List<LoveCardItemMirror>,
        responses: List<LoveCardResponseMirror>
    ): List<LoveCardDeck> {
        val itemsByStack = items.groupBy { it.stackId }
        val responsesByCard = responses.associateBy { "${it.stackId}:${it.cardId}" }
        return stacks
            .sortedByDescending { it.updatedAt }
            .map { stack ->
                LoveCardDeck(
                    stack = stack,
                    items = itemsByStack[stack.stackId]
                        .orEmpty()
                        .sortedBy { it.sortOrder }
                        .map { card ->
                            LoveCardDeckItem(
                                card = card,
                                response = responsesByCard["${stack.stackId}:${card.cardId}"]
                            )
                        }
                )
            }
    }

    /**
     * Receives an incoming love card stack from the sync protocol.
     */
    suspend fun receiveIncomingDeck(
        connectionId: String,
        stackId: String,
        title: String,
        senderName: String,
        unlockDate: Long?,
        cardsJson: String
    ) {
        val stack = LoveCardStackMirror(
            stackId = stackId,
            connectionId = connectionId.lowercase(),
            title = title,
            senderDisplayName = senderName,
            status = LoveCardStackStatus.SENT.name,
            localState = LoveCardLocalState.SYNCED.name,
            isIncoming = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            unlockDate = unlockDate
        )
        
        val cardsArray = JSONArray(cardsJson)
        val items = mutableListOf<LoveCardItemMirror>()
        for (i in 0 until cardsArray.length()) {
            val cardObj = cardsArray.getJSONObject(i)
            items.add(LoveCardItemMirror(
                cardId = cardObj.getString("cardId"),
                stackId = stackId,
                connectionId = connectionId.lowercase(),
                type = cardObj.getString("type"),
                prompt = cardObj.getString("prompt"),
                choicesJson = cardObj.optJSONArray("choices")?.toString(),
                theme = cardObj.optString("theme"),
                animationStyle = cardObj.optString("animationStyle"),
                decorationsJson = cardObj.optJSONArray("decorations")?.toString(),
                sortOrder = i
            ))
        }
        
        loveCardDao.upsertStacks(listOf(stack))
        loveCardDao.upsertItems(items)
    }

    /**
     * Marks a deck as opened by the partner.
     */
    suspend fun markDeckAsPartnerOpened(stackId: String) {
        val stack = loveCardDao.getStackById(stackId) ?: return
        loveCardDao.upsertStacks(listOf(stack.copy(
            status = LoveCardStackStatus.OPENED.name,
            updatedAt = System.currentTimeMillis()
        )))
    }

    /**
     * Receives answers for a love card stack from the partner.
     */
    suspend fun receiveAnswers(stackId: String, responsesJson: String): String? {
        android.util.Log.i("LoveCardRepository", "📥 [REPLY] Received answers for stack: $stackId")
        val stack = loveCardDao.getStackById(stackId) ?: run {
            android.util.Log.w("LoveCardRepository", "⚠️ [REPLY] Stack $stackId not found in DB. Cannot save answers.")
            return null
        }
        val responsesArray = JSONArray(responsesJson)
        val mirrors = mutableListOf<LoveCardResponseMirror>()
        for (i in 0 until responsesArray.length()) {
            val respObj = responsesArray.getJSONObject(i)
            mirrors.add(LoveCardResponseMirror(
                responseId = "$stackId:${respObj.getString("cardId")}",
                stackId = stackId,
                cardId = respObj.getString("cardId"),
                answerText = respObj.optString("answerText"),
                selectedChoice = respObj.optString("selectedChoice"),
                emojiReaction = respObj.optString("emojiReaction"),
                answeredAt = System.currentTimeMillis()
            ))
        }
        loveCardDao.upsertResponses(mirrors)
        loveCardDao.upsertStacks(listOf(stack.copy(
            status = LoveCardStackStatus.ANSWERED.name,
            updatedAt = System.currentTimeMillis()
        )))
        return stack.title
    }

    /**
     * Delete all love card data (stacks, items, responses) from local storage.
     */
    suspend fun deleteAllData() {
        loveCardDao.deleteAllResponses()
        loveCardDao.deleteAllItems()
        loveCardDao.deleteAllStacks()
    }
}
