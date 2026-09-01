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
        tzeitShabbatMinutes: Long = TZEIT_SHABBAT_FIXED_MINUTES,
    ): DayZmanim {
        val czc = complexCalendarFor(location, date)

        // The MISHOR pair — sea-level horizon. Kept because הנץ is displayed
        // from it (see below) and because שקיעה מישורית is shown as its own
        // row, so a user can see both conventions rather than being handed
        // one silently.
        val mishorSunrise: Instant? = czc.seaLevelSunrise?.toInstant()
        val mishorSunset: Instant? = czc.seaLevelSunset?.toInstant()

        // === THE HALACHIC DAY RUNS ON THE ELEVATION HORIZON ===
        //
        // Both endpoints, sunrise AND sunset, taken at the city's own height.
        // This is the Or HaChaim / Chazon Yosef convention, and it was
        // established here by measurement, not by assertion — the previous
        // sea-level rule had been asserted once and then "verified" by a test
        // that queried royzmanim.com with `&elevation=0` in the URL, i.e. it
        // asked the reference to assume our answer and then agreed with it.
        //
        // Measured for קרני שומרון, 2026-09-01, against royzmanim.com — the
        // posek's own reference implementation — driven live at two
        // elevations, everything else identical:
        //
        //                        elevation=0     elevation=320
        //   הנץ                     6:15            6:15      <- does NOT move
        //   סוף זמן ק"ש גר"א        9:27            9:26
        //   סוף זמן תפילה גר"א     10:31           10:30
        //   מנחה קטנה              16:24           16:26
        //   שקיעה                  19:04           19:07
        //
        // Three candidate grids were computed against those numbers. Only
        // elevation-sunrise → elevation-sunset reproduces them (it gives
        // 9:25:43 / 10:30:18 / 16:25:29); a mishor-sunrise → elevation-sunset
        // hybrid gives 10:32 for tefila and is ruled out outright. Matching
        // חזון שמים's statement of the Sephardi shita: "זמן קריאת שמע תפילה
        // וחמץ גר\"א – מחושב מזריחה בגובה ועד שקיעה בגובה".
        //
        // הנץ IS DELIBERATELY NOT THIS. royzmanim hardcodes sea-level sunrise
        // for the displayed netz (its own getNetz() calls getSeaLevelSunrise),
        // which the table above confirms — 6:15 at both elevations. So the
        // grid moves with height while the netz row does not, and that
        // asymmetry is the shita rather than an oversight.
        //
        // The VISIBLE netz (ChaiTables) remains vatikin-only: displayed as
        // הנץ הנראה and used for netz-anchored alarms, never as a grid
        // endpoint. Using it as the base skewed every sunrise-anchored zman in
        // hill towns (Karnei Shomron: netz +12 min → shma GRA +9, chatzot +5).
        val elevationSunrise: Instant? = czc.sunrise?.toInstant()
        val sunset: Instant? = czc.sunset?.toInstant()

        val baseSunrise: Instant? = elevationSunrise ?: mishorSunrise ?: visibleSunrise

        if (baseSunrise == null || sunset == null) {
            return emptyDay(
                location, date, visibleSunrise != null,
                mishorSunrise, visibleSunrise, sunset, mishorSunset,
            )
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

        // MGA — the luach's own shita, with 16.1° kept alongside it.
        //
        // THE LUACH (primary): the Magen Avraham day runs from 72 ZMANIYOT
        // minutes before sunrise to 72 zmaniyot minutes after sunset, so its
        // seasonal hour is exactly 1.2 × the GRA one — which is `shaahMga`,
        // already computed above. Sof zman shma is 3 such hours after alot,
        // tefila 4. Note alot is itself 72 zmaniyot before sunrise, so this
        // is self-consistent.
        //
        // This replaces KosherJava's 16.1° and 72-FIXED variants, neither of
        // which is what Ohr HaChaim prints. Read straight out of the Zemaneh
        // Yosef engine (royzmanim.com in Israel mode, config.fixedMil = true):
        // its dawn sits at exactly 72.00 zmaniyot minutes before sunrise in
        // every season, and its shaahMga / shaahGra is exactly 1.2. The 16.1°
        // version was up to 9 min 25 s away from the luach in midwinter — the
        // largest single error left in the engine. The formula below
        // reproduces the luach to within 4 seconds over 25 city/date points.
        val shmaMga = alot.plusMillis((shaahMga * 3.0).toLong())
        val tfilaMga = alot.plusMillis((shaahMga * 4.0).toLong())
        val shmaMga16 = czc.sofZmanShmaMGA16Point1Degrees?.toInstant()
        val tfilaMga16 = czc.sofZmanTfilaMGA16Point1Degrees?.toInstant()

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
        // Regular tzeit hakochavim — 13.5 zmaniyot minutes after shkia
        // (¾ mil; Maran Rav Ovadia's primary weekday tzeit). Equal to a 3.7°
        // solar depression at the equinox; expressed seasonally so it tracks
        // the day length. Zemaneh Yosef getTzet() in Ohr HaChaim / fixedMil
        // mode = sunset + fixedToSeasonal(13m30s) — identical.
        val tzeit = sunset.plusMillis(zmaniyotMinutes(TZEIT_ZMANIYOT_MINUTES))

        // Tzeit lechumra — THREE MEDIUM STARS, the tzeit most people actually
        // see: the true solar depression of 6.2° below the horizon on THIS
        // day (Peninei Halacha: ≈25.5 min at the equinox, 28 in deep winter,
        // 29.5 midsummer on a level horizon). A real astronomical angle, so
        // it self-corrects for every date and latitude — no seasonal scaling.
        val tzeitLechumra = czc.getSunsetOffsetByDegrees(
            com.kosherjava.zmanim.AstronomicalCalendar.GEOMETRIC_ZENITH + TZEIT_LECHUMRA_DEGREES
        )?.toInstant()

        // צאת שבת — fixed minutes after shkia, and the ONE zman here that is a
        // user setting rather than a fixed ruling. Ohr HaChaim / Zemaneh Yosef
        // publishes 30; much of Israel keeps 40; Jerusalem communities go
        // further still. The gap is a flat offset — identical in every city and
        // every season — so it is purely a question of minhag, not arithmetic.
        val tzeitShabbat = sunset.plusMillis(Duration.ofMinutes(tzeitShabbatMinutes).toMillis())
        // Rabbeinu Tam le-kulah (approved Zemaneh Yosef default, rtKulah=true):
        // the EARLIER of 72 zmaniyot and 72 fixed minutes after shkia.
        // Summer (shaah > 60): fixed wins; winter: zmaniyot wins.
        val tzeitRabbeinuTam = minOf(
            sunset.plusMillis(zmaniyotMinutes(RABBEINU_TAM_ZMANIYOT_MINUTES)),
            sunset.plusMillis(Duration.ofMinutes(RABBEINU_TAM_FIXED_MINUTES).toMillis()),
        )

        // Plag hamincha (Yalkut Yosef): one hour and 15 zmaniyot minutes before tzeit
        val plag = tzeit.minusMillis((shaahGra * PLAG_YY_SHAOS_BEFORE_TZEIT).toLong())
        // Plag by the GRA reckoning: the same 1¼ seasonal hours, but measured
        // back from SHKIA rather than from tzeit. Displayed alongside the
        // luach's own, never instead of it.
        val plagGra = sunset.minusMillis((shaahGra * PLAG_YY_SHAOS_BEFORE_TZEIT).toLong())

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
            sofZmanShmaMga16 = shmaMga16,
            sofZmanShmaGra = shmaGra,
            sofZmanTfilaMga = tfilaMga,
            sofZmanTfilaMga16 = tfilaMga16,
            sofZmanTfilaGra = tfilaGra,
            chatzot = chatzot,
            minchaGedola = minchaGedola,
            minchaKetana = minchaKetana,
            plagHaminchaYalkutYosef = plag,
            plagHaminchaGra = plagGra,
            shkia = sunset,
            shkiaMishor = mishorSunset,
            tzeitHakochavim = tzeit,
            tzeitLechumra = tzeitLechumra,
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
            // Elevation ON, so czc.sunrise / czc.sunset apply the horizon dip
            // for the city's own height. The mishor pair is still reachable
            // through seaLevelSunrise / seaLevelSunset, which is what the
            // displayed הנץ and the שקיעה מישורית row read.
            //
            // KosherJava throws on a negative elevation, and 27 of our
            // localities sit below sea level (Tiberias, Bet She'an, the Jordan
            // valley), so cities.json clamps those to 0 rather than letting a
            // Dead Sea town crash the engine.
            isUseElevation = true
        }
    }

    private fun emptyDay(
        location: EngineLocation,
        date: LocalDate,
        basedOnVisible: Boolean,
        mishorSunrise: Instant?,
        visibleSunrise: Instant?,
        sunset: Instant?,
        mishorSunset: Instant? = sunset,
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
        sofZmanShmaMga16 = null,
        sofZmanShmaGra = null,
        sofZmanTfilaMga = null,
        sofZmanTfilaMga16 = null,
        sofZmanTfilaGra = null,
        chatzot = null,
        minchaGedola = null,
        minchaKetana = null,
        plagHaminchaYalkutYosef = null,
        plagHaminchaGra = null,
        shkia = sunset,
        shkiaMishor = mishorSunset,
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
         * צאת הכוכבים לחומרא — three medium stars: a TRUE 6.2° solar
         * depression on the actual day (Peninei Halacha; ≈25.5 min at the
         * equinox, 28 in deep winter, 29.5 midsummer in Israel). This is the
         * tzeit most people can genuinely see three medium stars, used for
         * stringencies (motzei Shabbat / end of a fast in some communities).
         */
        const val TZEIT_LECHUMRA_DEGREES = 6.2

        /**
         * צאת שבת — fixed minutes after shkia. This is the app DEFAULT, not a
         * ruling: it is a user setting, because the practice genuinely varies.
         *
         * Measured against the Ohr HaChaim / Zemaneh Yosef luach across 5
         * cities × 5 dates, our 40 sits exactly 10 minutes after its 30 — a
         * flat offset with a spread of 4 seconds, i.e. purely the parameter.
         */
        const val TZEIT_SHABBAT_FIXED_MINUTES = 40L

        /** צאת שבת options offered in settings, with their provenance. */
        val TZEIT_SHABBAT_OPTIONS = listOf(
            25L to "כ\"ה דקות",
            30L to "ל' דקות — לוח אור החיים",
            35L to "ל\"ה דקות",
            40L to "מ' דקות — מנהג רווח",
            42L to "מ\"ב דקות",
            50L to "נ' דקות",
            72L to "ע\"ב דקות — ר\"ת",
        )

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
        /**
         * Plag hamincha — 1¼ seasonal hours before the END OF THE DAY. The
         * whole machloket is what "end of the day" means, and BOTH readings
         * are computed and displayed by this app.
         *
         * FROM TZEIT — the luach's own, and this constant's use site.
         *   Rav Yitzchak Yosef, verbatim: "פלג המנחה – שהוא שעה ורבע לפני
         *   צאת הכוכבים". Tzeit here is ¾ mil after shkia; Terumat HaDeshen
         *   (123, 167) puts a mil at 18 minutes, which the Shulchan Aruch
         *   adopts, giving 13.5 zmaniyot minutes — hence TZEIT_ZMANIYOT_MINUTES.
         *   The seasonal hour is the GRA one (netz→shkia), NOT the MGA one.
         *   Peninei Halacha attributes this side to Tosafot and others.
         *
         * FROM SHKIA — the GRA reckoning, exposed as plagHaminchaGra.
         *   Peninei Halacha, verbatim: "המנהג לעניין קבלת שבת להחשיב את פלג
         *   המנחה משקיעת החמה", citing the Gra, the Magen Avraham and the
         *   majority custom — the day ends at sunset. Its practical argument
         *   is that measuring from tzeit leaves almost no window at northern
         *   latitudes.
         *
         * The two differ by exactly those 13.5 zmaniyot minutes: ~16 min in
         * midsummer, ~11 in midwinter. Neither is MGA, which would use the
         * alot→tzeit day and land three quarters of an hour later still.
         *
         * Verified: the tzeit form against the Ohr HaChaim reference (25
         * points, 5 cities); the shkia form against Hebcal (240 points, 8
         * Israeli cities, 6 years). See PlagHaminchaTest.
         */
        const val PLAG_YY_SHAOS_BEFORE_TZEIT = 1.25

        const val DEFAULT_CANDLE_OFFSET_MINUTES = 20L
    }
}
