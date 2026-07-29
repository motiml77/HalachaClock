package com.zmanimclock.app.feature.chaitables.data.model

/**
 * Parameters for constructing a ChaiTables CGI request.
 */
data class ChaiTablesParams(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val timeZoneId: String,
    val hebrewYear: Int,
    val isIsrael: Boolean,
    val metroAreaName: String?,  // e.g. "jerusalem", "tel_aviv" - null for coordinate-based
    val searchRadius: String,    // "2" for Israel, "0"-"15" for worldwide
    val country: String,         // "Eretz_Yisroel", "USA", etc.
)

/**
 * A single parsed sunrise entry: Gregorian day-of-year + local time.
 * Time-of-day is the same every year for a given solar date and location.
 */
data class SunriseTimeEntry(
    val dayOfYear: Int,     // solar key (month*100+day)
    /** the real Gregorian date this row came from, as an epoch day. */
    val sourceEpochDay: Long = 0L,
    val hour: Int,          // 0-23
    val minute: Int,        // 0-59
    val second: Int,        // 0-59
)

/**
 * Parsed result from ChaiTables containing all visible sunrise times.
 * Keyed by Gregorian day-of-year so it's reusable across years.
 */
data class VisibleSunriseData(
    val locationKey: String,
    val entries: List<SunriseTimeEntry>,
    val fetchUrl: String,
)
