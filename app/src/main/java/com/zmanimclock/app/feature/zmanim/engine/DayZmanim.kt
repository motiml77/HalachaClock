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
 *  - Day base = VISIBLE sunrise (ChaiTables) when available, else sea-level sunrise.
 *  - Shaah zmanit (GRA) = (sea-level sunset − day base) / 12.
 *  - Alot = 72 zmaniyot minutes before day base.
 *  - Sof zman Shma (GRA) = day base + 3 shaos zmaniyot.
 *  - Chatzot = day base + 6 shaos zmaniyot.
 *  - Mincha gedola = the LATER of chatzot + 30 fixed minutes / chatzot + 0.5 shaah (machmir).
 *  - Plag hamincha (Yalkut Yosef) = tzeit (13.5) − 1 hour 15 zmaniyot minutes.
 *  - Tzeit weekday = sunset + 13.5 zmaniyot minutes.
 *  - Tzeit Shabbat = sunset + 40 fixed minutes.
 *  - Rabbeinu Tam = sunset + 72 zmaniyot minutes.
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

    // Morning deadlines
    val sofZmanShmaMga: Instant?,
    val sofZmanShmaGra: Instant?,
    val sofZmanTfilaMga: Instant?,
    val sofZmanTfilaGra: Instant?,

    // Midday
    val chatzot: Instant?,
    val minchaGedola: Instant?,
    val minchaKetana: Instant?,
    val plagHaminchaYalkutYosef: Instant?,

    // Sunset / night
    val shkia: Instant?,
    val tzeitHakochavim: Instant?,
    val tzeitShabbat: Instant?,
    val tzeitRabbeinuTam: Instant?,

    // Erev Shabbat / chag
    val candleLighting: Instant?,

    // Durations (milliseconds)
    val shaahZmanisGra: Long?,
    val shaahZmanisMga: Long?,
)
