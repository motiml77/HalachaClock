package com.zmanimclock.app.feature.calendar.model

/**
 * One month, laid out ready to draw.
 *
 * [weeks] is ALWAYS 6 rows of 7, even when the month only needs 5. A Hebrew
 * month is 29 or 30 days and can start on any weekday, so
 * `firstColumnOffset + daysInMonth` is always between 29 and 36 — never fewer
 * than 5 rows, never more than 6. Reserving the sixth keeps the grid a
 * constant height, so it does not jump under the user's finger mid-swipe.
 *
 * Column 0 is ראשון and column 6 is שבת. The app forces RTL, so declaring
 * cells in that order puts ראשון on the right by itself — this must not be
 * "corrected".
 */
data class MonthGrid(
    val ref: HebrewMonthRef,

    /** "אב" / "אדר א׳" — always from HebrewDateFormatter, never hand-written. */
    val hebrewMonthLabel: String,
    /** "תשפ״ו" */
    val hebrewYearLabel: String,
    /**
     * "יולי–אוגוסט 2026". Derived from the Hebrew month's own first and last
     * day, because a Hebrew month can straddle two Gregorian months AND two
     * Gregorian years (Tevet 5787 runs Dec 2026 into Jan 2027).
     */
    val gregorianSpanLabel: String,

    /** 0 = the 1st falls on ראשון … 6 = on שבת. */
    val firstColumnOffset: Int,
    /** 29 or 30. */
    val daysInMonth: Int,

    /** 6 × 7. Never null inside — filler days carry isInDisplayedMonth=false. */
    val weeks: List<List<CalendarDayMeta>>,

    /** The month's notable days, in date order, for the ribbon. */
    val events: List<MonthEvent>,
) {
    /** Every cell, flattened — convenient for tests and for lookups. */
    val allDays: List<CalendarDayMeta> get() = weeks.flatten()

    /** Only this month's own days, in order. */
    val daysInThisMonth: List<CalendarDayMeta>
        get() = allDays.filter { it.isInDisplayedMonth }

    /** Which of the 6 rows holds [meta], for collapsing to a single week. */
    fun weekIndexOf(meta: CalendarDayMeta): Int =
        weeks.indexOfFirst { row -> row.any { it.date == meta.date } }
}
