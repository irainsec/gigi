package com.aman.gigi.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aman.gigi.model.OutboundAction
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboundActionDao {

    @Query(
        """
        SELECT * FROM outbound_actions
        WHERE connectionId = :connectionId
          AND state IN ('QUEUED', 'FAILED_RETRYABLE')
          AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getDueActionsForConnection(connectionId: String, now: Long, limit: Int = 25): List<OutboundAction>

    @Query("SELECT * FROM outbound_actions WHERE id = :actionId LIMIT 1")
    suspend fun getActionById(actionId: String): OutboundAction?

    @Query("SELECT * FROM outbound_actions WHERE relatedScribbleId = :scribbleId LIMIT 1")
    suspend fun getActionByRelatedScribbleId(scribbleId: String): OutboundAction?

    @Query("SELECT * FROM outbound_actions WHERE connectionId = :connectionId AND state IN (:states)")
    suspend fun getActionsByStates(connectionId: String, states: List<String>): List<OutboundAction>

    @Query(
        """
        SELECT COUNT(*) FROM outbound_actions
        WHERE connectionId = :connectionId
          AND state IN ('QUEUED', 'SENDING', 'FAILED_RETRYABLE')
        """
    )
    fun observeActiveActionCount(connectionId: String): Flow<Int>

    @Query(
        """
        SELECT id FROM outbound_actions
        WHERE connectionId = :connectionId
          AND state IN ('ACCEPTED', 'DELIVERED', 'DISPLAYED', 'FAILED_PERMANENT')
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getTerminalActionIds(connectionId: String, limit: Int = 100): List<String>

    @Query(
        """
        UPDATE outbound_actions
        SET state = 'QUEUED', nextAttemptAt = :now, updatedAt = :now, lastError = 'Recovered due to timeout'
        WHERE connectionId = :connectionId
          AND state = 'SENDING'
          AND updatedAt <= :cutoff
        """
    )
    suspend fun requeueTimedOutSendingActions(connectionId: String, cutoff: Long, now: Long)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: OutboundAction)

    @Update
    suspend fun updateAction(action: OutboundAction)

    @Query("DELETE FROM outbound_actions WHERE state IN ('DISPLAYED', 'FAILED_PERMANENT') AND updatedAt < :cutoff")
    suspend fun deleteCompletedActionsOlderThan(cutoff: Long)

    @Query("DELETE FROM outbound_actions WHERE actionType = 'remote_command'")
    suspend fun deleteRemoteCommands()

    @Query("DELETE FROM outbound_actions")
    suspend fun deleteAllActions()
}
