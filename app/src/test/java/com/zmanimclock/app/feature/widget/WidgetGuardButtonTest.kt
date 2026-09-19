package com.zmanimclock.app.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The שומר לערבית button on the home-screen widget.
 *
 * It is a control on the widget, not a thing the widget shows — and that
 * distinction is what keeps two older guarantees intact: a widget switched down
 * to nothing must still be recognised as empty (so it falls back to the date
 * instead of rendering a blank rectangle), and a widget placed before the
 * button existed must get it without its owner reconfiguring anything.
 */
class WidgetGuardButtonTest {

    @Test
    fun `the button is on by default`() {
        assertTrue(WidgetPrefs.Config().showTzeitGuard)
    }

    @Test
    fun `the button alone does not make a widget non-empty`() {
        val onlyTheButton = WidgetPrefs.Config(
            showHebrewDate = false,
            showNextZman = false,
            showZmanim = false,
            showAlarms = false,
            showTzeitGuard = true,
        )
        assertTrue(
            "a widget with nothing but the button would be a blank rectangle",
            onlyTheButton.isEmpty,
        )
    }

    @Test
    fun `switching the button off does not empty a widget that shows something`() {
        assertFalse(WidgetPrefs.Config(showTzeitGuard = false).isEmpty)
    }

    @Test
    fun `unarmed the button is just the name`() {
        assertEquals("שומר לערבית", guardButtonLabel(null))
    }

    @Test
    fun `armed the button says when`() {
        assertEquals("שומר לערבית · דרוך ל-19:32", guardButtonLabel("19:32"))
    }
}
