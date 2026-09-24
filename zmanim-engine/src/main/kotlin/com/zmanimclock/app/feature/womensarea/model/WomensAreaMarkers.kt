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
    /** True on the day a הפסק טהרה was made (before its shkia). */
    val isHefsekDay: Boolean = false,
    /** Which day (1..7) of שבעה נקיים this is, if any. */
    val cleanDayNumber: Int? = null,
    /**
     * True on the 7th clean day: the tevila is after ITS tzeit only (the
     * night that opens the next Hebrew day).
     */
    val isTevilaDay: Boolean = false,
)

/**
 * date -> marker, from the latest veset (and the one before it, for the
 * haflaga) and the latest הפסק טהרה. Only the latest entry of each kind
 * drives the calendar, so older cycles do not accumulate markers.
 */
object WomensAreaMarkers {

    fun build(
        latestVeset: LocalDate?,
        latestVesetOnah: Onah?,
        previousVeset: LocalDate?,
        latestHefsek: LocalDate?,
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
        latestHefsek?.let { hefsek ->
            edit(hefsek) { it.copy(isHefsekDay = true) }
            WomensAreaCalculator.cleanDayDates(hefsek).forEachIndexed { i, date ->
                edit(date) { it.copy(cleanDayNumber = i + 1) }
            }
            edit(WomensAreaCalculator.tevilaDay(hefsek)) { it.copy(isTevilaDay = true) }
        }
        return markers
    }
}

/**
 * True when ליל הטבילה after [hefsek] falls on a separation day whose onah is
 * the night (or unknown) — a clash to ask a rabbi about, not something the
 * app should resolve.
 */
fun VesetPrediction.clashesWithTevila(hefsek: LocalDate): Boolean {
    val tevila = WomensAreaCalculator.tevilaNight(hefsek)
    return prishaDays.any { it.date == tevila && it.onah != Onah.DAY }
}
