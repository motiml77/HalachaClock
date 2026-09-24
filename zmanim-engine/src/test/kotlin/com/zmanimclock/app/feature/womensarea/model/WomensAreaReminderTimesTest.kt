package com.zmanimclock.app.feature.womensarea.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class WomensAreaReminderTimesTest {

    @Test
    fun `round-trips sorted and de-duplicated`() {
        val times = listOf(LocalTime.of(18, 30), LocalTime.of(7, 5), LocalTime.of(18, 30))
        val stored = WomensAreaReminderTimes.encode(times)
        assertEquals("07:05,18:30", stored)
        assertEquals(listOf(LocalTime.of(7, 5), LocalTime.of(18, 30)), WomensAreaReminderTimes.decode(stored))
    }

    @Test
    fun `nothing stored yet means the default, bad entries are dropped`() {
        assertEquals(WomensAreaReminderTimes.DEFAULT, WomensAreaReminderTimes.decode(null))
        assertEquals(listOf(LocalTime.of(9, 0)), WomensAreaReminderTimes.decode("9:00,25:00,xx,"))
    }

    @Test
    fun `at most MAX times`() {
        val many = (6..12).map { LocalTime.of(it, 0) }
        assertEquals(WomensAreaReminderTimes.MAX, WomensAreaReminderTimes.decode(WomensAreaReminderTimes.encode(many)).size)
    }
}
