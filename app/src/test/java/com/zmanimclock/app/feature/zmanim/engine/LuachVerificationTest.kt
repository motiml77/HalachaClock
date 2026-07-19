package com.zmanimclock.app.feature.zmanim.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * City-by-city verification against the authoritative implementation:
 * royzmanim.com (Zemaneh Yosef, "Chazon Yosef" / Ohr HaChaim mode — carries
 * the haskama of Rav Yitzchak Yosef).
 *
 * Reference values captured on 2026-07-16 from
 * royzmanim.com/calendar?lat=..&long=..&elevation=0&timeZone=Asia/Jerusalem
 * for the exact coordinates below (mishor base — the site shows sea-level
 * sunrise; ChaiTables visible netz is a separate layer on top).
 *
 * Tolerance: ±2 minutes — covers the site's per-zman display rounding
 * (floor/ceil varies by zman) plus sub-minute algorithm differences.
 * A larger gap means one of OUR anchors is wrong, not the luach.
 */
class LuachVerificationTest {

    private val engine = MaranZmanimEngine()
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val date: LocalDate = LocalDate.of(2026, 7, 16)

    private fun city(name: String, lat: Double, lon: Double) =
        EngineLocation(name, lat, lon, 0.0, "Asia/Jerusalem")

    // === ירושלים (31.778, 35.235) ===
    @Test
    fun jerusalem() = verifyCity(
        city("Jerusalem", 31.778, 35.235),
        alot = "04:20", misheyakir = "04:35", sunrise = "05:45",
        shmaMga = "08:33", shmaGra = "09:15", tfilaGra = "10:25",
        chatzot = "12:45", minchaGedola = "13:20", minchaKetana = "16:50",
        plagYY = "18:34", shkia = "19:45", tzeit = "20:01",
        rabbeinuTam = "20:57", chatzotLayla = "00:45",
    )

    // === בני ברק (32.081, 34.836) ===
    @Test
    fun bneiBrak() = verifyCity(
        city("Bnei Brak", 32.081, 34.836),
        alot = "04:21", misheyakir = "04:36", sunrise = "05:46",
        shmaMga = "08:34", shmaGra = "09:16", tfilaGra = "10:26",
        chatzot = "12:47", minchaGedola = "13:22", minchaKetana = "16:52",
        plagYY = "18:36", shkia = "19:47", tzeit = "20:04",
        rabbeinuTam = "20:59", chatzotLayla = "00:47",
    )

    // === צפת (32.963, 35.496) ===
    @Test
    fun tzfat() = verifyCity(
        city("Tzfat", 32.963, 35.496),
        alot = "04:16", misheyakir = "04:30", sunrise = "05:41",
        shmaMga = "08:30", shmaGra = "09:12", tfilaGra = "10:23",
        chatzot = "12:44", minchaGedola = "13:20", minchaKetana = "16:51",
        plagYY = "18:35", shkia = "19:47", tzeit = "20:03",
        rabbeinuTam = "20:59", chatzotLayla = "00:44",
    )

    // === אילת (29.558, 34.952) — דרום קיצוני ===
    @Test
    fun eilat() = verifyCity(
        city("Eilat", 29.558, 34.952),
        alot = "04:27", misheyakir = "04:42", sunrise = "05:51",
        shmaMga = "08:37", shmaGra = "09:18", tfilaGra = "10:28",
        chatzot = "12:46", minchaGedola = "13:21", minchaKetana = "16:49",
        plagYY = "18:31", shkia = "19:41", tzeit = "19:57",
        rabbeinuTam = "20:53", chatzotLayla = "00:46",
    )

    // === תל אביב (32.0853, 34.7818) — מישור החוף ===
    @Test
    fun telAviv() = verifyCity(
        city("Tel Aviv", 32.0853, 34.7818),
        alot = "04:21", misheyakir = "04:36", sunrise = "05:46",
        shmaMga = "08:34", shmaGra = "09:16", tfilaGra = "10:26",
        chatzot = "12:47", minchaGedola = "13:22", minchaKetana = "16:53",
        plagYY = "18:36", shkia = "19:48", tzeit = "20:04",
        rabbeinuTam = "21:00", chatzotLayla = "00:47",
    )

    // === טבריה (32.789, 35.531) ===
    @Test
    fun tiberias() = verifyCity(
        city("Tiberias", 32.789, 35.531),
        alot = "04:16", misheyakir = "04:31", sunrise = "05:41",
        shmaMga = "08:30", shmaGra = "09:12", tfilaGra = "10:23",
        chatzot = "12:44", minchaGedola = "13:19", minchaKetana = "16:51",
        plagYY = "18:34", shkia = "19:46", tzeit = "20:03",
        rabbeinuTam = "20:58", chatzotLayla = "00:44",
    )

