package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanimDesktopTheme
import java.awt.Cursor
import java.awt.Toolkit

/**
 * The folded state of the app: a bookmark on the left edge of the screen.
 *
 * The owner's request, verbatim in spirit: minimising should not drop the app
 * to the taskbar — it should tuck it against the side of the screen as a small
 * tab with an arrow, and clicking the tab opens it back up like a side panel.
 * So this window exists exactly while the app is folded, and its ONE job is to
 * be clicked.
 *
 * Design decisions, each with its reason:
 *  - LEFT edge, rounded on its RIGHT side only. The flat side is the screen
 *    edge it grows out of; rounding it would make the tab look like it is
 *    floating near the edge rather than attached to it. Under the app's forced
 *    RTL, `topStart`/`bottomStart` ARE the right-hand corners.
 *  - The arrow points RIGHT — the direction the window will actually open.
 *    An arrow that points at the edge would read as "put it away", which is
 *    the one thing clicking here cannot do.
 *  - `alwaysOnTop`. A bookmark that other windows can bury is lost; this is
 *    36dp wide and floats by the same licence the reminder popup does.
 *  - NOT focusable, and closing it opens the app. There is no interaction
 *    with a bookmark except through it; every path leads back to the window.
 *  - Vertically centred, where the thumb of a maximised window's scrollbar
 *    never lives and where the eye finds it without hunting the corners.
 */
@Composable
fun ApplicationScope.DockTabWindow(visible: Boolean, onOpen: () -> Unit) {
    // AWT reports the LOGICAL screen size (it applies the display scale
    // itself), which is the same unit Compose's dp positions use here.
    val screen = remember { Toolkit.getDefaultToolkit().screenSize }
    val state = rememberWindowState(
        position = WindowPosition(0.dp, ((screen.height - TAB_HEIGHT_PX) / 2).dp),
        size = DpSize(TAB_WIDTH, TAB_HEIGHT),
    )

    Window(
        onCloseRequest = onOpen,
        state = state,
        // ALWAYS composed, shown by flag. Creating a transparent window from
        // scratch at the moment of folding cost seconds of blank screen —
        // Windows builds a layered window, Skiko attaches a surface, and only
        // then does the first frame land. A window that already exists and
        // merely becomes visible appears immediately.
        visible = visible,
        title = "שעון מעורר - זמנים הלכתיים",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        ZmanimDesktopTheme {
            val ext = Ext.colors
            val cs = MaterialTheme.colorScheme
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)

            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)), shape)
                    // Hover brightens the whole tab, not just the arrow: the
                    // affordance is "this entire thing is a button".
                    .background(Color.White.copy(alpha = if (hovered) 0.10f else 0f), shape)
                    // GOLD, at the owner's request: a navy sliver on a busy
                    // desktop disappears against dark wallpaper and dark
                    // windows alike. The gold frame — same colour as the arrow
                    // — is what makes the bookmark read as OURS at a glance,
                    // and it brightens with the rest of the tab on hover.
                    .border(
                        1.5.dp,
                        if (hovered) ext.accentGold else ext.accentGold.copy(alpha = 0.75f),
                        shape,
                    )
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                // The arrow alone. The logo was tried here and dropped at the
                // owner's request — at bookmark size it renders as a smudged
                // square, and the tab needs to say exactly one thing: "open".
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "פתח את שעון מעורר - זמנים הלכתיים",
                    tint = ext.accentGold,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private val TAB_WIDTH = 36.dp
private val TAB_HEIGHT = 96.dp
private const val TAB_HEIGHT_PX = 96
