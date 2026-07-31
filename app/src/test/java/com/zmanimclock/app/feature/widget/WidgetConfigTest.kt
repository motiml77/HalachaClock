package com.zmanimclock.app.feature.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget configuration invariant.
 *
 * Every section is independently switchable, which means the user can switch
 * them ALL off — and a widget rendering nothing is a blank rectangle they
 * cannot fix without deleting it and starting over. [WidgetPrefs.Config.isEmpty]
 * is what the config screen and the loader both key off to prevent that.
 */
class WidgetConfigTest {

    @Test
    fun `the default configuration shows something`() {
        assertFalse(WidgetPrefs.Config().isEmpty)
    }

    @Test
    fun `everything off is recognised as empty`() {
        val blank = WidgetPrefs.Config(
            showHebrewDate = false,
            showNextZman = false,
            showZmanim = false,
            showAlarms = false,
        )
        assertTrue(blank.isEmpty)
    }

    @Test
    fun `zmanim switched on but with nothing selected still counts as empty`() {
        // The subtle case: the section toggle is on, so a naive check would
        // pass, but there are no rows to draw.
        val config = WidgetPrefs.Config(
            showHebrewDate = false,
            showNextZman = false,
            showZmanim = true,
            zmanim = emptyList(),
            showAlarms = false,
        )
        assertTrue(config.isEmpty)
    }

    @Test
    fun `any single section on is enough`() {
        val base = WidgetPrefs.Config(
            showHebrewDate = false,
            showNextZman = false,
            showZmanim = false,
            zmanim = emptyList(),
            showAlarms = false,
        )
        assertFalse("date alone", base.copy(showHebrewDate = true).isEmpty)
        assertFalse("next zman alone", base.copy(showNextZman = true).isEmpty)
        assertFalse("alarms alone", base.copy(showAlarms = true).isEmpty)
        assertFalse(
            "zmanim alone",
            base.copy(showZmanim = true, zmanim = WidgetPrefs.DEFAULT_SELECTION).isEmpty,
        )
    }

    @Test
    fun `the default zman selection is within the row budget`() {
        // More defaults than rows would silently drop the tail.
        assertTrue(WidgetPrefs.DEFAULT_SELECTION.size <= WidgetPrefs.MAX_ZMANIM)
        assertTrue(WidgetPrefs.DEFAULT_SELECTION.isNotEmpty())
    }
}
