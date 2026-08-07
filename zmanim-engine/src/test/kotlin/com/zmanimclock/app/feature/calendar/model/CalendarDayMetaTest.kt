package com.zmanimclock.app.feature.calendar.model

import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import com.zmanimclock.app.feature.zmanim.model.FastDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * What each cell claims about its day.
 *
 * The rule these tests protect: the grid and the zmanim screen must never
 * disagree. They read the same JewishCalendar with the same inIsrael flag and,
 * for fasts, literally the same FastDays implementation.
 */
class CalendarDayMetaTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun meta(year: Int, month: Int, day: Int) =
        MonthGridBuilder.metaFor(JewishDate(year, month, day).toLocalDate())

    // ------------------------------------------------------- rosh chodesh

    @Test
    fun `1 Tishrei is Rosh Hashana, not Rosh Chodesh`() {
        // KosherJava's rule is (day==1 && month!=TISHREI) || day==30. Pinning
        // it here so nobody "fixes" the calendar to double-label Rosh Hashana.
        assertTrue(meta(5787, 7, 1).isYomTovAssurBemelacha)
        assertTrue("1 Tishrei must not be flagged Rosh Chodesh", !meta(5787, 7, 1).isRoshChodesh)
    }

    @Test
    fun `the 1st of any other month is Rosh Chodesh`() {
        for (month in listOf(8, 9, 10, 11, 1, 2, 3, 4, 5, 6)) {
            assertTrue("month $month", meta(5787, month, 1).isRoshChodesh)
        }
    }

    @Test
    fun `day 30 is Rosh Chodesh — it is the first of a two-day Rosh Chodesh`() {
        // Av always has 30 days, so 30 Av is the first day of Rosh Chodesh Elul.
        val m = meta(5786, 5, 30)
        assertTrue(m.isRoshChodesh)
        assertEquals(30, m.hebrewDayOfMonth)
    }

    @Test
    fun `a two-day Rosh Chodesh is named for the month that is starting`() {
        val grid = MonthGridBuilder.build(HebrewMonthRef(5786, 5)) // אב
        val event = grid.events.single { it.date == JewishDate(5786, 5, 30).toLocalDate() }
        assertEquals("ראש חודש אלול", event.title)
    }

    // -------------------------------------------------------------- fasts

    @Test
    fun `the grid marks exactly the days FastDays marks, across eight years`() {
        for (y in 5786..5793) {
            val months = if (JewishDate(y, 7, 1).isJewishLeapYear)
                listOf(7, 8, 9, 10, 11, 12, 13, 1, 2, 3, 4, 5, 6)
            else listOf(7, 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6)
            for (month in months) {
                val ref = HebrewMonthRef(y, month)
                for (d in 1..ref.daysInMonth) {
                    val date = JewishDate(y, month, d).toLocalDate()
                    val fromGrid = MonthGridBuilder.metaFor(date).fast
                    val fromEngine = FastDays.fastOn(date, zone)
                    assertEquals("$date ($y/$month/$d)", fromEngine?.name, fromGrid?.name)
                }
            }
        }
    }

    @Test
    fun `Taanit Esther and Purim fall in Adar II in a leap year, and are not doubled`() {
        val leap = 5787
        // Adar I has no fast; Adar II carries Taanit Esther.
        val adarI = (1..HebrewMonthRef(leap, 12).daysInMonth)
            .count { meta(leap, 12, it).fast != null }
        val adarII = (1..HebrewMonthRef(leap, 13).daysInMonth)
            .count { meta(leap, 13, it).fast != null }
        assertEquals("Adar I must carry no fast", 0, adarI)
        assertEquals("Adar II must carry exactly Taanit Esther", 1, adarII)
    }

    // ------------------------------------------------- yom tov / chol hamoed

    @Test
    fun `Pesach in Israel is yom tov on 15 and 21 Nissan and chol hamoed between`() {
        assertTrue(meta(5786, 1, 15).isYomTovAssurBemelacha)
        for (d in 16..20) {
            assertTrue("$d Nissan", meta(5786, 1, d).isCholHamoed)
            assertTrue("$d Nissan must not be assur bemelacha", !meta(5786, 1, d).isYomTovAssurBemelacha)
        }
        assertTrue(meta(5786, 1, 21).isYomTovAssurBemelacha)
        // One day of yom tov, not two — inIsrael = true.
        assertTrue("22 Nissan is not yom tov in Israel", !meta(5786, 1, 22).isYomTovAssurBemelacha)
    }

    @Test
    fun `Succot in Israel ends with a single Shemini Atzeret on 22 Tishrei`() {
        assertTrue(meta(5787, 7, 15).isYomTovAssurBemelacha)
        for (d in 16..21) assertTrue("$d Tishrei", meta(5787, 7, d).isCholHamoed)
        assertTrue(meta(5787, 7, 22).isYomTovAssurBemelacha)
        assertTrue("23 Tishrei is not yom tov in Israel", !meta(5787, 7, 23).isYomTovAssurBemelacha)
    }

    // ------------------------------------------------------ modern holidays

    @Test
    fun `modern Israeli holidays are surfaced — the user asked for them on`() {
        // 5 Iyar is Yom Ha'atzmaut in a year with no deferral.
        val year = (5786..5800).first { y ->
            (1..HebrewMonthRef(y, 2).daysInMonth).any { meta(y, 2, it).isModernHoliday }
        }
        val modern = (1..HebrewMonthRef(year, 2).daysInMonth)
            .map { meta(year, 2, it) }
            .filter { it.isModernHoliday }
        assertTrue("no modern holiday found in Iyar $year", modern.isNotEmpty())
        modern.forEach {
            assertNotNull("${it.date} is a modern holiday with no name", it.yomTovName)
            assertTrue(
                "${it.date} (${it.yomTovName}) must not be marked assur bemelacha",
                !it.isYomTovAssurBemelacha,
            )
        }
    }

    // -------------------------------------------------------------- omer

    @Test
    fun `the omer runs 1 to 49 from 16 Nissan to 5 Sivan`() {
        assertEquals(1, meta(5786, 1, 16).omerDay)
        assertEquals(49, meta(5786, 3, 5).omerDay)
        assertNull(meta(5786, 1, 14).omerDay)
        assertNull(meta(5786, 1, 15).omerDay)
        assertNull(meta(5786, 3, 6).omerDay)
    }

    @Test
    fun `exactly 49 days of the year carry an omer count`() {
        val months = listOf(1, 2, 3)
        val count = months.sumOf { m ->
            (1..HebrewMonthRef(5786, m).daysInMonth).count { meta(5786, m, it).omerDay != null }
        }
        assertEquals(49, count)
    }

    // ------------------------------------------------------------ chanukah

    @Test
    fun `chanukah runs eight days and only its first day gets a ribbon chip`() {
        val kislev = MonthGridBuilder.build(HebrewMonthRef(5787, 9))
        val chanukahDays = kislev.daysInThisMonth.count { it.dayOfChanukah != null }
        assertTrue("Kislev should hold the first chanukah days, found $chanukahDays", chanukahDays >= 5)
        assertEquals(
            "only one chanukah chip per month",
            1,
            kislev.events.count { it.kind == MonthEvent.Kind.CHANUKAH },
        )
    }

    // -------------------------------------------------------------- parsha

    @Test
    fun `a parsha is named on Shabbat and on no other day`() {
        val grid = MonthGridBuilder.build(HebrewMonthRef(5786, 5))
        grid.daysInThisMonth.forEach { m ->
            if (m.parshaName != null) {
                assertTrue("${m.date} has a parsha but is not Shabbat", m.isShabbat)
            }
        }
        // And at least some Shabbatot in an ordinary month do have one.
        assertTrue(grid.daysInThisMonth.any { it.isShabbat && it.parshaName != null })
    }

    // --------------------------------------------------------------- misc

    @Test
    fun `Hebrew and Gregorian day labels are separate fields, never one string`() {
        val m = meta(5786, 5, 21)
        assertEquals("כ״א", m.hebrewDayLabel)
        assertEquals(m.date.dayOfMonth.toString(), m.gregorianDayLabel)
    }
}
