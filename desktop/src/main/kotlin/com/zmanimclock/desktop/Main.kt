package com.zmanimclock.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.data.DesktopPrefs
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.reminder.ReminderPopupWindow
import com.zmanimclock.desktop.reminder.ReminderScheduler
import com.zmanimclock.desktop.reminder.ZmanimTray
import com.zmanimclock.desktop.ui.AppTitleBar
import com.zmanimclock.desktop.ui.CalendarPane
import com.zmanimclock.desktop.ui.DockTabWindow
import com.zmanimclock.desktop.ui.SettingsPane
import com.zmanimclock.desktop.ui.ZmanimPane
import androidx.compose.runtime.DisposableEffect
import com.zmanimclock.desktop.system.SingleInstance
import com.zmanimclock.desktop.widget.WindowPinning
import com.zmanimclock.desktop.widget.ZmanimWidgetWindow

/**
 * "שעון זמנים" for Windows.
 *
 * A zmanim board and Hebrew calendar — NOT an alarm clock. No ringing, no
 * vibration, no snooze; at most a silent pop-up reminder the user opted into.
 *
 * The window is wide rather than phone-shaped, and that removes a whole
 * mechanism: on Android the calendar must collapse to a single week row so a
 * month grid and seventeen zman rows can share a narrow screen. Here they sit
 * side by side, so the collapsing machinery does not exist at all.
 *
 * This is deliberately a NORMAL, focusable window. The desktop widget is a
 * separate borderless one that cannot take keyboard input, so anything
 * involving typing or arrow keys — the calendar, city search, settings —
 * belongs here.
 */
fun main(args: Array<String>) {
    // Before anything is built. A second copy hands its request to the copy
    // already running and exits, so the user gets the window they asked for
    // instead of a duplicate of the whole app.
    if (!SingleInstance.claim()) return
    runApp(args)
}

private fun runApp(args: Array<String>) = application {
    // --tray: launched by the Windows Run key at logon. Start hidden in the
    // tray rather than throwing a window in the user's face at every boot.
    val startHidden = args.any { it.equals("--tray", ignoreCase = true) }

    val prefs = remember { DesktopPrefs.load() }
    val service = remember { DesktopZmanimService(prefs) }
    val scheduler = remember { ReminderScheduler(service) }

    var mainVisible by remember { mutableStateOf(!startHidden) }
    // Folded against the left edge of the screen as a bookmark (DockTabWindow).
    var docked by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(MainTab.ZMANIM) }

    // Sized to the CONTENT, not to what a desktop can spare, and the content
    // is different per tab — see [MainTab].
    val mainState = rememberWindowState(width = MainTab.ZMANIM.width, height = WINDOW_HEIGHT)

    // The window follows the tab. Only the width moves: a changing height as
    // well would make the whole window jump around, and the row count is what
    // sets the height anyway.
    LaunchedEffect(tab) {
        if (mainState.placement == WindowPlacement.Floating) {
            mainState.size = mainState.size.copy(width = tab.width)
        }
    }

    LaunchedEffect(scheduler) { scheduler.run() }
    val pending by scheduler.pending.collectAsState()

    // Launching the app again — from the Start menu, the shortcut, anywhere —
    // surfaces THIS window rather than starting a second copy.
    DisposableEffect(Unit) {
        SingleInstance.onShowRequested {
            docked = false
            mainVisible = true
            mainState.isMinimized = false
        }
        onDispose { SingleInstance.release() }
    }

    ZmanimTray(
        onShowMainWindow = {
            // Every state that can hide this window has to be undone here, or
            // the tray item does nothing and looks broken: closed to the tray,
            // minimised to the taskbar, folded to the edge, or simply behind
            // something. `toFront` alone fixes only the last.
            docked = false
            mainVisible = true
            mainState.isMinimized = false
        },
        onShowWidget = { service.update { it.copy(widgetVisible = true) } },
        // Closing the last window would end the process, so exit lives here.
        onExit = ::exitApplication,
    )

    ReminderPopupWindow(reminder = pending, onDismiss = scheduler::dismiss)

    // Returns early when prefs.widgetVisible is false, so no `if` here.
    ZmanimWidgetWindow(
        service,
        onOpenMain = {
            docked = false
            mainVisible = true
            mainState.isMinimized = false
        },
    )

    // The folded state. Clicking the bookmark reopens the window as a side
    // panel: flush against the same edge the tab lived on, vertically centred,
    // so the open motion reads as the tab expanding rather than a window
    // appearing somewhere unrelated.
    if (docked) {
        DockTabWindow(onOpen = {
            docked = false
            mainVisible = true
            mainState.isMinimized = false
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            mainState.position = WindowPosition(
                0.dp,
                (((screen.height - WINDOW_HEIGHT_PX).coerceAtLeast(0)) / 2).dp,
            )
        })
    }

    if (mainVisible && !docked) {
        Window(
            // Hide to the tray rather than quit: the reminder scheduler and
            // the widget both need the process alive.
            onCloseRequest = { mainVisible = false },
            title = "שעון זמנים",
            icon = painterResource("branding/logo.png"),
            state = mainState,
            // ORDINARY WINDOW, STATED OUTRIGHT.
            //
            // This is the default, and it is written down anyway because the
            // opposite was observed in the field: a running copy of this build
            // was measured holding WS_EX_TOPMOST, which parks the zmanim board
            // above every other program — nothing else can be brought in front
            // of it, and the taskbar button stops behaving like a normal one
            // because the window never properly leaves the foreground.
            //
            // Only two windows in this app may float: the widget, which the
            // user asks for, and the reminder popup, which is a notification.
            alwaysOnTop = false,
            // The app draws its own title bar — see AppTitleBar. The system one
            // is a strip of another design language, in the OS accent colour,
            // with its buttons laid out left-to-right above a right-to-left
            // window.
            undecorated = true,
            // Transparent so the ROUNDED CORNERS below are real: an opaque
            // undecorated window is a hard rectangle and the corners would be
            // painted-on fakes with the desktop showing through as black. The
            // widget has shipped with this same combination since it existed.
            transparent = true,
        ) {
            // Belt and braces for the same thing. `alwaysOnTop = false` covers
            // what Compose sets; this covers the flag arriving from anywhere
            // else, including a stale window from a previous run of the app.
            LaunchedEffect(window) {
                if (WindowPinning.clearTopmost(window)) {
                    println("main window was always-on-top; cleared")
                }
                // A floor, not a preference. Below roughly this the month grid
                // loses a column and the settings chips stop wrapping into
                // anything readable, so dragging smaller is simply refused
                // rather than allowed to produce a broken layout.
                window.minimumSize = java.awt.Dimension(360, 420)
            }

            ZmanimDesktopTheme {
                val shape = RoundedCornerShape(WINDOW_CORNER)
                Surface(
                    Modifier.fillMaxSize().clip(shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        AppTitleBar(
                            mainState,
                            onDock = {
                                docked = true
                                mainVisible = false
                            },
                            onClose = { mainVisible = false },
                        )
                        MainWindowContent(service, scheduler, tab) { tab = it }
                    }
                }
            }
        }
    }
}

