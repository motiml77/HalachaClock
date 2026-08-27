package com.zmanimclock.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zmanimclock.app.data.local.ZmanimDatabase
import com.zmanimclock.app.feature.alarms.data.AlarmDao
import com.zmanimclock.app.feature.chaitables.data.local.ChaiTablesDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZmanimDatabase {
        // Direct Boot (A3): store on device-protected storage so alarms +
        // visible-sunrise cache are reachable after a reboot BEFORE the user
        // unlocks the device for the first time. Migrate any old CE db once.
        val dpsContext = context.createDeviceProtectedStorageContext()
        // ONE-SHOT move only. Running it on every process start could clobber
        // the live device-protected database with a stale credential-encrypted
        // copy (and it can execute pre-unlock, via the directBootAware
        // BootReceiver, when CE storage is not even readable).
        runCatching {
            if (!dpsContext.getDatabasePath("zmanim.db").exists()) {
                dpsContext.moveDatabaseFrom(context, "zmanim.db")
            }
        }
        return Room.databaseBuilder(dpsContext, ZmanimDatabase::class.java, "zmanim.db")
            // v4→v5: ring duration moved from whole minutes to seconds — keep
            // the user's existing alarms by converting minutes×60 in place.
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            // Destructive fallback is limited to the PRE-RELEASE versions.
            // It must never apply to v4+, or a future schema bump would
            // silently wipe every alarm the user created.
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            .build()
    }

    /**
     * v5→v6: the visible-sunrise cache moved from a raw Gregorian
     * day-of-year key to a leap-safe (month*100+day) key. The old rows are
     * unreadable under the new scheme, so drop them — the bundled asset
     * re-seeds immediately and any custom city refetches on next use.
     * ALARMS ARE UNTOUCHED; only the derived sunrise cache is cleared.
     */
    /**
     * v6→v7: rows now carry the real Gregorian date they were recorded for,
     * so the DST re-basing no longer has to guess a year (a Hebrew year spans
     * two Gregorian years, so the guess was wrong for about half the table).
     * Existing rows get 0 = "unknown" and keep the old heuristic.
     */
    /**
     * v7→v8: per-alarm choice between a constant ring volume and a gentle
     * climb. DEFAULT 0 = constant, deliberately: the ramp used to be
     * unconditional, and existing alarms should pick up the corrected
     * behaviour rather than silently keep a fade the user never chose.
     * Anyone who actually wants it can switch it back on per alarm.
     */
    /**
     * v8→v9: the שומר לערבית flag. DEFAULT 0, so every alarm that already
     * exists keeps the old behaviour of being switched off rather than
     * removed — only the one-tap evening reminder opts into vanishing.
     */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE alarms ADD COLUMN deleteAfterFiring INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE alarms ADD COLUMN gradualVolume INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE chai_tables_cache ADD COLUMN sourceEpochDay INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM chai_tables_cache")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE alarms ADD COLUMN ringDurationSeconds INTEGER NOT NULL DEFAULT 60"
            )
            // Clamp to the new 10..180s range; a 5-min default becomes 180 (3 min cap)
            db.execSQL(
                "UPDATE alarms SET ringDurationSeconds = " +
                    "MIN(180, MAX(10, ringDurationMinutes * 60))"
            )
        }
    }

    @Provides
    fun provideChaiTablesDao(database: ZmanimDatabase): ChaiTablesDao =
        database.chaiTablesDao()

    @Provides
    fun provideAlarmDao(database: ZmanimDatabase): AlarmDao =
        database.alarmDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
}
