package com.zmanimclock.desktop.widget

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import java.awt.Window
import javax.swing.Timer

/**
 * The Win32 layer of the desktop widget — and the ONLY file in this build that
 * knows Windows exists.
 *
 * Everything the widget needs from the operating system is four calls, isolated
 * here on purpose (DESKTOP_PLAN 4.2): when a Windows feature update moves
 * something, one file changes and the zmanim engine is never touched.
 *
 * EVERY function returns a boolean and never throws. A widget that cannot style
 * itself must degrade to an ordinary little window, not take the app down with
 * it, so each JNA call is wrapped and a failure is reported rather than raised.
 *
 * ── Why a polling timer and not WNDPROC subclassing ──────────────────────────
 * The "correct" way to hold a window at the bottom of the z-order is to handle
 * WM_WINDOWPOSCHANGED, which means installing a window procedure via
 * SetWindowLongPtr(GWLP_WNDPROC) with a JNA Callback. That buys three problems:
 * the Callback must be strongly referenced for exactly as long as Windows might
 * invoke it or the GC collects it out from under native code; the calls arrive
 * on the AWT event thread that owns the window, so thread affinity has to be
 * respected; and the previous procedure has to be chained correctly through
 * CallWindowProc. Getting any of those wrong CRASHES THE JVM — it does not
 * degrade, it does not throw, it dies. A one-and-a-half-second poll that calls
 * SetWindowPos is the safe default: if it is late, the widget is briefly above
 * a window it should be under, which is a cosmetic fault and self-correcting.
 *
 * ── What is deliberately NOT done ────────────────────────────────────────────
 * No SetParent to WorkerW. That recipe depends on the shell's window hierarchy
 * and is already broken on current Windows: measured on this machine
 * (Windows 11 25H2, build 26200), SHELLDLL_DefView is a direct child of Progman
 * and not one of the 18 top-level WorkerW windows hosts it. Extended styles
 * plus HWND_BOTTOM need none of that hierarchy.
 *
 * ── Known, accepted limitation ───────────────────────────────────────────────
 * Win+D (show desktop) WILL hide a pinned widget, and there is no supported API
 * to opt out. That is why the poll below only ever re-asserts z-order and never
 * forces the window visible: the failure mode stays "hidden until you click the
 * desktop again" instead of a widget that fights the user's own keystroke.
 */
internal object WindowPinning {

    // ── Win32 constants ─────────────────────────────────────────────────────
    // Spelled out rather than imported from WinUser so this file states its own
    // contract and cannot drift with a JNA version bump.
    private const val GWL_EXSTYLE = -20

    /** No taskbar button and no Alt+Tab entry. A widget owns neither. */
    private const val WS_EX_TOOLWINDOW = 0x0000_0080

    /** Never take focus on click. A widget must not interrupt someone typing. */
    private const val WS_EX_NOACTIVATE = 0x0800_0000

    /** Always above non-topmost windows. Correct for the widget, never for the main window. */
    private const val WS_EX_TOPMOST = 0x0000_0008

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_NOOWNERZORDER = 0x0200

    private const val SW_HIDE = 0

    /** Show without activating — the whole point of a widget. */
    private const val SW_SHOWNA = 8

    /** (HWND)1 — the bottom of the z-order. */
    private val HWND_BOTTOM: HWND = HWND(Pointer.createConstant(1L))

    /** (HWND)-2 — above nothing in particular; clears the topmost band. */
    private val HWND_NOTOPMOST: HWND = HWND(Pointer.createConstant(-2L))

    /** Slow enough to be free, fast enough that a stray raise is not noticed. */
    private const val POLL_MILLIS = 1_500

    /**
     * Why the last call failed, for an honest message in the settings pane.
     * Null once something succeeds.
     */
    @Volatile
    var lastFailure: String? = null
        private set

    private var timer: Timer? = null

    // ── Public surface ──────────────────────────────────────────────────────

    /**
     * Applies the extended styles that make an ordinary Compose window read as a
     * widget. Applied in BOTH modes: floating and pinned differ only in
     * z-order, never in whether they own a taskbar button or steal focus.
     *
     * [noActivate] is separable on purpose. If WS_EX_NOACTIVATE turns out to
     * fight Compose's own event loop it can be dropped in floating mode and the
     * cost is only that clicking the widget steals focus. In pinned mode it is
     * load-bearing, so there the caller falls back to floating instead.
     *
     * Idempotent: if the styles are already set this returns true without
     * touching the window, so switching modes at runtime causes no flicker.
     */
    fun applyWidgetStyles(window: Window, noActivate: Boolean): Boolean {
        val hwnd = hwndOf(window) ?: return false
        val wanted = WS_EX_TOOLWINDOW or (if (noActivate) WS_EX_NOACTIVATE else 0)
        return guard("apply window styles") {
            val user32 = User32.INSTANCE
            val current = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
            if (current and wanted == wanted) return@guard true

            // WS_EX_TOOLWINDOW is only consulted when the taskbar button is
            // created, i.e. when the window is first shown. Setting it on an
            // already-visible window leaves the button behind, so the window is
            // hidden and re-shown around the change. This is done through
            // ShowWindow rather than Window.setVisible so AWT's own visibility
            // bookkeeping — and Compose's state that mirrors it — never sees a
            // transition it did not ask for. SW_SHOWNA restores it without
            // activating, which is the point of the exercise.
            val wasVisible = user32.IsWindowVisible(hwnd)
            if (wasVisible) user32.ShowWindow(hwnd, SW_HIDE)
            user32.SetWindowLong(hwnd, GWL_EXSTYLE, current or wanted)
            user32.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
            if (wasVisible) user32.ShowWindow(hwnd, SW_SHOWNA)

            // Read back rather than trust the write: SetWindowLong returns the
            // PREVIOUS value, so its result says nothing about whether the new
            // one took.
            user32.GetWindowLong(hwnd, GWL_EXSTYLE) and wanted == wanted
        }
    }

