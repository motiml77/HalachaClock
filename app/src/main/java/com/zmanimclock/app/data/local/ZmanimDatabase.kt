package com.zmanimclock.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity

/**
 * Single app database (pre-release: schema may change freely until first
 * publish; the builder uses destructive fallback during development).
 */
@Database(
    entities = [ChaiTablesEntity::class, AlarmEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class ZmanimDatabase : RoomDatabase() {
    abstract fun chaiTablesDao(): ChaiTablesDao
    abstract fun alarmDao(): AlarmDao
}
