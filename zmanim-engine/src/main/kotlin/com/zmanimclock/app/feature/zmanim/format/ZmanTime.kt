package com.zmanimclock.app.feature.zmanim.format

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The ONE way a zman is turned into a clock string. Every surface — the
 * zmanim screen, the widget, the status notification, the alarm screens, and
 * any future desktop build — goes through here.
 *
 * WHY THIS EXISTS AT ALL
 * The engine hands back [Instant]s to the second. What the user actually
 * compares between their phone and their computer is the rendered string, and
 * two correct implementations of "show the time" disagree easily:
 *
 *     shkia = 19:32:31
 *     truncate -> "19:32"
 *     round    -> "19:33"
 *
 * Same instant, same engine, all luach tests green — and a one-minute
 * discrepancy between two devices that looks exactly like a calculation bug.
 * Before this file, `DateTimeFormatter.ofPattern("HH:mm")` was written out
 * separately in six places, so nothing stopped a seventh from differing.
 *
 * THE RULE: TRUNCATE, NEVER ROUND.
 * This is what the app has always done (`ofPattern("HH:mm")` discards the
 * seconds field; it does not round it), and it is preserved deliberately
 * rather than re-litigated: switching to rounding would silently shift up to
 * half the displayed zmanim by a minute for every existing user. Changing it
 * is a halachic decision, not a formatting cleanup, and would need its own
 * ruling.
 *
 * ZmanTimeFormatTest pins the behaviour, and it lives in this module, so a
 * drift on any consumer breaks that consumer's build.
 */
object ZmanTime {

    /** 24-hour, zero-padded. The app's only clock pattern. */
    const val PATTERN_24H: String = "HH:mm"

    /**
     * The one formatter instance. [DateTimeFormatter] is immutable and
     * thread-safe, so sharing it is correct and avoids re-parsing the pattern
     * on every widget redraw.
     */
    val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(PATTERN_24H)
}

/**
 * This instant as "HH:mm" in [zone] — seconds truncated, never rounded.
 * See [ZmanTime] for why that matters.
 */
fun Instant.asZmanTime(zone: ZoneId): String =
    ZmanTime.FORMATTER.format(this.atZone(zone))

/**
 * [asZmanTime] for the nullable times the engine returns: a zman can be
 * undefined at extreme latitudes, and callers should propagate that rather
 * than invent a string.
 */
fun Instant?.asZmanTimeOrNull(zone: ZoneId): String? = this?.asZmanTime(zone)
