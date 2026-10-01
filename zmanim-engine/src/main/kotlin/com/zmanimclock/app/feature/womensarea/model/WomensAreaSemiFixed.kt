package com.zmanimclock.app.feature.womensarea.model

import java.time.LocalDate

/**
 * וסת חצי קבוע, as she set it by her rabbi's ruling: she never sees before
 * day [minDay] of the count from the previous veset — that veset's day is 1,
 * counted exactly as a haflaga is.
 *
 * THE METHOD THE APP FOLLOWS (and says so where it is set)
 * Seeing never before day N is itself a kind of veset — a veset NOT to see
 * in those days — so the separation days that fall before day N of the count
 * are not shown: a short haflaga, a יום החודש, the days carried over from
 * earlier vesets, and the עונה בינונית too when N is past day 30. Every day
 * from N on is shown as usual. Not every posek holds this, which is why it is
 * set only by her own choice, after asking a rabbi.
 *
 * A sighting before day N contradicts it. That cycle then shows EVERY
 * separation day, with a warning to ask a rabbi; [CANCEL_AFTER] such
 * sightings in a row cancel the setting altogether.
 */
data class SemiFixedVeset(
    val minDay: Int,
    /**
     * When it was set. Only vesets on or after this date can contradict it —
     * the ones already recorded before it were what she and her rabbi looked at.
     */
    val since: LocalDate,
)

/** Where the latest veset stands against a [SemiFixedVeset]. */
data class SemiFixedStatus(
    /** True when the latest veset came before day minDay — this cycle shows every day. */
    val latestContradicts: Boolean,
    /** The latest veset's haflaga, when there is a previous veset to count from. */
    val latestInterval: Int?,
    /** How many vesets in a row, back from the latest, came before day minDay. */
    val consecutiveContradictions: Int,
) {
    /** [WomensAreaSemiFixed.CANCEL_AFTER] in a row: the setting is cancelled. */
    val shouldCancel: Boolean get() = consecutiveContradictions >= WomensAreaSemiFixed.CANCEL_AFTER
}

object WomensAreaSemiFixed {

    /** Contradicting sightings in a row that cancel the setting. */
    const val CANCEL_AFTER = 3

    /** The range she can set N in. */
    const val MIN_DAY = 10
    const val MAX_DAY = 60

    /**
     * True when [veset] came before day [semiFixed].minDay of the count from
     * [previous] — and only for a veset on or after the setting's date.
     */
    fun contradicts(veset: LocalDate, previous: LocalDate?, semiFixed: SemiFixedVeset): Boolean {
        if (veset < semiFixed.since) return false
        val interval = WomensAreaCalculator.haflagaInterval(veset, previous) ?: return false
        return interval < semiFixed.minDay
    }

    /** The latest veset's standing, from every recorded veset (any order). */
    fun status(vesets: List<LocalDate>, semiFixed: SemiFixedVeset): SemiFixedStatus {
        val newestFirst = vesets.distinct().sortedDescending()
        var run = 0
        while (run < newestFirst.size &&
            contradicts(newestFirst[run], newestFirst.getOrNull(run + 1), semiFixed)
        ) run++
        val latest = newestFirst.firstOrNull()
        return SemiFixedStatus(
            latestContradicts = run > 0,
            latestInterval = latest?.let { WomensAreaCalculator.haflagaInterval(it, newestFirst.getOrNull(1)) },
            consecutiveContradictions = run,
        )
    }

    /**
     * The day a prediction leaves out before — [semiFixed]'s N, unless the
     * veset it is computed from contradicted it (then null: every day shown).
     */
    fun minDayFor(semiFixed: SemiFixedVeset?, contradicted: Boolean): Int? =
        semiFixed?.minDay?.takeUnless { contradicted }
}
