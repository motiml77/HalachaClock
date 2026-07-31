package com.zmanimclock.app.scheduling

/**
 * The alarm loudness scale, and the one piece of arithmetic behind it.
 *
 * 10..100 is the ordinary range: it sets the system ALARM stream, where 100
 * means the hardware maximum. Above 100 the stream has nothing left to give,
 * so the extra loudness is real amplification of the signal, applied by a
 * [android.media.audiofx.LoudnessEnhancer] in AlarmSoundService.
 *
 * Kept out of the Service so it can be unit-tested on the JVM — getting this
 * wrong by a factor of ten is either inaudible or painful.
 */
object AlarmVolume {

    const val MIN_PERCENT = 10
    /** The device maximum. Everything above this is amplification. */
    const val NORMAL_MAX_PERCENT = 100
    const val BOOSTED_MAX_PERCENT = 120

    /**
     * Millibels of gain per percent above 100 (100 mB = 1 dB), so 120% lands
     * on +6 dB.
     *
     * A literal reading of "120%" would be 20% more amplitude, i.e.
     * 20·log10(1.2) = 1.58 dB — inaudible through a pillow, which would make
     * the setting feel broken. +6 dB is a clear step up and still inside what
     * the effect compresses cleanly rather than clipping.
     */
    const val MILLIBELS_PER_PERCENT_OVER_100 = 30
    const val MAX_BOOST_MILLIBELS = 600

    /** Gain to apply for [volumePercent]; 0 means "no boost needed". */
    fun boostMillibels(volumePercent: Int): Int =
        ((volumePercent - NORMAL_MAX_PERCENT) * MILLIBELS_PER_PERCENT_OVER_100)
            .coerceIn(0, MAX_BOOST_MILLIBELS)

    /** True when this alarm needs the amplifier at all. */
    fun needsBoost(volumePercent: Int): Boolean = volumePercent > NORMAL_MAX_PERCENT

    /** The level to drive the system ALARM stream to, as a percentage. */
    fun streamPercent(volumePercent: Int): Int =
        volumePercent.coerceIn(MIN_PERCENT, NORMAL_MAX_PERCENT)

    // ---- optional gentle climb ----

    /** Where a gradual ring starts, as a fraction of the target. */
    const val RAMP_START_VOLUME = 0.2f
    const val VOLUME_STEP = 0.1f
    /** The original gentle pace, and the slowest one still allowed. */
    const val MAX_RAMP_STEP_MS = 2_500L
    const val MIN_RAMP_STEP_MS = 200L
    /** The climb must be finished this far into the ring, at the latest. */
    const val RAMP_FRACTION_OF_RING = 0.6

    const val MIN_RING_SECONDS = 10
    const val MAX_RING_SECONDS = 180

    /** Number of steps from [RAMP_START_VOLUME] up to full. */
    fun rampSteps(): Int =
        Math.ceil(((1f - RAMP_START_VOLUME) / VOLUME_STEP).toDouble()).toInt().coerceAtLeast(1)

    /**
     * Step interval for an alarm whose ring lasts [ringDurationSeconds].
     *
     * At the old FIXED 2.5 s the climb took 20 s, but the ring-duration
     * slider starts at 10 s — so a short gradual alarm was auto-silenced
     * while still at half volume, having never reached the loudness the user
     * chose. Pacing the climb to the ring makes that impossible.
     */
    fun rampIntervalMs(ringDurationSeconds: Int): Long {
        val ring = ringDurationSeconds.coerceIn(MIN_RING_SECONDS, MAX_RING_SECONDS)
        val windowMs = (ring * 1000L * RAMP_FRACTION_OF_RING).toLong()
        return (windowMs / rampSteps()).coerceIn(MIN_RAMP_STEP_MS, MAX_RAMP_STEP_MS)
    }

    /** How long the whole climb takes for that ring duration. */
    fun rampTotalMs(ringDurationSeconds: Int): Long =
        rampIntervalMs(ringDurationSeconds) * rampSteps()
}
