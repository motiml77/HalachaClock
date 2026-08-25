package com.zmanimclock.app.feature.zmanim.display

import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.format.asZmanTime
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.relevantTimedZmanim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The external sites versus WHAT THE SCREEN ACTUALLY PRINTS.
 *
 * Every other zmanim test in this repo compares an external reference against
 * the ENGINE. That is a different question. A value can be correct in
 * DayZmanim and still never reach the user: `relevantTimedZmanim` may filter
 * its kind out, `instantOf` may map the kind to another field, or `asZmanTime`
 * may render a different minute than the reference prints. All three have
 * happened here — the mishor netz once printed 05:58 twice because its row was
 * gated on the value existing rather than on it rendering differently.
 *
 * So this walks the exact path the UI walks:
 *
 *     day.relevantTimedZmanim(date) -> (kind, instant) -> instant.asZmanTime(zone)
 *
 * and compares the resulting STRING against what an outside authority
 * publishes. It is the test behind docs/PLAG_COMPARISON.md,
 * docs/VERIFICATION_TABLES.md and section 2 of docs/CALENDAR_VERIFICATION.md;
 * regenerate those tables from here if any number moves.
 *
 * TRUNCATION IS DELIBERATE, AND IS WHY SOME ROWS SIT ONE MINUTE APART.
 * `asZmanTime` formats HH:mm, which truncates; Hebcal and most luchot round.
 * For an END boundary — sof zman shma, sof zman tfila — truncating is the safe
 * direction, because an earlier displayed zman never carries a user past a
 * deadline. Note that PLAG IS A START BOUNDARY (earliest arvit, early kabbalat
 * Shabbat), so for these two rows truncation is the LESS safe direction and the
 * luach itself rounds them up. That is under a minute and is an open question
 * for the app's owner, recorded in docs/PLAG_COMPARISON.md — not something to
 * change here silently.
 *
 * Where this test tolerates a minute it also proves the tolerance is only that:
 * the external value must be exactly one minute later AND our own seconds must
 * be 30-59. The second-resolution test then closes the question entirely — the
 * two engines agree to within a couple of seconds at the instant level.
 */
class AppDisplayVsExternalTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val engine = MaranZmanimEngine()

    private data class Ext(val city: String, val date: String, val time: String)

    private val cities = mapOf(
        "ירושלים" to (31.7683 to 35.2137),
        "תל אביב" to (32.0853 to 34.7818),
        "חיפה" to (32.7940 to 34.9896),
        "באר שבע" to (31.2530 to 34.7915),
        "צפת" to (32.9646 to 35.4960),
        "טבריה" to (32.7959 to 35.5308),
        "אשדוד" to (31.8044 to 34.6553),
        "אילת" to (29.5577 to 34.9519),
    )

    private fun locate(city: String): EngineLocation {
        val ll = cities.getValue(city)
        return EngineLocation(city, ll.first, ll.second, 0.0, "Asia/Jerusalem")
    }

    /** The instant behind the row the screen would list, or null if it lists none. */
    private fun shownInstant(city: String, date: LocalDate, kind: ZmanKind): Instant? =
        engine.calculate(locate(city), date)
            .relevantTimedZmanim(date)
            .firstOrNull { it.first == kind }
            ?.second

    /** Exactly the string the screen would print for [kind]. */
    private fun displayed(city: String, date: LocalDate, kind: ZmanKind): String? =
        shownInstant(city, date, kind)?.asZmanTime(zone)

    private fun toMinutes(hhmm: String) =
        hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

    // ------------------------------------------------------------------
    // 1. פלג המנחה של הלוח — against the luach's own published reference
    // ------------------------------------------------------------------

    /**
     * docs/luach_ohr_hachaim_reference.csv, captured from לוח אור החיים itself.
     * Seconds are included there; the app truncates them, so only the first
     * five characters can be compared to the screen.
     */
    private val luachPlag = listOf(
        Ext("ירושלים", "2026-07-29", "18:27:31"),
        Ext("ירושלים", "2026-09-21", "17:34:54"),
        Ext("ירושלים", "2026-12-15", "15:45:08"),
        Ext("ירושלים", "2027-03-22", "16:49:14"),
        Ext("ירושלים", "2028-02-29", "16:37:34"),
        Ext("תל אביב", "2026-07-29", "18:29:50"),
        Ext("תל אביב", "2026-09-21", "17:36:44"),
        Ext("תל אביב", "2026-12-15", "15:46:19"),
        Ext("תל אביב", "2027-03-22", "16:51:05"),
        Ext("תל אביב", "2028-02-29", "16:39:12"),
        Ext("חיפה", "2026-07-29", "18:30:10"),
        Ext("חיפה", "2026-09-21", "17:35:58"),
        Ext("חיפה", "2026-12-15", "15:44:03"),
        Ext("חיפה", "2027-03-22", "16:50:19"),
        Ext("חיפה", "2028-02-29", "16:37:57"),
        Ext("באר שבע", "2026-07-29", "18:28:26"),
        Ext("באר שבע", "2026-09-21", "17:36:38"),
        Ext("באר שבע", "2026-12-15", "15:47:57"),
        Ext("באר שבע", "2027-03-22", "16:50:58"),
        Ext("באר שבע", "2028-02-29", "16:39:38"),
        Ext("צפת", "2026-07-29", "18:28:26"),
        Ext("צפת", "2026-09-21", "17:33:57"),
        Ext("צפת", "2026-12-15", "15:41:41"),
        Ext("צפת", "2027-03-22", "16:48:18"),
        Ext("צפת", "2028-02-29", "16:35:50"),
    )

    @Test
    fun `the luach plag on screen equals the luach's own published value`() {
        for ((city, date, published) in luachPlag) {
            assertEquals(
                "$city $date",
                published.substring(0, 5),
                displayed(city, LocalDate.parse(date), ZmanKind.PLAG_HAMINCHA),
            )
        }
        assertEquals("reference points covered", 25, luachPlag.size)
    }

    // ------------------------------------------------------------------
    // 2. פלג הגר״א — against Hebcal, an entirely separate implementation
    // ------------------------------------------------------------------

    /**
     * Hebcal's `plagHaMincha` (@hebcal/noaa), fetched per city and date.
     * Hebcal rounds to the minute and we truncate; every disagreement below is
     * required to be exactly that, and the test proves it rather than assuming.
     */
    private val hebcalPlagGra = listOf(
        Ext("ירושלים", "2026-01-15", "15:53"),
        Ext("ירושלים", "2026-03-21", "16:35"),
        Ext("ירושלים", "2026-06-21", "18:19"),
        Ext("ירושלים", "2026-07-29", "18:12"),
        Ext("ירושלים", "2026-08-23", "17:52"),
        Ext("ירושלים", "2026-09-21", "17:21"),
        Ext("ירושלים", "2026-12-15", "15:34"),
        Ext("ירושלים", "2027-03-22", "16:36"),
        Ext("ירושלים", "2028-02-29", "16:25"),
        Ext("תל אביב", "2026-01-15", "15:55"),
        Ext("תל אביב", "2026-03-21", "16:37"),
        Ext("תל אביב", "2026-06-21", "18:21"),
        Ext("תל אביב", "2026-07-29", "18:14"),
        Ext("תל אביב", "2026-08-23", "17:54"),
        Ext("תל אביב", "2026-09-21", "17:23"),
        Ext("תל אביב", "2026-12-15", "15:35"),
        Ext("תל אביב", "2027-03-22", "16:37"),
        Ext("תל אביב", "2028-02-29", "16:26"),
        Ext("חיפה", "2026-01-15", "15:53"),
        Ext("חיפה", "2026-03-21", "16:36"),
        Ext("חיפה", "2026-06-21", "18:22"),
        Ext("חיפה", "2026-07-29", "18:15"),
        Ext("חיפה", "2026-08-23", "17:54"),
        Ext("חיפה", "2026-09-21", "17:22"),
        Ext("חיפה", "2026-12-15", "15:33"),
        Ext("חיפה", "2027-03-22", "16:37"),
        Ext("חיפה", "2028-02-29", "16:25"),
        Ext("באר שבע", "2026-01-15", "15:56"),
        Ext("באר שבע", "2026-03-21", "16:37"),
        Ext("באר שבע", "2026-06-21", "18:19"),
        Ext("באר שבע", "2026-07-29", "18:13"),
        Ext("באר שבע", "2026-08-23", "17:54"),
        Ext("באר שבע", "2026-09-21", "17:23"),
        Ext("באר שבע", "2026-12-15", "15:37"),
        Ext("באר שבע", "2027-03-22", "16:37"),
        Ext("באר שבע", "2028-02-29", "16:27"),
        Ext("צפת", "2026-01-15", "15:50"),
        Ext("צפת", "2026-03-21", "16:34"),
        Ext("צפת", "2026-06-21", "18:20"),
        Ext("צפת", "2026-07-29", "18:13"),
        Ext("צפת", "2026-08-23", "17:52"),
        Ext("צפת", "2026-09-21", "17:20"),
        Ext("צפת", "2026-12-15", "15:30"),
        Ext("צפת", "2027-03-22", "16:35"),
        Ext("צפת", "2028-02-29", "16:23"),
        Ext("טבריה", "2026-01-15", "15:50"),
        Ext("טבריה", "2026-03-21", "16:34"),
        Ext("טבריה", "2026-06-21", "18:20"),
        Ext("טבריה", "2026-07-29", "18:12"),
        Ext("טבריה", "2026-08-23", "17:52"),
        Ext("טבריה", "2026-09-21", "17:20"),
        Ext("טבריה", "2026-12-15", "15:31"),
        Ext("טבריה", "2027-03-22", "16:34"),
        Ext("טבריה", "2028-02-29", "16:23"),
        Ext("אשדוד", "2026-01-15", "15:56"),
        Ext("אשדוד", "2026-03-21", "16:37"),
        Ext("אשדוד", "2026-06-21", "18:21"),
        Ext("אשדוד", "2026-07-29", "18:14"),
        Ext("אשדוד", "2026-08-23", "17:55"),
        Ext("אשדוד", "2026-09-21", "17:24"),
        Ext("אשדוד", "2026-12-15", "15:36"),
        Ext("אשדוד", "2027-03-22", "16:38"),
        Ext("אשדוד", "2028-02-29", "16:27"),
        Ext("אילת", "2026-01-15", "15:58"),
        Ext("אילת", "2026-03-21", "16:36"),
        Ext("אילת", "2026-06-21", "18:16"),
        Ext("אילת", "2026-07-29", "18:10"),
        Ext("אילת", "2026-08-23", "17:51"),
        Ext("אילת", "2026-09-21", "17:22"),
        Ext("אילת", "2026-12-15", "15:39"),
        Ext("אילת", "2027-03-22", "16:36"),
        Ext("אילת", "2028-02-29", "16:27"),
    )

    @Test
    fun `the GRA plag on screen equals Hebcal, up to our truncation`() {
        var identical = 0
        var truncation = 0
        for ((city, date, external) in hebcalPlagGra) {
            val d = LocalDate.parse(date)
            val instant = shownInstant(city, d, ZmanKind.PLAG_HAMINCHA_GRA)
                ?: error("$city $date: the app lists no GRA plag row at all")
            val shown = instant.asZmanTime(zone)
            if (shown == external) {
                identical++
                continue
            }
            // Not identical — prove it is the rounding convention and not a
            // disagreement about the number itself.
            val delta = toMinutes(external) - toMinutes(shown)
            val sec = instant.atZone(zone).second
            assertTrue(
                "$city $date: Hebcal $external, screen $shown (:$sec). " +
                    "A gap of this shape is not the truncation convention.",
                delta == 1 && sec >= 30,
            )
            truncation++
        }
        assertEquals("every point accounted for", hebcalPlagGra.size, identical + truncation)
        // Pinned, so a silent switch from truncating to rounding shows up here
        // rather than as a quiet one-minute drift across the whole app.
        assertEquals("identical to the minute", 40, identical)
        assertEquals("differing only by truncation", 32, truncation)
    }

    /**
     * The same 72 Hebcal points at SECOND resolution (`sec=1`).
     *
     * The minute-level test above can only ever prove "within a minute", which
     * is exactly the resolution at which a real disagreement would hide. This
     * one compares the instants, and the whole spread collapses to seconds —
     * which is what actually retires the question of whether the one-minute
     * rows mean anything.
     */
    private val hebcalPlagGraSeconds = listOf(
        Ext("ירושלים", "2026-01-15", "15:53:22"),
        Ext("ירושלים", "2026-03-21", "16:35:13"),
        Ext("ירושלים", "2026-06-21", "18:18:48"),
        Ext("ירושלים", "2026-07-29", "18:12:05"),
        Ext("ירושלים", "2026-08-23", "17:52:20"),
        Ext("ירושלים", "2026-09-21", "17:21:16"),
        Ext("ירושלים", "2026-12-15", "15:33:51"),
        Ext("ירושלים", "2027-03-22", "16:35:34"),
        Ext("ירושלים", "2028-02-29", "16:24:42"),
        Ext("תל אביב", "2026-01-15", "15:54:33"),
        Ext("תל אביב", "2026-03-21", "16:36:57"),
        Ext("תל אביב", "2026-06-21", "18:21:11"),
        Ext("תל אביב", "2026-07-29", "18:14:18"),
        Ext("תל אביב", "2026-08-23", "17:54:21"),
        Ext("תל אביב", "2026-09-21", "17:23:01"),
        Ext("תל אביב", "2026-12-15", "15:34:59"),
        Ext("תל אביב", "2027-03-22", "16:37:20"),
        Ext("תל אביב", "2028-02-29", "16:26:15"),
        Ext("חיפה", "2026-01-15", "15:52:31"),
        Ext("חיפה", "2026-03-21", "16:36:11"),
        Ext("חיפה", "2026-06-21", "18:21:48"),
        Ext("חיפה", "2026-07-29", "18:14:35"),
        Ext("חיפה", "2026-08-23", "17:54:10"),
        Ext("חיפה", "2026-09-21", "17:22:14"),
        Ext("חיפה", "2026-12-15", "15:32:46"),
        Ext("חיפה", "2027-03-22", "16:36:33"),
        Ext("חיפה", "2028-02-29", "16:25:01"),
        Ext("באר שבע", "2026-01-15", "15:55:55"),
        Ext("באר שבע", "2026-03-21", "16:36:52"),
        Ext("באר שבע", "2026-06-21", "18:19:27"),
        Ext("באר שבע", "2026-07-29", "18:12:59"),
        Ext("באר שבע", "2026-08-23", "17:53:33"),
        Ext("באר שבע", "2026-09-21", "17:22:55"),
        Ext("באר שבע", "2026-12-15", "15:36:32"),
        Ext("באר שבע", "2027-03-22", "16:37:13"),
        Ext("באר שבע", "2028-02-29", "16:26:41"),
        Ext("צפת", "2026-01-15", "15:50:11"),
        Ext("צפת", "2026-03-21", "16:34:10"),
        Ext("צפת", "2026-06-21", "18:20:08"),
        Ext("צפת", "2026-07-29", "18:12:50"),
        Ext("צפת", "2026-08-23", "17:52:19"),
        Ext("צפת", "2026-09-21", "17:20:14"),
        Ext("צפת", "2026-12-15", "15:30:25"),
        Ext("צפת", "2027-03-22", "16:34:33"),
        Ext("צפת", "2028-02-29", "16:22:54"),
        Ext("טבריה", "2026-01-15", "15:50:21"),
        Ext("טבריה", "2026-03-21", "16:34:01"),
        Ext("טבריה", "2026-06-21", "18:19:38"),
        Ext("טבריה", "2026-07-29", "18:12:25"),
        Ext("טבריה", "2026-08-23", "17:52:01"),
        Ext("טבריה", "2026-09-21", "17:20:05"),
        Ext("טבריה", "2026-12-15", "15:30:36"),
        Ext("טבריה", "2027-03-22", "16:34:24"),
        Ext("טבריה", "2028-02-29", "16:22:51"),
        Ext("אשדוד", "2026-01-15", "15:55:33"),
        Ext("אשדוד", "2026-03-21", "16:37:27"),
        Ext("אשדוד", "2026-06-21", "18:21:07"),
        Ext("אשדוד", "2026-07-29", "18:14:22"),
        Ext("אשדוד", "2026-08-23", "17:54:36"),
        Ext("אשדוד", "2026-09-21", "17:23:30"),
        Ext("אשדוד", "2026-12-15", "15:36:01"),
        Ext("אשדוד", "2027-03-22", "16:37:48"),
        Ext("אשדוד", "2028-02-29", "16:26:55"),
        Ext("אילת", "2026-01-15", "15:58:04"),
        Ext("אילת", "2026-03-21", "16:36:07"),
        Ext("אילת", "2026-06-21", "18:15:30"),
        Ext("אילת", "2026-07-29", "18:09:47"),
        Ext("אילת", "2026-08-23", "17:51:24"),
        Ext("אילת", "2026-09-21", "17:22:10"),
        Ext("אילת", "2026-12-15", "15:39:01"),
        Ext("אילת", "2027-03-22", "16:36:25"),
        Ext("אילת", "2028-02-29", "16:26:56"),
    )

    @Test
    fun `at second resolution our GRA plag and Hebcal's agree to within seconds`() {
        var worst = 0L
        var worstAt = ""
        for ((city, date, exact) in hebcalPlagGraSeconds) {
            val d = LocalDate.parse(date)
            val ours = shownInstant(city, d, ZmanKind.PLAG_HAMINCHA_GRA)!!.atZone(zone)
            val theirs = d.atTime(
                exact.substring(0, 2).toInt(),
                exact.substring(3, 5).toInt(),
                exact.substring(6, 8).toInt(),
            ).atZone(zone)
            val off = abs(Duration.between(theirs, ours).seconds)
            if (off > worst) { worst = off; worstAt = "$city $date (ours $ours, Hebcal $exact)" }
        }
        // Two independent NOAA implementations, so a couple of seconds is the
        // floor. Anything above this is a real disagreement, not a convention.
        assertTrue("worst gap ${worst}s at $worstAt", worst <= 5)
    }

    // ------------------------------------------------------------------
    // 3. אתר ישיבה — the single point read straight off the site
    // ------------------------------------------------------------------

    /**
     * From yeshiva.org.il for ירושלים, 23.8.2026. The site publishes the GRA
     * reckoning — shkia minus 1¼ zmaniyot hours — which is why it lands on the
     * PLAG_HAMINCHA_GRA row and not on the luach's.
     *
     * Their שקיעה that day is 19:17 against our 19:14, because they apply
     * Jerusalem's elevation and the luach does not. That difference is
     * deliberate and covered elsewhere; the plag itself matches to the minute.
     */
    @Test
    fun `the GRA plag on screen equals what yeshiva org il prints`() {
        assertEquals(
            "17:52",
            displayed("ירושלים", LocalDate.of(2026, 8, 23), ZmanKind.PLAG_HAMINCHA_GRA),
        )
    }

    // ------------------------------------------------------------------
    // 4. The two rows Hebcal computes differently. Stated as formulas and
    //    checked, so "it is a shita" is never merely asserted in a doc.
    // ------------------------------------------------------------------

    private val ruleCities = listOf("ירושלים", "צפת", "באר שבע")
    private val ruleDates = listOf(
        LocalDate.of(2026, 7, 27),   // long summer day
        LocalDate.of(2026, 12, 15),  // short winter day
        LocalDate.of(2027, 4, 10),
        LocalDate.of(2026, 3, 27),   // DST begins
        LocalDate.of(2026, 10, 25),  // DST ends
        LocalDate.of(2028, 2, 29),   // Gregorian leap day
        LocalDate.of(2028, 3, 24),   // DST in a leap year
    )

    /**
     * Hebcal always takes half a shaah zmanit. We take whichever is LATER,
     * so the two must part company exactly when the day is short enough that
     * half a shaah zmanit falls under thirty fixed minutes — and by exactly
     * that shortfall. In London in January the shortfall reaches nine minutes.
     */
    @Test
    fun `mincha gedola is the LATER of half a zmanit hour and thirty fixed minutes`() {
        for (city in ruleCities) for (date in ruleDates) {
            val day = engine.calculate(locate(city), date)
            val halfShaah = Duration.between(day.hanetzMishor!!, day.shkia!!).toMillis() / 24
            val predicted = day.chatzot!!
                .plusMillis(maxOf(halfShaah, Duration.ofMinutes(30).toMillis()))
            val off = abs(Duration.between(predicted, day.minchaGedola!!).seconds)
            assertTrue("$city $date: mincha gedola is ${off}s off the max() rule", off <= 2)
        }
    }

    /**
     * Hebcal always takes 72 fixed minutes. We take whichever is EARLIER — the
     * lenient reading — so in winter, when a zmanit minute is short, we land
     * ten to twelve minutes before Hebcal, and in summer both land on the same
     * minute because the fixed 72 is then the earlier of the two.
     */
    @Test
    fun `rabbeinu tam is the EARLIER of seventy-two zmaniyot and seventy-two fixed`() {
        for (city in ruleCities) for (date in ruleDates) {
            val day = engine.calculate(locate(city), date)
            val shaah = Duration.between(day.hanetzMishor!!, day.shkia!!).toMillis() / 12
            val predicted = day.shkia!!.plusMillis(
                minOf((shaah * 72.0 / 60.0).toLong(), Duration.ofMinutes(72).toMillis()),
            )
            val off = abs(Duration.between(predicted, day.tzeitRabbeinuTam!!).seconds)
            assertTrue("$city $date: rabbeinu tam is ${off}s off the min() rule", off <= 2)
        }
    }

    // ------------------------------------------------------------------
    // 5. The rows the screen must NOT print
    // ------------------------------------------------------------------

    /**
     * Without ChaiTables data the ordinary netz IS the mishor netz, so a
     * separate "הנץ מישור" row would print the same minute twice. That reached
     * a device once. The gate is on the RENDERED MINUTE, not on the value
     * existing, and this pins it.
     */
    @Test
    fun `the mishor netz row is suppressed when it would duplicate the netz`() {
        for (city in cities.keys) for (date in ruleDates) {
            val kinds = engine.calculate(locate(city), date)
                .relevantTimedZmanim(date).map { it.first }
            assertTrue(
                "$city $date: HANETZ_MISHOR was listed though it renders the same minute as HANETZ",
                ZmanKind.HANETZ_MISHOR !in kinds,
            )
            assertTrue("$city $date: HANETZ must always be listed", ZmanKind.HANETZ in kinds)
        }
    }

    /**
     * The GRA plag is always earlier than the luach's — the whole gap is the
     * 13.5 zmaniyot minutes between shkia and tzeit — so it must be listed
     * first. A list showing them the other way round reads as a mistake even
     * when both numbers are right.
     */
    @Test
    fun `the GRA plag is listed before the luach plag`() {
        for ((city, date, _) in hebcalPlagGra) {
            val d = LocalDate.parse(date)
            val kinds = engine.calculate(locate(city), d).relevantTimedZmanim(d).map { it.first }
            val gra = kinds.indexOf(ZmanKind.PLAG_HAMINCHA_GRA)
            val luach = kinds.indexOf(ZmanKind.PLAG_HAMINCHA)
            assertTrue("$city $date: both plag rows must be listed", gra >= 0 && luach >= 0)
            assertTrue("$city $date: the GRA plag must come first", gra < luach)
        }
    }
}
