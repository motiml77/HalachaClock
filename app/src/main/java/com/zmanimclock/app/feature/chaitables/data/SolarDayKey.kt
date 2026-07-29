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

    /**
     * Decode a key back to a date in [year] — needed by consumers that must
     * know WHEN a cached row's wall-clock time was recorded (the DST re-basing
     * in ChaiTablesRepository).
     *
     * SOLAR KEYS ONLY. The legacy 1..366 day-of-year space overlaps this one
     * (101..366 is ambiguous), so it cannot be decoded unambiguously — and it
     * does not need to be: the v5→v6 migration deletes every legacy row, and
     * the preloader rewrites the bundled asset into this scheme. Anything that
     * is not a valid solar key (markers, the sentinel, stale rows) returns
     * null, and callers fall back to leaving the stored time untouched.
     */
    fun toDate(key: Int, year: Int): LocalDate? {
        if (!isSolarKey(key)) return null
        val month = key / 100
        val day = key % 100
        if (month !in 1..12 || day !in 1..31) return null
        // 29 Feb has no date in a plain year — use 28 Feb, its solar twin
        if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) {
            return LocalDate.of(year, 2, 28)
        }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
}
