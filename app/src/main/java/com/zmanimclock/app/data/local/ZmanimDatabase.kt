package com.zmanimclock.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity
import com.zmanimclock.app.feature.womensarea.data.WomensAreaDao
import com.zmanimclock.app.feature.womensarea.data.WomensAreaEntryEntity

/**
 * Single app database. Real testers have real data as of v15/v16 (Internal +
 * Closed Testing) — every schema change from here needs a real [androidx.room.migration.Migration],
 * not the destructive fallback (which is now scoped only to the pre-release
 * versions 1-3, see AppModule).
 */
@Database(
    entities = [ChaiTablesEntity::class, AlarmEntity::class, WomensAreaEntryEntity::class],
    version = 12,
    exportSchema = false,
)
abstract class ZmanimDatabase : RoomDatabase() {
    abstract fun chaiTablesDao(): ChaiTablesDao
    abstract fun alarmDao(): AlarmDao
    abstract fun womensAreaDao(): WomensAreaDao
}
