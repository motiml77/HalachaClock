package com.zmanimclock.app.feature.zmanim.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The engine pinned against THE LUACH ITSELF.
 *
 * Every other verification in this project asks "does an independent
 * astronomer agree with our arithmetic". This one asks the only question
 * that actually settles a halachic dispute: does לוח אור החיים agree.
 *
 * The expected values were read out of royzmanim.com — the Zemaneh Yosef
 * site of the Ohr HaChaim calendar (R' Shlomo Benizri & R' Asher Darshan,
 * with the approval of Hakham Ovadia Yosef zt"l and the ongoing haskama of
 * HaRav Yitzhak Yosef) — from its own ZemanFunctions engine at second
 * precision, in Israel mode (config.fixedMil = true, elevation 0).
 *
 * A one-minute tolerance is used because the luach publishes to the minute;
 * in practice the engine lands within a few seconds. The two zmanim where a
 * shita decision is still open (צאת הכוכבים and צאת שבת) are deliberately
 * NOT asserted here — see [luachShitaGapsAreKnownAndMeasured].
 */
class OhrHachaimLuachTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val toleranceSeconds = 60L

    private val jerusalem = EngineLocation("ירושלים", 31.778, 35.235, 0.0, "Asia/Jerusalem")
    private val telAviv = EngineLocation("תל אביב", 32.0853, 34.7818, 0.0, "Asia/Jerusalem")
    private val safed = EngineLocation("צפת", 32.9646, 35.4960, 0.0, "Asia/Jerusalem")

    private fun assertMatches(label: String, actual: Instant?, expected: String, date: LocalDate) {
        requireNotNull(actual) { "$label was null on $date" }
        val want = date.atTime(LocalTime.parse(expected)).atZone(zone).toInstant()
        val delta = Math.abs(actual.epochSecond - want.epochSecond)
        assertTrue(
            "$label on $date: engine ${actual.atZone(zone).toLocalTime()} " +
                "vs luach $expected — off by ${delta}s",
            delta <= toleranceSeconds,
        )
    }

    @Test
    fun `Jerusalem matches the luach in high summer`() {
        val d = LocalDate.of(2026, 7, 29)
        val z = MaranZmanimEngine().calculate(jerusalem, d)
        assertMatches("עלות השחר", z.alotHashachar, "04:30:12", d)
        assertMatches("משיכיר", z.misheyakir60, "04:43:57", d)
        assertMatches("הנץ", z.hanetzMishor, "05:52:44", d)
        assertMatches("ק\"ש מג\"א", z.sofZmanShmaMga, "08:37:47", d)
        assertMatches("ק\"ש גר\"א", z.sofZmanShmaGra, "09:19:03", d)
        assertMatches("תפילה גר\"א", z.sofZmanTfilaGra, "10:27:49", d)
        assertMatches("חצות", z.chatzot, "12:45:34", d)
        assertMatches("מנחה גדולה", z.minchaGedola, "13:19:57", d)
        assertMatches("מנחה קטנה", z.minchaKetana, "16:46:04", d)
        assertMatches("פלג", z.plagHaminchaYalkutYosef, "18:27:31", d)
        assertMatches("שקיעה", z.shkia, "19:38:00", d)
        assertMatches("צאה\"כ לקולא", z.tzeitHakochavim, "19:53:29", d)
        assertMatches("ר\"ת", z.tzeitRabbeinuTam, "20:50:00", d)
    }

    @Test
    fun `Jerusalem matches the luach in deep winter`() {
        // Winter is where the old 16.1-degree MGA was worst: 9 min 25 s out.
        val d = LocalDate.of(2026, 12, 15)
        val z = MaranZmanimEngine().calculate(jerusalem, d)
        assertMatches("עלות השחר", z.alotHashachar, "05:30:47", d)
        assertMatches("הנץ", z.hanetzMishor, "06:31:20", d)
        assertMatches("ק\"ש מג\"א", z.sofZmanShmaMga, "08:32:26", d)
        assertMatches("ק\"ש גר\"א", z.sofZmanShmaGra, "09:02:43", d)
        assertMatches("חצות", z.chatzot, "11:34:08", d)
        assertMatches("מנחה גדולה", z.minchaGedola, "12:04:08", d)
        assertMatches("שקיעה", z.shkia, "16:36:51", d)
        assertMatches("צאה\"כ לקולא", z.tzeitHakochavim, "16:48:12", d)
        // In winter RT le-kulah takes the ZMANIYOT side, not the fixed 72
        assertMatches("ר\"ת", z.tzeitRabbeinuTam, "17:37:24", d)
    }

    @Test
    fun `Tel Aviv matches the luach on Taanit Esther in a Hebrew leap year`() {
        val d = LocalDate.of(2027, 3, 22)
        val z = MaranZmanimEngine().calculate(telAviv, d)
        assertMatches("עלות השחר", z.alotHashachar, "04:29:33", d)
        assertMatches("הנץ", z.hanetzMishor, "05:42:39", d)
        assertMatches("ק\"ש מג\"א", z.sofZmanShmaMga, "08:08:49", d)
        assertMatches("ק\"ש גר\"א", z.sofZmanShmaGra, "08:45:22", d)
        assertMatches("חצות", z.chatzot, "11:47:49", d)
        assertMatches("שקיעה", z.shkia, "17:53:30", d)
        assertMatches("צאה\"כ לקולא", z.tzeitHakochavim, "18:07:13", d)
    }

    @Test
    fun `Safed matches the luach on the Gregorian leap day`() {
        val d = LocalDate.of(2028, 2, 29)
        val z = MaranZmanimEngine().calculate(safed, d)
        assertMatches("עלות השחר", z.alotHashachar, "04:57:58", d)
        assertMatches("הנץ", z.hanetzMishor, "06:06:45", d)
        assertMatches("ק\"ש מג\"א", z.sofZmanShmaMga, "08:24:19", d)
        assertMatches("ק\"ש גר\"א", z.sofZmanShmaGra, "08:58:42", d)
        assertMatches("חצות", z.chatzot, "11:50:25", d)
        assertMatches("מנחה קטנה", z.minchaKetana, "15:11:17", d)
        assertMatches("שקיעה", z.shkia, "17:34:35", d)
        assertMatches("צאה\"כ לקולא", z.tzeitHakochavim, "17:47:29", d)
        assertMatches("ר\"ת", z.tzeitRabbeinuTam, "18:43:22", d)
    }

    @Test
    fun `the MGA shita is the luach's 72 zmaniyot, not 16 point 1 degrees`() {
        // The correction itself, stated as an invariant: the MGA seasonal hour
        // is exactly 1.2x the GRA one, and sof zman shma MGA is 3 of them
        // after alot. If someone re-points this at a degree-based calculation
        // the identity breaks immediately.
        for (date in listOf(
            LocalDate.of(2026, 7, 29), LocalDate.of(2026, 12, 15),
            LocalDate.of(2027, 3, 22), LocalDate.of(2028, 2, 29),
        )) {
            for (city in listOf(jerusalem, telAviv, safed)) {
                val z = MaranZmanimEngine().calculate(city, date)
                val shaahGra = z.shaahZmanisGra!!
                val alot = z.alotHashachar!!
                val expected = alot.plusMillis((shaahGra * 1.2 * 3).toLong())
                val delta = Math.abs(z.sofZmanShmaMga!!.epochSecond - expected.epochSecond)
                assertTrue(
                    "${city.name} $date: MGA is not alot + 3 MGA-hours (off ${delta}s)",
                    delta <= 1,
                )
                // and the 16.1 degree reading must still be available, separately
                assertTrue("16.1° shita missing", z.sofZmanShmaMga16 != null)
            }
        }
    }

    @Test
    fun `luach shita gaps are known and measured`() {
        // Two zmanim still differ from the luach BY CHOICE, pending a ruling:
        //   צאת הכוכבים — luach: shkia + 20 FIXED minutes; ours: 6.2°
        //   צאת שבת     — luach: shkia + 30 FIXED minutes; ours: 40 fixed
        // This test does not judge them, it pins the size of the gap so it
        // cannot drift silently while the question is open.
        val d = LocalDate.of(2026, 7, 29)
        val z = MaranZmanimEngine().calculate(jerusalem, d)

        val luachTzeit = d.atTime(LocalTime.parse("19:58:00")).atZone(zone).toInstant()
        val tzeitGap = z.tzeitLechumra!!.epochSecond - luachTzeit.epochSecond
        assertTrue(
            "צאת הכוכבים gap vs the luach changed: ${tzeitGap}s (was ~465s)",
            tzeitGap in 400..530,
        )

        val luachShabbat = d.atTime(LocalTime.parse("20:08:00")).atZone(zone).toInstant()
        val shabbatGap = z.tzeitShabbat!!.epochSecond - luachShabbat.epochSecond
        assertTrue(
            "צאת שבת gap vs the luach changed: ${shabbatGap}s (was exactly 600s = 40 vs 30 min)",
            shabbatGap == 600L,
        )
    }
}
