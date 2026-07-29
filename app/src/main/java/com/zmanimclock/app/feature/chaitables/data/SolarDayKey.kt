package com.zmanimclock.app.feature.chaitables.data

import java.time.LocalDate

/**
 * Cache key for the visible-sunrise almanac.
 *
 * The ChaiTables data is SOLAR: 1 April has the same visible sunrise every
 * year. Keying it by [LocalDate.dayOfYear] silently breaks that, because a
 * leap day shifts every subsequent day by one — 1 April is day 92 in a leap
 * year and day 91 otherwise. A table fetched in a leap year would then be
 * read one day off for the rest of the following three years (and vice
 * versa), moving הנץ הנראה by up to ~1 minute at the equinoxes.
 *
 * The key is therefore (month, dayOfMonth) packed into an Int, which is
 * stable across every year. 29 February gets its own slot; when it has no
 * data of its own the repository falls back to 28 February, the nearest
 * solar neighbour.
 *
 * Values stay inside 1..1231 so they never collide with the legacy 1..366
 * day-of-year keys or with [ChaiTablesRepository.SENTINEL_DAY] (0).
 */
object SolarDayKey {

    /** month * 100 + dayOfMonth, e.g. 1 April → 401. */
    fun of(date: LocalDate): Int = date.monthValue * 100 + date.dayOfMonth

    /** The key to try when [of] has no row — 29 Feb falls back to 28 Feb. */
    fun fallbackFor(date: LocalDate): Int? =
        if (date.monthValue == 2 && date.dayOfMonth == 29) 228 else null

    /** True for keys written by this scheme (as opposed to legacy 1..366). */
    fun isSolarKey(key: Int): Boolean = key >= 101
}
