package com.aman.gigi.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aman.gigi.model.LoveCardItemMirror
import com.aman.gigi.model.LoveCardResponseMirror
import com.aman.gigi.model.LoveCardStackMirror
import kotlinx.coroutines.flow.Flow

@Dao
interface LoveCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStacks(stacks: List<LoveCardStackMirror>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<LoveCardItemMirror>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResponses(responses: List<LoveCardResponseMirror>)

    @Query("SELECT * FROM love_card_stacks ORDER BY updatedAt DESC")
    fun observeStacks(): Flow<List<LoveCardStackMirror>>

    @Query("SELECT * FROM love_card_items ORDER BY sortOrder ASC")
    fun observeItems(): Flow<List<LoveCardItemMirror>>

    @Query("SELECT * FROM love_card_responses ORDER BY answeredAt ASC")
    fun observeResponses(): Flow<List<LoveCardResponseMirror>>

    @Query("SELECT * FROM love_card_stacks")
    suspend fun getAllStacksOnce(): List<LoveCardStackMirror>

    @Query("SELECT * FROM love_card_items")
    suspend fun getAllItemsOnce(): List<LoveCardItemMirror>

    @Query("SELECT * FROM love_card_responses")
    suspend fun getAllResponsesOnce(): List<LoveCardResponseMirror>

    @Query("SELECT * FROM love_card_stacks WHERE localState != :syncedState")
    suspend fun getUnsyncedStacks(syncedState: String): List<LoveCardStackMirror>

    @Query("SELECT * FROM love_card_items WHERE stackId IN (:stackIds)")
    suspend fun getItemsForStacks(stackIds: List<String>): List<LoveCardItemMirror>

    @Query("SELECT * FROM love_card_responses WHERE stackId IN (:stackIds)")
    suspend fun getResponsesForStacks(stackIds: List<String>): List<LoveCardResponseMirror>

    @Query("SELECT * FROM love_card_stacks WHERE stackId = :stackId LIMIT 1")
    suspend fun getStackById(stackId: String): LoveCardStackMirror?

    @Query("SELECT * FROM love_card_items WHERE stackId = :stackId ORDER BY sortOrder ASC")
    suspend fun getItemsForStack(stackId: String): List<LoveCardItemMirror>

    @Query("SELECT * FROM love_card_responses WHERE stackId = :stackId ORDER BY answeredAt ASC")
    suspend fun getResponsesForStack(stackId: String): List<LoveCardResponseMirror>

    @Query("DELETE FROM love_card_responses")
    suspend fun deleteAllResponses()

    @Query("DELETE FROM love_card_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM love_card_stacks")
    suspend fun deleteAllStacks()

    @Query("DELETE FROM love_card_stacks WHERE stackId = :stackId")
    suspend fun deleteStackById(stackId: String)

    @Query("DELETE FROM love_card_stacks WHERE stackId NOT IN (:stackIds) AND localState = :syncedState AND updatedAt < :cutoff")
    suspend fun deleteSyncedStacksNotInList(stackIds: List<String>, syncedState: String, cutoff: Long)

    @Query("DELETE FROM love_card_items WHERE stackId NOT IN (SELECT stackId FROM love_card_stacks) AND stackId != 'pending'")
    suspend fun deleteOrphanedItems()

    @Query("DELETE FROM love_card_responses WHERE stackId NOT IN (SELECT stackId FROM love_card_stacks)")
    suspend fun deleteOrphanedResponses()
}

