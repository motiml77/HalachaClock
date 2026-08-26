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
import androidx.compose.ui.unit.DpSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import com.zmanimclock.app.feature.zmanim.model.ZmanKind
import com.zmanimclock.desktop.ui.AlertsPane
import com.zmanimclock.desktop.ui.RoundIconButton
import com.zmanimclock.desktop.ui.CalendarPane
import com.zmanimclock.desktop.ui.DockTabWindow
import com.zmanimclock.desktop.ui.SettingsPane
import com.zmanimclock.desktop.ui.ZMANIM_COLUMN_WIDTH
import com.zmanimclock.desktop.ui.ZmanimPane
import androidx.compose.runtime.DisposableEffect
import com.zmanimclock.desktop.system.SingleInstance
import com.zmanimclock.desktop.widget.WindowPinning
import com.zmanimclock.desktop.widget.ZmanimWidgetWindow

/**
 * "שעון מעורר - זמנים הלכתיים" for Windows.
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

    // Width per tab; the height is the WORK AREA's, set by snapToEdge below.
    val mainState = rememberWindowState(width = MainTab.ZMANIM.width, height = 560.dp)

    // The window follows the tab. Only the width moves: a changing height as
    // well would make the whole window jump around, and the panel's height
    // belongs to the screen edge anyway.
    LaunchedEffect(tab) {
        if (mainState.placement == WindowPlacement.Floating) {
            mainState.size = mainState.size.copy(width = tab.width)
        }
    }

    LaunchedEffect(scheduler) { scheduler.run() }

    // --demo-reminder: put a sample banner on screen at startup. This is how
    // the reminder surface gets seen — and verified on a real screen — without
    // waiting for an actual zman.
    LaunchedEffect(Unit) {
        if (args.any { it.equals("--demo-reminder", ignoreCase = true) }) scheduler.showSample()
    }
    val pending by scheduler.pending.collectAsState()

    // Re-assert the Run key on every start while the setting is on. This is
    // not paranoia: the launcher was RENAMED (HalachClock.exe -> Halacha
    // Clock.exe), so every registry value written before the rename points at
    // an executable that no longer exists, and autostart would silently die on
    // the first boot after the upgrade. Rewriting from the current process
    // repairs any stale path, every launch, for free.
    LaunchedEffect(Unit) {
        if (prefs.startWithWindows && com.zmanimclock.desktop.system.StartupManager.isSupported()) {
            com.zmanimclock.desktop.system.StartupManager.setEnabled(true)
        }
    }

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
    DockTabWindow(
        visible = docked,
        onOpen = {
            // Just the flags: the snap-to-edge effect above fires on the
            // visibility transition and does the positioning.
            docked = false
            mainVisible = true
            mainState.isMinimized = false
        },
    )

    // The window is COMPOSED unconditionally and shown by flag, same as the
    // bookmark. Folding used to dispose this window and reopening rebuilt it,
    // which is why each transition sat on a blank desktop for seconds.
    Window(
        // Hide to the tray rather than quit: the reminder scheduler and
        // the widget both need the process alive.
        onCloseRequest = { mainVisible = false },
        visible = mainVisible && !docked,
        title = "שעון מעורר - זמנים הלכתיים",
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
        // Keyed on visibility, not just the window: the window is now composed
        // before it is ever shown, and an invisible window has no HWND to
        // clear — the pass has to re-run once it actually appears.
        LaunchedEffect(window, mainVisible, docked) {
            if (WindowPinning.clearTopmost(window)) {
                println("main window was always-on-top; cleared")
            }

            // A SIDE PANEL, not a floating window — the owner's ask, and the
            // bookmark already implied it: the folded state lives flush
            // against the left edge, so the open state grows out of that same
            // edge instead of appearing as an unrelated window in the middle
            // of the screen. Flush left, the full height of the WORK AREA
            // (screen minus taskbar), like a system flyout.
            //
            // Set from INSIDE the window, keyed on the AWT window itself. The
            // first attempt snapped from the application scope and did
            // nothing: that effect runs before Compose has created the window,
            // so the initial rememberWindowState values were applied on top of
            // it and the panel opened at Windows' cascade position (measured:
            // 48,48 at 400x560 instead of 0,0 at 400x<work area>).
            if (mainVisible && !docked) {
                val wa = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment().maximumWindowBounds
                mainState.placement = WindowPlacement.Floating
                mainState.position = WindowPosition(wa.x.dp, wa.y.dp)
                mainState.size = DpSize(tab.width, wa.height.dp)

                // AND the AWT window directly, which is what actually makes
                // this stick. Setting the Compose state alone loses a race on
                // the FIRST open: when Compose realises a window whose
                // position is still PlatformDefault it writes the window's
                // real bounds BACK into the state, and that write can land
                // after this effect. Traced live — the state went from my
                // Absolute(0,0) to Absolute(48,48) and the window never moved;
                // only the second time the effect ran did it snap.
                //
                // Dp and AWT's user-space units are 1:1 here (a 400.dp window
                // reports width=400 in window.bounds even at 275% scaling),
                // which is the same assumption maximumWindowBounds is read
                // under, so no conversion belongs in either direction.
                window.setBounds(wa.x, wa.y, tab.width.value.toInt(), wa.height)
            }
            // A floor, not a preference. Below roughly this the month grid
            // loses a column and the settings chips stop wrapping into
            // anything readable, so dragging smaller is simply refused
            // rather than allowed to produce a broken layout.
            // Must sit below the narrowest tab (320) or AWT silently clamps
            // the panel wider than asked and the measurement above is moot.
            window.minimumSize = java.awt.Dimension(300, 400)
        }

        ZmanimDesktopTheme {
            // Rounded ONLY on the side that faces the desktop. The left side
            // is the screen edge the panel grows out of, and a rounded corner
            // there would read as a window hovering NEAR the edge rather than
            // a panel attached to it — the same rule the bookmark follows.
            // Under the app's forced RTL, topStart/bottomStart ARE the right-
            // hand corners.
            val shape = RoundedCornerShape(topStart = WINDOW_CORNER, bottomStart = WINDOW_CORNER)
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
    // Every width here is the MEASURED floor, not a guess: each pane was
    // rendered offscreen at a sweep of widths and read for clipping.
    //   320 — the zman list. At 300 the long labels ("פלג המנחה (מהשקיעה)",
    //         "הנץ החמה (מישורי)") touch the row edge with no margin left.
    //   660 — the calendar: the fixed zmanim column PLUS room for the month.
    //         The two sides used to split the window 45/55, which meant
    //         opening the calendar re-proportioned the zmanim list instead of
    //         adding to it. Swept 620/660/700 and read each.
    //   560 — settings. Verified clean; both card columns still breathe.
    ZMANIM("זמני היום", ZMANIM_COLUMN_WIDTH),
    CALENDAR("לוח שנה", 660.dp),
    // The SAME column as the zmanim list, deliberately: the two are read one
    // after the other ("what is next" then "what did I set for it"), and a
    // panel that changed width between them would make the whole window jump
    // on every switch.
    ALERTS("התראות", ZMANIM_COLUMN_WIDTH),

    /**
     * Reached by the gear, NOT by the pill. Settings is a place you visit to
     * change something and then leave; the other three are places you look at.
     * Giving it a quarter of a permanent segmented control implied it was a
     * destination of the same rank, and cost the three real ones a quarter of
     * their width apiece.
     */
    SETTINGS("הגדרות", 560.dp),
    ;

    companion object {
        /** The three that appear in the pill. Settings is deliberately absent. */
        val PILL: List<MainTab> = listOf(ZMANIM, CALENDAR, ALERTS)
    }
}

