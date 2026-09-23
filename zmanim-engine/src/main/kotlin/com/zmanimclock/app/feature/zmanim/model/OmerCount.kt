package com.zmanimclock.app.feature.zmanim.model

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * Sefirat HaOmer: the day number for a given night, and the fixed nightly
 * count text ("היום ... לעומר") for each of the 49 days.
 *
 * [JewishCalendar.getDayOfOmer] is the SAME source the calendar tab already
 * trusts for its own `omerDay` — not re-derived here.
 *
 * THE OFF-BY-ONE THAT MATTERS: the omer alert fires at tzeit, i.e. AT THE
 * MOMENT THE NEXT HEBREW DAY BEGINS. [JewishCalendar] maps a Gregorian civil
 * date to its Jewish equivalent at plain midnight — it has no notion of
 * "evening", so building it straight from the fire instant's own civil date
 * gives the day that is ENDING, not the one that just started. Concretely:
 * the first Sefirah night is the evening of the civil date whose DAYTIME was
 * 15 Nissan; [JewishCalendar] built from THAT civil date reports 15 Nissan
 * (dayOfOmer = -1, before the count begins) — it is the FOLLOWING civil
 * date, whose daytime is 16 Nissan, that reports dayOfOmer = 1. So: the
 * count for an alarm firing on the evening of civil date D is
 * [JewishCalendar] built from D.plusDays(1), always — see [dayOfOmerAtTzeit].
 */
object OmerCount {

    /** The final counted day — day 49, erev Shavuot. Single source of truth
     *  for callers that need to detect "this was the last count of the
     *  season" (e.g. auto-retiring the alarm) instead of hardcoding 49. */
    val LAST_DAY: Int get() = COUNT_TEXT.size

    /**
     * The omer day (1..49) for an alert firing at tzeit on the evening of
     * [fireDate] (civil date, in [zone]) — null on every night outside the
     * 49 nights of the omer. See the class doc for the plusDays(1) reasoning.
     */
    fun dayOfOmerAtTzeit(fireDate: LocalDate, zone: ZoneId): Int? {
        val jc = JewishCalendar(GregorianCalendar.from(fireDate.plusDays(1).atStartOfDay(zone)))
            .apply { inIsrael = true }
        return jc.dayOfOmer.takeIf { it in 1..LAST_DAY }
    }

    /**
     * The fixed nightly count, "היום ... לעומר" — the traditional wording
     * printed in the siddur, not generated: Hebrew's irregular counting
     * forms (teens take a SINGULAR noun; the classical unit-then-ten order,
     * "אחד ועשרים" not "עשרים ואחד") are exactly the kind of grammar a
     * generator gets subtly wrong, and this is text people are fulfilling a
     * mitzvah by saying aloud. Spot-check this against a siddur before it
     * ships — it was written from memory of the standard nusach, not read
     * off an external verified source the way the zmanim anchors were.
     */
    fun countText(day: Int): String? = COUNT_TEXT.getOrNull(day - 1)

    /**
     * The civil year of the omer season if [today] is an ORDINARY day of the
     * count, else null. This drives the once-a-season enable PROMPT (not a
     * fire time): it is eligible on any omer day (16 Nissan … day 49) that is
     * neither Shabbat nor Yom Tov, so —
     *  - it never fires during Pesach's own Yom Tov (15 Nissan is not yet an
     *    omer day; Pesach VII is excluded as assur-bemelacha), which is the
     *    owner's explicit requirement: offer it only once that Yom Tov is out;
     *  - it stays quiet on Shabbat/Yom Tov within the omer and simply appears
     *    the next ordinary day the app is opened.
     * The omer never spans a civil-year boundary (always spring), so the year
     * is a safe per-season key. Fire timing stays governed by
     * [dayOfOmerAtTzeit].
     */
    fun seasonYearOrNull(today: LocalDate, zone: ZoneId): Int? {
        val jc = JewishCalendar(GregorianCalendar.from(today.atStartOfDay(zone)))
            .apply { inIsrael = true }
        val eligible = jc.dayOfOmer in 1..LAST_DAY && !jc.isAssurBemelacha
        return today.year.takeIf { eligible }
    }