    /**
     * Takes a window OUT of the always-on-top band, and says whether it was in
     * it to begin with.
     *
     * The main window is an ordinary application window and must never be
     * topmost — a zmanim board that floats over everything cannot be worked
     * behind, and on this machine one was measured sitting at
     * WS_EX_TOPMOST while a second instance of the same build was not. Rather
     * than chase which of the tray, the reminder popup or the widget leaked the
     * flag onto the wrong HWND, the main window asserts NOTOPMOST for itself
     * once it is displayable. Asserting a property you depend on is cheaper
     * than proving nothing can ever set it.
     *
     * Returns true when the flag had to be cleared, so a caller can log that it
     * actually happened rather than assume the problem is gone.
     */
    fun clearTopmost(window: Window): Boolean {
        val hwnd = hwndOf(window) ?: return false
        return guard("clear always-on-top") {
            val user32 = User32.INSTANCE
            val was = user32.GetWindowLong(hwnd, GWL_EXSTYLE) and WS_EX_TOPMOST != 0
            user32.SetWindowPos(
                hwnd, HWND_NOTOPMOST, 0, 0, 0, 0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE,
            )
            was
        }
    }

    /**
     * Drops the window to the bottom of the z-order, where the desktop lives.
     * Never moves, resizes or activates it.
     */
    fun sendToBottom(window: Window): Boolean {
        val hwnd = hwndOf(window) ?: return false
        return guard("send window to bottom") {
            User32.INSTANCE.SetWindowPos(
                hwnd, HWND_BOTTOM, 0, 0, 0, 0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE or SWP_NOOWNERZORDER,
            )
        }
    }

    /**
     * Starts or stops holding [window] on the desktop.
     *
     * This is the entire difference between the two widget modes. Floating is
     * `alwaysOnTop = true` and no timer; pinned is `alwaysOnTop = false` plus
     * this poll. Switching between them starts or stops the timer and nothing
     * else — the window is never torn down and rebuilt.
     *
     * Returns false if pinning could not be established, which the caller is
     * expected to treat as "fall back to floating and say so", not as silence.
     */
    fun setPinned(window: Window, pinned: Boolean): Boolean {
        stopTimer()
        if (!pinned) return true
        if (!sendToBottom(window)) return false

        return guard("start the pinning timer") {
            // javax.swing.Timer fires on the AWT event thread — the thread that
            // owns this window — so the re-assert runs with the same affinity
            // the window was created with.
            val t = Timer(POLL_MILLIS) {
                // A disposed window has no HWND; stop rather than log forever.
                if (!window.isDisplayable) stopTimer() else sendToBottom(window)
            }
            t.isRepeats = true
            t.start()
            timer = t
            true
        }
    }

    /** Stops the poll. Safe to call when nothing is running. */
    fun stopTimer() {
        runCatching { timer?.stop() }
        timer = null
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /**
     * The AWT window's native handle, or null when there isn't one yet.
     *
     * Compose creates the window and shows it after the first composition, so
     * on the first frame there is genuinely no HWND — that is an expected null,
     * not an error, and is not recorded as a failure.
     */
    private fun hwndOf(window: Window): HWND? {
        if (!Platform.isWindows()) return null
        if (!window.isDisplayable) return null
        return runCatching { HWND(Native.getWindowPointer(window)) }.getOrElse {
            lastFailure = "לא ניתן לאתר את מזהה החלון של Windows (${it.javaClass.simpleName})"
            null
        }
    }

    /**
     * Runs a Win32 block and converts any failure into false.
     *
     * Deliberately catches Throwable: a missing native library surfaces as
     * UnsatisfiedLinkError, not an Exception, and a widget that cannot style
     * itself is a cosmetic problem — it must not stop the app from telling
     * someone what time שקיעה is.
     */
    private inline fun guard(what: String, block: () -> Boolean): Boolean =
        try {
            val ok = block()
            if (ok) lastFailure = null else lastFailure = "Windows דחתה את הבקשה: $what"
            ok
        } catch (t: Throwable) {
            lastFailure = "$what — ${t.javaClass.simpleName}: ${t.message.orEmpty()}"
            false
        }
}
