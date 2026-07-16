package com.zmanimclock.app.feature.alerts.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY zmanId ASC, offsetMinutes ASC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isActive = 1")
    fun getActiveAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isActive = 1")
    suspend fun getActiveAlertsList(): List<AlertEntity>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Long): AlertEntity?

    @Query("SELECT * FROM alerts WHERE zmanId = :zmanId")
    fun getAlertsByZman(zmanId: String): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity): Long

    @Update
    suspend fun updateAlert(alert: AlertEntity)

    @Delete
    suspend fun deleteAlert(alert: AlertEntity)

    @Query("UPDATE alerts SET isActive = :isActive WHERE id = :id")
    suspend fun toggleAlert(id: Long, isActive: Boolean)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
