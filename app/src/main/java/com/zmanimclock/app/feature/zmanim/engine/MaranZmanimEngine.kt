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
 * THE CENTRAL RULE: the seasonal-hour grid (shaah zmanit and every zman
 * derived from it — alot, misheyakir, sof zman shma/tfila, mincha, plag,
 * tzeit) runs on the SEA-LEVEL (mishor) day, sunrise→sunset, exactly like
 * the luach ("Sunrise (Sea Level)" on royzmanim.com). Chatzot is the sun's
 * transit. The VISIBLE sunrise (ChaiTables) serves vatikin only: it is the
 * displayed הנץ הנראה and the anchor of netz alarms — never the grid base.
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

        // === THE ROOT PRINCIPLE: the seasonal-hour grid runs on the SEA-LEVEL
        // (mishor) day — sunrise to sunset — NEVER on the visible netz.
        //
        // Chazon Yosef / Zemaneh Yosef (royzmanim.com shows "Sunrise (Sea
        // Level)"; its GRA shaah = mishor day / 12): terrain delays what the
        // eye sees, not the halachic day. The VISIBLE netz (ChaiTables) is the
        // vatikin davening time — displayed as הנץ הנראה and used for
        // netz-anchored alarms via instantOf(HANETZ) — but it must not stretch
        // or shift the shaah zmanit. Using it as the grid base skewed every
        // sunrise-anchored zman in hill towns (Karnei Shomron: netz +12 min →
        // shma GRA +9, chatzot +5), while sunset-anchored zmanim stayed right.
        val baseSunrise: Instant? = mishorSunrise ?: visibleSunrise

        if (baseSunrise == null || sunset == null) {
            return emptyDay(location, date, visibleSunrise != null, mishorSunrise, visibleSunrise, sunset)
        }

        // Shaah zmanit (GRA): the mishor day divided by 12
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

        // MGA — the two shitot the luach prints side by side:
        //  16.1°: dawn/nightfall at 16.1° actual solar depression on THIS day
        //         (longer than 72 min in winter/summer, 72 at the equinox);
        //  72':   dawn/nightfall at 72 fixed clock minutes.
        // Both use KosherJava's canonical day-of-alos→tzais 3/12 and 4/12.
        val shmaMga16 = czc.sofZmanShmaMGA16Point1Degrees?.toInstant()
        val tfilaMga16 = czc.sofZmanTfilaMGA16Point1Degrees?.toInstant()
        val shmaMga72 = czc.sofZmanShmaMGA72Minutes?.toInstant()
        val tfilaMga72 = czc.sofZmanTfilaMGA72Minutes?.toInstant()

        // === Midday: TRUE solar noon (sun transit) ===
        // Chazon Yosef (ROYZmanim getChatzot === getSunTransit) fixes chatzot at
        // the sun's meridian crossing — an independent astronomical event, NOT
        // "netz + 6 seasonal hours". With the sea-level netz the midpoint equals
        // the transit, but the VISIBLE netz is asymmetric (terrain delays only
        // sunrise), so netz+6 drifts off true chatzot — e.g. Karnei Shomron
        // showed 12:51 instead of the correct 12:46. Anchor to the mishor
        // sunrise↔sunset midpoint (= solar noon); fall back to netz+6 only if
        // the mishor sunrise is unavailable.
        val chatzot: Instant = czc.sunTransit?.toInstant()
            ?: mishorSunrise?.let {
                Instant.ofEpochMilli((it.toEpochMilli() + sunset.toEpochMilli()) / 2)
            }
            ?: fromBase(6.0)
        // Mincha gedola: 30 fixed minutes after chatzot, or half a shaah zmanit
        // after chatzot — whichever is LATER (the machmir position of the luach)
        val minchaGedolaFixed = chatzot.plusMillis(Duration.ofMinutes(MINCHA_GEDOLA_FIXED_MINUTES).toMillis())
        val minchaGedolaZmanis = chatzot.plusMillis((shaahGra / 2.0).toLong())
        val minchaGedola = maxOf(minchaGedolaFixed, minchaGedolaZmanis)
        val minchaKetana = fromBase(9.5)

        // === Night ===
        val tzeit = sunset.plusMillis(zmaniyotMinutes(TZEIT_ZMANIYOT_MINUTES))

        // Tzeit lechumra — Zemaneh Yosef getTzetHumra: how long the sun takes
        // to reach 5.075° below the horizon ON THE EQUINOX DAY (≈20 min in
        // Israel), expressed as a fraction of that day's shaah and applied to
        // the current shaah. Grows to ~24 min midsummer, shrinks in winter.
        val tzeitLechumra = equinoxDegreeSeasonalFraction(location, date, TZEIT_LECHUMRA_DEGREES)
            ?.let { fraction -> sunset.plusMillis((fraction * shaahGra).toLong()) }

        val tzeitShabbat = sunset.plusMillis(Duration.ofMinutes(TZEIT_SHABBAT_FIXED_MINUTES).toMillis())
        // Rabbeinu Tam le-kulah (approved Zemaneh Yosef default, rtKulah=true):
        // the EARLIER of 72 zmaniyot and 72 fixed minutes after shkia.
        // Summer (shaah > 60): fixed wins; winter: zmaniyot wins.
        val tzeitRabbeinuTam = minOf(
            sunset.plusMillis(zmaniyotMinutes(RABBEINU_TAM_ZMANIYOT_MINUTES)),
            sunset.plusMillis(Duration.ofMinutes(RABBEINU_TAM_FIXED_MINUTES).toMillis()),
        )

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
            sofZmanShmaMga = shmaMga16,
            sofZmanShmaMga72 = shmaMga72,
            sofZmanShmaGra = shmaGra,
            sofZmanTfilaMga = tfilaMga16,
            sofZmanTfilaMga72 = tfilaMga72,
            sofZmanTfilaGra = tfilaGra,
            chatzot = chatzot,
            minchaGedola = minchaGedola,
            minchaKetana = minchaKetana,
            plagHaminchaYalkutYosef = plag,
            shkia = sunset,
            tzeitHakochavim = tzeit,
            tzeitLechumra = tzeitLechumra,
            tzeitShabbat = tzeitShabbat,
            tzeitRabbeinuTam = tzeitRabbeinuTam,
            candleLighting = candleLighting,
            shaahZmanisGra = shaahGra.toLong(),
            shaahZmanisMga = shaahMga.toLong(),
        )
    }

    /**
     * The luach's degree→seasonal-minutes calibration (Zemaneh Yosef
     * durationOfEquinoxDegreeSeasonalHour): on the equinox day (17 March) at
     * this location, measure how long the sun takes to sink from sunset to
     * [degrees] below the horizon, and return it as a fraction of that day's
     * shaah zmanit. The caller multiplies by the current day's shaah.
     */
    private fun equinoxDegreeSeasonalFraction(
        location: EngineLocation,
        date: LocalDate,
        degrees: Double,
    ): Double? {
        val eq = complexCalendarFor(location, LocalDate.of(date.year, 3, 17))
        val eqSunrise = eq.seaLevelSunrise?.toInstant() ?: return null
        val eqSunset = eq.seaLevelSunset?.toInstant() ?: return null
        val eqTarget = eq.getSunsetOffsetByDegrees(
            com.kosherjava.zmanim.AstronomicalCalendar.GEOMETRIC_ZENITH + degrees
        )?.toInstant() ?: return null
        val eqShaah = Duration.between(eqSunrise, eqSunset).toMillis() / 12.0
        if (eqShaah <= 0) return null
        return Duration.between(eqSunset, eqTarget).toMillis() / eqShaah
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
        sofZmanShmaMga72 = null,
        sofZmanShmaGra = null,
        sofZmanTfilaMga = null,
        sofZmanTfilaMga72 = null,
        sofZmanTfilaGra = null,
        chatzot = null,
        minchaGedola = null,
        minchaKetana = null,
        plagHaminchaYalkutYosef = null,
        shkia = sunset,
        tzeitHakochavim = null,
        tzeitLechumra = null,
        tzeitShabbat = null,
        tzeitRabbeinuTam = null,
        candleLighting = null,
        shaahZmanisGra = null,
        shaahZmanisMga = null,
    )

    companion object {
        /** עלות השחר — 72 zmaniyot minutes before the netz. */
        const val ALOT_ZMANIYOT_MINUTES = 72.0

        /**
         * משיכיר (טלית ותפילין) — 66 zmaniyot minutes before the netz.
         * Kept as the earlier variant; the luach standard is 60 (see below),
         * verified against royzmanim.com (Chazon Yosef mode, 4 cities).
         */
        const val MISHEYAKIR_ZMANIYOT_MINUTES = 66.0

        /**
         * משיכיר — 60 zmaniyot minutes (exactly one shaah zmanit) before the
         * netz. THE luach value: "Earliest Tallit and Tefilin" on
         * royzmanim.com equals sunrise minus one seasonal hour in all
         * verified cities (2026-07-16).
         */
        const val MISHEYAKIR_STRICT_ZMANIYOT_MINUTES = 60.0

        /** MGA day is extended 72 zmaniyot on each side: shaah = 14.4/12 = 1.2 GRA. */
        const val MGA_EXTENSION_FACTOR = 1.2

        /** מנחה גדולה — fixed minutes after chatzot (machmir vs half-shaah). */
        const val MINCHA_GEDOLA_FIXED_MINUTES = 30L

        /** צאת הכוכבים חול — 13.5 zmaniyot minutes after shkia (3.7° at the equinox). */
        const val TZEIT_ZMANIYOT_MINUTES = 13.5

        /**
         * צאת הכוכבים לחומרא — solar depression 5.075° calibrated on the
         * equinox day (Zemaneh Yosef stringentNightfall; ≈20 min at the
         * equinox in Israel), scaled by the current shaah zmanit.
         */
        const val TZEIT_LECHUMRA_DEGREES = 5.075

        /** צאת שבת — 40 fixed minutes after shkia. */
        const val TZEIT_SHABBAT_FIXED_MINUTES = 40L

        /**
         * רבנו תם — the EARLIER of 72 zmaniyot / 72 fixed minutes after shkia
         * ("RT le-kulah"). Verified against the Zemaneh Yosef source
         * (ROYZmanim.js getTzetRT, config default rtKulah=true) and against
         * royzmanim.com output ("Rabbenu Tam (Fixed)" on 2026-07-16, when the
         * fixed 72 was the earlier one). The old app code (ccdad36) used the
         * same min() rule.
         */
        const val RABBEINU_TAM_ZMANIYOT_MINUTES = 72.0
        const val RABBEINU_TAM_FIXED_MINUTES = 72L

        /** פלג המנחה (ילקוט יוסף) — hour + 15 zmaniyot minutes before tzeit. */
        const val PLAG_YY_SHAOS_BEFORE_TZEIT = 1.25

        const val DEFAULT_CANDLE_OFFSET_MINUTES = 20L
    }
}
