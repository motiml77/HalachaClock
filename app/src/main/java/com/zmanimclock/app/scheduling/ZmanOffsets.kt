package com.zmanimclock.app.scheduling

import com.zmanimclock.app.feature.settings.data.UserPreferences
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine

/**
 * The two user-chosen offsets that MOVE a zman rather than merely display it:
 * how long before shkia to alert for candle lighting, and how long after it
 * צאת שבת falls.
 *
 * They travel together, and as ONE object, on purpose.
 *
 * They used to be two separate `Long` parameters with sensible defaults on
 * every scheduler entry point. That looked harmless and was not: several call
 * sites read the user's preferences, used them for the location, and then
 * called through WITHOUT the offsets — so the default 20/40 silently replaced
 * the user's choice. Nothing failed, nothing logged; the alarm was simply
 * armed at the wrong minute. Skipping and un-skipping a צאת שבת alarm set to
 * ר"ת (72) re-armed it at 40 — ringing 32 minutes before צאת שבת by the shita
 * the user had explicitly picked.
 *
 * There is deliberately NO default on the scheduler parameters any more. A
 * missing offset is now a compile error instead of a wrong time, which is the
 * only way this class of mistake stops recurring.
 */
data class ZmanOffsets(
    val candleLightingMinutes: Long,
    val tzeitShabbatMinutes: Long,
) {
    companion object {
        /** The engine's own defaults — for previews and tests, never for arming. */
        val DEFAULT = ZmanOffsets(
            candleLightingMinutes = MaranZmanimEngine.DEFAULT_CANDLE_OFFSET_MINUTES,
            tzeitShabbatMinutes = MaranZmanimEngine.TZEIT_SHABBAT_FIXED_MINUTES,
        )

        fun from(prefs: UserPreferences) = ZmanOffsets(
            candleLightingMinutes = prefs.candleLightingMinutes.toLong(),
            tzeitShabbatMinutes = prefs.tzeitShabbatMinutes.toLong(),
        )
    }
}
