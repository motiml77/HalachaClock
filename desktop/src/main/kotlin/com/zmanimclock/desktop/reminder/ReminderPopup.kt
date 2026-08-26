package com.zmanimclock.desktop.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanNumberFamily
import com.zmanimclock.desktop.ZmanimDesktopTheme
import java.awt.Cursor
import java.awt.Toolkit

/**
 * The visible half of the silent reminders: a banner hanging from the TOP
 * edge of the screen, above everything, until the user acknowledges it.
 *
 * WHY THE REMINDER IS A COMPOSE WINDOW AND NOT A NOTIFICATION
 *
 * `java.awt.TrayIcon.displayMessage` — and therefore Compose's
 * `TrayState.sendNotification`, which is a thin wrapper over it — CANNOT BE
 * SILENT. The chain was followed end to end in OpenJDK's source:
 * `sendNotification` → `Tray.desktop.kt` → `TrayIcon.MessageType` →
 * `awt_TrayIcon.cpp` → `Shell_NotifyIcon` with `NIF_INFO`, whose `dwInfoFlags`
 * is set to exactly one of `NIIF_ERROR / WARNING / INFO / NONE`. `NIIF_NOSOUND`
 * — the one flag Windows offers to suppress the chime — does not appear in
 * that file at all, and `NIIF_NONE` means "no icon", not "no sound". There is
 * no seam to inject it through. A zmanim board that chimes is an alarm clock,
 * and this application is explicitly not one, so that whole path is off the
 * table. A toast would also fade on its own, and fading on its own is now the
 * opposite of the contract:
 *
 * THE BANNER STAYS UNTIL אישור IS CLICKED — the owner's requirement, replacing
 * the old eight-second self-dismissing card at the bottom corner. A reminder
 * that removes itself is a reminder the user can miss by being out of the
 * room; one that waits is the whole point of asking for it. The cost is
 * inherent and accepted: the scheduler shows one reminder at a time, so an
 * unacknowledged banner holds later zmanim back until it is clicked (they are
 * then swept, and anything older than the five-minute delivery window is
 * dropped as stale rather than fired in a burst).
 *
 * SHAPE AND PLACE: hanging from the top edge, horizontally centred — where
 * every platform teaches the eye that transient announcements live, and where
 * it cannot cover the taskbar or the app's own side panel. It follows the
 * bookmark's design language exactly: flat on the screen edge it grows out
 * of, rounded on the side facing the desktop, the hero gradient as ground,
 * and the gold frame drawn on the three VISIBLE sides only — a gold line
 * along the top would draw the banner's own boundary against the edge and
 * turn "emerging from the screen edge" into "parked near it".
 *
 * `transparent = true` for the real rounded corners, the same combination the
 * bookmark, the widget and the main panel have shipped with. (An earlier
 * revision of this file avoided transparency citing Skiko's black-window
 * history; the app has since shipped four transparent windows without a
 * single such report on this machine, and the design language won.)
 *
 * ALWAYS ON TOP, and honestly so: over films, music, browsers — that is the
 * owner's explicit instruction. What it deliberately does NOT do is steal
 * focus (`focusable = false`): it must never take the caret from a sentence
 * being typed. Buttons receive mouse clicks regardless of focus, so אישור
 * works without the window ever being focused.
 */
@Composable
fun ApplicationScope.ReminderPopupWindow(
    reminder: PendingReminder?,
    onDismiss: () -> Unit,
) {
    if (reminder == null) return

    // Centred on the primary screen. Toolkit reports the LOGICAL size — the
    // same unit Compose's dp positions use here (see DockTabWindow).
    val screen = remember { Toolkit.getDefaultToolkit().screenSize }
    val x = ((screen.width - BANNER_WIDTH_PX) / 2).coerceAtLeast(0)

    Window(
        onCloseRequest = onDismiss,
        state = rememberWindowState(
            position = WindowPosition(x.dp, 0.dp),
            size = DpSize(BANNER_WIDTH, BANNER_HEIGHT),
        ),
        title = "שעון מעורר - זמנים הלכתיים",
        icon = painterResource("branding/logo.png"),
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
    ) {
        ZmanimDesktopTheme {
            ReminderBanner(reminder, onDismiss)
        }
    }
}

/**
 * The banner's face, with no [Window] around it — split out so RenderShotTest
 * can shoot it at its real size. Every clipping bug this project has shipped
 * was invisible until something was actually rendered.
 */
@Composable
fun ReminderBanner(reminder: PendingReminder, onDismiss: () -> Unit) {
    val ext = Ext.colors
    // Flat top (the screen edge), rounded toward the desktop. Symmetric, so
    // RTL corner resolution cannot put a curve on the wrong side.
    val shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(ext.heroTop, ext.heroBottom)), shape)
            // The bookmark's three-sided gold frame, rotated to a top-edge
            // window: left, bottom and right stroked; the top edge open so the
            // shape runs off the screen. Hand-drawn because Modifier.border
            // strokes the whole outline or nothing.
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                val half = stroke / 2f
                val r = 18.dp.toPx()
                val left = half
                val right = size.width - half
                val bottom = size.height - half
                val path = Path().apply {
                    moveTo(left, 0f)
                    lineTo(left, bottom - r)
                    // 180° is the left edge of the corner circle; sweeping
                    // -90° runs clockwise-on-screen to the circle's bottom.
                    arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 180f, -90f, false)
                    lineTo(right - r, bottom)
                    arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 90f, -90f, false)
                    lineTo(right, 0f)
                }
                drawPath(path, ext.accentGold.copy(alpha = 0.85f), style = Stroke(width = stroke))
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "שעון מעורר - זמנים הלכתיים",
            style = MaterialTheme.typography.labelMedium,
            color = ext.heroLabel,
        )

        // THE USER'S OWN NAME IS THE MESSAGE. They wrote it when they made the
        // alert, and it says what they actually meant — "לצאת לתפילה" carries
        // the intent that "מנחה קטנה" only implies. The zman it derives from
        // is the supporting line beneath it, not the headline.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                reminder.alert.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ext.heroText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminder.alert.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.heroLabel,
                )
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text(
                    reminder.time,
                    fontFamily = ZmanNumberFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ext.accentGold,
                )
            }
        }

        ConfirmButton(onDismiss)
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * אישור — the one way off the screen, so it earns the one accent. Gold pill,
 * navy text: the banner's colours inverted, which is what makes a single
 * button read as THE button. Sized for a mouse ("small but not too small" was
 * the specification): a 32dp-tall target is comfortably clickable without
 * competing with the zman line above it.
 */
@Composable
private fun ConfirmButton(onClick: () -> Unit) {
    val ext = Ext.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Text(
        "אישור",
        modifier = Modifier
            .background(
                if (hovered) ext.accentGold else ext.accentGold.copy(alpha = 0.88f),
                RoundedCornerShape(50),
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = ext.onAccentGold,
    )
}

/**
 * ~10cm x ~5cm at the owner's request. Centimetres only exist on a physical
 * panel, so the conversion anchors on Windows' nominal 96 dp/inch: 10cm =
 * 3.94in = 378dp, 5cm = 189dp. On a scaled display Windows multiplies the
 * same factor into every window, so the banner keeps its proportion to
 * everything else on screen — which is what a physical-size request is
 * actually asking for.
 */
private val BANNER_WIDTH = 378.dp
private val BANNER_HEIGHT = 189.dp
private const val BANNER_WIDTH_PX = 378
