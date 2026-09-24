package com.zmanimclock.app.feature.womensarea.model

import java.time.LocalTime
import java.util.Locale

/**
 * The times of day the שבעה נקיים reminders fire at, as one stored string —
 * "08:00,18:00". Sorted and de-duplicated on the way in AND out, so the order
 * she added them in never matters and a hand-edited or partly corrupt value
 * drops only its bad entries.
 */
object WomensAreaReminderTimes {

    /** Clean days: two a day — morning and afternoon — until she changes them. */
    val DEFAULT: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(18, 0))

    /** ערב טבילה: one, in the afternoon of the 7th day, in time to get ready. */
    val TEVILA_DEFAULT: List<LocalTime> = listOf(LocalTime.of(16, 0))

    /** How many she can add; each is 7 scheduled notifications. */
    const val MAX = 4

    fun encode(times: List<LocalTime>): String =
        normalize(times).joinToString(",", transform = ::format)

    fun decode(stored: String?, default: List<LocalTime> = DEFAULT): List<LocalTime> {
        if (stored == null) return default
        return normalize(
            stored.split(',').mapNotNull { part ->
                val (h, m) = part.trim().split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { LocalTime.of(h.toInt(), m.toInt()) }.getOrNull()
            },
        )
    }

    /** "08:00" — Locale.ROOT so the digits never follow the device's language. */
    fun format(time: LocalTime): String = String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)

    private fun normalize(times: List<LocalTime>): List<LocalTime> =
        times.map { it.withSecond(0).withNano(0) }.distinct().sorted().take(MAX)
}
