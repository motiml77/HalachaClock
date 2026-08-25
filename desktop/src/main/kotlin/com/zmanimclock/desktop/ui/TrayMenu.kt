package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.ZmanimDesktopTheme
import java.awt.Cursor
import java.awt.Toolkit
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

/**
 * The tray icon's right-click menu, drawn by Compose instead of by AWT.
 *
 * `androidx.compose.ui.window.Tray`'s `menu` block renders through
 * `java.awt.PopupMenu` — a native Win32 menu whose item text is drawn through
 * plain GDI, without the Uniscribe pass ordinary Win32 controls get. The
 * result, on a real machine: "הצג חלון ראשי" came out as "הצג חלוו,ראשי" —
 * letters swapped and a phantom comma, not merely unshaped. This is a real,
 * long-standing AWT limitation (JDK-4919147, JDK-6238731 among the open
 * reports), not something this app was doing wrong, and there is no parameter
 * on `PopupMenu` that fixes it.
 *
 * So the tray menu is this ordinary Compose window instead — rendered through
 * Skia exactly like every other Hebrew string in the app, which has never had
 * this bug. See `ZmanimTray.kt`: it drives a raw `java.awt.TrayIcon` for the
 * icon and the click events only, and calls this for the menu itself.
 */
@Composable
fun ApplicationScope.TrayMenuWindow(
    at: IntOffset,
    onDismiss: () -> Unit,
    items: @Composable ColumnScope.() -> Unit,
) {
    // Opens ABOVE and to the LEFT of the click point — where a native context
    // menu opens near a bottom-right tray icon — and clamped to the screen so
    // an unusual tray position (top of screen, a secondary monitor) cannot
    // push it past the edge. Toolkit's screen size is already in the same
    // logical unit this codebase treats as dp elsewhere (see DockTabWindow).
    val screen = remember { Toolkit.getDefaultToolkit().screenSize }
    val x = (at.x - MENU_WIDTH_PX).coerceIn(0, (screen.width - MENU_WIDTH_PX).coerceAtLeast(0))
    val y = (at.y - MENU_HEIGHT_PX).coerceIn(0, (screen.height - MENU_HEIGHT_PX).coerceAtLeast(0))

    Window(
        onCloseRequest = onDismiss,
        state = rememberWindowState(
            position = WindowPosition(x.dp, y.dp),
            size = DpSize(MENU_WIDTH, MENU_HEIGHT),
        ),
        title = "שעון מעורר - זמנים הלכתיים",
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        // Must take focus, unlike the reminder popup and the bookmark: losing
        // focus is exactly how this menu knows to close itself, below.
        focusable = true,
    ) {
        LaunchedEffect(window) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent) = Unit
                override fun windowLostFocus(e: WindowEvent) = onDismiss()
            }
            window.addWindowFocusListener(listener)
            window.toFront()
            window.requestFocus()
        }

        ZmanimDesktopTheme {
            TrayMenuContent(onDismiss, items)
        }
    }
}

/**
 * The menu's content alone, with no [Window] around it — split out so
 * `RenderShotTest` can shoot it offscreen at its real size the same way every
 * other pane in this app is checked. Every clipping bug this project has
 * shipped was invisible until something was actually rendered.
 */
@Composable
fun TrayMenuContent(onDismiss: () -> Unit, items: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .onPreviewKeyEvent {
                if (it.key == Key.Escape) { onDismiss(); true } else false
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 6.dp), content = items)
    }
}

@Composable
fun TrayMenuItem(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Text(
        label,
        // No explicit textAlign: Text's default is Start, which under this
        // app's forced RTL resolves to the RIGHT — the reading edge — exactly
        // like every other full-width Hebrew label in the app.
        modifier = Modifier.fillMaxWidth()
            .background(if (hovered) cs.surfaceVariant else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = cs.onSurface,
    )
}

@Composable
fun TrayMenuDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 5.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

private val MENU_WIDTH = 190.dp
private val MENU_HEIGHT = 132.dp
private const val MENU_WIDTH_PX = 190
private const val MENU_HEIGHT_PX = 132
