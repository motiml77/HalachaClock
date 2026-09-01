package com.zmanimclock.app.feature.zmanim.engine

import java.time.Instant
import java.time.LocalDate

/**
 * The full set of daily zmanim according to Maran's shita as published in the
 * Ohr HaChaim / "Chazon Yosef — Moreshet Maran" luach of Rav Yitzchak Yosef.
 *
 * All times are [Instant]s (UTC); render them in the location's time zone.
 * Fields are nullable — polar/extreme conditions can make a time undefined.
 *
 * Derivation anchors (verified city-by-city against the luach):
 *  - The seasonal-hour grid runs on the SEA-LEVEL day (sunrise→sunset);
 *    the ChaiTables visible netz is the vatikin display/alarm time only.
 *  - Shaah zmanit (GRA) = sea-level day / 12; chatzot = sun transit.
 *  - Alot = 72 zmaniyot minutes before sunrise (= 16.04° at the equinox).
 *  - Sof zman Shma (GRA) = sunrise + 3 shaos zmaniyot.
 *  - Shma/Tfila MGA — TWO displayed shitot, as in the luach:
 *      16.1° — dawn/nightfall at 16.1° solar depression on the actual day;
 *      72'  — dawn/nightfall at 72 fixed minutes.
 *  - Mincha gedola = the LATER of chatzot + 30 fixed minutes / chatzot + 0.5 shaah (machmir).
 *  - Plag hamincha (Yalkut Yosef) = tzeit (13.5) − 1 hour 15 zmaniyot minutes.
 *  - Tzeit weekday = sunset + 13.5 zmaniyot minutes (3.7° at the equinox).
 *  - Tzeit lechumra = 5.075° calibrated on the equinox day (≈20 min there),
 *    scaled by the current shaah zmanit — Zemaneh Yosef getTzetHumra.
 *  - Tzeit Shabbat = sunset + 40 fixed minutes.
 *  - Rabbeinu Tam = the earlier of sunset + 72 zmaniyot / + 72 fixed
 *    minutes ("le-kulah", per the approved Zemaneh Yosef implementation).
 */
data class DayZmanim(
    val date: LocalDate,
    val location: EngineLocation,
    /** True when the day base is the ChaiTables visible sunrise. */
    val basedOnVisibleSunrise: Boolean,

    // Night / dawn
    val chatzotLayla: Instant?,
    val alotHashachar: Instant?,
    val misheyakir66: Instant?,
    val misheyakir60: Instant?,

    // Sunrise
    val hanetzVisible: Instant?,
    val hanetzMishor: Instant?,

    // Morning deadlines — MGA in the two displayed shitot.
    // The PRIMARY one is the luach's own: a day running from 72 zmaniyot
    // minutes before sunrise to 72 zmaniyot minutes after sunset, i.e. exactly
    // 1.2 × the GRA shaah. Verified against royzmanim.com (Zemaneh Yosef, the
    // Ohr HaChaim calendar) to within 4 seconds over 25 city/date points.
    // 16.1° is kept as the secondary shita for those who follow it; it is up
    // to 9.5 minutes away from the luach in midwinter.
    val sofZmanShmaMga: Instant?,   // 72 zmaniyot minutes — the luach
    val sofZmanShmaMga16: Instant?, // 16.1° solar depression
    val sofZmanShmaGra: Instant?,
    val sofZmanTfilaMga: Instant?,   // 72 zmaniyot minutes — the luach
    val sofZmanTfilaMga16: Instant?, // 16.1°
    val sofZmanTfilaGra: Instant?,

    // Midday
    val chatzot: Instant?,
    val minchaGedola: Instant?,
    val minchaKetana: Instant?,
    val plagHaminchaYalkutYosef: Instant?,
    /**
     * Plag by the GRA reckoning — shkia minus 1¼ seasonal hours.
     *
     * A SECOND, displayed shita, not a replacement. The luach's own plag
     * (above) measures back from tzeit and is verified against it to the
     * second; this one measures back from shkia, which is what the widely-used
     * Religious-Zionist calendars (yeshiva.org.il among them) publish. The two
     * differ by exactly the 13.5 zmaniyot minutes of tzeit — about 15 minutes
     * in midsummer, 11 in midwinter — so the gap is a shita, not an error, and
     * showing both lets a user follow either.
     */
    val plagHaminchaGra: Instant?,

    // Sunset / night
    /**
     * The halachic שקיעה: sunset at the city's OWN height, which is what the
     * luach prints and what everything below is measured from.
     */
    val shkia: Instant?,
    /**
     * The same moment reckoned at the sea-level horizon — earlier than [shkia]
     * by ~45 s at 100 m and ~4½ min in Jerusalem.
     *
     * Carried alongside rather than discarded because the two are a live
     * machloket, not a right and a wrong answer: the Or HaChaim luach and
     * royzmanim print the elevation one, while Rav David Yosef, the Gra"z
     * Meltzer and Rav Sternbuch hold mishor — the last two explicitly as a
     * chumra for a de'oraita boundary. A user who follows that view, or who
     * simply wants to see what his own luach shows, can have the number
     * instead of being told a single answer is the answer.
     */
    val shkiaMishor: Instant?,
    val tzeitHakochavim: Instant?,
    /** 5.075° equinox-calibrated, seasonally scaled (≈20 min at the equinox). */
    val tzeitLechumra: Instant?,
    val tzeitShabbat: Instant?,
    val tzeitRabbeinuTam: Instant?,

    // Erev Shabbat / chag
    val candleLighting: Instant?,

    // Durations (milliseconds)
    val shaahZmanisGra: Long?,
    val shaahZmanisMga: Long?,
)
