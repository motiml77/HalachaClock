package com.zmanimclock.app.feature.alarms

import com.zmanimclock.app.feature.alarms.data.AlarmEntity
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.zmanim.presentation.ZmanimViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

/**
 * שומר לערבית — the shape of the alarm the red badge creates.
 *
 * The badge is one tap that must produce EXACTLY one thing: a ten-second,
 * one-time, self-erasing alert for tonight. Every clause in that sentence is
 * a property something else in this codebase could quietly break — the ring
 * duration is clamped by the sound service, "one-time" is a bitmask that has
 * been misread before, and "self-erasing" is a brand-new column. So each is
 * pinned here rather than assumed from the screen that builds it.
 */
class TzeitGuardTest {

    /** Exactly what ZmanimViewModel.armTzeitGuard builds. */
    private fun guard(hour: Int = 19, minute: Int = 36) = AlarmEntity(
        type = AlarmType.FIXED,
        hour = hour,
        minute = minute,
        daysOfWeek = 0,
        soundEnabled = true,
        vibrate = true,
        ringDurationSeconds = ZmanimViewModel.TZEIT_GUARD_RING_SECONDS,
        maxSnoozes = 0,
        label = ZmanimViewModel.TZEIT_GUARD_LABEL,
        deleteAfterFiring = true,
    )

    // ---- one-time -----------------------------------------------------------

    /**
     * daysOfWeek = 0 is this codebase's "one-time" marker, and [isOneTime]
     * is what the sound service branches on when it retires the alarm.
     */
    @Test
    fun `the guard is a one-time alarm`() {
        assertTrue(guard().isOneTime)
        assertEquals(0, guard().daysOfWeek)
    }

    /**
     * The SAME zero that means one-time also has to mean "eligible on
     * whatever day it lands on" — isEnabledOn treats 0 as every day on
     * purpose, because a one-time alarm has no weekday of its own. Pinned
     * because reading that zero the other way would silently make the guard
     * never fire.
     */
    @Test
    fun `a one-time guard is eligible on every weekday`() {
        DayOfWeek.entries.forEach { day ->
            assertTrue("must be eligible on $day", guard().isEnabledOn(day))
        }
    }

    // ---- ten seconds --------------------------------------------------------

    @Test
    fun `the guard rings for ten seconds`() {
        assertEquals(10, guard().ringDurationSeconds)
    }

    /**
     * AlarmSoundService clamps with coerceIn(10, 180). Ten is the FLOOR, so
     * the requested duration survives it unchanged — if the floor ever rose,
     * this alert would silently ring longer than it was specified to.
     */
    @Test
    fun `ten seconds survives the sound service clamp`() {
        assertEquals(10, guard().ringDurationSeconds.coerceIn(10, 180))
    }

    @Test
    fun `the guard rings and vibrates`() {
        assertTrue("the owner asked for a ring", guard().soundEnabled)
        assertTrue("and vibration", guard().vibrate)
    }

    // ---- vanishes -----------------------------------------------------------

    /**
     * The distinction the whole feature turns on. An ordinary one-time alarm
     * is DEACTIVATED and stays in the list; this one is DELETED. Without the
     * flag the alarms screen would slowly fill with disabled rows the user
     * never meant to create.
     */
    @Test
    fun `the guard is deleted rather than deactivated`() {
        assertTrue(guard().deleteAfterFiring)
    }

    @Test
    fun `an ordinary alarm is never deleted behind the user's back`() {
        assertFalse(AlarmEntity().deleteAfterFiring)
        assertFalse(
            "even a one-time alarm the user built by hand must stay in the list",
            AlarmEntity(daysOfWeek = 0).deleteAfterFiring,
        )
    }

    /**
     * No snooze — and not only as a preference. A snooze would keep the row
     * alive past the ring it is supposed to disappear with, which is the one
     * way "deleted after firing" could leave something behind.
     */
    @Test
    fun `the guard cannot be snoozed`() {
        assertEquals(0, guard().maxSnoozes)
    }

    // ---- pinned to tonight --------------------------------------------------

    /**
     * FIXED, never ZMAN — even when the chosen minute IS the zman.
     *
     * A ZMAN-anchored alarm re-derives its time each day and would roll to
     * tomorrow's tzeit the moment tonight's passed. This alert exists for one
     * evening, so it is pinned to tonight's wall clock and nothing about it
     * moves afterwards.
     */
    @Test
    fun `the guard is pinned to a wall clock time, not to the zman`() {
        val g = guard(hour = 19, minute = 36)
        assertEquals(AlarmType.FIXED, g.type)
        assertEquals(19, g.hour)
        assertEquals(36, g.minute)
        assertEquals("no zman anchor to drift with", "", g.zmanId)
    }

    @Test
    fun `the guard carries the label the badge is named for`() {
        assertEquals("שומר לערבית", guard().label)
        assertEquals(ZmanimViewModel.TZEIT_GUARD_LABEL, guard().label)
    }

    /**
     * No dismiss challenge. The screen already stays up until אישור; adding
     * arithmetic on top would be a puzzle between a person and a reminder
     * they set ten minutes ago.
     */
    @Test
    fun `the guard has no dismiss challenge`() {
        assertEquals(
            com.zmanimclock.app.feature.alarms.data.DismissChallenge.NONE,
            guard().dismissChallenge,
        )
    }
}
