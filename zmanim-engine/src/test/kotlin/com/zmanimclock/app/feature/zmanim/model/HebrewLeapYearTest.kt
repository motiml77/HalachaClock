package com.zmanimclock.app.feature.zmanim.model

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * שנה מעוברת — the HEBREW leap year (13 months: Adar I + Adar II), which
 * occurs 7 times in every 19-year cycle (years 3, 6, 8, 11, 14, 17, 19).
 *
 * The classic bug is Taanit Esther and Purim landing in Adar I instead of
 * Adar II: in a leap year they belong to the SECOND Adar, a full month
 * later. A fast banner shown a month early is a real halachic failure, so
 * the observed dates are pinned against Hebcal's Israel calendar.
 */
class HebrewLeapYearTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun jewish(date: LocalDate) =
        JewishCalendar(GregorianCalendar.from(date.atStartOfDay(zone))).apply { inIsrael = true }

    /** Hebrew years that are leap, spanning the next two 19-year cycles. */
    private val hebrewLeapYears = listOf(5784, 5787, 5790, 5793, 5795, 5798, 5801, 5806)

    /** A Hebrew year is leap when (7y + 1) mod 19 < 7 — the Metonic rule. */
    private fun isHebrewLeap(year: Int): Boolean = (7 * year + 1) % 19 < 7

    @Test
    fun `the years we test really are Hebrew leap years`() {
        hebrewLeapYears.forEach { y ->
            assertTrue("$y should be a Hebrew leap year", isHebrewLeap(y))
        }
        // and a couple that are not
        listOf(5785, 5786, 5788, 5789, 5804).forEach { y ->
            assertTrue("$y should NOT be leap", !isHebrewLeap(y))
        }
        // cross-check the rule against KosherJava on a real date in 5787
        val inLeapYear = jewish(LocalDate.of(2027, 3, 22))
        assertEquals(5787, inLeapYear.jewishYear)
        assertTrue("KosherJava disagrees about 5787", inLeapYear.isJewishLeapYear)
    }

    @Test
    fun `Taanit Esther falls in Adar II in every Hebrew leap year`() {
        // Verified against Hebcal (Israel): 2027-03-22, 2030-03-18, 2033-03-14
        // all fall in Hebrew leap years 5787, 5790 and 5793.
        val known = mapOf(
            LocalDate.of(2027, 3, 22) to 5787,
            LocalDate.of(2030, 3, 18) to 5790,
            LocalDate.of(2033, 3, 14) to 5793,
        )
        known.forEach { (date, expectedYear) ->
            val fast = FastDays.fastOn(date, zone)
            assertEquals("no Taanit Esther on $date", "תענית אסתר", fast?.name)

            val jc = jewish(date)
            assertEquals("wrong Hebrew year for $date", expectedYear, jc.jewishYear)
            assertTrue("$date should be in a leap year", jc.isJewishLeapYear)
            // ADAR_II == 13 in KosherJava's numbering
            assertEquals(
                "Taanit Esther on $date is in month ${jc.jewishMonth}, expected Adar II (13)",
                JewishCalendar.ADAR_II, jc.jewishMonth,
            )
        }
    }

    @Test
    fun `no fast is reported anywhere in Adar I of a leap year`() {
        // The whole of Adar I must be fast-free — this is exactly where a
        // 12-month assumption would wrongly place Taanit Esther.
        for (hebrewYear in hebrewLeapYears.filter { it in 5787..5798 }) {
            var date = LocalDate.of(2026, 1, 1)
            val end = LocalDate.of(2039, 12, 31)
            var checked = 0
            while (date.isBefore(end)) {
                val jc = jewish(date)
                if (jc.jewishYear == hebrewYear && jc.jewishMonth == JewishCalendar.ADAR) {
                    // In a leap year, month 12 is Adar I
                    checked++
                    assertEquals(
                        "a fast was reported in Adar I of $hebrewYear on $date",
                        null, FastDays.fastOn(date, zone),
                    )
                }
                date = date.plusDays(1)
            }
            assertTrue("never visited Adar I of $hebrewYear", checked >= 29)
        }
    }

    @Test
    fun `every Hebrew leap year still has exactly the six public fasts`() {
        // A leap year adds a month but not a fast. Count the distinct fasts
        // observed within each leap Hebrew year.
        for (hebrewYear in listOf(5787, 5790, 5793)) {
            val names = mutableSetOf<String>()
            var date = LocalDate.of(2026, 1, 1)
            val end = LocalDate.of(2034, 12, 31)
            while (date.isBefore(end)) {
                if (jewish(date).jewishYear == hebrewYear) {
                    FastDays.fastOn(date, zone)?.let { names += it.name }
                }
                date = date.plusDays(1)
            }
            assertEquals(
                "wrong fast set in leap year $hebrewYear: $names",
                setOf(
                    "צום גדליה", "יום הכיפורים", "צום עשרה בטבת",
                    "תענית אסתר", "צום י\"ז בתמוז", "תשעה באב",
                ),
                names,
            )
        }
    }

    @Test
    fun `fast days never repeat within one Hebrew leap year`() {
        // Guards against a fast being emitted in BOTH Adars.
        for (hebrewYear in listOf(5787, 5790)) {
            val seen = mutableMapOf<String, MutableList<LocalDate>>()
            var date = LocalDate.of(2026, 1, 1)
            val end = LocalDate.of(2031, 12, 31)
            while (date.isBefore(end)) {
                if (jewish(date).jewishYear == hebrewYear) {
                    FastDays.fastOn(date, zone)?.let {
                        seen.getOrPut(it.name) { mutableListOf() } += date
                    }
                }
                date = date.plusDays(1)
            }
            seen.forEach { (name, dates) ->
                assertEquals("$name occurred ${dates.size} times in $hebrewYear: $dates", 1, dates.size)
            }
        }
    }

    @Test
    fun `Purim in a leap year is a month after the Adar I date`() {
        // Sanity on the calendar itself: 14 Adar II is ~30 days after 14 Adar I.
        val purim5787 = LocalDate.of(2027, 3, 23) // Hebcal
        val jc = jewish(purim5787)
        assertEquals(JewishCalendar.ADAR_II, jc.jewishMonth)
        assertEquals(14, jc.jewishDayOfMonth)

        // 14 Adar I is a month earlier and is NOT a fast/Purim in our model
        val adarI14 = purim5787.minusDays(30)
        assertEquals(JewishCalendar.ADAR, jewish(adarI14).jewishMonth)
        assertEquals(null, FastDays.fastOn(adarI14, zone))
    }

    @Test
    fun `Yom Tov skipping still works in a leap year`() {
        // Pesach in leap year 5787 falls in Nisan, after the extra month.
        var pesach: LocalDate? = null
        var d = LocalDate.of(2027, 3, 1)
        while (d.isBefore(LocalDate.of(2027, 6, 1))) {
            val jc = jewish(d)
            if (jc.jewishMonth == JewishCalendar.NISSAN && jc.jewishDayOfMonth == 15) {
                pesach = d; break
            }
            d = d.plusDays(1)
        }
        assertTrue("could not locate Pesach in 5787", pesach != null)
        assertTrue(
            "Pesach $pesach should be assur bemelacha",
            jewish(pesach!!).isYomTovAssurBemelacha,
        )
    }
}