    // === קרני שומרון (32.1667, 35.0833) — hill town, visible netz +12 min ===
    // THE ROOT-PRINCIPLE REGRESSION. Reference captured 2026-07-19 from
    // royzmanim.com/calendar?lat=32.1667&long=35.0833&elevation=0.
    // The REAL ChaiTables visible netz for that day (05:58:27, asset day 200)
    // is fed into the engine — and the whole grid must still match the
    // luach's sea-level values. Before the fix this drifted: shma GRA 09:25
    // instead of 09:16, chatzot 12:51 instead of 12:46.
    @Test
    fun karneiShomronGridIgnoresVisibleNetz() {
        val ksDate = LocalDate.of(2026, 7, 19)
        val location = city("Karnei Shomron", 32.1667, 35.0833)
        val visibleNetz = ksDate.atTime(LocalTime.of(5, 58, 27)).atZone(zone).toInstant()
        val day = engine.calculate(location, ksDate, visibleSunrise = visibleNetz)

        fun checkAt(zman: String, expectedLocal: String, actual: Instant?, nextDay: Boolean = false) {
            assertTrue("קרני שומרון/$zman: engine returned null", actual != null)
            val expectedDate = if (nextDay) ksDate.plusDays(1) else ksDate
            val expected = expectedDate.atTime(LocalTime.parse(expectedLocal)).atZone(zone).toInstant()
            val diff = Duration.between(expected, actual).abs()
            assertTrue(
                "קרני שומרון/$zman: expected ~$expectedLocal but engine gave " +
                    "${actual!!.atZone(zone).toLocalTime()} (off by ${diff.toMinutes()}m)",
                diff <= Duration.ofMinutes(2),
            )
        }

        checkAt("עלות השחר", "04:22", day.alotHashachar)
        checkAt("משיכיר", "04:37", day.misheyakir60)
        checkAt("הנץ מישור", "05:46", day.hanetzMishor)
        checkAt("הנץ הנראה", "05:58", day.hanetzVisible) // vatikin layer intact
        checkAt("סוזק\"ש מג\"א", "08:34", day.sofZmanShmaMga)
        checkAt("סוזק\"ש גר\"א", "09:16", day.sofZmanShmaGra)
        checkAt("סו\"ז ברכות ק\"ש", "10:26", day.sofZmanTfilaGra)
        checkAt("חצות", "12:46", day.chatzot)
        checkAt("מנחה גדולה", "13:21", day.minchaGedola)
        checkAt("מנחה קטנה", "16:51", day.minchaKetana)
        checkAt("פלג ילקו\"י", "18:34", day.plagHaminchaYalkutYosef)
        checkAt("שקיעה", "19:45", day.shkia)
        checkAt("צאת הכוכבים", "20:01", day.tzeitHakochavim)
        checkAt("רבנו תם", "20:57", day.tzeitRabbeinuTam)
        checkAt("חצות לילה", "00:46", day.chatzotLayla, nextDay = true)
    }

    @Suppress("LongParameterList")
    private fun verifyCity(
        location: EngineLocation,
        alot: String, misheyakir: String, sunrise: String,
        shmaMga: String, shmaGra: String, tfilaGra: String,
        chatzot: String, minchaGedola: String, minchaKetana: String,
        plagYY: String, shkia: String, tzeit: String,
        rabbeinuTam: String, chatzotLayla: String,
    ) {
        val day = engine.calculate(location, date)

        check(location.name, "עלות השחר", alot, day.alotHashachar)
        check(location.name, "משיכיר", misheyakir, day.misheyakir60)
        check(location.name, "הנץ מישור", sunrise, day.hanetzMishor)
        check(location.name, "סוזק\"ש מג\"א", shmaMga, day.sofZmanShmaMga)
        check(location.name, "סוזק\"ש גר\"א", shmaGra, day.sofZmanShmaGra)
        check(location.name, "סו\"ז ברכות ק\"ש", tfilaGra, day.sofZmanTfilaGra)
        check(location.name, "חצות", chatzot, day.chatzot)
        check(location.name, "מנחה גדולה", minchaGedola, day.minchaGedola)
        check(location.name, "מנחה קטנה", minchaKetana, day.minchaKetana)
        check(location.name, "פלג ילקו\"י", plagYY, day.plagHaminchaYalkutYosef)
        check(location.name, "שקיעה", shkia, day.shkia)
        check(location.name, "צאת הכוכבים", tzeit, day.tzeitHakochavim)
        check(location.name, "רבנו תם", rabbeinuTam, day.tzeitRabbeinuTam)
        // chatzot layla lands after midnight (next civil day)
        check(location.name, "חצות לילה", chatzotLayla, day.chatzotLayla, nextDay = true)
    }

    private fun check(
        city: String,
        zman: String,
        expectedLocal: String,
        actual: Instant?,
        nextDay: Boolean = false,
        toleranceMinutes: Long = 2,
    ) {
        assertTrue("$city/$zman: engine returned null", actual != null)
        val expectedDate = if (nextDay) date.plusDays(1) else date
        val expected = expectedDate.atTime(LocalTime.parse(expectedLocal)).atZone(zone).toInstant()
        val diff = Duration.between(expected, actual).abs()
        assertTrue(
            "$city/$zman: expected ~$expectedLocal but engine gave " +
                "${actual!!.atZone(zone).toLocalTime()} (off by ${diff.toMinutes()}m${diff.toSecondsPart()}s)",
            diff <= Duration.ofMinutes(toleranceMinutes),
        )
    }
}
