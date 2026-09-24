package com.zmanimclock.app.feature.womensarea.model

import java.time.LocalDate

/** Everything the Women's Area draws on one Hebrew day of its calendar. */
data class WomensAreaMarker(
    /** This day's number in the count from the latest veset (the veset day = 1), if within it. */
    val countDayNumber: Int? = null,
    /** True on the veset day itself. */
    val isVesetDay: Boolean = false,
    /** The onah reported for the veset — carried on the veset day only. */
    val vesetOnah: Onah? = null,
    /** The separation days that fall here — more than one when two kinds coincide. */
    val prisha: List<PrishaDay> = emptyList(),
    /** Which day (1..7) of the clean count this is, if any. */
    val cleanDayNumber: Int? = null,
)

/**
 * date -> marker, from the latest veset (and the one before it, for the
 * haflaga) and the latest first-clean-day. Only the latest entry of each kind
 * drives the calendar, so older cycles do not accumulate markers.
 */
object WomensAreaMarkers {

    fun build(
        latestVeset: LocalDate?,
        latestVesetOnah: Onah?,
        previousVeset: LocalDate?,
        latestFirstCleanDay: LocalDate?,
    ): Map<LocalDate, WomensAreaMarker> {
        val markers = mutableMapOf<LocalDate, WomensAreaMarker>()
        fun edit(date: LocalDate, change: (WomensAreaMarker) -> WomensAreaMarker) {
            markers[date] = change(markers[date] ?: WomensAreaMarker())
        }

        latestVeset?.let { start ->
            val prediction = WomensAreaCalculator.predict(start, latestVesetOnah, previousVeset)
            for (n in 1..prediction.lastCountedDay) {
                edit(start.plusDays((n - 1).toLong())) { it.copy(countDayNumber = n) }
            }
            edit(start) { it.copy(isVesetDay = true, vesetOnah = latestVesetOnah) }
            prediction.prishaDays.forEach { day -> edit(day.date) { it.copy(prisha = it.prisha + day) } }
        }
        latestFirstCleanDay?.let { firstCleanDay ->
            WomensAreaCalculator.cleanDayDates(firstCleanDay).forEachIndexed { i, date ->
                edit(date) { it.copy(cleanDayNumber = i + 1) }
            }
        }
        return markers
    }
}
