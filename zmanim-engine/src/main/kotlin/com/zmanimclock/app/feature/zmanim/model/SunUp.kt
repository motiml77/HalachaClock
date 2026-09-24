package com.zmanimclock.app.feature.zmanim.model

import com.zmanimclock.app.feature.zmanim.engine.DayZmanim
import java.time.Instant
import java.time.ZoneId

/**
 * Whether the sun is up at [at] — what picks the sun or the moon-and-stars
 * picture in the hero card on both the phone and the desktop.
 *
 * Day runs from the day's own הנץ to its own שקיעה: the visible netz when the
 * city has one, else the mishor netz — the same sunrise the zmanim list shows,
 * so the picture never turns to a sun before the list says the sun is up.
 * Everything else, dusk and the small hours included, is night.
 *
 * Lives here rather than in either UI so the two cannot disagree about which
 * picture a given minute gets.
 *
 * At a latitude where the sun does not rise or set that day the boundary is
 * undefined; the local clock (06:00–18:00) stands in rather than pinning the
 * picture to one state for weeks.
 */
fun DayZmanim.isSunUpAt(at: Instant): Boolean {
    val rise = hanetzVisible ?: hanetzMishor
    val set = shkia
    if (rise == null || set == null) {
        val hour = at.atZone(ZoneId.of(location.timeZoneId)).hour
        return hour in 6 until 18
    }
    return !at.isBefore(rise) && at.isBefore(set)
}
