package com.zmanimclock.app.feature.alarm.presentation

/**
 * When אישור is allowed to close the ringing screen.
 *
 * Pulled out of the composable so the rule can be stated once and pinned by a
 * test. It is the only place that decides whether a dismiss challenge stands
 * between the user and silence, and getting it wrong in either direction is
 * bad: too loose and the challenge stops waking anyone, too strict and someone
 * is trapped behind a screen they cannot close.
 *
 * THE RULE: the challenge applies only WHILE THE ALARM IS MAKING NOISE.
 *
 * Its purpose is to stop a half-asleep hand from silencing an alarm that has
 * not actually woken anybody. Once the sound and vibration have run for the
 * full duration the user configured and stopped on their own, that purpose is
 * spent — the alarm did its whole job — and continuing to demand arithmetic
 * only risks stranding someone in front of a screen with no way out. That trap
 * did not exist while the screen closed itself with the ring; it appeared the
 * moment the screen started outliving it.
 */
internal fun canDismiss(
    /** Sound and vibration have stopped; only acknowledgement remains. */
    silenced: Boolean,
    /** The expected answer, or null when no challenge is configured. */
    expectedAnswer: Int?,
    /** What the user typed, if anything. */
    typedAnswer: String,
): Boolean = when {
    silenced -> true
    expectedAnswer == null -> true
    else -> typedAnswer.toIntOrNull() == expectedAnswer
}

/** Whether the challenge UI should be on screen at all. Mirrors [canDismiss]. */
internal fun challengeVisible(silenced: Boolean, hasChallenge: Boolean): Boolean =
    !silenced && hasChallenge
