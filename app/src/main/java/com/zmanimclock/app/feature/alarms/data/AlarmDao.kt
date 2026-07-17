package com.zmanimclock.app.feature.alarms.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY type ASC, hour ASC, minute ASC, zmanId ASC")
    fun getAllAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isActive = 1")
    fun getActiveAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE isActive = 1")
    suspend fun getActiveAlarmsList(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmEntity)

    @Query("UPDATE alarms SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("UPDATE alarms SET snoozeCount = :count WHERE id = :id")
    suspend fun setSnoozeCount(id: Long, count: Int)

    @Query("UPDATE alarms SET skipUntilEpochMs = :epochMs WHERE id = :id")
    suspend fun setSkipUntil(id: Long, epochMs: Long)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)
}
