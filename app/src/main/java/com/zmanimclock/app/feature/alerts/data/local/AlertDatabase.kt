package com.zmanimclock.app.feature.alerts.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesEntity

@Database(
    entities = [AlertEntity::class, ChaiTablesEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AlertDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun chaiTablesDao(): ChaiTablesDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chai_tables_cache` (
                        `locationKey` TEXT NOT NULL,
                        `dayOfYear` INTEGER NOT NULL,
                        `sunriseHour` INTEGER NOT NULL,
                        `sunriseMinute` INTEGER NOT NULL,
                        `sunriseSecond` INTEGER NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`locationKey`, `dayOfYear`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chai_tables_cache_locationKey` " +
                        "ON `chai_tables_cache` (`locationKey`)"
                )
            }
        }
    }
}
