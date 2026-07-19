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

    // Morning deadlines — MGA in both displayed shitot (16.1° / 72 fixed)
    val sofZmanShmaMga: Instant?,   // 16.1° solar depression
    val sofZmanShmaMga72: Instant?, // 72 fixed minutes
    val sofZmanShmaGra: Instant?,
    val sofZmanTfilaMga: Instant?,   // 16.1°
    val sofZmanTfilaMga72: Instant?, // 72 fixed
    val sofZmanTfilaGra: Instant?,

    // Midday
    val chatzot: Instant?,
    val minchaGedola: Instant?,
    val minchaKetana: Instant?,
    val plagHaminchaYalkutYosef: Instant?,

    // Sunset / night
    val shkia: Instant?,
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
