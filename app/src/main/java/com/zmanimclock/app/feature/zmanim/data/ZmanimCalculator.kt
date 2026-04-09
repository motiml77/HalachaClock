package com.zmanimclock.app.feature.zmanim.data

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.zmanim.data.model.DayZmanim
import com.zmanimclock.app.feature.zmanim.data.model.ZmanId
import com.zmanimclock.app.feature.zmanim.data.model.ZmanTime
import com.zmanimclock.app.location.model.AppGeoLocation
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZmanimCalculator @Inject constructor() {

    private val hebrewFormatter = HebrewDateFormatter().apply {
        isUseGershGershayim = true
        isHebrewFormat = true
    }

    fun calculateZmanim(
        location: AppGeoLocation,
        date: Calendar = Calendar.getInstance(location.timeZone),
        useElevation: Boolean = false,
        candleLightingOffset: Double = 20.0,
        visibleSunrise: Date? = null,
    ): DayZmanim {
        val geoLocation = location.toKosherJavaGeoLocation()
        // Ensure the calendar uses the location's timezone
        date.timeZone = location.timeZone
        val cal = ComplexZmanimCalendar(geoLocation).apply {
            calendar = date
            isUseElevation = useElevation
            setCandleLightingOffset(candleLightingOffset)
        }

        val jewishCal = JewishCalendar(date)
        val hebrewDate = formatHebrewDate(jewishCal)

        val zmanim = ZmanId.entries.map { zmanId ->
            calculateSingleZman(cal, jewishCal, zmanId, location, useElevation, visibleSunrise)
        }

        val now = Date()
        val sortedZmanim = markNextZman(zmanim, now)

        return DayZmanim(
            date = date.time,
            hebrewDate = hebrewDate,
            locationName = location.cityNameHebrew,
            zmanim = sortedZmanim,
            shaahZmanisGra = cal.shaahZmanisGra,
            shaahZmanisMga = cal.shaahZmanis72MinutesZmanis,
        )
    }

    private fun calculateSingleZman(
        cal: ComplexZmanimCalendar,
        jewishCal: JewishCalendar,
        zmanId: ZmanId,
        location: AppGeoLocation,
        useElevation: Boolean,
        visibleSunrise: Date? = null,
    ): ZmanTime {
        val time: Date? = when (zmanId) {
            // Night
            ZmanId.CHATZOT_LAYLA -> calculateSolarMidnight(cal)

            // Dawn
            ZmanId.ALOS_90 -> cal.alos90Zmanis
            ZmanId.ALOS_72 -> cal.alos72Zmanis

            // Misheyakir - Zemaneh Yosef specific
            ZmanId.MISHEYAKIR_66 -> calculateMisheyakir66(cal)
            ZmanId.MISHEYAKIR_60 -> calculateMisheyakir60(cal)

            // Sunrise
            ZmanId.HANETZ_SEA -> cal.seaLevelSunrise
            ZmanId.HANETZ_ELEVATED -> if (useElevation) cal.sunrise else cal.seaLevelSunrise
            ZmanId.HANETZ_VISIBLE -> visibleSunrise // From ChaiTables (null if unavailable)

            // Shma
            ZmanId.SOF_ZMAN_SHMA_GRA -> cal.sofZmanShmaGRA
            ZmanId.SOF_ZMAN_SHMA_MGA -> cal.sofZmanShmaMGA72MinutesZmanis

            // Tfila
            ZmanId.SOF_ZMAN_TFILA_GRA -> cal.sofZmanTfilaGRA
            ZmanId.SOF_ZMAN_TFILA_MGA -> cal.sofZmanTfilaMGA72MinutesZmanis

            // Midday
            ZmanId.MINCHA_GEDOLA -> cal.minchaGedolaGreaterThan30

            // Afternoon
            ZmanId.MINCHA_KETANA_161 -> cal.minchaKetana16Point1Degrees
            ZmanId.MINCHA_KETANA_72 -> cal.minchaKetana72Minutes
            ZmanId.PLAG_YALKUT_YOSEF -> calculatePlagYalkutYosef(cal)

            // Sunset
            ZmanId.SHKIA_SEA -> cal.seaLevelSunset
            ZmanId.SHKIA_ELEVATED -> if (useElevation) cal.sunset else cal.seaLevelSunset
            ZmanId.SHKIA_GENERAL -> cal.sunset

            // Bein HaShmashos - Yereim: 13.5 minutes before sunset
            ZmanId.BEIN_HASHMASHOT_YEREIM -> calculateBeinHashmashosYereim(cal)

            // Tzais Geonim
            ZmanId.TZAIS_3_8 -> cal.tzaisGeonim3Point8Degrees
            ZmanId.TZAIS_4_61 -> cal.tzaisGeonim4Point61Degrees
            ZmanId.TZAIS_4_8 -> cal.tzaisGeonim4Point8Degrees
            ZmanId.TZAIS_5_95 -> cal.tzaisGeonim5Point95Degrees
            ZmanId.TZAIS_7_67 -> cal.tzaisGeonim7Point67Degrees
            ZmanId.TZAIS_8_5 -> cal.tzaisGeonim8Point5Degrees
            ZmanId.TZAIS_9_75 -> cal.tzaisGeonim9Point75Degrees

            // Zemaneh Yosef tzais
            ZmanId.TZAIS_13_5_ZMANIYOT -> calculateTzais13Point5Zmaniyot(cal)
            ZmanId.TZAIS_LECHUMRA -> calculateTzaisLeChumra(cal)

            // Shabbat exit times
            ZmanId.TZAIS_SHABBAT_8_5 -> cal.tzaisGeonim8Point5Degrees
            ZmanId.TZAIS_SHABBAT_YY -> calculateTzaisShabbatAmudeiHoraah(cal) // Yalkut Yosef uses the most lenient opinion
            ZmanId.TZAIS_SHABBAT_AH -> calculateTzaisShabbatAmudeiHoraah(cal)
            ZmanId.TZAIS_SHABBAT_AH_40 -> calculateTzaisShabbatAH40(cal)
            ZmanId.TZAIS_RT_AH -> calculateTzaisRTAmudeiHoraah(cal)

            // Shaah Zmanit - these return durations, not times
            ZmanId.SHAAH_ZMANIT_GRA -> null
            ZmanId.SHAAH_ZMANIT_MGA -> null

            // Kiddush Levana
            ZmanId.KIDDUSH_LEVANA_3 -> jewishCal.tchilasZmanKidushLevana3Days
            ZmanId.KIDDUSH_LEVANA_7 -> jewishCal.tchilasZmanKidushLevana7Days
            ZmanId.KIDDUSH_LEVANA_15 -> jewishCal.sofZmanKidushLevana15Days

            // Candle Lighting
            ZmanId.CANDLE_LIGHTING -> cal.candleLighting
        }

        // For shaah zmanit, create display value instead of time
        val displayValue = when (zmanId) {
            ZmanId.SHAAH_ZMANIT_GRA -> formatShaahZmanit(cal.shaahZmanisGra)
            ZmanId.SHAAH_ZMANIT_MGA -> formatShaahZmanit(cal.shaahZmanis72MinutesZmanis)
            else -> null
        }

        val now = Date()
        return ZmanTime(
            id = zmanId,
            time = time,
            isPassed = time != null && time.before(now),
            isNext = false,
            displayValue = displayValue,
        )
    }

    // --- Zemaneh Yosef specific calculations ---

    /** Solar midnight: midpoint between today's chatzot and tomorrow's chatzot */
    private fun calculateSolarMidnight(cal: ComplexZmanimCalendar): Date? {
        val todayChatzot = cal.chatzos ?: return null
        val tomorrow = cal.calendar.clone() as Calendar
        tomorrow.add(Calendar.DAY_OF_MONTH, 1)
        val tomorrowCal = ComplexZmanimCalendar(cal.geoLocation).apply { calendar = tomorrow }
        val tomorrowChatzot = tomorrowCal.chatzos ?: return null
        val midnightMillis = todayChatzot.time + (tomorrowChatzot.time - todayChatzot.time) / 2
        return Date(midnightMillis)
    }

    /** Misheyakir 66 zmaniyot minutes: sunrise - 1.1 shaos zmaniyos */
    private fun calculateMisheyakir66(cal: ComplexZmanimCalendar): Date? {
        val sunrise = cal.sunrise ?: cal.seaLevelSunrise ?: return null
        val shaah = cal.shaahZmanisGra
        if (shaah == Long.MIN_VALUE) return null
        return Date(sunrise.time - (shaah * 1.1).toLong())
    }

    /** Misheyakir 60 zmaniyot minutes: sunrise - 1.0 shaah zmanit */
    private fun calculateMisheyakir60(cal: ComplexZmanimCalendar): Date? {
        val sunrise = cal.sunrise ?: cal.seaLevelSunrise ?: return null
        val shaah = cal.shaahZmanisGra
        if (shaah == Long.MIN_VALUE) return null
        return Date(sunrise.time - shaah)
    }

    /** Bein Hashmashos Yereim: 13.5 fixed minutes before sunset */
    private fun calculateBeinHashmashosYereim(cal: ComplexZmanimCalendar): Date? {
        val sunset = cal.sunset ?: cal.seaLevelSunset ?: return null
        return Date(sunset.time - (13.5 * 60 * 1000).toLong())
    }

    /** Plag Yalkut Yosef: tzais 13.5 zmaniyot - 1.25 shaos zmaniyos */
    private fun calculatePlagYalkutYosef(cal: ComplexZmanimCalendar): Date? {
        val tzais = calculateTzais13Point5Zmaniyot(cal) ?: return null
        val shaah = cal.shaahZmanisGra
        if (shaah == Long.MIN_VALUE) return null
        return Date(tzais.time - (shaah * 1.25).toLong())
    }

    /** Tzais 13.5 zmaniyot minutes after sunset */
    private fun calculateTzais13Point5Zmaniyot(cal: ComplexZmanimCalendar): Date? {
        val sunset = cal.sunset ?: cal.seaLevelSunset ?: return null
        val shaah = cal.shaahZmanisGra
        if (shaah == Long.MIN_VALUE) return null
        return Date(sunset.time + (shaah * 0.225).toLong()) // 13.5/60 = 0.225
    }

    /** Tzais LeChumra: 20 zmaniyot minutes after sunset */
    private fun calculateTzaisLeChumra(cal: ComplexZmanimCalendar): Date? {
        val sunset = cal.sunset ?: cal.seaLevelSunset ?: return null
        val shaah = cal.shaahZmanisGra
        if (shaah == Long.MIN_VALUE) return null
        return Date(sunset.time + (shaah / 3).toLong()) // 20/60 = 1/3
    }

    /** Tzais Shabbat Amudei Horaah: 7.165 degrees, minimum 20 fixed minutes */
    private fun calculateTzaisShabbatAmudeiHoraah(cal: ComplexZmanimCalendar): Date? {
        val sunset = cal.sunset ?: return null
        val degreeBased = cal.getSunsetOffsetByDegrees(90.0 + 7.165)
        val minimum20 = Date(sunset.time + 20 * 60 * 1000)
        return if (degreeBased != null && degreeBased.after(minimum20)) degreeBased else minimum20
    }

    /** Tzais Shabbat AH under 40: min of 7.165 degrees and 40 fixed minutes */
    private fun calculateTzaisShabbatAH40(cal: ComplexZmanimCalendar): Date? {
        val amudeiHoraah = calculateTzaisShabbatAmudeiHoraah(cal) ?: return null
        val sunset = cal.sunset ?: return null
        val fixed40 = Date(sunset.time + 40 * 60 * 1000)
        return if (amudeiHoraah.before(fixed40)) amudeiHoraah else fixed40
    }

    /** Tzais RT Amudei Horaah LeKula: min of 72 fixed and 72 zmaniyot */
    private fun calculateTzaisRTAmudeiHoraah(cal: ComplexZmanimCalendar): Date? {
        val fixed72 = cal.tzais72 ?: return null
        val zmaniyot72 = cal.tzais72Zmanis ?: return fixed72
        return if (fixed72.before(zmaniyot72)) fixed72 else zmaniyot72
    }

    // --- Helpers ---

    private fun markNextZman(zmanim: List<ZmanTime>, now: Date): List<ZmanTime> {
        val nextZman = zmanim
            .filter { it.time != null && it.time.after(now) }
            .minByOrNull { it.time!!.time }

        return zmanim.map { zman ->
            zman.copy(isNext = nextZman != null && zman.id == nextZman.id)
        }
    }

    private fun formatShaahZmanit(millis: Long): String {
        if (millis == Long.MIN_VALUE) return "--:--"
        val totalMinutes = millis / 60000
        val seconds = (millis % 60000) / 1000
        return "$totalMinutes \u05d3\u05e7' $seconds \u05e9\u05e0'"
    }

    private fun formatHebrewDate(jewishCal: JewishCalendar): String {
        return hebrewFormatter.format(jewishCal as JewishDate)
    }

    fun getHebrewHoliday(date: Calendar = Calendar.getInstance()): String? {
        val jewishCal = JewishCalendar(date)
        val yomTovIndex = jewishCal.yomTovIndex
        return if (yomTovIndex > 0) hebrewFormatter.formatYomTov(jewishCal) else null
    }
}
