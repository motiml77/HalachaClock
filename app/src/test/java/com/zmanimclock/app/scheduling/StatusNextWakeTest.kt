package com.zmanimclock.app.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The status line has to come back on its own.
 *
 * Since Android 14 the user can swipe the persistent notification away, and
 * some OEM cleaners remove it without any delete intent. The receiver's chain
 * is the only thing that can undo that, so how long it is allowed to sleep IS
 * how long the line can stay gone.
 */
class StatusNextWakeTest {

    private val now = Instant.parse("2026-09-19T10:00:00Z")

    @Test
    fun `a zman minutes away wakes the chain 30 seconds after it passes`() {
        val zman = now.plus(Duration.ofMinutes(4))
        assertEquals(zman.plusSeconds(30), statusNextWake(zman, now))
    }

    @Test
    fun `a zman hours away does not let the chain sleep past the heal interval`() {
        val zman = now.plus(Duration.ofHours(5))
        assertEquals(now.plus(STATUS_HEAL_INTERVAL), statusNextWake(zman, now))
    }

    @Test
    fun `with no upcoming zman it retries within the heal interval, not the hour`() {
        assertEquals(now.plus(STATUS_HEAL_INTERVAL), statusNextWake(null, now))
    }

    @Test
    fun `it never sleeps longer than the heal interval whatever the zman`() {
        listOf(0L, 1L, 14L, 15L, 16L, 90L, 600L).forEach { minutes ->
            val wake = statusNextWake(now.plus(Duration.ofMinutes(minutes)), now)
            assertTrue(
                "zman in $minutes min must not push the wake past the heal interval",
                !wake.isAfter(now.plus(STATUS_HEAL_INTERVAL)),
            )
            assertTrue("and the wake is always in the future", wake.isAfter(now))
        }
    }

    @Test
    fun `the heal interval is short enough to notice and long enough to be cheap`() {
        assertTrue(STATUS_HEAL_INTERVAL <= Duration.ofMinutes(15))
        assertTrue(STATUS_HEAL_INTERVAL >= Duration.ofMinutes(5))
    }
}
