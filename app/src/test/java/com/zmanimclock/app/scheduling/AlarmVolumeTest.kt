package com.zmanimclock.app.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The above-100% boost, pinned.
 *
 * This is an alarm: a factor-of-ten error in the gain is either completely
 * inaudible (the user oversleeps) or painfully loud. Both directions are
 * bad enough to be worth a test that fails loudly if the mapping is ever
 * "tidied up".
 */
class AlarmVolumeTest {

    @Test
    fun `the ordinary range never asks for a boost`() {
        for (percent in AlarmVolume.MIN_PERCENT..AlarmVolume.NORMAL_MAX_PERCENT) {
            assertFalse("$percent% must not boost", AlarmVolume.needsBoost(percent))
            assertEquals("$percent% must be 0 gain", 0, AlarmVolume.boostMillibels(percent))
        }
    }

    @Test
    fun `100 percent is exactly the device maximum with no amplification`() {
        // The default for a new alarm. If this ever starts boosting, every
        // existing alarm gets louder without the user asking.
        assertFalse(AlarmVolume.needsBoost(100))
        assertEquals(0, AlarmVolume.boostMillibels(100))
        assertEquals(100, AlarmVolume.streamPercent(100))
    }

    @Test
    fun `120 percent is plus 6 dB — not 60 and not 0 point 6`() {
        // 600 millibels. The unit is millibels (100 mB = 1 dB), which is the
        // easiest thing in this file to get wrong by 10x in either direction.
        assertEquals(600, AlarmVolume.boostMillibels(120))
        assertTrue(AlarmVolume.needsBoost(120))
    }

    @Test
    fun `the gain climbs smoothly between 100 and 120`() {
        assertEquals(0, AlarmVolume.boostMillibels(100))
        assertEquals(150, AlarmVolume.boostMillibels(105))
        assertEquals(300, AlarmVolume.boostMillibels(110))
        assertEquals(450, AlarmVolume.boostMillibels(115))
        assertEquals(600, AlarmVolume.boostMillibels(120))
        // strictly increasing, no plateaus or jumps inside the range
        var previous = -1
        for (percent in 100..120) {
            val gain = AlarmVolume.boostMillibels(percent)
            assertTrue("gain went backwards at $percent%", gain >= previous)
            previous = gain
        }
    }

    @Test
    fun `out-of-range input is clamped rather than trusted`() {
        // A corrupt row or a future UI change must never reach the amplifier
        // with an absurd value.
        assertEquals(0, AlarmVolume.boostMillibels(0))
        assertEquals(0, AlarmVolume.boostMillibels(-500))
        assertEquals(
            AlarmVolume.MAX_BOOST_MILLIBELS,
            AlarmVolume.boostMillibels(1_000),
        )
        assertEquals(AlarmVolume.MIN_PERCENT, AlarmVolume.streamPercent(-3))
        assertEquals(AlarmVolume.NORMAL_MAX_PERCENT, AlarmVolume.streamPercent(9_999))
    }

    @Test
    fun `the stream is driven to its maximum for every boosted level`() {
        // Above 100 the extra loudness comes from the amplifier, so the stream
        // must already be maxed — otherwise the boost is just making up for
        // headroom we failed to use.
        for (percent in 101..AlarmVolume.BOOSTED_MAX_PERCENT) {
            assertEquals(
                "stream must be maxed at $percent%",
                AlarmVolume.NORMAL_MAX_PERCENT,
                AlarmVolume.streamPercent(percent),
            )
        }
    }

    // ---- the optional gentle climb ----

    @Test
    fun `the climb always finishes before the alarm stops ringing`() {
        // THE BUG THIS PINS: the step interval used to be a fixed 2.5s, so the
        // climb took 20s — while the ring-duration slider starts at 10s. A
        // gradual alarm set to 10 or 15 seconds was auto-silenced while still
        // at roughly half volume, having never once reached the loudness the
        // user actually chose. Someone could sleep straight through an alarm
        // that was, by the numbers, ~12 dB quieter than what the UI promised.
        for (ring in AlarmVolume.MIN_RING_SECONDS..AlarmVolume.MAX_RING_SECONDS) {
            val total = AlarmVolume.rampTotalMs(ring)
            val ringMs = ring * 1000L
            assertTrue(
                "ring ${ring}s: climb takes ${total}ms, alarm stops at ${ringMs}ms",
                total < ringMs,
            )
        }
    }

    @Test
    fun `the shortest possible ring still reaches full volume in time`() {
        val ring = AlarmVolume.MIN_RING_SECONDS
        assertTrue(AlarmVolume.rampTotalMs(ring) < ring * 1000L)
        // …and does it without degenerating into an instant jump
        assertTrue(AlarmVolume.rampIntervalMs(ring) >= AlarmVolume.MIN_RAMP_STEP_MS)
    }

    @Test
    fun `a long ring keeps the original gentle pace`() {
        // No reason to rush a 3-minute alarm; it should still feel gradual.
        assertEquals(
            AlarmVolume.MAX_RAMP_STEP_MS,
            AlarmVolume.rampIntervalMs(AlarmVolume.MAX_RING_SECONDS),
        )
    }

    @Test
    fun `the climb actually starts quiet and ends at full`() {
        assertTrue(AlarmVolume.RAMP_START_VOLUME > 0f)
        assertTrue(AlarmVolume.RAMP_START_VOLUME < 1f)
        // stepping rampSteps() times from the start must land at or above 1.0
        val reached = AlarmVolume.RAMP_START_VOLUME +
            AlarmVolume.VOLUME_STEP * AlarmVolume.rampSteps()
        assertTrue("climb ends at $reached, short of full volume", reached >= 1f)
    }

    @Test
    fun `an out-of-range ring duration is clamped rather than dividing by zero`() {
        assertTrue(AlarmVolume.rampIntervalMs(0) > 0)
        assertTrue(AlarmVolume.rampIntervalMs(-10) > 0)
        assertTrue(AlarmVolume.rampIntervalMs(100_000) > 0)
    }
}
