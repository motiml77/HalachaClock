package com.zmanimclock.app.feature.chaitables.data.remote

import android.util.Log
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.chaitables.data.model.ChaiTablesParams
import com.zmanimclock.app.feature.chaitables.data.model.SunriseTimeEntry
import com.zmanimclock.app.feature.chaitables.data.model.VisibleSunriseData
import org.jsoup.Jsoup
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses HTML responses from ChaiTables.com into structured sunrise data.
 *
 * The HTML contains a table with:
 * - 14 columns for regular year (day + 12 months + extra)
 * - 15 columns for leap year (day + 13 months + extra)
 * - Columns ordered: Tishrei, Cheshvan, ..., Elul
 * - Times in HH:MM:SS format
 *
 * We convert each entry to a Gregorian day-of-year + time, so the data
 * is reusable across years (sunrise depends on solar/Gregorian date, not Hebrew).
 */
@Singleton
class ChaiTablesHtmlParser @Inject constructor() {

    companion object {
        private const val TAG = "ChaiTablesParser"
        private const val MIN_VALID_ENTRIES = 300
    }

    /**
     * Parse the HTML response and extract visible sunrise times.
     * Returns entries keyed by Gregorian day-of-year (1-366) with time components.
     */
    fun parse(html: String, params: ChaiTablesParams, locationKey: String): VisibleSunriseData? {
        try {
            val doc = Jsoup.parse(html)
            val tables = doc.select("table")

            // Find the zman table: first row has 13-16 cells
            val zmanTable = tables.firstOrNull { table ->
                val firstRow = table.select("tr").firstOrNull()
                val cellCount = firstRow?.select("td")?.size ?: 0
                cellCount in 13..16
            }

            if (zmanTable == null) {
                Log.w(TAG, "No valid zman table found in HTML response")
                return null
            }

            val rows = zmanTable.select("tr")
            if (rows.size < 2) {
                Log.w(TAG, "Zman table has too few rows: ${rows.size}")
                return null
            }

            val headerCells = rows[0].select("td")
            val totalColumns = headerCells.size
            val monthCount = totalColumns - 2
            val isLeapYear = JewishDate(params.hebrewYear, 7, 1).isJewishLeapYear

            val entries = mutableMapOf<Int, SunriseTimeEntry>() // dayOfYear -> entry

            // Parse data rows (skip header)
            for (rowIndex in 1 until rows.size) {
                val cells = rows[rowIndex].select("td")
                if (cells.isEmpty()) continue

                val dayText = cells[0].text().trim()
                val hebrewDay = dayText.toIntOrNull() ?: continue
                if (hebrewDay !in 1..30) continue

                for (colIndex in 1..minOf(monthCount, cells.size - 2)) {
                    val cell = cells.getOrNull(colIndex) ?: continue
                    val timeText = cell.text().trim()
                    if (timeText.isBlank() || timeText == "--:--:--") continue

                    // Convert column to KosherJava Hebrew month
                    val kjMonth = chaiTablesColumnToKJMonth(colIndex, isLeapYear)
                    if (kjMonth == -1) continue

                    // Validate day exists in this month
                    val maxDays = try {
                        JewishDate(params.hebrewYear, kjMonth, 1).getDaysInJewishMonth()
                    } catch (e: Exception) {
                        30
                    }
                    if (hebrewDay > maxDays) continue

                    // Parse time
                    val timeParts = timeText.split(":")
                    if (timeParts.size < 2) continue
                    val hour = timeParts[0].trim().toIntOrNull() ?: continue
                    val minute = timeParts[1].trim().toIntOrNull() ?: continue
                    val second = if (timeParts.size >= 3) timeParts[2].trim().toIntOrNull() ?: 0 else 0

                    // Convert Hebrew date to Gregorian day-of-year
                    try {
                        val jewishDate = JewishDate(params.hebrewYear, kjMonth, hebrewDay)
                        val gregCal = jewishDate.getGregorianCalendar()
                        // Solar key (month*100+day) — stable across leap years
                        val dayOfYear = com.zmanimclock.app.feature.chaitables.data.SolarDayKey.of(
                            java.time.LocalDate.of(
                                gregCal.get(Calendar.YEAR),
                                gregCal.get(Calendar.MONTH) + 1,
                                gregCal.get(Calendar.DAY_OF_MONTH),
                            )
                        )

                        entries[dayOfYear] = SunriseTimeEntry(
                            dayOfYear = dayOfYear,
                            hour = hour,
                            minute = minute,
                            second = second,
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid date: year=${params.hebrewYear} month=$kjMonth day=$hebrewDay", e)
                    }
                }
            }

            if (entries.isEmpty()) {
                Log.w(TAG, "No entries parsed from ChaiTables HTML")
                return null
            }

            if (entries.size < MIN_VALID_ENTRIES) {
                Log.w(TAG, "Only ${entries.size} entries parsed (expected >= $MIN_VALID_ENTRIES)")
            }

            Log.i(TAG, "Parsed ${entries.size} sunrise entries for location $locationKey")

            return VisibleSunriseData(
                locationKey = locationKey,
                entries = entries.values.toList(),
                fetchUrl = "",
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ChaiTables HTML", e)
            return null
        }
    }

    /**
     * Convert ChaiTables column index (1=Tishrei) to KosherJava month (1=Nissan, 7=Tishrei).
     */
    private fun chaiTablesColumnToKJMonth(column: Int, isLeapYear: Boolean): Int {
        if (isLeapYear) {
            return when (column) {
                1 -> 7; 2 -> 8; 3 -> 9; 4 -> 10; 5 -> 11
                6 -> 12; 7 -> 13; 8 -> 1; 9 -> 2; 10 -> 3
                11 -> 4; 12 -> 5; 13 -> 6; else -> -1
            }
        } else {
            return when (column) {
                1 -> 7; 2 -> 8; 3 -> 9; 4 -> 10; 5 -> 11
                6 -> 12; 7 -> 1; 8 -> 2; 9 -> 3; 10 -> 4
                11 -> 5; 12 -> 6; else -> -1
            }
        }
    }
}
