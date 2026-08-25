package com.zmanimclock.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.data.DesktopPrefs
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.reminder.ReminderPopupWindow
import com.zmanimclock.desktop.reminder.ReminderScheduler
import com.zmanimclock.desktop.reminder.ZmanimTray
import com.zmanimclock.desktop.ui.AppTitleBar
import com.zmanimclock.desktop.ui.CalendarPane
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
            mainVisible = true
            mainState.isMinimized = false
        }
        onDispose { SingleInstance.release() }
    }

    ZmanimTray(
        onShowMainWindow = {
            // Three separate states can hide this window and all three have to
            // be undone, or the tray item does nothing and looks broken: it can
            // be closed to the tray, minimised to the taskbar, or simply behind
            // something. `toFront` alone fixes only the third.
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
            mainVisible = true
            mainState.isMinimized = false
        },
    )

    if (mainVisible) {
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
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize()) {
                        AppTitleBar(mainState) { mainVisible = false }
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
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.fillMaxWidth()) {
            MainTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = {
                        // Leaving settings may have changed the city, the two
                        // halachic offsets or the reminder set; tell the
                        // scheduler now rather than making the user wait for
                        // the next tick to notice.
                        if (tab == MainTab.SETTINGS && t != MainTab.SETTINGS) scheduler.invalidate()
                        onTabChange(t)
                    },
                    text = { Text(t.label, style = MaterialTheme.typography.titleSmall) },
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(top = 4.dp)) {
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
