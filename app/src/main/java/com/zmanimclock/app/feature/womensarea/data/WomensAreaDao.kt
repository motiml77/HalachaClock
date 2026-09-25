package com.zmanimclock.app.feature.womensarea.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WomensAreaDao {

    @Query("SELECT * FROM womens_area_entries ORDER BY epochDay DESC")
    fun getAllEntries(): Flow<List<WomensAreaEntryEntity>>

    @Query("SELECT * FROM womens_area_entries WHERE type = :type ORDER BY epochDay DESC LIMIT 1")
    fun getLatestByType(type: WomensAreaEntryType): Flow<WomensAreaEntryEntity?>

    /**
     * The chronologically previous entry of [type] before [beforeEpochDay] —
     * NOT "the previously inserted row". Entries can be added out of date
     * order (a backfilled or corrected date), and haflaga must always compare
     * against the calendar-previous cycle, never the insert-order-previous one.
     */
    @Query(
        "SELECT * FROM womens_area_entries WHERE type = :type AND epochDay < :beforeEpochDay " +
            "ORDER BY epochDay DESC LIMIT 1"
    )
    suspend fun getPreviousByType(type: WomensAreaEntryType, beforeEpochDay: Long): WomensAreaEntryEntity?

    @Query("SELECT * FROM womens_area_entries WHERE id = :id")
    suspend fun getById(id: Long): WomensAreaEntryEntity?

    @Insert
    suspend fun insert(entry: WomensAreaEntryEntity): Long

    @Update
    suspend fun update(entry: WomensAreaEntryEntity)

    @Delete
    suspend fun delete(entry: WomensAreaEntryEntity)

    /** For keeping only the last few vesets — see WomensAreaHistory.idsToPrune. */
    @Query("DELETE FROM womens_area_entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
