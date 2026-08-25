package com.zmanimclock.desktop.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanNumberFamily
import com.zmanimclock.desktop.ZmanimDesktopTheme
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * The visible half of the silent reminders, plus the tray icon.
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
 * table for the reminder itself. The tray icon itself is still used — for the
 * ICON and the click events, which have no sound to suppress; see
 * ZmanimTray.kt for why its MENU is no longer the `Tray()` composable's
 * built-in one.
 *
 * So v1's reminder surface is a small, always-on-top, undecorated Compose
 * window near the bottom-right of the primary screen: the zman's name, its
 * time, and nothing else. It is genuinely silent, needs no AppUserModelID
 * registration, ships no native binary, and cannot be broken by a Windows
 * notification-stack regression. THE DOCUMENTED UPGRADE PATH is a bundled
 * SnoreToast helper (`snoretoast.exe -silent`), which buys a real Windows
 * toast that lands in Action Center — at the cost of an LGPL binary in the
 * installer, an AUMID registered through a shortcut (jpackage cannot do it),
 * and a post-install step. That trade is deliberately deferred, not forgotten;
 * see docs/DESKTOP_PLAN.md §3.
 *
 * NOT TRANSPARENT, ON PURPOSE. `transparent = true` is the natural way to get
 * rounded corners, but Compose Desktop's transparent windows have a real crash
 * and black-window history on Windows GPUs (skiko#327, CMP#3171, CMP#3757,
 * CMP-7404), and clicks do not pass through transparent regions anyway
 * (CMP-6036, closed as Obsolete). An opaque card with a border is the boring
 * choice and the correct one for something that must never fail loudly.
 */
private val PopupWidth = 300.dp
private val PopupHeight = 110.dp

/**
 * Shows [reminder] when there is one, and calls [onDismiss] when the user
 * clicks it or after [showFor] elapses — whichever comes first.
 *
 * Wire it up as:
 * ```
 * val pending by scheduler.pending.collectAsState()
 * ReminderPopupWindow(pending, scheduler::dismiss)
 * ```
 */
@Composable
fun ApplicationScope.ReminderPopupWindow(
    reminder: PendingReminder?,
    onDismiss: () -> Unit,
    showFor: Duration = ReminderScheduler.POPUP_DURATION,
) {
    if (reminder == null) return

    Window(
        onCloseRequest = onDismiss,
        state = rememberWindowState(
            width = PopupWidth,
            height = PopupHeight,
            // AbsoluteAlignment rather than Alignment.BottomEnd: the whole app
            // forces LayoutDirection.Rtl, and a direction-aware alignment would
            // be an invitation for this to end up on the wrong side of the
            // screen. Compose subtracts the screen insets itself, so this sits
            // above the taskbar rather than under it.
            position = WindowPosition.Aligned(AbsoluteAlignment.BottomRight),
        ),
        title = "שעון מעורר - זמנים הלכתיים",
        icon = painterResource("branding/logo.png"),
        undecorated = true,
        resizable = false,
        alwaysOnTop = true,
        // Never steal the caret from whatever the user is typing. A reminder
        // that interrupts a sentence is worse than no reminder.
        focusable = false,
    ) {
        // Keyed on the reminder, so a new one restarts the countdown instead of
        // inheriting the remainder of the previous one's.
        LaunchedEffect(reminder) {
            delay(showFor.toMillis().coerceAtLeast(0L))
            onDismiss()
        }

        ZmanimDesktopTheme {
            ReminderCard(reminder, onDismiss)
        }
    }
}

@Composable
private fun ReminderCard(reminder: PendingReminder, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, cs.outline)
            .clickable(onClick = onDismiss),
        color = cs.surface,
    ) {
        Row(Modifier.fillMaxSize()) {
            // Under RTL this stripe lands on the right edge — the reading edge.
            Spacer(Modifier.width(4.dp).fillMaxHeight().background(ext.accentGold))

            Column(
                Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "שעון מעורר - זמנים הלכתיים",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        reminder.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Text(
                        reminder.time,
                        fontFamily = ZmanNumberFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "לחצו לסגירה",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
