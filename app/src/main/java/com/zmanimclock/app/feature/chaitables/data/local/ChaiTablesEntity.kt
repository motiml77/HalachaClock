package com.zmanimclock.app.feature.chaitables.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity for cached visible sunrise data from ChaiTables.
 *
 * KEY INSIGHT: Visible sunrise depends on the sun's position relative to terrain,
 * which is determined by the GREGORIAN/solar date (earth's orbital position).
 * April 9 has the same visible sunrise every year (±seconds).
 *
 * Therefore we cache by GREGORIAN day-of-year (1-366) and store only the
 * local time (hours, minutes, seconds). This means:
 * - One fetch per location, valid FOREVER
 * - No need to re-fetch when the Hebrew year changes
 * - To use: convert Hebrew date → Gregorian → day of year → lookup time → combine with actual date
 *
 * locationKey: city ID (e.g. "jerusalem") or "gps_31.778_35.235" for GPS
 * dayOfYear: Gregorian day of year (1-366)
 * sunriseHour/Minute/Second: local time components (in location's timezone)
 * sunriseEpochSeconds: -1 means "no data available" sentinel
 */
@Entity(
    tableName = "chai_tables_cache",
    primaryKeys = ["locationKey", "dayOfYear"],
    indices = [Index(value = ["locationKey"])],
)
data class ChaiTablesEntity(
    val locationKey: String,
    val dayOfYear: Int,            // 1-366 (Gregorian)
    val sunriseHour: Int,          // 0-23, local time
    val sunriseMinute: Int,        // 0-59
    val sunriseSecond: Int,        // 0-59
    val fetchedAt: Long,           // when this was cached (epoch millis)
)
