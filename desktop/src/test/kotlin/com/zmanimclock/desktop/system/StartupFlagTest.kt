package com.zmanimclock.desktop.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which launches count as "Windows did this at logon".
 *
 * Main.kt asks exactly one question of the command line and branches the
 * whole startup experience on the answer: true folds the app to its saved
 * screen edge, false opens the window. Getting it wrong in either direction
 * is a bug the owner sees on every single boot — a window covering the
 * desktop at logon, or a shortcut that opens nothing when double-clicked —
 * so the rule is pinned here rather than left as an inline `args.contains`.
 */
class StartupFlagTest {

    @Test
    fun `a plain launch is not a logon`() {
        assertFalse(StartupManager.launchedAtLogon(emptyArray()))
        assertFalse(StartupManager.launchedAtLogon(arrayOf("--demo-reminder")))
    }

    @Test
    fun `the flag the Run value carries is recognised`() {
        assertTrue(StartupManager.launchedAtLogon(arrayOf(StartupManager.STARTUP_FLAG)))
    }

    @Test
    fun `the old flag still counts, because it is in real registries today`() {
        // Every Run value written by a shipped build says --tray. The
        // re-assert on launch upgrades it, but the first boot after an update
        // can easily happen before the app has ever been run manually, and
        // that boot must fold like any other rather than fall through to the
        // manual-launch path and throw a window up at logon.
        assertTrue(StartupManager.launchedAtLogon(arrayOf(StartupManager.LEGACY_TRAY_FLAG)))
        assertTrue(StartupManager.launchedAtLogon(arrayOf("--tray")))
    }

    @Test
    fun `case does not matter, and neither do other arguments`() {
        // The Run value is written by this app, so the case is known — but a
        // user editing the registry by hand, or a shortcut someone copied, is
        // not worth failing over.
        assertTrue(StartupManager.launchedAtLogon(arrayOf("--STARTUP")))
        assertTrue(StartupManager.launchedAtLogon(arrayOf("--Tray")))
        assertTrue(StartupManager.launchedAtLogon(arrayOf("--demo-reminder", "--startup")))
    }

    @Test
    fun `a near miss is not a logon`() {
        // Substring matching would make these true. Equality is deliberate.
        assertFalse(StartupManager.launchedAtLogon(arrayOf("--startup-delay")))
        assertFalse(StartupManager.launchedAtLogon(arrayOf("startup")))
        assertFalse(StartupManager.launchedAtLogon(arrayOf("--traymenu")))
    }

    @Test
    fun `the value name is still the one existing installs have on disk`() {
        // Not cosmetic. Renaming this orphans every installed copy's autostart
        // — the old value stays in the registry forever, pointing at whatever
        // path it was written with, and the app can neither see nor remove it.
        // The FLAG inside the value was renamed in this change; the value NAME
        // must not be, and this fails loudly if anyone tries.
        val field = StartupManager::class.java.getDeclaredField("VALUE_NAME")
        field.isAccessible = true
        assertEquals("ZmanimClock", field.get(null))
    }
}