@Composable
private fun MainWindowContent(
    service: DesktopZmanimService,
    scheduler: ReminderScheduler,
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Set by a bell in the zmanim list; consumed by the alerts pane, which
    // opens its editor prefilled with that zman.
    var pendingAlertKind by remember { mutableStateOf<ZmanKind?>(null) }

    fun goTo(t: MainTab) {
        // Leaving settings may have changed the city, the halachic offsets or
        // the alert set; tell the scheduler now rather than making the user
        // wait for the next tick to notice.
        if (tab == MainTab.SETTINGS && t != MainTab.SETTINGS) scheduler.invalidate()
        onTabChange(t)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // A segmented pill rather than Material's TabRow. The stock TabRow
            // is a full-width strip with an underline — the visual language of
            // a browser, not of a small always-there panel. A pill track with
            // a filled thumb reads instantly as "one of three", costs less
            // height, and matches the rounded shell it lives in.
            Row(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(cs.surfaceVariant.copy(alpha = 0.55f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MainTab.PILL.forEach { t ->
                    val selected = tab == t
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) cs.primaryContainer else Color.Transparent)
                            .clickable { goTo(t) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            // LAST child, so under the app's forced RTL it lands on the LEFT —
            // the far side from where reading starts, which is where a utility
            // control belongs.
            RoundIconButton(
                icon = Icons.Outlined.Settings,
                description = "הגדרות",
                size = 30.dp,
                tint = if (tab == MainTab.SETTINGS) cs.onPrimaryContainer else cs.onSurfaceVariant,
                onClick = { goTo(if (tab == MainTab.SETTINGS) MainTab.ZMANIM else MainTab.SETTINGS) },
            )
        }

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                MainTab.ZMANIM -> ZmanimPane(service) { kind ->
                    pendingAlertKind = kind
                    goTo(MainTab.ALERTS)
                }
                MainTab.CALENDAR -> CalendarPane(service)
                MainTab.ALERTS -> AlertsPane(
                    service = service,
                    prefillKind = pendingAlertKind,
                    onPrefillConsumed = { pendingAlertKind = null },
                )
                MainTab.SETTINGS -> SettingsPane(service)
            }
        }
    }
}

/** The window's corner radius — the shell is transparent so these are real. */
private val WINDOW_CORNER = 14.dp
