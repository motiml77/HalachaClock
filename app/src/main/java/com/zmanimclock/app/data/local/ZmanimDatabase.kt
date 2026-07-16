package com.zmanimclock.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zmanimclock.app.feature.alerts.data.local.AlertDao
import com.zmanimclock.app.feature.alerts.data.local.AlertEntity
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity

/**
 * Single app database (pre-release: schema may change freely until first
 * publish; migrations start once the app ships).
 */
@Database(
    entities = [ChaiTablesEntity::class, AlertEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ZmanimDatabase : RoomDatabase() {
    abstract fun chaiTablesDao(): ChaiTablesDao
    abstract fun alertDao(): AlertDao
}
