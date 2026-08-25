package com.zmanimclock.desktop.system

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.swing.SwingUtilities

/**
 * One copy of "שעון מעורר - זמנים הלכתיים" at a time.
 *
 * This is not tidiness. The app keeps living in the tray after its window is
 * closed and it starts itself at logon, so the ways to end up with two copies
 * are ordinary: launch it from the Start menu while the tray copy is already
 * running, or double-click the shortcut when the window happens to be hidden.
 * Two copies were measured running side by side on the developer's own machine
 * — two tray icons, two reminder schedulers firing the same popup twice, two
 * windows with the same title, and a stale one holding always-on-top over
 * everything else.
 *
 * ── Why a loopback socket and not a lock file ────────────────────────────────
 * A lock file answers "is another copy running" but not "and please show
 * yourself", which is the behaviour a user expects: clicking the shortcut again
 * should surface the window that already exists, not silently do nothing. A
 * lock file also survives a crash and then blocks every future launch until
 * someone deletes it by hand. A bound socket is released by the operating
 * system when the process dies, however it dies, and doubles as the channel for
 * the "show yourself" message. `FileLock` shares the first property but not the
 * second.
 *
 * ── Bound to the loopback address on purpose ─────────────────────────────────
 * `InetAddress.getLoopbackAddress()`, never the wildcard: the port must not be
 * reachable from the network, and on Windows binding to the wildcard also
 * raises a firewall prompt the first time, which is an alarming thing for a
 * zmanim clock to do.
 *
 * ── Failure is not fatal ─────────────────────────────────────────────────────
 * If the port is taken by something that is NOT us — another program, a
 * leftover socket in TIME_WAIT — [claim] returns true and the app starts
 * anyway. Refusing to launch a clock because a port was busy would be a far
 * worse bug than the duplicate this file exists to prevent.
 */
object SingleInstance {

    /**
     * Fixed, private, and above the range Windows hands out for ephemeral
     * ports so an outgoing connection cannot land on it by chance.
     */
    private const val PORT = 50871

    /** One byte, so both ends agree on what a "show yourself" ping looks like. */
    private const val PING: Int = 0x5A

    private var server: ServerSocket? = null

    @Volatile
    private var showHandler: (() -> Unit)? = null

    /**
     * Returns true if this process should go on to start the UI.
     *
     * When another copy already holds the port this pings it — so the copy
     * already running surfaces its window — and returns false, meaning: exit
     * quietly, the user has been served by the instance that was already there.
     */
    fun claim(): Boolean {
        val bound = try {
            ServerSocket(PORT, 4, InetAddress.getLoopbackAddress())
        } catch (_: IOException) {
            null
        }

        if (bound == null) {
            // Something holds the port. If it is us, this wakes it and we are
            // done; if it is anything else, the ping fails and we start anyway
            // rather than leaving the user with no app at all.
            return !pingExistingInstance()
        }

        server = bound
        Thread({ acceptLoop(bound) }, "single-instance").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /**
     * Registers what to do when a second launch asks us to surface.
     *
     * The handler is invoked on the AWT event thread, because everything it
     * will touch — window visibility, minimised state — belongs to that thread.
     */
    fun onShowRequested(handler: () -> Unit) {
        showHandler = handler
    }

    /** Releases the port. The OS does this too; this makes it immediate. */
    fun release() {
        runCatching { server?.close() }
        server = null
    }

    private fun pingExistingInstance(): Boolean = runCatching {
        Socket(InetAddress.getLoopbackAddress(), PORT).use { socket ->
            socket.soTimeout = PING_TIMEOUT_MILLIS
            socket.getOutputStream().apply {
                write(PING)
                flush()
            }
        }
        true
    }.getOrDefault(false)

    private fun acceptLoop(bound: ServerSocket) {
        while (!bound.isClosed) {
            val socket = try {
                bound.accept()
            } catch (_: IOException) {
                return // closed, or the port went away; either way we are done
            }
            socket.use {
                runCatching {
                    it.soTimeout = PING_TIMEOUT_MILLIS
                    // Only a real ping counts. A port scanner opening and
                    // closing the socket must not pop the window open.
                    if (it.getInputStream().read() == PING) {
                        showHandler?.let { handler -> SwingUtilities.invokeLater(handler) }
                    }
                }
            }
        }
    }

    private const val PING_TIMEOUT_MILLIS = 2_000
}
