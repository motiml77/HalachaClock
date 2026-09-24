package com.zmanimclock.app.feature.zmanim.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The hero's "בעוד …" figure, shared by the phone and the desktop. */
class CountdownTest {

    private val now = Instant.parse("2026-09-25T14:00:00Z")

    @Test
    fun `hours minutes and seconds when an hour or more is left`() {
        assertEquals("1:24:36", countdownText(now, now.plusSeconds(1 * 3600 + 24 * 60 + 36)))
    }

    @Test
    fun `no hours field under an hour`() {
        assertEquals("6:32", countdownText(now, now.plusSeconds(6 * 60 + 32)))
        assertEquals("0:09", countdownText(now, now.plusSeconds(9)))
    }

    @Test
    fun `a reached or passed target reads zero, never negative`() {
        assertEquals("0:00", countdownText(now, now))
        assertEquals("0:00", countdownText(now, now.minusSeconds(30)))
    }
}
