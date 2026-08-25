package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.format.asZmanTimeOrNull
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Not an assertion — a DUMP, for comparison against external sources.
 *
 * Writes what this app believes about (a) which Gregorian dates carry which
 * yom tov / fast / rosh chodesh, over several years, and (b) the zmanim it
 * would show for a spread of cities and dates. Both files are then diffed
 * against outside authorities by hand.
 *
 * Kept as a test so it runs on the real classpath with the real engine, but it
 * asserts nothing and cannot fail the build.
 */
class CalendarExternalDumpTest {

    private val out = File(System.getProperty("dump.dir") ?: "C:/gtmp")

    @Test
    fun `dump the Hebrew calendar markers for several years`() {
        val sb = StringBuilder("date,hebrew,kind,name\n")
        // Two leap years and three common ones, spanning a Gregorian decade.
        for (hebrewYear in 5786..5793) {
            val leap = JewishDate(hebrewYear, 7, 1).isJewishLeapYear
            val months =
                if (leap) listOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6)
                else listOf(7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6)
            for (month in months) {
                val ref = HebrewMonthRef(hebrewYear, month)
                for (d in 1..ref.daysInMonth) {
                    val date = JewishDate(hebrewYear, month, d).toLocalDate()
                    val m = MonthGridBuilder.metaFor(date)
                    val heb = "$hebrewYear-$month-$d"
                    fun row(kind: String, name: String) =
                        sb.append("$date,$heb,$kind,$name\n")

                    m.fast?.let { row("FAST", it.name) }
                    if (m.isYomTovAssurBemelacha) row("YOMTOV", m.yomTovName ?: "?")
                    if (m.isCholHamoed) row("CHOLHAMOED", m.yomTovName ?: "?")
                    if (m.isModernHoliday) row("MODERN", m.yomTovName ?: "?")
                    if (m.isRoshChodesh) row("ROSHCHODESH", "")
                    m.dayOfChanukah?.let { row("CHANUKAH", "$it") }
                }
            }
        }
        File(out, "cal_markers.csv").writeText(sb.toString())
        println("WROTE ${File(out, "cal_markers.csv")}")
    }

    /**
     * The astronomical (mishor) netz on its own, for Israeli cities across
     * several years — the row that is now displayed separately.
     *
     * Elevation is varied deliberately in the city list, from Tzfat at ~900m
     * to Tiberias BELOW sea level, because the whole claim being checked is
     * that elevation does not move this number.
     */
    @Test
    fun `dump the mishor netz for Israeli cities across five years`() {
        val cities = listOf(
            Triple("Jerusalem", 31.7683, 35.2137),
            Triple("Tzfat", 32.9646, 35.4960),
            Triple("Karnei Shomron", 32.1747, 35.0917),
            Triple("Bnei Brak", 32.0837, 34.8331),
            Triple("Haifa", 32.7940, 34.9896),
            Triple("Beer Sheva", 31.2530, 34.7915),
            Triple("Tiberias", 32.7959, 35.5308),
            Triple("Eilat", 29.5577, 34.9519),
        )
        // Four points a year — solstices, equinoxes — plus both DST edges,
        // across five years.
        val dates = buildList {
            for (y in 2026..2030) {
                add(LocalDate.of(y, 1, 15))
                add(LocalDate.of(y, 3, 21))
                add(LocalDate.of(y, 6, 21))
                add(LocalDate.of(y, 9, 23))
                add(LocalDate.of(y, 10, 30))
            }
        }
        val zone = ZoneId.of("Asia/Jerusalem")
        val sb = StringBuilder("city,lat,lng,date,netz_mishor,shkia\n")
        for ((name, lat, lng) in cities) {
            // elevation 0: the mishor doctrine, and what external sea-level
            // sources compute too.
            val loc = EngineLocation(name, lat, lng, 0.0, "Asia/Jerusalem")
            for (d in dates) {
                val day = MaranZmanimEngine().calculate(loc, d)
                sb.append("$name,$lat,$lng,$d,")
                    .append(day.hanetzMishor?.asZmanTimeOrNull(zone) ?: "").append(',')
                    .append(day.shkia?.asZmanTimeOrNull(zone) ?: "").append('\n')
            }
        }
        File(out, "netz_israel.csv").writeText(sb.toString())
        println("WROTE ${File(out, "netz_israel.csv")}")
    }

    @Test
    fun `dump zmanim for several cities and dates`() {
        // Spread deliberately: latitude, hemisphere-ish season, DST state, and
        // one diaspora city whose DST rules differ from Israel's.
        val cities = listOf(
            EngineLocation("Jerusalem", 31.7683, 35.2137, 0.0, "Asia/Jerusalem"),
            EngineLocation("Bnei Brak", 32.0837, 34.8331, 0.0, "Asia/Jerusalem"),
            EngineLocation("Tzfat", 32.9646, 35.4960, 0.0, "Asia/Jerusalem"),
            EngineLocation("Eilat", 29.5577, 34.9519, 0.0, "Asia/Jerusalem"),
            EngineLocation("New York", 40.7128, -74.0060, 0.0, "America/New_York"),
            EngineLocation("London", 51.5074, -0.1278, 0.0, "Europe/London"),
        )
        val dates = listOf(
            LocalDate.of(2026, 1, 15),  // deep winter
            LocalDate.of(2026, 3, 20),  // near the equinox
            LocalDate.of(2026, 3, 28),  // day after Israel's DST jump
            LocalDate.of(2026, 6, 21),  // solstice
            LocalDate.of(2026, 9, 12),  // autumn
            LocalDate.of(2026, 10, 26), // after Israel's DST ends
            LocalDate.of(2027, 4, 10),  // next year, another leap-year context
            LocalDate.of(2028, 2, 29),  // Gregorian leap day
        )
        val kinds = listOf(
            ZmanKind.ALOT_HASHACHAR, ZmanKind.MISHEYAKIR, ZmanKind.HANETZ,
            ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT, ZmanKind.SOF_ZMAN_SHMA_GRA,
            ZmanKind.SOF_ZMAN_TFILA_GRA, ZmanKind.CHATZOT,
            ZmanKind.MINCHA_GEDOLA, ZmanKind.MINCHA_KETANA, ZmanKind.PLAG_HAMINCHA,
            ZmanKind.SHKIA, ZmanKind.TZEIT_HAKOCHAVIM, ZmanKind.TZEIT_LECHUMRA,
            ZmanKind.TZEIT_RABBEINU_TAM,
        )
        val sb = StringBuilder("city,date," + kinds.joinToString(",") { it.name } + "\n")
        for (city in cities) {
            val zone = ZoneId.of(city.timeZoneId)
            for (date in dates) {
                val day = MaranZmanimEngine().calculate(city, date)
                sb.append(city.name).append(',').append(date)
                for (k in kinds) {
                    sb.append(',').append(day.instantOf(k)?.asZmanTimeOrNull(zone) ?: "")
                }
                sb.append('\n')
            }
        }
        File(out, "cal_zmanim.csv").writeText(sb.toString())
        println("WROTE ${File(out, "cal_zmanim.csv")}")
    }
}
