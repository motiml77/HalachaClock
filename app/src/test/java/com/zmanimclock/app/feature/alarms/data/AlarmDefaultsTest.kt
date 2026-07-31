package com.zmanimclock.app.feature.alarms.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Defaults a newly created alarm gets.
 *
 * These are the settings a user never touches, so they are the ones that
 * actually decide whether the alarm wakes them. Each is pinned deliberately.
 */
class AlarmDefaultsTest {

    private val fresh = AlarmEntity()

    @Test
    fun `a new alarm rings at a constant volume, not a fade`() {
        // The ramp used to be unconditional, which made the first seconds —
        // exactly the ones a deep sleeper needs — the quietest. Constant is
        // now the default and the climb is opt-in.
        assertFalse(fresh.gradualVolume)
    }

    @Test
    fun `a new alarm is at plain 100 percent with no boost`() {
        // 100 = the device maximum. Anything above it amplifies the signal,
        // and that must never happen without the user asking for it.
        assertEquals(100, fresh.volumePercent)
    }

    @Test
    fun `a new alarm does not snooze`() {
        // 0 = no snooze. Guarded because a non-zero default here previously
        // let an unattended alarm loop instead of stopping.
        assertEquals(0, fresh.maxSnoozes)
    }

    @Test
    fun `a new alarm has sound and vibration on`() {
        assert(fresh.soundEnabled)
        assert(fresh.vibrate)
    }

    @Test
    fun `a new alarm has no dismissal challenge`() {
        assertEquals(DismissChallenge.NONE, fresh.dismissChallenge)
    }
}
