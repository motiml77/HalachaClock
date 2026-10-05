package com.zmanimclock.app.feature.location

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.calendar.model.HebrewMonthSequence
import com.zmanimclock.app.feature.calendar.model.MonthGridBuilder
import com.zmanimclock.app.feature.calendar.model.toLocalDate
import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.model.OmerCount
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.hebrewNameOf
import com.zmanimclock.app.feature.zmanim.model.instantOf
import com.zmanimclock.app.feature.zmanim.model.isSecondNightLightingDay
import com.zmanimclock.app.feature.zmanim.model.isZmanRelevantOn
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

/**
 * One day of Yom Tov in Eretz Yisrael, two everywhere else.
 *
 * The first half of this file exists for ONE reason: Israel must be exactly
 * what it was before the second day was added. The second half pins the
 * diaspora rules.
 */
class HolidayLocaleTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val newYork = ZoneId.of("America/New_York")

    private fun heb(year: Int, month: Int, day: Int): LocalDate =
        JewishDate(year, month, day).toLocalDate()

    // ======================= ISRAEL IS UNCHANGED =======================

    @Test
    fun `every Israeli city is Israel and no city abroad is`() {
        for (c in CityCatalog.cities) {
            assertEquals(
                "${c.id} (${c.country}, ${c.timeZoneId})",
                c.country == "IL",
                HolidayLocale.inIsrael(c.timeZoneId),
            )
        }
    }

    /** The calendar grid: omitting the flag IS the old Israeli calendar. */
    @Test
    fun `the default grid is the Israeli grid, month for month`() {
        for (i in 0 until HebrewMonthSequence.size) {
            val ref = HebrewMonthSequence.refAt(i)
            assertEquals(
                "month $i",
                MonthGridBuilder.build(ref),
                MonthGridBuilder.build(ref, inIsrael = true),
            )
        }
    }

    /**
     * The rule for the zmanim list as it was before this change, written out
     * independently of the new code. Over four whole Hebrew years, for an
     * Israeli zone, the new function must agree with it on every single day.
     */
    @Test
    fun `Israel keeps the exact old candle lighting and tzeit shabbat days`() {
        var d = heb(5786, JewishDate.TISHREI, 1)
        val end = heb(5790, JewishDate.TISHREI, 1)
        var checked = 0
        while (d.isBefore(end)) {
            val jc = JewishCalendar(GregorianCalendar.from(d.atStartOfDay(jerusalem))).apply { inIsrael = true }
            val oldCandles = d.dayOfWeek == DayOfWeek.FRIDAY || jc.isErevYomTov || jc.isErevYomTovSheni
            val oldTzeit = d.dayOfWeek == DayOfWeek.SATURDAY || jc.isYomTovAssurBemelacha
            assertEquals("candles $d", oldCandles, isZmanRelevantOn(ZmanKind.CANDLE_LIGHTING, d, jerusalem))
            assertEquals("tzeit $d", oldTzeit, isZmanRelevantOn(ZmanKind.TZEIT_SHABBAT, d, jerusalem))
            assertFalse("no second night in Israel $d", isSecondNightLightingDay(d, jerusalem))
            d = d.plusDays(1)
            checked++
        }
        assertTrue(checked > 1400)
    }

    @Test
    fun `Israel's candle lighting time and name are untouched on every Yom Tov eve`() {
        val engine = MaranZmanimEngine()
        val loc = EngineLocation("Jerusalem", 31.778, 35.235, 0.0, "Asia/Jerusalem")
        for (d in listOf(heb(5787, JewishDate.NISSAN, 14), heb(5787, JewishDate.NISSAN, 15), heb(5787, JewishDate.TISHREI, 1))) {
            val z = engine.calculate(loc, d)
            assertEquals(z.candleLighting, z.instantOf(ZmanKind.CANDLE_LIGHTING))
            assertEquals("הדלקת נרות", z.hebrewNameOf(ZmanKind.CANDLE_LIGHTING))
        }
    }

    @Test
    fun `Israel's omer count and alarm gate see one day of Yom Tov`() {
        // The eve of Pesach VII (20 Nissan): the NEXT day is Yom Tov in both.
        // 15 Nissan 5787 evening enters 16 Nissan — a plain day in Israel.
        val erev16 = heb(5787, JewishDate.NISSAN, 15)
        assertNotNull(OmerCount.dayOfOmerAtTzeit(erev16, jerusalem))
    }

    // ======================= THE SECOND DAY, ABROAD =======================

    @Test
    fun `sixteenth of Nissan is Yom Tov abroad and Chol Hamoed in Israel`() {
        val d = heb(5787, JewishDate.NISSAN, 16)
        val israel = MonthGridBuilder.metaFor(d, inIsrael = true)
        val abroad = MonthGridBuilder.metaFor(d, inIsrael = false)
        assertTrue(israel.isCholHamoed); assertFalse(israel.isYomTovAssurBemelacha)
        assertTrue(abroad.isYomTovAssurBemelacha); assertFalse(abroad.isCholHamoed)
    }

    @Test
    fun `the second days of Pesach, Shavuot, Sukkot and Simchat Torah are Yom Tov only abroad`() {
        val days = mapOf(
            "22 Nissan" to heb(5787, JewishDate.NISSAN, 22),
            "7 Sivan" to heb(5787, JewishDate.SIVAN, 7),
            "16 Tishrei" to heb(5787, JewishDate.TISHREI, 16),
            "23 Tishrei" to heb(5787, JewishDate.TISHREI, 23),
        )
        for ((name, d) in days) {
            assertTrue("$name abroad", MonthGridBuilder.metaFor(d, inIsrael = false).isYomTovAssurBemelacha)
            assertFalse("$name in Israel", MonthGridBuilder.metaFor(d, inIsrael = true).isYomTovAssurBemelacha)
        }
    }

    @Test
    fun `Rosh Hashana has two days in Israel as well as abroad`() {
        val second = heb(5787, JewishDate.TISHREI, 2)
        assertTrue(MonthGridBuilder.metaFor(second, inIsrael = true).isYomTovAssurBemelacha)
        assertTrue(MonthGridBuilder.metaFor(second, inIsrael = false).isYomTovAssurBemelacha)
    }

    @Test
    fun `a first day abroad lights the second night after nightfall`() {
        val first = heb(5787, JewishDate.NISSAN, 15)
        val last = heb(5787, JewishDate.NISSAN, 21)
        for (d in listOf(first, last)) {
            if (d.dayOfWeek == DayOfWeek.FRIDAY) continue
            assertTrue("$d", isSecondNightLightingDay(d, newYork))
            assertTrue("$d relevant", isZmanRelevantOn(ZmanKind.CANDLE_LIGHTING, d, newYork))
            assertFalse("$d is a plain day in Israel", isSecondNightLightingDay(d, jerusalem))
        }
        // 16 Nissan is the SECOND day: nothing to light tonight (Shabbat or not).
        assertFalse(isSecondNightLightingDay(heb(5787, JewishDate.NISSAN, 16), newYork))
    }

    @Test
    fun `Yom Tov that falls on Friday lights for Shabbat before sunset as always`() {
        var d = heb(5786, JewishDate.TISHREI, 1)
        val end = heb(5800, JewishDate.TISHREI, 1)
        var friday = 0
        while (d.isBefore(end)) {
            if (d.dayOfWeek == DayOfWeek.FRIDAY) {
                assertFalse("$d", isSecondNightLightingDay(d, newYork)); friday++
            }
            d = d.plusDays(1)
        }
        assertTrue(friday > 100)
    }

    @Test
    fun `the second night candle time is after the stars and after Shabbat when the day is Shabbat`() {
        val engine = MaranZmanimEngine()
        val ny = CityCatalog.byId("new_york")!!
        val loc = EngineLocation(ny.nameEnglish, ny.latitude, ny.longitude, ny.elevation, ny.timeZoneId)
        // find a first day of a two-day Yom Tov that is not Friday, and one that is Shabbat
        var d = heb(5786, JewishDate.TISHREI, 1)
        val end = heb(5800, JewishDate.TISHREI, 1)
        var weekday = false; var shabbat = false
        while (d.isBefore(end) && !(weekday && shabbat)) {
            if (isSecondNightLightingDay(d, newYork)) {
                val z = engine.calculate(loc, d)
                val t = z.instantOf(ZmanKind.CANDLE_LIGHTING)!!
                assertTrue("$d is after sunset", t.isAfter(z.shkia!!))
                assertTrue("$d is in the zmanim list", z.relevantTimedZmanim(d).any { it.first == ZmanKind.CANDLE_LIGHTING })
                assertTrue("$d is named for the second night", z.hebrewNameOf(ZmanKind.CANDLE_LIGHTING).contains("ליל יו\"ט שני"))
                if (d.dayOfWeek == DayOfWeek.SATURDAY) { assertEquals(z.tzeitShabbat, t); shabbat = true } else weekday = true
            }
            d = d.plusDays(1)
        }
        assertTrue("saw a weekday and a Shabbat first day", weekday && shabbat)
    }

    @Test
    fun `an omer alert is held back on the eve of the second day abroad but not in Israel`() {
        // Evening of 15 Nissan enters 16 Nissan: Yom Tov abroad, Chol Hamoed in Israel.
        val d = heb(5787, JewishDate.NISSAN, 15)
        val enteredAbroad = JewishCalendar(GregorianCalendar.from(d.plusDays(1).atStartOfDay(newYork))).apply { inIsrael = false }
        val enteredIsrael = JewishCalendar(GregorianCalendar.from(d.plusDays(1).atStartOfDay(jerusalem))).apply { inIsrael = true }
        assertTrue(enteredAbroad.isAssurBemelacha)
        assertFalse(enteredIsrael.isAssurBemelacha)
    }
}