    private val COUNT_TEXT = listOf(
        "היום יום אחד לעומר",
        "היום שני ימים לעומר",
        "היום שלשה ימים לעומר",
        "היום ארבעה ימים לעומר",
        "היום חמשה ימים לעומר",
        "היום ששה ימים לעומר",
        "היום שבעה ימים, שהם שבוע אחד לעומר",
        "היום שמונה ימים, שהם שבוע אחד ויום אחד לעומר",
        "היום תשעה ימים, שהם שבוע אחד ושני ימים לעומר",
        "היום עשרה ימים, שהם שבוע אחד ושלשה ימים לעומר",
        "היום אחד עשר יום, שהם שבוע אחד וארבעה ימים לעומר",
        "היום שנים עשר יום, שהם שבוע אחד וחמשה ימים לעומר",
        "היום שלשה עשר יום, שהם שבוע אחד וששה ימים לעומר",
        "היום ארבעה עשר יום, שהם שני שבועות לעומר",
        "היום חמשה עשר יום, שהם שני שבועות ויום אחד לעומר",
        "היום ששה עשר יום, שהם שני שבועות ושני ימים לעומר",
        "היום שבעה עשר יום, שהם שני שבועות ושלשה ימים לעומר",
        "היום שמונה עשר יום, שהם שני שבועות וארבעה ימים לעומר",
        "היום תשעה עשר יום, שהם שני שבועות וחמשה ימים לעומר",
        "היום עשרים יום, שהם שני שבועות וששה ימים לעומר",
        "היום אחד ועשרים יום, שהם שלשה שבועות לעומר",
        "היום שנים ועשרים יום, שהם שלשה שבועות ויום אחד לעומר",
        "היום שלשה ועשרים יום, שהם שלשה שבועות ושני ימים לעומר",
        "היום ארבעה ועשרים יום, שהם שלשה שבועות ושלשה ימים לעומר",
        "היום חמשה ועשרים יום, שהם שלשה שבועות וארבעה ימים לעומר",
        "היום ששה ועשרים יום, שהם שלשה שבועות וחמשה ימים לעומר",
        "היום שבעה ועשרים יום, שהם שלשה שבועות וששה ימים לעומר",
        "היום שמונה ועשרים יום, שהם ארבעה שבועות לעומר",
        "היום תשעה ועשרים יום, שהם ארבעה שבועות ויום אחד לעומר",
        "היום שלשים יום, שהם ארבעה שבועות ושני ימים לעומר",
        "היום אחד ושלשים יום, שהם ארבעה שבועות ושלשה ימים לעומר",
        "היום שנים ושלשים יום, שהם ארבעה שבועות וארבעה ימים לעומר",
        "היום שלשה ושלשים יום, שהם ארבעה שבועות וחמשה ימים לעומר",
        "היום ארבעה ושלשים יום, שהם ארבעה שבועות וששה ימים לעומר",
        "היום חמשה ושלשים יום, שהם חמשה שבועות לעומר",
        "היום ששה ושלשים יום, שהם חמשה שבועות ויום אחד לעומר",
        "היום שבעה ושלשים יום, שהם חמשה שבועות ושני ימים לעומר",
        "היום שמונה ושלשים יום, שהם חמשה שבועות ושלשה ימים לעומר",
        "היום תשעה ושלשים יום, שהם חמשה שבועות וארבעה ימים לעומר",
        "היום ארבעים יום, שהם חמשה שבועות וחמשה ימים לעומר",
        "היום אחד וארבעים יום, שהם חמשה שבועות וששה ימים לעומר",
        "היום שנים וארבעים יום, שהם ששה שבועות לעומר",
        "היום שלשה וארבעים יום, שהם ששה שבועות ויום אחד לעומר",
        "היום ארבעה וארבעים יום, שהם ששה שבועות ושני ימים לעומר",
        "היום חמשה וארבעים יום, שהם ששה שבועות ושלשה ימים לעומר",
        "היום ששה וארבעים יום, שהם ששה שבועות וארבעה ימים לעומר",
        "היום שבעה וארבעים יום, שהם ששה שבועות וחמשה ימים לעומר",
        "היום שמונה וארבעים יום, שהם ששה שבועות וששה ימים לעומר",
        "היום תשעה וארבעים יום, שהם שבעה שבועות לעומר",
    )
}
