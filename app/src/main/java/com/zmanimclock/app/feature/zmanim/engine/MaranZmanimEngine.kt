package com.zmanimclock.app.feature.zmanim.engine

import com.kosherjava.zmanim.ComplexZmanimCalendar
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zmanim engine implementing Maran's shita per the Ohr HaChaim /
 * "Chazon Yosef — Moreshet Maran" luach (Rav Yitzchak Yosef).
 *
 * KosherJava is used strictly as an astronomical substrate (sea-level sunrise
 * and sunset); it is NOT the halachic authority. Every halachic time below is
 * derived explicitly from the luach's anchors so each constant can be verified
 * city-by-city against moreshet-maran.com and the "Chazon Yosef" mode of
 * royzmanim.com.
 *
 * THE CENTRAL RULE: the base of the day ("netz") is the VISIBLE sunrise
 * (ChaiTables) when available — not the astronomical/mishor sunrise. All
 * sunrise-anchored times (alot, misheyakir, sof zman shma/tfila, chatzot,
 * and the shaah zmanit itself) follow the visible netz.
 */
@Singleton
class MaranZmanimEngine @Inject constructor() {

    /**
     * Calculate the full daily set.
     *
     * @param visibleSunrise the ChaiTables visible sunrise for [date] at
     *   [location], if available. When null the engine falls back to the
     *   sea-level (mishor) sunrise and marks the result accordingly.
     * @param candleLightingOffsetMinutes minutes before shkia for candle
     *   lighting on erev Shabbat/chag (20 default; Yerushalayim custom is 40).
     */
    fun calculate(
        location: EngineLocation,
        date: LocalDate,
        visibleSunrise: Instant? = null,
        candleLightingOffsetMinutes: Long = DEFAULT_CANDLE_OFFSET_MINUTES,
    ): DayZmanim {
        val czc = complexCalendarFor(location, date)

        val mishorSunrise: Instant? = czc.seaLevelSunrise?.toInstant()
        val sunset: Instant? = czc.seaLevelSunset?.toInstant()

        // === The base of the day: visible netz first, mishor fallback ===
        val baseSunrise: Instant? = visibleSunrise ?: mishorSunrise

        if (baseSunrise == null || sunset == null) {
            return emptyDay(location, date, visibleSunrise != null, mishorSunrise, visibleSunrise, sunset)
        }

        // Shaah zmanit (GRA): the day from netz to shkia divided by 12
        val dayMillis = Duration.between(baseSunrise, sunset).toMillis()
        val shaahGra = dayMillis / 12.0
        // Shaah zmanit (MGA-72 zmaniyot): day extended by 72 zmaniyot minutes
        // on each side => exactly 1.2 * shaahGra
        val shaahMga = shaahGra * MGA_EXTENSION_FACTOR

        fun fromBase(shaos: Double): Instant = baseSunrise.plusMillis((shaahGra * shaos).toLong())
        fun zmaniyotMinutes(minutes: Double): Long = (shaahGra * minutes / 60.0).toLong()

        // === Dawn (anchored to the VISIBLE netz — the fix over the old code) ===
        val alot = baseSunrise.minusMillis(zmaniyotMinutes(ALOT_ZMANIYOT_MINUTES))
        val misheyakir66 = baseSunrise.minusMillis(zmaniyotMinutes(MISHEYAKIR_ZMANIYOT_MINUTES))
        val misheyakir60 = baseSunrise.minusMillis(zmaniyotMinutes(MISHEYAKIR_STRICT_ZMANIYOT_MINUTES))

        // === Morning deadlines ===
        val shmaGra = fromBase(3.0)
        val tfilaGra = fromBase(4.0)
        val shmaMga = alot.plusMillis((3.0 * shaahMga).toLong())
        val tfilaMga = alot.plusMillis((4.0 * shaahMga).toLong())

        // === Midday ===
        val chatzot = fromBase(6.0)
        // Mincha gedola: 30 fixed minutes after chatzot, or half a shaah zmanit
        // after chatzot — whichever is LATER (the machmir position of the luach)
        val minchaGedolaFixed = chatzot.plusMillis(Duration.ofMinutes(MINCHA_GEDOLA_FIXED_MINUTES).toMillis())
        val minchaGedolaZmanis = chatzot.plusMillis((shaahGra / 2.0).toLong())
        val minchaGedola = maxOf(minchaGedolaFixed, minchaGedolaZmanis)
        val minchaKetana = fromBase(9.5)

        // === Night ===
        val tzeit = sunset.plusMillis(zmaniyotMinutes(TZEIT_ZMANIYOT_MINUTES))
        val tzeitShabbat = sunset.plusMillis(Duration.ofMinutes(TZEIT_SHABBAT_FIXED_MINUTES).toMillis())
        val tzeitRabbeinuTam = sunset.plusMillis(zmaniyotMinutes(RABBEINU_TAM_ZMANIYOT_MINUTES))

        // Plag hamincha (Yalkut Yosef): one hour and 15 zmaniyot minutes before tzeit
        val plag = tzeit.minusMillis((shaahGra * PLAG_YY_SHAOS_BEFORE_TZEIT).toLong())

        // Solar midnight: 12 mean hours after chatzot (luach convention)
        val chatzotLayla = chatzot.plusMillis(Duration.ofHours(12).toMillis())

        val candleLighting = sunset.minusMillis(Duration.ofMinutes(candleLightingOffsetMinutes).toMillis())

        return DayZmanim(
            date = date,
            location = location,
            basedOnVisibleSunrise = visibleSunrise != null,
            chatzotLayla = chatzotLayla,
            alotHashachar = alot,
            misheyakir66 = misheyakir66,
            misheyakir60 = misheyakir60,
            hanetzVisible = visibleSunrise,
            hanetzMishor = mishorSunrise,
            sofZmanShmaMga = shmaMga,
            sofZmanShmaGra = shmaGra,
            sofZmanTfilaMga = tfilaMga,
            sofZmanTfilaGra = tfilaGra,
            chatzot = chatzot,
            minchaGedola = minchaGedola,
            minchaKetana = minchaKetana,
            plagHaminchaYalkutYosef = plag,
            shkia = sunset,
            tzeitHakochavim = tzeit,
            tzeitShabbat = tzeitShabbat,
            tzeitRabbeinuTam = tzeitRabbeinuTam,
            candleLighting = candleLighting,
            shaahZmanisGra = shaahGra.toLong(),
            shaahZmanisMga = shaahMga.toLong(),
        )
    }

