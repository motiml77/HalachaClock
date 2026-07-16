package com.zmanimclock.app.feature.zmanim.engine

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Diagnostic printer: dumps the full engine output for a city/date so it can
 * be compared by hand against a published luach (moreshet-maran.com /
 * the printed Ohr HaChaim). Not an assertion test — always passes.
 */
class PrintDayTimesTest {

    @Test
    fun printJerusalemToday() {
        val engine = MaranZmanimEngine()
        val zone = ZoneId.of("Asia/Jerusalem")
        val date = LocalDate.of(2026, 7, 16)
        val jerusalem = EngineLocation("Jerusalem", 31.778, 35.235, 0.0, "Asia/Jerusalem")

        // Visible sunrise for Jerusalem, day-of-year 197 (16 July), from the
        // bundled ChaiTables asset — passed in exactly as the repository would.
        val visible = date.atTime(LocalTime.of(5, 44, 25)).atZone(zone).toInstant()

        fun fmt(i: Instant?): String =
            i?.atZone(zone)?.format(DateTimeFormatter.ofPattern("HH:mm:ss")) ?: "--"

        fun dump(label: String, d: DayZmanim) {
            println("=== $label ===")
            println("עלות השחר      : ${fmt(d.alotHashachar)}")
            println("משיכיר (60)    : ${fmt(d.misheyakir60)}")
            println("הנץ מישור      : ${fmt(d.hanetzMishor)}")
            println("הנץ הנראה      : ${fmt(d.hanetzVisible)}")
            println("סוזק\"ש מג\"א    : ${fmt(d.sofZmanShmaMga)}")
            println("סוזק\"ש גר\"א    : ${fmt(d.sofZmanShmaGra)}")
            println("סו\"ז תפילה מג\"א: ${fmt(d.sofZmanTfilaMga)}")
            println("סו\"ז תפילה גר\"א: ${fmt(d.sofZmanTfilaGra)}")
            println("חצות היום      : ${fmt(d.chatzot)}")
            println("מנחה גדולה     : ${fmt(d.minchaGedola)}")
            println("מנחה קטנה      : ${fmt(d.minchaKetana)}")
            println("פלג ילקו\"י     : ${fmt(d.plagHaminchaYalkutYosef)}")
            println("שקיעה          : ${fmt(d.shkia)}")
            println("צאת הכוכבים    : ${fmt(d.tzeitHakochavim)}")
            println("צאת שבת        : ${fmt(d.tzeitShabbat)}")
            println("רבנו תם        : ${fmt(d.tzeitRabbeinuTam)}")
            println("חצות לילה      : ${fmt(d.chatzotLayla)}")
            println("שעה זמנית גר\"א : ${d.shaahZmanisGra?.let { it / 60000 }} דק'")
        }

        dump("ירושלים 2026-07-16 — מישור", engine.calculate(jerusalem, date))
        dump("ירושלים 2026-07-16 — הנץ הנראה", engine.calculate(jerusalem, date, visibleSunrise = visible))

        val eilat = EngineLocation("Eilat", 29.558, 34.952, 0.0, "Asia/Jerusalem")
        val telAviv = EngineLocation("Tel Aviv", 32.0853, 34.7818, 0.0, "Asia/Jerusalem")
        dump("אילת 2026-07-16 — מישור", engine.calculate(eilat, date))
        dump("תל אביב 2026-07-16 — מישור", engine.calculate(telAviv, date))
    }
}
