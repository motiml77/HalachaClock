package com.zmanimclock.app.feature.zmanim.format

import com.zmanimclock.app.feature.zmanim.engine.EngineLocation
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.app.feature.zmanim.model.instantOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The parity contract: the same instant must render as the same string on
 * every platform.
 *
 * The luach tests prove the engine computes the right [Instant]. They say
 * nothing about what the user actually reads. A phone that truncates and a
 * computer that rounds show "19:32" and "19:33" for one identical moment,
 * with every other test green — which is indistinguishable, from the outside,
 * from a broken calculation.
 *
 * This test lives in the shared module, so it runs for every consumer.
 */
class ZmanTimeFormatTest {

    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val newYork = ZoneId.of("America/New_York")

    /** An instant at a given Jerusalem wall-clock time, to the second. */
    private fun at(h: Int, m: Int, s: Int, date: LocalDate = LocalDate.of(2026, 8, 5)) =
        date.atTime(h, m, s).atZone(jerusalem).toInstant()

    // ---------------------------------------------------------------- rule

    @Test
    fun `seconds are truncated, never rounded — this is the whole point`() {
        // 59 seconds is still the earlier minute. A rounding implementation
        // would say "19:33" here, and that single character is the entire
        // failure mode this file exists to prevent.
        assertEquals("19:32", at(19, 32, 59).asZmanTime(jerusalem))
        assertEquals("19:32", at(19, 32, 30).asZmanTime(jerusalem))
        assertEquals("19:32", at(19, 32, 1).asZmanTime(jerusalem))
        assertEquals("19:32", at(19, 32, 0).asZmanTime(jerusalem))
    }

    @Test
    fun `hours and minutes are zero padded to two digits`() {
        // "5:07" instead of "05:07" would misalign every time column in the
        // list and the widget.
        assertEquals("05:07", at(5, 7, 0).asZmanTime(jerusalem))
        assertEquals("00:03", at(0, 3, 0).asZmanTime(jerusalem))
    }

    @Test
    fun `midnight is 00 and noon is 12 — 24-hour clock, never AM PM`() {
        assertEquals("00:00", at(0, 0, 0).asZmanTime(jerusalem))
        assertEquals("12:00", at(12, 0, 0).asZmanTime(jerusalem))
        assertEquals("23:59", at(23, 59, 59).asZmanTime(jerusalem))
    }

    @Test
    fun `the zone argument decides the answer, not the machine's default`() {
        // The desktop build will run on machines set to any locale and any
        // time zone. Formatting must depend only on the location's zone.
        val instant = Instant.parse("2026-08-05T16:32:27Z")
        assertEquals("19:32", instant.asZmanTime(jerusalem))
        assertEquals("12:32", instant.asZmanTime(newYork))
    }

    @Test
    fun `an undefined zman stays null instead of becoming a string`() {
        // At extreme latitudes the engine legitimately returns null. Callers
        // must be able to hide the row, not print "00:00".
        val absent: Instant? = null
        assertNull(absent.asZmanTimeOrNull(jerusalem))
        assertEquals("19:32", at(19, 32, 27).asZmanTimeOrNull(jerusalem))
    }

    @Test
    fun `DST transition days format in local wall-clock terms`() {
        // Israel springs forward on the Friday before the last Sunday in
        // March; 2026-03-27 02:00 -> 03:00. A zman computed on that day must
        // still render as the wall-clock time people actually see.
        val beforeJump = LocalDate.of(2026, 3, 27).atTime(1, 30, 45)
            .atZone(jerusalem).toInstant()
        assertEquals("01:30", beforeJump.asZmanTime(jerusalem))

        val afterJump = LocalDate.of(2026, 3, 27).atTime(6, 15, 10)
            .atZone(jerusalem).toInstant()
        assertEquals("06:15", afterJump.asZmanTime(jerusalem))
    }

    // ------------------------------------------------- end-to-end anchors

    /**
     * A few real engine outputs, pinned as rendered strings.
     *
     * Deliberately small. The luach tests already pin the instants; these
     * exist only to prove the whole chain — engine to screen — still produces
     * these exact characters. Chosen so that several have seconds ≥ 30, which
     * is what makes them able to tell truncation and rounding apart:
     * עלות at 04:35:47 renders "04:35" and would render "04:36" if anyone
     * switched to rounding.
     */
    private fun render(loc: EngineLocation, date: LocalDate, zone: ZoneId, kind: ZmanKind): String? =
        MaranZmanimEngine().calculate(loc, date).instantOf(kind)?.asZmanTimeOrNull(zone)

    @Test
    fun `Jerusalem 5 August 2026 renders exactly these times`() {
        val loc = EngineLocation("Jerusalem", 31.778, 35.235, 0.0, "Asia/Jerusalem")
        val date = LocalDate.of(2026, 8, 5)
        fun t(k: ZmanKind) = render(loc, date, jerusalem, k)

        assertEquals("00:45", t(ZmanKind.CHATZOT_LAYLA))
        assertEquals("04:35", t(ZmanKind.ALOT_HASHACHAR))       // :47 — rounds to 04:36
        assertEquals("05:57", t(ZmanKind.HANETZ))
        assertEquals("08:40", t(ZmanKind.SOF_ZMAN_SHMA_MGA_72_ZMANIYOT))
        assertEquals("09:21", t(ZmanKind.SOF_ZMAN_SHMA_GRA))
        assertEquals("12:45", t(ZmanKind.CHATZOT))
        assertEquals("19:32", t(ZmanKind.SHKIA))
        assertEquals("19:47", t(ZmanKind.TZEIT_HAKOCHAVIM))     // :44 — rounds to 19:48
        assertEquals("20:44", t(ZmanKind.TZEIT_RABBEINU_TAM))
    }

    @Test
    fun `New York 15 January 2026 renders exactly these times`() {
        // A diaspora city in winter: different zone, different DST rules, and
        // a chatzot layla that lands after midnight.
        val loc = EngineLocation("New York", 40.7128, -74.0060, 0.0, "America/New_York")
        val date = LocalDate.of(2026, 1, 15)
        fun t(k: ZmanKind) = render(loc, date, newYork, k)

        assertEquals("00:05", t(ZmanKind.CHATZOT_LAYLA))
        assertEquals("06:20", t(ZmanKind.ALOT_HASHACHAR))
        assertEquals("07:17", t(ZmanKind.HANETZ))
        assertEquals("09:41", t(ZmanKind.SOF_ZMAN_SHMA_GRA))
        assertEquals("12:05", t(ZmanKind.CHATZOT))
        assertEquals("16:53", t(ZmanKind.SHKIA))
        assertEquals("17:24", t(ZmanKind.TZEIT_LECHUMRA))       // :41 — rounds to 17:25
    }
}
