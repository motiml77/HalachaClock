package com.zmanimclock.app.feature.chaitables.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChaiTablesDao {

    @Query(
        "SELECT * FROM chai_tables_cache " +
            "WHERE locationKey = :locationKey AND dayOfYear = :dayOfYear LIMIT 1"
    )
    suspend fun getSunrise(locationKey: String, dayOfYear: Int): ChaiTablesEntity?

    @Query(
        "SELECT COUNT(*) FROM chai_tables_cache " +
            "WHERE locationKey = :locationKey"
    )
    suspend fun getCountForLocation(locationKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ChaiTablesEntity>)

    @Query("DELETE FROM chai_tables_cache WHERE locationKey = :locationKey")
    suspend fun deleteForLocation(locationKey: String)
}
