package com.aman.gigi.repository

import com.aman.gigi.db.ScribbleDao
import com.aman.gigi.model.Scribble
import com.aman.gigi.model.ScribbleStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.aman.gigi.widget.LatestNoteWidget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing scribbles
 */
@Singleton
class ScribbleRepository @Inject constructor(
    private val scribbleDao: ScribbleDao,
    @ApplicationContext private val context: Context
) {
    
    /**
     * Get all scribbles for a connection
     */
    fun getScribbleSummariesByConnection(connectionId: String): Flow<List<com.aman.gigi.model.ScribbleSummary>> {
        return scribbleDao.getScribbleSummariesByConnection(connectionId)
    }

    fun getFullScribblesFlowByConnection(connectionId: String): Flow<List<Scribble>> {
        return scribbleDao.getFullScribblesFlowByConnection(connectionId)
    }

    suspend fun getFullScribblesByConnection(connectionId: String): List<Scribble> {
        return scribbleDao.getFullScribblesByConnection(connectionId)
    }
    
    /**
     * Get pending scribbles (to be sent)
     */
    fun getPendingScribbleSummaries(): Flow<List<com.aman.gigi.model.ScribbleSummary>> {
        return scribbleDao.getPendingScribbleSummaries()
    }
    
    /**
     * Get received scribbles (to be displayed)
     */
    suspend fun getReceivedScribbleSummaries(limit: Int = 10): List<com.aman.gigi.model.ScribbleSummary> {
        return scribbleDao.getReceivedScribbleSummaries(limit)
    }

    suspend fun getLatestScribbleSummaries(limit: Int = 1): List<com.aman.gigi.model.ScribbleSummary> {
        return scribbleDao.getLatestScribbleSummaries(limit)
    }
    
    /**
     * Get scribble by ID
     */
    suspend fun getScribbleById(scribbleId: String): Scribble? {
        return scribbleDao.getScribbleById(scribbleId)
    }
    
    /**
     * Create a new scribble (to send)
     */
    suspend fun createScribble(scribble: Scribble): String {
        val scribbleId = scribble.scribbleId.ifEmpty { java.util.UUID.randomUUID().toString() }
        
        // Safety: Simplify drawing if it's too complex (decimation)
        val processedScribble = simplifyScribble(scribble)
        
        val newScribble = processedScribble.copy(
            scribbleId = scribbleId,
            status = com.aman.gigi.model.ScribbleStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        scribbleDao.insertScribble(newScribble)
        runCatching { LatestNoteWidget.refresh(context) }
        return scribbleId
    }

    /**
     * Reduces the number of points in a scribble if it's too dense.
     * This prevents OOM during sync and lag during rendering.
     */
    private fun simplifyScribble(scribble: Scribble): Scribble {
        val totalPoints = scribble.strokes.sumOf { it.points.size }
        val pointThreshold = 3000
        val hardLimit = 5000
        
        if (totalPoints <= pointThreshold) return scribble
        
        android.util.Log.i("ScribbleRepo", "⚠️ Scribble too complex ($totalPoints points). Simplifiying...")
        
        // 1. Hard limit on total points (drop late strokes)
        var currentCount = 0
        val limitedStrokes = mutableListOf<com.aman.gigi.model.Stroke>()
        for (stroke in scribble.strokes) {
            if (currentCount + stroke.points.size > hardLimit) {
                // Take only partial points if we can, or just break
                val remaining = hardLimit - currentCount
                if (remaining > 10) {
                    limitedStrokes.add(stroke.copy(points = stroke.points.take(remaining)))
                }
                break
            }
            limitedStrokes.add(stroke)
            currentCount += stroke.points.size
        }

        // 2. Decimation: Keep every Nth point if still too dense per stroke
        // (Simplified decimation: if a stroke has > 200 points, keep every 2nd point)
        val optimizedStrokes = limitedStrokes.map { stroke ->
            if (stroke.points.size > 200) {
                stroke.copy(points = stroke.points.filterIndexed { index, _ -> index % 2 == 0 })
            } else {
                stroke
            }
        }

        return scribble.copy(strokes = optimizedStrokes)
    }
    
    /**
     * Save received scribble
     */
    suspend fun saveReceivedScribble(scribble: Scribble) {
        val receivedScribble = scribble.copy(
            status = ScribbleStatus.RECEIVED,
            receivedAt = System.currentTimeMillis()
        )
        scribbleDao.insertScribble(receivedScribble)
        runCatching { LatestNoteWidget.refresh(context) }
    }
    
    /**
     * Update scribble status
     */
    suspend fun updateScribbleStatus(scribbleId: String, status: ScribbleStatus) {
        scribbleDao.updateScribbleStatus(scribbleId, status)
    }
    
    /**
     * Mark scribble as sent
     */
    suspend fun markAsSent(scribbleId: String) {
        scribbleDao.markAsSent(
            scribbleId = scribbleId,
            status = ScribbleStatus.SENT,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Mark scribble as displayed
     */
    suspend fun markAsDisplayed(scribbleId: String) {
        scribbleDao.markAsDisplayed(
            scribbleId = scribbleId,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Get scribbles by status
     */
    suspend fun getScribbleSummariesByStatus(status: ScribbleStatus, limit: Int = 10): List<com.aman.gigi.model.ScribbleSummary> {
        return scribbleDao.getScribbleSummariesByStatus(status, limit)
    }
    
    /**
     * Delete scribble
     */
    suspend fun deleteScribble(scribbleId: String) {
        scribbleDao.deleteScribble(scribbleId)
    }
    
    /**
     * Delete all scribbles for a connection
     */
    suspend fun deleteScribblesByConnection(connectionId: String) {
        scribbleDao.deleteScribblesByConnection(connectionId)
    }
    
    /**
     * Clean up old displayed scribbles
     */
    suspend fun cleanupOldScribbles(olderThanMillis: Long = 24 * 60 * 60 * 1000) {
        val cutoffTime = System.currentTimeMillis() - olderThanMillis
        scribbleDao.deleteOldDisplayedScribbles(cutoffTime)
    }
    
    /**
     * Delete all scribbles
     */
    suspend fun deleteAllScribbles() {
        scribbleDao.deleteAllScribbles()
    }

    /**
     * Enforce history retention policy.
     * Creators: Infinite history (no action).
     * Joiners: 24 hour history (delete older).
     */
    suspend fun enforceHistoryRetention(connectionId: String, historyDays: Int) {
        if (historyDays > 0) {
            val cutoff = System.currentTimeMillis() - (historyDays.toLong() * 24L * 60L * 60L * 1000L)
            scribbleDao.deleteOldHistory(connectionId, cutoff)
        }
    }

    /**
     * Clear all history for a specific connection manually
     */
    suspend fun clearHistory(connectionId: String) {
        scribbleDao.deleteScribblesByConnection(connectionId)
    }
}
