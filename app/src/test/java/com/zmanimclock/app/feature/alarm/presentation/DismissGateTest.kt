package com.zmanimclock.app.feature.alarm.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dismiss challenge applies while the alarm is making noise, and only then.
 *
 * Both directions matter. Too loose and the challenge stops waking anyone; too
 * strict and a user who cannot solve it is stranded in front of a screen that
 * will not close — a trap that did not exist while the screen closed itself
 * along with the ring, and that appeared the moment it started outliving it.
 */
class DismissGateTest {

    private val answer = 71 // 23 + 48

    // ------------------------------------------------ while still ringing

    @Test
    fun `a wrong answer does not dismiss while ringing`() {
        assertFalse(canDismiss(silenced = false, expectedAnswer = answer, typedAnswer = "70"))
    }

    @Test
    fun `an empty answer does not dismiss while ringing`() {
        assertFalse(canDismiss(silenced = false, expectedAnswer = answer, typedAnswer = ""))
    }

    @Test
    fun `non-numeric input does not dismiss and does not crash`() {
        assertFalse(canDismiss(silenced = false, expectedAnswer = answer, typedAnswer = "abc"))
        assertFalse(canDismiss(silenced = false, expectedAnswer = answer, typedAnswer = "-"))
        assertFalse(
            canDismiss(silenced = false, expectedAnswer = answer, typedAnswer = "99999999999999"),
        )
    }

    @Test
    fun `the right answer dismisses while ringing`() {
        assertTrue(canDismiss(silenced = false, expectedAnswer = answer, typedAnswer = "71"))
    }

    @Test
    fun `with no challenge configured, dismissing is always allowed`() {
        assertTrue(canDismiss(silenced = false, expectedAnswer = null, typedAnswer = ""))
    }

    // ------------------------------------------- after the ring has ended

    @Test
    fun `once silenced, אישור works without solving anything`() {
        // The user's rule: "the solution is needed only inside the configured
        // time". The alarm rang for its whole duration and stopped on its own,
        // so the challenge has nothing left to protect.
        assertTrue(canDismiss(silenced = true, expectedAnswer = answer, typedAnswer = ""))
        assertTrue(canDismiss(silenced = true, expectedAnswer = answer, typedAnswer = "wrong"))
        assertTrue(canDismiss(silenced = true, expectedAnswer = answer, typedAnswer = "70"))
    }

    // ------------------------------------------------------ the UI mirrors it

    @Test
    fun `the challenge is only on screen when it is actually enforced`() {
        assertTrue(challengeVisible(silenced = false, hasChallenge = true))
        assertFalse("no point showing a problem that is not gating anything",
            challengeVisible(silenced = true, hasChallenge = true))
        assertFalse(challengeVisible(silenced = false, hasChallenge = false))
        assertFalse(challengeVisible(silenced = true, hasChallenge = false))
    }
}