/**
 * Each tab carries the width it needs, and the window follows.
 *
 * The zmanim board is one narrow column of "name — time" and nothing else
 * wants width; making the window wide enough for the month grid meant that
 * ninety percent of the time the user looked at a mostly empty rectangle. So
 * the window is as narrow as the list, and OPENING THE CALENDAR WIDENS IT —
 * the month grid appears alongside rather than forcing the board to live in a
 * window sized for a different tab.
 */
private enum class MainTab(val label: String, val width: Dp) {
    ZMANIM("זמני היום", 400.dp),
    CALENDAR("לוח שנה", 780.dp),
    // Two columns — the city list and the filters — and they stop being
    // readable much below this.
    SETTINGS("הגדרות", 620.dp),
}

@Composable
private fun MainWindowContent(
    service: DesktopZmanimService,
    scheduler: ReminderScheduler,
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        // A segmented pill rather than Material's TabRow. The stock TabRow is
        // a full-width strip with an underline — the visual language of a
        // browser, not of a small always-there panel. A pill track with a
        // filled thumb reads instantly as "one of three", costs less height,
        // and gives the top of the window a shape that matches the rounded
        // shell it now lives in.
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(cs.surfaceVariant.copy(alpha = 0.55f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MainTab.entries.forEach { t ->
                val selected = tab == t
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) cs.primaryContainer else Color.Transparent)
                        .clickable {
                            // Leaving settings may have changed the city, the
                            // halachic offsets or the reminder set; tell the
                            // scheduler now rather than making the user wait
                            // for the next tick to notice.
                            if (tab == MainTab.SETTINGS && t != MainTab.SETTINGS) scheduler.invalidate()
                            onTabChange(t)
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                MainTab.ZMANIM -> ZmanimPane(service)
                MainTab.CALENDAR -> CalendarPane(service)
                MainTab.SETTINGS -> SettingsPane(service)
            }
        }
    }
}

/** One height for every tab; only the width moves. */
private val WINDOW_HEIGHT = 560.dp
private const val WINDOW_HEIGHT_PX = 560

/** The window's corner radius — the shell is transparent so these are real. */
private val WINDOW_CORNER = 14.dp