    private fun complexCalendarFor(location: EngineLocation, date: LocalDate): ComplexZmanimCalendar {
        val cal: Calendar = GregorianCalendar(location.timeZone).apply {
            clear()
            set(date.year, date.monthValue - 1, date.dayOfMonth)
        }
        return ComplexZmanimCalendar(location.toKosherJavaGeoLocation()).apply {
            calendar = cal
            // Ohr HaChaim convention: mishor (sea-level) horizon; visible
            // terrain is handled by ChaiTables, never by elevation math.
            isUseElevation = false
        }
    }

    private fun emptyDay(
        location: EngineLocation,
        date: LocalDate,
        basedOnVisible: Boolean,
        mishorSunrise: Instant?,
        visibleSunrise: Instant?,
        sunset: Instant?,
    ): DayZmanim = DayZmanim(
        date = date,
        location = location,
        basedOnVisibleSunrise = basedOnVisible,
        chatzotLayla = null,
        alotHashachar = null,
        misheyakir66 = null,
        misheyakir60 = null,
        hanetzVisible = visibleSunrise,
        hanetzMishor = mishorSunrise,
        sofZmanShmaMga = null,
        sofZmanShmaGra = null,
        sofZmanTfilaMga = null,
        sofZmanTfilaGra = null,
        chatzot = null,
        minchaGedola = null,
        minchaKetana = null,
        plagHaminchaYalkutYosef = null,
        shkia = sunset,
        tzeitHakochavim = null,
        tzeitShabbat = null,
        tzeitRabbeinuTam = null,
        candleLighting = null,
        shaahZmanisGra = null,
        shaahZmanisMga = null,
    )

    companion object {
        /** עלות השחר — 72 zmaniyot minutes before the netz. */
        const val ALOT_ZMANIYOT_MINUTES = 72.0

        /** משיכיר — 66 zmaniyot minutes before the netz (luach standard). */
        const val MISHEYAKIR_ZMANIYOT_MINUTES = 66.0

        /** משיכיר לחומרא — 60 zmaniyot minutes before the netz. */
        const val MISHEYAKIR_STRICT_ZMANIYOT_MINUTES = 60.0

        /** MGA day is extended 72 zmaniyot on each side: shaah = 14.4/12 = 1.2 GRA. */
        const val MGA_EXTENSION_FACTOR = 1.2

        /** מנחה גדולה — fixed minutes after chatzot (machmir vs half-shaah). */
        const val MINCHA_GEDOLA_FIXED_MINUTES = 30L

        /** צאת הכוכבים חול — 13.5 zmaniyot minutes after shkia. */
        const val TZEIT_ZMANIYOT_MINUTES = 13.5

        /** צאת שבת — 40 fixed minutes after shkia. */
        const val TZEIT_SHABBAT_FIXED_MINUTES = 40L

        /** רבנו תם — 72 zmaniyot minutes after shkia. */
        const val RABBEINU_TAM_ZMANIYOT_MINUTES = 72.0

        /** פלג המנחה (ילקוט יוסף) — hour + 15 zmaniyot minutes before tzeit. */
        const val PLAG_YY_SHAOS_BEFORE_TZEIT = 1.25

        const val DEFAULT_CANDLE_OFFSET_MINUTES = 20L
    }
}
