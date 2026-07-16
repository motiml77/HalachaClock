package com.zmanimclock.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity

/**
 * Single app database. Alert entities join in the alarm-engine phase
 * (pre-release: schema may change freely until first publish).
 */
@Database(
    entities = [ChaiTablesEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ZmanimDatabase : RoomDatabase() {
    abstract fun chaiTablesDao(): ChaiTablesDao
}
