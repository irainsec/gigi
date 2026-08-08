package com.aman.gigi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.aman.gigi.model.BreakCardSessionMirror

@Dao
interface BreakCardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: BreakCardSessionMirror)
}
