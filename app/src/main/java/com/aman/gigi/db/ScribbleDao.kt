package com.aman.gigi.db

import androidx.room.*
import com.aman.gigi.model.Scribble
import com.aman.gigi.model.ScribbleStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Scribble operations
 */
@Dao
interface ScribbleDao {
    
    @Query("SELECT scribbleId, connectionId, status, createdAt, mediaType, revealType, isSent FROM scribbles WHERE connectionId = :connectionId ORDER BY createdAt DESC")
    fun getScribbleSummariesByConnection(connectionId: String): Flow<List<com.aman.gigi.model.ScribbleSummary>>
    
    @Query("SELECT * FROM scribbles WHERE connectionId = :connectionId ORDER BY createdAt DESC")
    fun getFullScribblesFlowByConnection(connectionId: String): Flow<List<Scribble>>

    @Query("SELECT * FROM scribbles WHERE connectionId = :connectionId ORDER BY createdAt DESC")
    suspend fun getFullScribblesByConnection(connectionId: String): List<Scribble>
    
    @Query("SELECT scribbleId, connectionId, status, createdAt, mediaType, revealType, isSent FROM scribbles WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getScribbleSummariesByStatus(status: ScribbleStatus, limit: Int = 10): List<com.aman.gigi.model.ScribbleSummary>
    
    @Query("SELECT * FROM scribbles WHERE scribbleId = :scribbleId")
    suspend fun getScribbleById(scribbleId: String): Scribble?

    /**
     * Photo sparkles for a connection, newest first — summaries only, so the reveal screen
     * can page back through history without loading every base64 blob.
     */
    @Query(
        """
        SELECT scribbleId, connectionId, status, createdAt, mediaType, revealType, isSent
        FROM scribbles
        WHERE connectionId = :connectionId
          AND ((mediaBase64 IS NOT NULL AND mediaBase64 != '') OR (mediaUrl IS NOT NULL AND mediaUrl != ''))
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun getSparkleSummariesByConnection(connectionId: String, limit: Int): List<com.aman.gigi.model.ScribbleSummary>
    
    @Query("SELECT scribbleId, connectionId, status, createdAt, mediaType, revealType, isSent FROM scribbles WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY createdAt ASC")
    fun getPendingScribbleSummaries(): Flow<List<com.aman.gigi.model.ScribbleSummary>>
    
    @Query("SELECT scribbleId, connectionId, status, createdAt, mediaType, revealType, isSent FROM scribbles WHERE status = 'RECEIVED' ORDER BY receivedAt ASC LIMIT :limit")
    suspend fun getReceivedScribbleSummaries(limit: Int = 10): List<com.aman.gigi.model.ScribbleSummary>

    @Query("SELECT scribbleId, connectionId, status, createdAt, mediaType, revealType, isSent FROM scribbles ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLatestScribbleSummaries(limit: Int = 1): List<com.aman.gigi.model.ScribbleSummary>

    @Query("SELECT * FROM scribbles ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestScribbleOnce(): Scribble?

    @Query("SELECT * FROM scribbles WHERE connectionId = :connectionId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestScribbleForConnection(connectionId: String): Scribble?

    /**
     * Memory counts per connection for the Memories hub.
     * Only rows that actually carry something to look at are counted — presence pings
     * and empty payloads share this table.
     */
    @Query(
        """
        SELECT connectionId AS connectionId, COUNT(*) AS total
        FROM scribbles
        WHERE (mediaBase64 IS NOT NULL AND mediaBase64 != '')
           OR (mediaUrl IS NOT NULL AND mediaUrl != '')
           OR (secretMessage IS NOT NULL AND secretMessage != '')
           OR (strokes IS NOT NULL AND strokes != '' AND strokes != '[]')
        GROUP BY connectionId
        """
    )
    fun getMemoryCountsByConnection(): Flow<List<com.aman.gigi.model.ConnectionMemoryCount>>

    @Query("DELETE FROM scribbles WHERE connectionId = :connectionId AND createdAt < :timestamp")
    suspend fun deleteOldHistory(connectionId: String, timestamp: Long)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScribble(scribble: Scribble)
    
    @Update
    suspend fun updateScribble(scribble: Scribble)
    
    @Query("UPDATE scribbles SET status = :status WHERE scribbleId = :scribbleId")
    suspend fun updateScribbleStatus(scribbleId: String, status: ScribbleStatus)
    
    @Query("UPDATE scribbles SET status = :status, sentAt = :timestamp WHERE scribbleId = :scribbleId")
    suspend fun markAsSent(scribbleId: String, status: ScribbleStatus, timestamp: Long)

    /** Remembers where an offloaded sparkle lives so history can re-fetch it later. */
    @Query("UPDATE scribbles SET mediaUrl = :mediaUrl WHERE scribbleId = :scribbleId")
    suspend fun updateMediaUrl(scribbleId: String, mediaUrl: String)
    
    @Query("UPDATE scribbles SET displayedAt = :timestamp, status = 'DISPLAYED' WHERE scribbleId = :scribbleId")
    suspend fun markAsDisplayed(scribbleId: String, timestamp: Long)
    
    @Query("DELETE FROM scribbles WHERE scribbleId = :scribbleId")
    suspend fun deleteScribble(scribbleId: String)
    
    @Query("DELETE FROM scribbles WHERE connectionId = :connectionId")
    suspend fun deleteScribblesByConnection(connectionId: String)
    
    @Query("DELETE FROM scribbles WHERE status = 'DISPLAYED' AND displayedAt < :timestamp")
    suspend fun deleteOldDisplayedScribbles(timestamp: Long)
    
    @Query("DELETE FROM scribbles")
    suspend fun deleteAllScribbles()
}
