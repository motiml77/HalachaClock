package com.zmanimclock.app.feature.womensarea.model

import java.time.LocalDate

/**
 * וסת חצי קבוע, as she set it by her rabbi's ruling: she never sees before
 * day [minDay] of the count from the previous veset — that veset's day is 1,
 * counted exactly as a haflaga is. By HEBREW days (each date is one Hebrew
 * day, its night the evening before), and regardless of onah: a sighting by
 * day and one by night count alike — only the day number matters.
 *
 * THE METHOD THE APP FOLLOWS (and says so where it is set)
 * Seeing never before day N is itself a kind of veset — a veset NOT to see
 * in those days — so while it stands, the separation days before day N of
 * the count are not shown: a short haflaga, a יום החודש, the days carried
 * over from earlier vesets, and the עונה בינונית too when N is past day 30.
 *
 * - One sighting before day N: that cycle shows every separation day, with a
 *   warning. The next sighting on time (day N or later) brings it straight back.
 * - [WomensAreaSemiFixed.IN_A_ROW] sightings in a row before day N uproot it:
 *   every separation day is shown from then on, until [WomensAreaSemiFixed.IN_A_ROW]
 *   sightings in a row on time establish it again.
 */
data class SemiFixedVeset(
    val minDay: Int,
    /**
     * Only vesets on or after this date count — the ones recorded before were
     * what she and her rabbi looked at. Moved past the veset that establishes
     * it again, so the count of contradictions starts afresh from there.
     */
    val since: LocalDate,
    /** The veset that completed the run that uprooted it, while it stays uprooted; null while it stands. */
    val uprootedAt: LocalDate? = null,
)

enum class SemiFixedMode { STANDS, UPROOTED }

/** A change of mode the recorded vesets lead to — for the app to save, and to tell her once. */
sealed class SemiFixedTransition {
    abstract val at: LocalDate
    data class Uprooted(override val at: LocalDate) : SemiFixedTransition()
    data class Reestablished(override val at: LocalDate) : SemiFixedTransition()
}

/** Where the vesets leave a [SemiFixedVeset]. */
data class SemiFixedStatus(
    val mode: SemiFixedMode,
    /** True when it stands but the latest veset came before day minDay — this cycle shows every day. */
    val latestContradicts: Boolean,
    /** The latest veset's haflaga, when there is a previous veset to count from. */
    val latestInterval: Int?,
    /**
     * The run at the end that counts toward the next change: while it stands,
     * sightings in a row before day minDay; while uprooted, sightings in a
     * row on time.
     */
    val run: Int,
    /** The latest change of mode found beyond what was saved, if any. */
    val transition: SemiFixedTransition? = null,
)

object WomensAreaSemiFixed {

    /** Sightings in a row that uproot it — and, on time, that establish it again. */
    const val IN_A_ROW = 3

    /** The range she can set N in. */
    const val MIN_DAY = 10
    const val MAX_DAY = 60

    /** True when [veset] came before day [minDay] of the count from [previous]. Null with nothing to count from. */
    fun isBeforeMinDay(veset: LocalDate, previous: LocalDate?, minDay: Int): Boolean? =
        WomensAreaCalculator.haflagaInterval(veset, previous)?.let { it < minDay }

    /**
     * True when [veset] came before day [semiFixed].minDay of the count from
     * [previous] — and only for a veset on or after the setting's date.
     */
    fun contradicts(veset: LocalDate, previous: LocalDate?, semiFixed: SemiFixedVeset): Boolean =
        veset >= semiFixed.since && isBeforeMinDay(veset, previous, semiFixed.minDay) == true

    /**
     * Runs the recorded vesets (any order) through the rule, from where
     * [semiFixed] was last saved: from [SemiFixedVeset.uprootedAt] while
     * uprooted, otherwise from [SemiFixedVeset.since].
     */
    fun status(vesets: List<LocalDate>, semiFixed: SemiFixedVeset): SemiFixedStatus {
        val all = vesets.distinct().sorted()
        var mode = if (semiFixed.uprootedAt != null) SemiFixedMode.UPROOTED else SemiFixedMode.STANDS
        var run = 0
        var transition: SemiFixedTransition? = null
        var latestCounted = false
        all.forEachIndexed { i, date ->
            val counts = semiFixed.uprootedAt?.let { date > it } ?: (date >= semiFixed.since)
            val early = if (counts) isBeforeMinDay(date, all.getOrNull(i - 1), semiFixed.minDay) else null
            latestCounted = early != null
            if (early == null) return@forEachIndexed
            when (mode) {
                SemiFixedMode.STANDS -> {
                    run = if (early) run + 1 else 0
                    if (run == IN_A_ROW) {
                        mode = SemiFixedMode.UPROOTED
                        run = 0
                        transition = SemiFixedTransition.Uprooted(date)
                    }
                }
                SemiFixedMode.UPROOTED -> {
                    run = if (!early) run + 1 else 0
                    if (run == IN_A_ROW) {
                        mode = SemiFixedMode.STANDS
                        run = 0
                        transition = SemiFixedTransition.Reestablished(date)
                    }
                }
            }
        }
        val latest = all.lastOrNull()
        return SemiFixedStatus(
            mode = mode,
            latestContradicts = mode == SemiFixedMode.STANDS && run > 0 && latestCounted,
            latestInterval = latest?.let { WomensAreaCalculator.haflagaInterval(it, all.getOrNull(all.size - 2)) },
            run = run,
            transition = transition,
        )
    }

    /** What to save after [transition]: uprooted from its veset, or standing again with the count starting afresh after it. */
    fun after(semiFixed: SemiFixedVeset, transition: SemiFixedTransition): SemiFixedVeset = when (transition) {
        is SemiFixedTransition.Uprooted -> semiFixed.copy(uprootedAt = transition.at)
        is SemiFixedTransition.Reestablished -> semiFixed.copy(uprootedAt = null, since = transition.at.plusDays(1))
    }

    /** The day the current cycle leaves out before — N while it stands and the latest veset kept to it; else null. */
    fun minDayFor(semiFixed: SemiFixedVeset?, status: SemiFixedStatus?): Int? {
        if (semiFixed == null || status == null) return null
        return semiFixed.minDay.takeIf { status.mode == SemiFixedMode.STANDS && !status.latestContradicts }
    }
}
