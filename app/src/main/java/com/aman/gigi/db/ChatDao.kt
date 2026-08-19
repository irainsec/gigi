package com.aman.gigi.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aman.gigi.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE connectionId = :connectionId AND (TRIM(text) != '' OR TRIM(gifUrl) != '') ORDER BY timestamp ASC")
    fun messagesFor(connectionId: String): Flow<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE TRIM(text) = '' AND TRIM(gifUrl) = ''")
    suspend fun deleteBlankMessages()

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
