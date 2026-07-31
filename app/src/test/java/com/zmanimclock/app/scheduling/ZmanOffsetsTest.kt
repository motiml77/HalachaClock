package com.zmanimclock.app.scheduling

import com.zmanimclock.app.feature.settings.data.UserPreferences
import com.zmanimclock.app.feature.zmanim.engine.MaranZmanimEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The two offsets that MOVE a zman rather than merely display it.
 *
 * An audit found several scheduler paths that read the user's preferences,
 * used them for the location, and then called through WITHOUT the offsets —
 * so the defaults silently replaced the user's choice and the alarm was armed
 * at the wrong minute, with nothing logged. The structural fix is that the
 * scheduler parameters no longer have defaults, so the compiler catches an
 * omission. These tests guard the value mapping itself.
 */
class ZmanOffsetsTest {

    @Test
    fun `offsets come from the user's preferences, not the engine defaults`() {
        val prefs = UserPreferences(candleLightingMinutes = 40, tzeitShabbatMinutes = 72)
        val offsets = ZmanOffsets.from(prefs)
        assertEquals(40L, offsets.candleLightingMinutes)
        assertEquals(72L, offsets.tzeitShabbatMinutes)
    }

    @Test
    fun `the Jerusalem and Rabbeinu Tam choices differ from the defaults`() {
        // If these ever coincided, the bug this guards against would be
        // invisible in testing while still being wrong in the field.
        val prefs = UserPreferences(candleLightingMinutes = 40, tzeitShabbatMinutes = 72)
        val offsets = ZmanOffsets.from(prefs)
        assertNotEquals(ZmanOffsets.DEFAULT.candleLightingMinutes, offsets.candleLightingMinutes)
        assertNotEquals(ZmanOffsets.DEFAULT.tzeitShabbatMinutes, offsets.tzeitShabbatMinutes)
    }

    @Test
    fun `the defaults match the engine's own constants`() {
        assertEquals(
            MaranZmanimEngine.DEFAULT_CANDLE_OFFSET_MINUTES,
            ZmanOffsets.DEFAULT.candleLightingMinutes,
        )
        assertEquals(
            MaranZmanimEngine.TZEIT_SHABBAT_FIXED_MINUTES,
            ZmanOffsets.DEFAULT.tzeitShabbatMinutes,
        )
    }

    @Test
    fun `a default-constructed preferences object maps to the engine defaults`() {
        // The app's shipped defaults and the engine's must not drift apart.
        assertEquals(ZmanOffsets.DEFAULT, ZmanOffsets.from(UserPreferences()))
    }

    @Test
    fun `every offered tzeit Shabbat option survives the mapping`() {
        MaranZmanimEngine.TZEIT_SHABBAT_OPTIONS.forEach { (minutes, label) ->
            val offsets = ZmanOffsets.from(
                UserPreferences(tzeitShabbatMinutes = minutes.toInt())
            )
            assertEquals("option '$label' lost its value", minutes, offsets.tzeitShabbatMinutes)
        }
    }
}
