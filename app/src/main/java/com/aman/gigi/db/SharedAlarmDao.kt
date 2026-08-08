package com.aman.gigi.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aman.gigi.model.SharedAlarmMirror
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedAlarmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: SharedAlarmMirror)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(alarms: List<SharedAlarmMirror>)

    @Query("SELECT * FROM shared_alarm_mirrors WHERE isActive = 1 ORDER BY dueAt ASC")
    fun observeActiveAlarms(): Flow<List<SharedAlarmMirror>>

    @Query("SELECT * FROM shared_alarm_mirrors WHERE isActive = 1 AND connectionId = :connectionId ORDER BY dueAt ASC")
    fun observeActiveAlarmsForConnection(connectionId: String): Flow<List<SharedAlarmMirror>>

    @Query("SELECT * FROM shared_alarm_mirrors WHERE isActive = 1")
    suspend fun getAllActiveAlarmsOnce(): List<SharedAlarmMirror>

    @Query("SELECT * FROM shared_alarm_mirrors")
    suspend fun getAllAlarmsOnce(): List<SharedAlarmMirror>

    @Query("SELECT * FROM shared_alarm_mirrors WHERE alarmId = :alarmId LIMIT 1")
    suspend fun getAlarmById(alarmId: String): SharedAlarmMirror?

    @Query("DELETE FROM shared_alarm_mirrors WHERE alarmId = :alarmId")
    suspend fun deleteById(alarmId: String)

    @Query("DELETE FROM shared_alarm_mirrors WHERE connectionId = :connectionId")
    suspend fun deleteByConnection(connectionId: String)

    @Query("DELETE FROM shared_alarm_mirrors")
    suspend fun deleteAll()
}
