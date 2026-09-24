package com.zmanimclock.app.feature.zmanim.format

import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * The hero's "בעוד …" figure: "1:24:36", or "6:32" once under an hour —
 * hours only when there are any. Counts to the zman's exact second, so it
 * reaches zero when the zman does, not at the truncated minute the list shows.
 *
 * One formatter for the phone and the desktop, so the same moment reads the
 * same on both. Never negative: a target already reached reads "0:00".
 */
fun countdownText(now: Instant, target: Instant): String {
    val d = Duration.between(now, target)
    if (d.isNegative || d.isZero) return "0:00"
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    // Locale.ROOT: Western digits whatever the device language is set to.
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}
