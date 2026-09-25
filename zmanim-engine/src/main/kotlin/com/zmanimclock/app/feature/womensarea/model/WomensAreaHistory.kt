package com.zmanimclock.app.feature.womensarea.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.HebrewMonthRef
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.toGregorianCalendar
import java.time.LocalDate

/** One recorded veset as the history needs it. [id] is the entry's row id, carried through for edit/delete. */
data class VesetRecord(val id: Long, val date: LocalDate, val onah: Onah?)

/** One recorded הפסק טהרה. */
data class HefsekRecord(val id: Long, val date: LocalDate)

/**
 * One cycle of the history: a veset, the haflaga that led to it, what was
 * computed from it, and the hefsek (if any) recorded before the next veset.
 */
data class Cycle(
    val veset: VesetRecord,
    /** From the previous veset (that one = 1) to this one — null for the oldest kept. */
    val haflagaInterval: Int?,
    /** 1..30 — the Hebrew day-of-month, for comparing יום החודש across cycles. */
    val hebrewDayOfMonth: Int,
    val prediction: VesetPrediction,
    val hefsek: HefsekRecord?,
)

/**
 * Something the history shows that repeats — always among vesets of one
 * [onah]. Never a ruling: a thing to bring to a rabbi.
 */
sealed class HistoryPattern {
    abstract val onah: Onah

    /** The last [count] haflagot (newest back) are all [days] long. */
    data class SameHaflaga(val days: Int, val count: Int, override val onah: Onah) : HistoryPattern()

    /** The last [count] vesets fell on the same Hebrew day-of-month, in consecutive months. */
    data class SameDayOfMonth(val dayOfMonth: Int, val count: Int, override val onah: Onah) : HistoryPattern()

    /** The last [count] haflagot change by the same [step] each time (a dilug), e.g. 28, 29, 30. */
    data class SteadyHaflagaStep(val step: Int, val count: Int, override val onah: Onah) : HistoryPattern()
}

/**
 * The Women's Area history: the last [KEEP] vesets and what was computed on
 * each, laid out so a pattern can be SEEN — never decided. Pure: the app
 * feeds it rows and draws what comes back.
 */
object WomensAreaHistory {

    /** How many vesets are kept; a new one beyond this replaces the oldest. */
    const val KEEP = 6

    /** Patterns are reported from this many repeats; 3 is when poskim speak of a וסת קבוע. */
    const val MIN_REPEAT = 3

    /** The cycles, newest first. */
    fun cycles(vesets: List<VesetRecord>, hefseks: List<HefsekRecord>): List<Cycle> {
        val sorted = vesets.sortedBy { it.date }
        return sorted.mapIndexed { i, veset ->
            val previous = sorted.getOrNull(i - 1)?.date
            val next = sorted.getOrNull(i + 1)?.date
            Cycle(
                veset = veset,
                haflagaInterval = WomensAreaCalculator.haflagaInterval(veset.date, previous),
                hebrewDayOfMonth = JewishDate(veset.date.toGregorianCalendar()).jewishDayOfMonth,
                prediction = WomensAreaCalculator.predict(veset.date, veset.onah, previous),
                // The hefsek of this cycle: the latest one on or after this veset and before the next.
                hefsek = hefseks
                    .filter { it.date >= veset.date && (next == null || it.date < next) }
                    .maxByOrNull { it.date },
            )
        }.reversed()
    }

    /**
     * The entry ids to delete so only the last [KEEP] vesets remain, together
     * with every hefsek older than the oldest veset kept (it belongs to a
     * cycle no longer shown).
     */
    fun idsToPrune(vesets: List<VesetRecord>, hefseks: List<HefsekRecord>): Set<Long> {
        val sorted = vesets.sortedByDescending { it.date }
        if (sorted.size <= KEEP) return emptySet()
        val oldestKept = sorted[KEEP - 1].date
        return (sorted.drop(KEEP).map { it.id } + hefseks.filter { it.date < oldestKept }.map { it.id }).toSet()
    }

    /**
     * What repeats in [cycles] (newest first), each counted back from the
     * newest — and ONLY among vesets that were all in the same onah (all
     * ביום or all בלילה): a pattern that mixes day and night is not one, and
     * is not reported at all. For a haflaga that means both of its ends.
     */
    fun patterns(cycles: List<Cycle>): List<HistoryPattern> {
        val onah = cycles.firstOrNull()?.veset?.onah ?: return emptyList()
        // The newest vesets that share the newest one's onah, unbroken.
        val sameOnah = cycles.takeWhile { it.veset.onah == onah }
        // Haflagot whose BOTH ends are in that run: cycle i's haflaga runs from veset i+1 to veset i.
        val haflagot = sameOnah.dropLast(1).map { it.haflagaInterval!! }
        val found = mutableListOf<HistoryPattern>()

        // Equal haflagot.
        if (haflagot.isNotEmpty()) {
            val run = haflagot.takeWhile { it == haflagot.first() }.size
            if (run >= MIN_REPEAT) found += HistoryPattern.SameHaflaga(haflagot.first(), run, onah)
        }

        // Same day of the month, in consecutive Hebrew months.
        var dayRun = 1
        while (dayRun < sameOnah.size && sameDayNextMonth(sameOnah[dayRun].veset.date, sameOnah[dayRun - 1].veset.date)) dayRun++
        if (dayRun >= MIN_REPEAT) found += HistoryPattern.SameDayOfMonth(sameOnah.first().hebrewDayOfMonth, dayRun, onah)

        // A steady, non-zero step between haflagot (dilug) — needs 3 haflagot.
        if (haflagot.size >= MIN_REPEAT) {
            val step = haflagot[0] - haflagot[1]
            if (step != 0) {
                var n = 2
                while (n < haflagot.size && haflagot[n - 1] - haflagot[n] == step) n++
                if (n >= MIN_REPEAT) found += HistoryPattern.SteadyHaflagaStep(step, n, onah)
            }
        }
        return found
    }

    /** True when [later] is the same Hebrew day-of-month as [earlier], in the very next Hebrew month. */
    private fun sameDayNextMonth(earlier: LocalDate, later: LocalDate): Boolean {
        val a = JewishDate(earlier.toGregorianCalendar())
        val b = JewishDate(later.toGregorianCalendar())
        if (a.jewishDayOfMonth != b.jewishDayOfMonth) return false
        val ia = HebrewMonthSequence.indexOf(HebrewMonthRef(a.jewishYear, a.jewishMonth))
        val ib = HebrewMonthSequence.indexOf(HebrewMonthRef(b.jewishYear, b.jewishMonth))
        return ia >= 0 && ib == ia + 1
    }
}
