package com.aman.gigi.repository

import com.aman.gigi.db.OutboundActionDao
import com.aman.gigi.model.OutboundAction
import com.aman.gigi.model.OutboundActionState
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboundActionRepository @Inject constructor(
    private val outboundActionDao: OutboundActionDao
) {

    suspend fun enqueueAction(
        connectionId: String,
        actionType: String,
        payload: JSONObject,
        requiresDisplayReceipt: Boolean = false,
        actionId: String = UUID.randomUUID().toString(),
        targetDeviceId: String? = null
    ): String {
        outboundActionDao.insertAction(
            OutboundAction(
                id = actionId,
                connectionId = connectionId,
                actionType = actionType,
                payloadJson = payload.toString(),
                requiresDisplayReceipt = requiresDisplayReceipt,
                targetDeviceId = targetDeviceId
            )
        )
        return actionId
    }

    suspend fun enqueueRemoteCommand(
        connectionId: String,
        command: String,
        data: JSONObject? = null,
        requiresDisplayReceipt: Boolean = false,
        targetDeviceId: String? = null
    ): String {
        val payload = JSONObject().apply {
            put("command", command)
            if (data != null) put("data", data)
        }
        return enqueueAction(
            connectionId = connectionId,
            actionType = "remote_command",
            payload = payload,
            requiresDisplayReceipt = requiresDisplayReceipt,
            targetDeviceId = targetDeviceId
        )
    }

    suspend fun enqueueScribbleIfMissing(connectionId: String, scribbleId: String, requiresDisplayReceipt: Boolean = true) {
        if (outboundActionDao.getActionByRelatedScribbleId(scribbleId) != null) return

        outboundActionDao.insertAction(
            OutboundAction(
                id = scribbleId,
                connectionId = connectionId,
                actionType = "scribble",
                payloadJson = "{}",
                relatedScribbleId = scribbleId,
                requiresDisplayReceipt = requiresDisplayReceipt
            )
        )
    }

    suspend fun getDueActions(connectionId: String, now: Long, limit: Int = 25): List<OutboundAction> {
        return outboundActionDao.getDueActionsForConnection(connectionId, now, limit)
    }

    suspend fun getAction(actionId: String): OutboundAction? = outboundActionDao.getActionById(actionId)

    suspend fun markSending(actionId: String) {
        updateActionState(actionId, OutboundActionState.SENDING)
    }

    suspend fun markQueued(actionId: String) {
        updateActionState(actionId, OutboundActionState.QUEUED, error = null)
    }

    suspend fun markAccepted(actionId: String) {
        updateActionState(actionId, OutboundActionState.ACCEPTED, error = null)
    }

    suspend fun markDelivered(actionId: String) {
        updateActionState(actionId, OutboundActionState.DELIVERED, error = null)
    }

    suspend fun markDisplayed(actionId: String) {
        updateActionState(actionId, OutboundActionState.DISPLAYED, error = null)
    }

    suspend fun markFailedRetryable(actionId: String, nextAttemptAt: Long, error: String?) {
        val existing = outboundActionDao.getActionById(actionId) ?: return
        outboundActionDao.updateAction(
            existing.copy(
                state = OutboundActionState.FAILED_RETRYABLE.name,
                attemptCount = existing.attemptCount + 1,
                nextAttemptAt = nextAttemptAt,
                updatedAt = System.currentTimeMillis(),
                lastError = error
            )
        )
    }

    suspend fun markFailedPermanent(actionId: String, error: String?) {
        val existing = outboundActionDao.getActionById(actionId) ?: return
        outboundActionDao.updateAction(
            existing.copy(
                state = OutboundActionState.FAILED_PERMANENT.name,
                updatedAt = System.currentTimeMillis(),
                lastError = error
            )
        )
    }

    suspend fun updateRemoteAssetUrl(actionId: String, remoteAssetUrl: String) {
        val existing = outboundActionDao.getActionById(actionId) ?: return
        outboundActionDao.updateAction(
            existing.copy(
                remoteAssetUrl = remoteAssetUrl,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun resetStuckActions() {
        // Intentionally left to per-connection recovery through requeueInFlightActions.
    }

    suspend fun requeueTimedOutSendingActions(connectionId: String, timeoutMillis: Long = 30000L) {
        val now = System.currentTimeMillis()
        outboundActionDao.requeueTimedOutSendingActions(connectionId, now - timeoutMillis, now)
    }


    suspend fun requeueInFlightActions(connectionIds: List<String>) {
        val now = System.currentTimeMillis()
        connectionIds.forEach { connectionId ->
            outboundActionDao.getActionsByStates(
                connectionId,
                listOf(OutboundActionState.SENDING.name, OutboundActionState.ACCEPTED.name)
            ).forEach { action ->
                outboundActionDao.updateAction(
                    action.copy(
                        state = OutboundActionState.QUEUED.name,
                        nextAttemptAt = now,
                        updatedAt = now,
                        lastError = "Recovered after process restart"
                    )
                )
            }
        }
    }

    fun observeActiveActionCount(connectionId: String): Flow<Int> {
        return outboundActionDao.observeActiveActionCount(connectionId)
    }

    suspend fun getTerminalActionIds(connectionId: String, limit: Int = 100): List<String> {
        return outboundActionDao.getTerminalActionIds(connectionId, limit)
    }

    suspend fun pruneCompletedActions(olderThanMillis: Long) {
        outboundActionDao.deleteCompletedActionsOlderThan(System.currentTimeMillis() - olderThanMillis)
    }

    private suspend fun updateActionState(
        actionId: String,
        state: OutboundActionState,
        error: String? = null
    ) {
        val existing = outboundActionDao.getActionById(actionId) ?: return
        outboundActionDao.updateAction(
            existing.copy(
                state = state.name,
                updatedAt = System.currentTimeMillis(),
                lastError = error ?: existing.lastError
            )
        )
    }

    suspend fun deleteRemoteCommands() {
        outboundActionDao.deleteRemoteCommands()
    }

    suspend fun deleteAllActions() {
        outboundActionDao.deleteAllActions()
    }
}
