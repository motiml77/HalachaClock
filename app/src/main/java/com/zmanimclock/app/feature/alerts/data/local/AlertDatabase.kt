package com.zmanimclock.app.feature.alerts.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AlertEntity::class], version = 1, exportSchema = true)
abstract class AlertDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
}
