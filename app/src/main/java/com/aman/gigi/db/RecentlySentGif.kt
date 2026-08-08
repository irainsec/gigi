package com.aman.gigi.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recently_sent_gifs", indices = [Index(value = ["url"], unique = true)])
data class RecentlySentGif(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface RecentlySentGifDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gif: RecentlySentGif)

    @Query("SELECT url FROM recently_sent_gifs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentGifs(limit: Int = 20): Flow<List<String>>

    @Query("DELETE FROM recently_sent_gifs WHERE id NOT IN (SELECT id FROM recently_sent_gifs ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteOldGifs(limit: Int = 20)
}
