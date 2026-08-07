package com.zmanimclock.app.feature.zmanim.model

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * Fast-day detection — computed from the Hebrew calendar for EVERY year, no
 * hardcoded dates. [JewishCalendar.isTaanis] already applies the deferral
 * rules (a fast falling on Shabbat is observed on Sunday; Taanit Esther moves
 * back to Thursday), so the observed date is always correct.
 *
 * Entry/exit times per the luach:
 *  - Minor fasts (Gedalia, 10 Tevet, Esther, 17 Tammuz): dawn (alot) → tzeit.
 *  - Tisha B'Av: sunset the evening before → tzeit.
 *  - Yom Kippur: sunset the evening before → tzeit Shabbat (40 min, like
 *    motzaei Shabbat — it is a full Yom Tov).
 *
 * Verified against Hebcal's Israel calendar for 2026–2028 in FastDaysTest.
 */
object FastDays {

    data class FastDay(
        val name: String,
        /** True for the 25-hour fasts that begin at sunset the evening before. */
        val startsEveningBefore: Boolean,
        /** True when the fast ends like Shabbat (Yom Kippur). */
        val endsLikeShabbat: Boolean,
    )

    /** The fast observed on [date] in Israel, or null. */
    fun fastOn(date: LocalDate, zone: ZoneId): FastDay? =
        fastOn(
            JewishCalendar(GregorianCalendar.from(date.atStartOfDay(zone)))
                .apply { inIsrael = true },
        )

    /**
     * The fast on [jc], or null.
     *
     * The calendar grid already holds a Hebrew date and has no time zone (a
     * Gregorian date maps to the same Hebrew date everywhere), so it enters
     * here rather than going back through a zone. One implementation, two
     * entry points — the grid and the zmanim banner can never disagree about
     * which day is a fast.
     */
    fun fastOn(jc: JewishCalendar): FastDay? {
        if (!jc.isTaanis) return null
        return when (jc.yomTovIndex) {
            JewishCalendar.SEVENTEEN_OF_TAMMUZ ->
                FastDay("צום י\"ז בתמוז", startsEveningBefore = false, endsLikeShabbat = false)
            JewishCalendar.TISHA_BEAV ->
                FastDay("תשעה באב", startsEveningBefore = true, endsLikeShabbat = false)
            JewishCalendar.FAST_OF_GEDALYAH ->
                FastDay("צום גדליה", startsEveningBefore = false, endsLikeShabbat = false)
            JewishCalendar.YOM_KIPPUR ->
                FastDay("יום הכיפורים", startsEveningBefore = true, endsLikeShabbat = true)
            JewishCalendar.TENTH_OF_TEVES ->
                FastDay("צום עשרה בטבת", startsEveningBefore = false, endsLikeShabbat = false)
            JewishCalendar.FAST_OF_ESTHER ->
                FastDay("תענית אסתר", startsEveningBefore = false, endsLikeShabbat = false)
            else -> null // isTaanis but not a public fast we surface
        }
    }
}
