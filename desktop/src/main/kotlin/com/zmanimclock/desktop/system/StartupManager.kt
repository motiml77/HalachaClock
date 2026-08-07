package com.zmanimclock.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * "עלה עם Windows" — the per-user Run key, and nothing else.
 *
 * The value written is:
 *
 *     HKCU\Software\Microsoft\Windows\CurrentVersion\Run
 *         ZmanimClock = "C:\...\HalachClock.exe" --tray
 *
 * HKCU, not HKLM. Per-user means no administrator rights are needed and it
 * lines up with the installer's `perUserInstall = true`; HKLM would demand
 * elevation to tick a checkbox. Turning the setting off DELETES the value
 * rather than writing some "enabled=false" flag — a leftover key that claims to
 * be disabled is exactly the residue people rightly resent.
 *
 * The `--tray` flag means a boot brings the app up hidden in the tray instead of
 * throwing a window at someone who is trying to log in.
 *
 * ⚠️ WHY THIS FILE DESERVES A WARNING, NOT JUST A DOC COMMENT
 * Writing a Run key is MITRE ATT&CK T1547.001 — the single most common malware
 * persistence technique there is. An UNSIGNED binary that registers itself to
 * start with the machine is a textbook heuristic match for adware, and Windows
 * 11's Smart App Control blocks unsigned binaries whether or not they came from
 * the internet; a self-signed certificate scores exactly the same as no
 * signature at all. Nothing in this file can fix that: the fix is a real code
 * signing certificate, or the MSIX/Store route, whose WinRT StartupTask API
 * replaces this registry write with a documented one that the user can see and
 * manage in Task Manager. See DESKTOP_PLAN 5.4.
 *
 * Every function here swallows its failures and returns false. Autostart is a
 * convenience; a locked-down registry, a policy, or an anti-virus product
 * blocking the write must not produce a stack trace in a zmanim clock.
 */
object StartupManager {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    /** ASCII and stable. Renaming this orphans every existing installation's value. */
    private const val VALUE_NAME = "ZmanimClock"

    /** Boot straight to the tray — no window in anyone's face at login. */
    const val TRAY_FLAG = "--tray"

    /**
     * True when the Run value exists and is not blank.
     *
     * Deliberately does NOT compare against the current executable path. After
     * an upgrade or a move the stored path can legitimately differ, and
     * reporting "off" for a value that is plainly there would make the checkbox
     * lie about the state of the machine. [setEnabled] rewrites the path
     * anyway, so a stale entry is repaired the next time it is touched.
     */
    fun isEnabled(): Boolean = read()?.isNotBlank() == true

    /**
     * Writes or removes the Run value. Returns true only if the registry now
     * matches what was asked for.
     *
     * Returns false when [executablePath] cannot be resolved — under Gradle
     * there is no launcher .exe to point at, and registering `java.exe` with a
     * classpath would be a value that breaks the moment the build directory is
     * cleaned. Better to refuse and let the UI say so.
     */
    fun setEnabled(on: Boolean): Boolean {
        if (!Platform.isWindows()) return false
        return if (on) enable() else disable()
    }

    /** The current Run value, or null if unset/unreadable. Useful for diagnostics. */
    fun read(): String? {
        if (!Platform.isWindows()) return null
        return runCatching {
            if (!Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
                null
            } else {
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
            }
        }.getOrNull()
    }

    /**
     * True when there is a real launcher to register — i.e. the app is
     * installed rather than being run from Gradle. The settings pane should
     * disable the checkbox and explain, instead of offering a toggle that
     * cannot work.
     */
    fun isSupported(): Boolean = Platform.isWindows() && executablePath() != null

    /**
     * The installed launcher .exe, or null when running from a development
     * build.
     *
     * Three sources, most trustworthy first:
     *
     *  1. `jpackage.app-path` — the system property jpackage's own launcher
     *     sets, pointing at itself. Exactly the answer wanted, when it exists.
     *  2. `ProcessHandle.current().info().command()` — the real process image.
     *     Under Gradle this is java.exe/javaw.exe, which is explicitly rejected:
     *     registering the JVM would produce a Run value that survives the build
     *     directory it depends on.
     *  3. The app-image layout, where the launcher sits one level above the
     *     bundled runtime: `<app>/HalachClock.exe` next to `<app>/runtime/`.
     */
    fun executablePath(): String? {
        fromProperty()?.let { return it }
        fromProcess()?.let { return it }
        return fromAppImage()
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun enable(): Boolean {
        val exe = executablePath() ?: return false
        // Quoted: Program Files has a space in it, and an unquoted path there is
        // the classic unquoted-service-path bug.
        val command = "\"$exe\" $TRAY_FLAG"
        return runCatching {
            if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, RUN_KEY)) {
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, RUN_KEY)
            }
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME, command,
            )
            read() == command
        }.getOrDefault(false)
    }

    private fun disable(): Boolean = runCatching {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
        }
        // Already absent counts as success: the user asked for "not starting
        // with Windows", and that is the state either way.
        !Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
    }.getOrDefault(false)

    private fun fromProperty(): String? =
        System.getProperty("jpackage.app-path")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isFile }
            ?.absolutePath

    private fun fromProcess(): String? = runCatching {
        ProcessHandle.current().info().command().orElse(null)
            ?.takeIf { !isJavaLauncher(it) }
            ?.let { File(it) }
            ?.takeIf { it.isFile }
            ?.absolutePath
    }.getOrNull()

    private fun fromAppImage(): String? = runCatching {
        // java.home is <app>/runtime in a jpackage image, so the launcher is its
        // sibling. Any other layout simply yields no match and autostart is
        // reported unsupported.
        val appDir = File(System.getProperty("java.home")).parentFile ?: return@runCatching null
        appDir.listFiles { f: File -> f.isFile && f.name.endsWith(".exe", ignoreCase = true) }
            ?.minByOrNull { it.name.length }
            ?.absolutePath
    }.getOrNull()

    private fun isJavaLauncher(path: String): Boolean {
        val name = File(path).name.lowercase()
        return name == "java.exe" || name == "javaw.exe" || name == "java" || name == "javaw"
    }
}
