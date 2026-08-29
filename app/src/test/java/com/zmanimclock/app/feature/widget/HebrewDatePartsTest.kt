package com.zmanimclock.app.feature.widget

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [hebrewDateParts] is what the date-only widget draws, one line per field —
 * pinned against a real, previously-verified date rather than a hand-derived
 * one: 27.8.2026 in Jerusalem rendered as "י״ד אלול תשפ״ו" from this exact
 * formatter config in a live run this session, and 27.8.2026 is a Thursday.
 */
class HebrewDatePartsTest {

    private val formatter = HebrewDateFormatter().apply {
        isHebrewFormat = true
        isUseGershGershayim = true
    }
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun `breaks a verified date into its four lines`() {
        val parts = hebrewDateParts(LocalDate.of(2026, 8, 27), zone, formatter)

        assertEquals("יום חמישי", parts.weekday)
        assertEquals("י״ד", parts.day)
        assertEquals("אלול", parts.month)
        assertEquals("תשפ״ו", parts.year)
    }

    @Test
    fun `a single-digit day still carries its own gershayim`() {
        // Rosh Chodesh Elul: 1 Elul 5786 is 14.8.2026.
        val parts = hebrewDateParts(LocalDate.of(2026, 8, 14), zone, formatter)

        assertEquals("א׳", parts.day)
        assertEquals("אלול", parts.month)
    }

    @Test
    fun `every field is non-blank for an ordinary date`() {
        val parts = hebrewDateParts(LocalDate.of(2026, 1, 1), zone, formatter)

        assertEquals(false, parts.weekday.isBlank())
        assertEquals(false, parts.day.isBlank())
        assertEquals(false, parts.month.isBlank())
        assertEquals(false, parts.year.isBlank())
    }
}
