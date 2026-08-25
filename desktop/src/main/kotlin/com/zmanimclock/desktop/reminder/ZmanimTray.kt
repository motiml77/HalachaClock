package com.zmanimclock.desktop.reminder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.ApplicationScope
import com.zmanimclock.desktop.ui.TrayMenuDivider
import com.zmanimclock.desktop.ui.TrayMenuItem
import com.zmanimclock.desktop.ui.TrayMenuWindow
import java.awt.Image
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * The tray icon, and its right-click menu.
 *
 * NOT `androidx.compose.ui.window.Tray`. That composable's icon and click
 * handling are fine; its `menu` block is what is avoided, because it renders
 * through `java.awt.PopupMenu` and that garbles Hebrew on Windows — see the
 * doc comment on `TrayMenuWindow` in `ui/TrayMenu.kt` for the concrete bug and
 * why there is no fix on `PopupMenu` itself. So this drives a raw
 * `java.awt.TrayIcon` directly: it supplies only the icon, the tooltip and the
 * click events, and every pixel of the menu is a Compose window instead.
 *
 * LEFT CLICK opens the main window — what Windows users expect of a tray
 * icon. RIGHT CLICK opens the menu at the click point.
 *
 * `TrayState` / `TrayState.sendNotification` are deliberately not used
 * anywhere in this file, same as before: `TrayIcon.displayMessage` cannot be
 * silent (`NIIF_NOSOUND` is never set along that path — see the reminder
 * popup's file header for the full trace), and a zmanim board that chimes is
 * an alarm clock, which this app is explicitly not.
 */
@Composable
fun ApplicationScope.ZmanimTray(
    onShowMainWindow: () -> Unit,
    onShowWidget: () -> Unit,
    onExit: () -> Unit,
    tooltip: String = "שעון מעורר - זמנים הלכתיים",
) {
    // Where to draw the menu, or null when it is closed. This is the exact
    // screen point the user right-clicked — TrayIcon reports it directly, no
    // translation needed.
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }

    DisposableEffect(Unit) {
        val icon = installTrayIcon(
            tooltip = tooltip,
            onLeftClick = onShowMainWindow,
            onRightClick = { x, y -> menuAt = IntOffset(x, y) },
        )
        onDispose { icon?.let { runCatching { SystemTray.getSystemTray().remove(it) } } }
    }

    val at = menuAt
    if (at != null) {
        TrayMenuWindow(at = at, onDismiss = { menuAt = null }) {
            TrayMenuItem("הצג חלון ראשי") { menuAt = null; onShowMainWindow() }
            TrayMenuItem("הצג ווידג'ט") { menuAt = null; onShowWidget() }
            TrayMenuDivider()
            TrayMenuItem("יציאה") { menuAt = null; onExit() }
        }
    }
}

/**
 * Installs the icon and returns it, or null when there is no shell tray to
 * attach to (a minimal Windows configuration, some remote sessions) or the
 * logo resource cannot be loaded. Never throws: a tray icon is a convenience,
 * and its absence must not take the rest of the app down with it.
 */
private fun installTrayIcon(
    tooltip: String,
    onLeftClick: () -> Unit,
    onRightClick: (x: Int, y: Int) -> Unit,
): TrayIcon? {
    if (!SystemTray.isSupported()) return null
    val image = loadTrayImage() ?: return null

    val icon = TrayIcon(image, tooltip).apply {
        // Let AWT scale the source bitmap to whatever the shell's actual tray
        // icon size is (16px, or larger at 125%/150% scaling) instead of
        // stretching it once at a fixed size.
        isImageAutoSize = true
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when (e.button) {
                    MouseEvent.BUTTON1 -> onLeftClick()
                    MouseEvent.BUTTON3 -> onRightClick(e.xOnScreen, e.yOnScreen)
                }
            }
        })
    }

    return runCatching {
        SystemTray.getSystemTray().add(icon)
        icon
    }.getOrNull()
}

private fun loadTrayImage(): Image? = runCatching {
    val url = object {}.javaClass.getResource("/branding/logo.png") ?: return@runCatching null
    Toolkit.getDefaultToolkit().createImage(url)
}.getOrNull()
