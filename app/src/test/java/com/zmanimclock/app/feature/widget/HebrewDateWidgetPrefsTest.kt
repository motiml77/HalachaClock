package com.zmanimclock.app.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewDateWidgetPrefsTest {

    @Test
    fun `the default configuration is fully opaque navy`() {
        val config = HebrewDateWidgetPrefs.Config()
        assertEquals(WidgetColorPreset.NAVY, config.preset)
        assertEquals(100, config.opacityPercent)
    }

    @Test
    fun `every preset key is unique`() {
        val keys = WidgetColorPreset.entries.map { it.key }
        assertEquals("no two presets share a persisted key", keys.size, keys.toSet().size)
    }

    @Test
    fun `every preset is fully opaque on its own — opacity is a separate knob`() {
        // 0xFF______: alpha byte set, so a preset's own color never silently
        // fades the widget — only Config.opacityPercent does that.
        WidgetColorPreset.entries.forEach { preset ->
            assertEquals(
                "${preset.key} must carry alpha FF",
                0xFF,
                (preset.color ushr 24) and 0xFF,
            )
        }
    }

    @Test
    fun `an unknown or missing key falls back to the default preset`() {
        assertEquals(WidgetColorPreset.DEFAULT, WidgetColorPreset.fromKey(null))
        assertEquals(WidgetColorPreset.DEFAULT, WidgetColorPreset.fromKey("not-a-real-key"))
    }

    @Test
    fun `every preset round-trips through its own key`() {
        WidgetColorPreset.entries.forEach { preset ->
            assertEquals(preset, WidgetColorPreset.fromKey(preset.key))
        }
    }

    @Test
    fun `at least the default preset is dark enough for white text`() {
        // A loose luminance guard, not a full WCAG check: catches the class of
        // mistake — someone adding a pale preset — that would leave the
        // widget's fixed-white text unreadable. See WidgetColorPreset's doc.
        WidgetColorPreset.entries.forEach { preset ->
            val r = (preset.color ushr 16) and 0xFF
            val g = (preset.color ushr 8) and 0xFF
            val b = preset.color and 0xFF
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            assertTrue("${preset.key} is too light for fixed white text ($luminance)", luminance < 140)
        }
    }
}
