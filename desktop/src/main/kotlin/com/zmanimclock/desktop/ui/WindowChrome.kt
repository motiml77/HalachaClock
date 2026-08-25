package com.zmanimclock.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.zmanimclock.desktop.Ext
import java.awt.Cursor

/**
 * The window's own title bar, drawn by the app instead of by Windows.
 *
 * The system title bar is a strip of someone else's design language sitting on
 * top of a Hebrew, right-to-left, navy application: it renders its buttons at
 * the left in a left-to-right order, paints itself in the OS accent colour, and
 * on this machine came out bright blue above a dark navy window. Drawing it
 * here costs three window operations and buys a window that looks like one
 * object rather than two.
 *
 * WHAT MOVING THE BAR IN-APP COSTS, AND HOW EACH COST IS PAID:
 *  - Dragging the window. `WindowDraggableArea` restores it, and only over the
 *    empty part of the bar — dragging must not start from a button.
 *  - Double-click to maximise/restore. Wired below; people use it without
 *    thinking about it and its absence reads as a broken window.
 *  - The system menu (Alt+Space, right-click the bar). Not reimplemented.
 *    Accepted: minimise, maximise and close are all present as buttons, and
 *    Alt+F4 still closes.
 *  - Snap layouts (hovering the maximise button on Windows 11). Lost. That one
 *    genuinely needs the real caption button, and it is the price of the
 *    change the owner asked for.
 *
 * LAYOUT, AND THE RTL RULE THAT DECIDES IT: under the app's forced RTL the
 * FIRST child of a Row is placed on the RIGHT. So the title is declared first
 * and sits on the right, where a Hebrew reader starts, and the caption buttons
 * follow and sit on the left.
 *
 * Within that group the buttons are declared minimise, maximise, close, which
 * RTL renders right-to-left — putting CLOSE in the outermost corner, exactly
 * where a mirrored window puts it. That matters more than the order itself: a
 * destructive button belongs at the edge the hand throws itself at, not in the
 * middle where it can be hit on the way to something else.
 */
@Composable
fun WindowScope.AppTitleBar(
    state: WindowState,
    /**
     * The minimise button DOCKS rather than minimises, at the owner's request:
     * the window folds into a slim bookmark on the left edge of the screen
     * (see DockTabWindow) instead of dropping to the taskbar. The taskbar
     * button still minimises the normal way, so both behaviours exist.
     */
    onDock: () -> Unit,
    onClose: () -> Unit,
) {
    val ext = Ext.colors

    fun toggleMaximise() {
        state.placement =
            if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating
            else WindowPlacement.Maximized
    }

    Column {
    Row(
        Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT).background(ext.heroTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // FIRST child, so under RTL it lands on the RIGHT. Everything that is
        // not a button also drags the window, which the system bar gave us for
        // free and which has to be handed back deliberately.
        WindowDraggableArea(Modifier.weight(1f)) {
            Box(
                Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT)
                    .padding(horizontal = 12.dp),
                // Start, not End: under RTL the start edge IS the right edge,
                // so the name sits against the corner the reader begins at.
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "שעון מעורר - זמנים הלכתיים",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ext.heroText,
                    // The panel is 320dp at its narrowest and the product's
                    // name is long. Ellipsis rather than clipping: a name that
                    // trails off reads as a name too long for the bar, while a
                    // name sliced mid-letter reads as a broken window.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        CaptionButton(Icons.Filled.Minimize, "קפל לצד המסך", onClick = onDock)
        CaptionButton(
            if (state.placement == WindowPlacement.Maximized) Icons.Filled.FilterNone
            else Icons.Filled.CropSquare,
            if (state.placement == WindowPlacement.Maximized) "שחזר" else "הגדל",
        ) { toggleMaximise() }
        CaptionButton(Icons.Filled.Close, "סגור", danger = true, onClick = onClose)
    }
    // A single gold hairline under the caption — the one line of the brand's
    // accent on the window frame itself. It does the visual work the OS
    // accent-coloured bar used to do (separating chrome from content) in the
    // app's own colour instead of Windows'.
    HorizontalDivider(thickness = 1.dp, color = ext.accentGold.copy(alpha = 0.55f))
    }
}

/** 32dp: the height of a Windows 11 caption, so the app does not look taller. */
val TITLE_BAR_HEIGHT = 32.dp

@Composable
private fun CaptionButton(
    icon: ImageVector,
    description: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val ext = Ext.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Box(
        // 38, not Windows' 44: three of them cost 132dp of a 320dp panel,
        // which is more than a third of the bar spent on chrome. Still a
        // comfortable mouse target.
        Modifier.size(width = 38.dp, height = TITLE_BAR_HEIGHT)
            .background(
                when {
                    // Red on hover, like every Windows close button. The colour
                    // is the warning; without it this is three identical
                    // squares and the destructive one is a guess.
                    hovered && danger -> Color(0xFFC42B1C)
                    hovered -> ext.heroInner
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (hovered && danger) Color.White else ext.heroLabel,
            modifier = Modifier.size(15.dp),
        )
    }
}
