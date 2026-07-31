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
}
