package com.aman.gigi.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row per track you've played, newest wins.
 *
 * `songId` is the primary key rather than an autoincrement, so replaying a track
 * updates its timestamp and moves it up the list instead of filling history with
 * duplicates of the song you had on repeat all afternoon.
 */
@Entity(tableName = "recent_plays")
data class RecentPlay(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val playedAt: Long
)

@Dao
interface RecentPlayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(play: RecentPlay)

    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<RecentPlay>>

    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int = 20): List<RecentPlay>

    /** Keeps the table from growing without bound. */
    @Query(
        "DELETE FROM recent_plays WHERE songId NOT IN " +
            "(SELECT songId FROM recent_plays ORDER BY playedAt DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int = 100)

    @Query("DELETE FROM recent_plays")
    suspend fun clear()
}
