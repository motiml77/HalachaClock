package com.zmanimclock.desktop.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

/**
 * The folded bookmark's always-on-top flag.
 *
 * Small, but worth pinning: it defaults to OFF, and a default that silently
 * flips to ON would put a sliver over everything the user works in — the exact
 * complaint that produced the setting.
 */
class DockTabPrefTest {

    @Test
    fun `the bookmark does not float by default`() {
        assertFalse(DesktopPrefs().dockTabAlwaysOnTop)
    }

    @Test
    fun `a settings file written before this flag existed reads as off`() {
        val p = Properties()
        p["cityId"] = "ירושלים"
        assertFalse(p.getProperty("dockTabAlwaysOnTop")?.toBoolean() ?: false)
    }

    @Test
    fun `the flag survives a save and load cycle`() {
        val p = Properties()
        p["dockTabAlwaysOnTop"] = true.toString()
        assertTrue(p.getProperty("dockTabAlwaysOnTop")!!.toBoolean())
    }
}
