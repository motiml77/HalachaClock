package com.zmanimclock.desktop.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.Ext
import com.zmanimclock.desktop.ZmanNumberFamily
import com.zmanimclock.desktop.ZmanimDesktopTheme
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.ui.ZmanListRow
import com.zmanimclock.desktop.ui.countdown
import kotlinx.coroutines.delay
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.Properties

/**
 * Rejected once for being far too large. This is a glance, not a screen: the
 * Hebrew date, what is coming, and a short list — at the same density as the
 * main window's zman rows, in bodySmall/labelSmall.
 */
private val WIDGET_WIDTH = 260.dp
private val WIDGET_HEIGHT = 220.dp

/** Below this the card cannot render its own text; a restore is clamped up to it. */
private const val MIN_WIDGET_PIXELS = 160

/** Dragging fires componentMoved continuously; the file is written at most this often. */
private const val PLACEMENT_FLUSH_MILLIS = 2_000L

/**
 * The desktop widget — ONE window that serves both modes the user asked for.
 *
 * | mode     | what it gives            | how                                  |
 * |----------|--------------------------|--------------------------------------|
 * | floating | always visible, on top   | `alwaysOnTop = true`                 |
 * | pinned   | sits on the desktop      | `alwaysOnTop = false` + HWND_BOTTOM  |
 *
 * The temptation is to build two windows. It is unnecessary and it would create
 * two interaction models for one component, which is a bug in waiting.
 * Everything below is shared: undecorated, transparent, resizable, and — in
 * BOTH modes — WS_EX_TOOLWINDOW and WS_EX_NOACTIVATE. The only differences are
 * the `alwaysOnTop` boolean and, when pinned, the polling timer in
 * [WindowPinning]. Switching modes at runtime therefore starts or stops that
 * timer and flips one parameter; the window is never torn down.
 *
 * THREE CONSEQUENCES OF WS_EX_NOACTIVATE, ALL DESIGNED FOR RATHER THAN PATCHED:
 *
 *  1. No keyboard input, ever. Mouse clicks and dragging still work, so this is
 *     a read-only card plus clicks. Anything involving typing or arrow keys —
 *     city search, the calendar, settings — lives in the main window, which is
 *     why the two-window split was fixed early rather than discovered here.
 *  2. Clicks do not pass through transparent regions. JetBrains closed that
 *     request (CMP-6036) as Obsolete on 2025-12-15, so it is permanent. Hence
 *     an OPAQUE rounded card, not an irregular shape with a large transparent
 *     area.
 *  3. Win+D will hide the widget when pinned, and no supported API prevents it.
 *     It comes back when the desktop is clicked again.
 *
 * Position is stored as screen + offset rather than absolute coordinates, and
 * re-validated on every start — see [WidgetPlacement].
 */
@Composable
fun ApplicationScope.ZmanimWidgetWindow(
    service: DesktopZmanimService,
    onOpenMain: () -> Unit,
) {
    // Idempotent to call unconditionally from the application scope: the window
    // simply does not exist while the widget is switched off.
    if (!service.prefs.widgetVisible) return

    val pinRequested = service.prefs.widgetPinnedToDesktop

    // Pinning is the one thing that can fail in a way the user must be told
    // about. When it does, the widget falls back to floating and says so on the
    // card, rather than sitting there quietly not being pinned.
    var pinFailed by remember { mutableStateOf(false) }

    val state = rememberWindowState(
        // PlatformDefault, not an absolute position: the saved placement is
        // applied below in device pixels, where monitor bounds are also
        // expressed. Handing Compose a Dp position as well would mean two
        // authorities for one number.
        position = WindowPosition.PlatformDefault,
        size = DpSize(WIDGET_WIDTH, WIDGET_HEIGHT),
    )

    Window(
        // Closing hides the widget; it does not quit the app. The tray and the
        // main window are still there.
        onCloseRequest = { service.update { it.copy(widgetVisible = false) } },
        state = state,
        title = "שעון מעורר - זמנים הלכתיים",
        undecorated = true,
        transparent = true,
        resizable = true,
        // The Compose-level half of "never steal focus"; WS_EX_NOACTIVATE is
        // the Win32 half, and both are applied in both modes.
        focusable = false,
        alwaysOnTop = !pinRequested || pinFailed,
    ) {
        // Placement: restore once, then flush changes on a slow loop.
        LaunchedEffect(window) {
            if (!awaitDisplayable(window)) return@LaunchedEffect
            WidgetPlacement.restore(window)
            // Restoring moves the window, which marks it dirty; clear that so
            // the first flush does not rewrite what was just read.
            WidgetPlacement.clearDirty()
            while (true) {
                delay(PLACEMENT_FLUSH_MILLIS)
                if (WidgetPlacement.consumeDirty()) WidgetPlacement.capture(window)
            }
        }

        // Styles and z-order. Keyed on the mode so a settings change re-runs it.
        LaunchedEffect(window, pinRequested) {
            if (!awaitDisplayable(window)) return@LaunchedEffect

            // Degradation path: if WS_EX_NOACTIVATE cannot be applied, retry
            // without it. In floating mode the only cost is that clicking the
            // widget steals focus. In pinned mode it matters more, but the
            // fallback below covers the case where pinning itself then fails.
            if (!WindowPinning.applyWidgetStyles(window, noActivate = true)) {
                WindowPinning.applyWidgetStyles(window, noActivate = false)
            }

            pinFailed = pinRequested && !WindowPinning.setPinned(window, pinRequested)
        }

        DisposableEffect(window) {
            // An AWT listener rather than a snapshot of Compose's WindowState:
            // the window is also moved natively by WindowDraggableArea, and
            // this is the one place that sees every move regardless of origin.
            val listener = object : ComponentAdapter() {
                override fun componentMoved(e: ComponentEvent) = WidgetPlacement.markDirty()
                override fun componentResized(e: ComponentEvent) = WidgetPlacement.markDirty()
            }
            window.addComponentListener(listener)
            onDispose {
                window.removeComponentListener(listener)
                WidgetPlacement.capture(window)
                WindowPinning.stopTimer()
            }
        }

        ZmanimDesktopTheme {
            WidgetCard(service, onOpenMain, pinFailed)
        }
    }
}

/**
 * The card itself.
 *
 * The whole card is a drag handle. That rules out making the card body itself
 * clickable — a `clickable` child consumes the pointer-down that
 * [WindowDraggableArea] needs to start a drag, so "click anywhere to open" and
 * "drag anywhere to move" cannot both cover the same pixels. Opening the main
 * window therefore has its own small control in the header, which is also the
 * only part of the card that is not a drag handle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowScope.WidgetCard(
    service: DesktopZmanimService,
    onOpenMain: () -> Unit,
    pinFailed: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val ext = Ext.colors

    var now by remember { mutableStateOf(Instant.now()) }

    // The same wall-clock tick the main window uses: it lands on the second
    // boundary and self-corrects after the machine sleeps, instead of drifting
    // further out the longer the widget stays open.
    LaunchedEffect(Unit) {
        while (true) {
            val n = Instant.now()
            now = n
            delay(1_000L - (n.toEpochMilli() % 1_000L))
        }
    }

    val view = service.view(LocalDate.now(service.zone), now)
    val next = service.nextZman(now)
    // Same formatter as everywhere else, by construction: the string is taken
    // from the row the service already built rather than formatted again here.
    val nextTime = view.rows.firstOrNull { it.isNext }?.time
    val rows = view.rows.filter { it.kind.name in service.prefs.widgetZmanim }

    WindowDraggableArea(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(14.dp),
            color = cs.surface,
            contentColor = cs.onSurface,
            border = BorderStroke(1.dp, cs.outlineVariant),
        ) {
            Column(Modifier.fillMaxSize().padding(vertical = 8.dp)) {

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${view.hebrewDate} · ${view.cityName}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "פתיחה",
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenMain)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                    )
                }

                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ext.nextRow)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            // Null once the day's last zman has passed — said
                            // plainly rather than shown as a stale countdown.
                            if (next != null) "הזמן הבא · ${next.first.hebrewName}" else "היום הסתיים",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            nextTime ?: "—",
                            fontFamily = ZmanNumberFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                        )
                    }
                    if (next != null) {
                        Text(
                            "בעוד ${countdown(now, next.second)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 1.dp, color = cs.outlineVariant)

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (rows.isEmpty()) {
                        Text(
                            "לא נבחרו זמנים להצגה",
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        // The main window's row, in its compact form — one
                        // definition of what a zman row looks like.
                        rows.forEach { ZmanListRow(it, compact = true) }
                    }
                }

                if (pinFailed) {
                    Text(
                        "לא ניתן לנעוץ לשולחן העבודה — מוצג במצב צף",
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.deadline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Compose creates the window and shows it AFTER the first composition, so on
 * frame zero there is no native handle to style. Waits up to three seconds.
 */
private suspend fun awaitDisplayable(window: java.awt.Window): Boolean {
    repeat(60) {
        if (window.isDisplayable) return true
        delay(50)
    }
    return window.isDisplayable
}

/**
 * Where the widget sits, stored as MONITOR + OFFSET rather than absolute
 * coordinates.
 *
 * jpackage's launcher is already Per-Monitor-V2 DPI aware, so no manifest
 * patching is needed — but DPI awareness is not DPI correctness. Swing/AWT
 * historically handled only Per-Monitor v1, and the JetBrains Runtime rounds
 * fractional scale factors to integers. Add a monitor being unplugged, a
 * resolution change, or a laptop docked to a different desk, and an absolute
 * coordinate saved yesterday can be off-screen today — a widget nobody can find
 * and nobody can move back.
 *
 * So: remember which monitor, and where on it, and re-validate on every start.
 * Everything here is in AWT device pixels, the same space as GraphicsDevice
 * bounds and Window.getLocation, so no density conversion enters the arithmetic.
 */
private object WidgetPlacement {

    @Volatile
    private var dirty = false

    fun markDirty() {
        dirty = true
    }

    fun clearDirty() {
        dirty = false
    }

    fun consumeDirty(): Boolean {
        val d = dirty
        dirty = false
        return d
    }

    /** Beside settings.properties, but a separate file: placement is not a setting. */
    private fun file(): File = File(
        File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "HalachClock"),
        "widget.properties",
    )

    fun restore(window: java.awt.Window) {
        runCatching {
            val f = file()
            if (!f.exists()) return
            val p = Properties()
            f.inputStream().use { p.load(it) }

            val offsetX = p.getProperty("offsetX")?.toIntOrNull() ?: return
            val offsetY = p.getProperty("offsetY")?.toIntOrNull() ?: return
            val storedW = p.getProperty("width")?.toIntOrNull() ?: window.width
            val storedH = p.getProperty("height")?.toIntOrNull() ?: window.height
            val index = p.getProperty("screenIndex")?.toIntOrNull() ?: 0
            val signature = p.getProperty("screenBounds").orEmpty()

            val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            if (screens.isEmpty()) return

            // Geometry first, index second. Monitors get reordered by the OS far
            // more readily than they change resolution, so matching the saved
            // bounds finds the right screen even when its index moved. If
            // neither matches, the default placement is kept rather than
            // guessing — better centred than lost.
            val screen = screens.firstOrNull { signature.isNotEmpty() && signatureOf(it) == signature }
                ?: screens.getOrNull(index)
                ?: return

            val b = screen.defaultConfiguration.bounds
            val w = storedW.coerceIn(MIN_WIDGET_PIXELS, b.width)
            val h = storedH.coerceIn(MIN_WIDGET_PIXELS, b.height)
            // Clamped so the card is always fully on that monitor, whatever the
            // stored offset meant on the layout it was saved under.
            val x = (b.x + offsetX).coerceIn(b.x, b.x + b.width - w)
            val y = (b.y + offsetY).coerceIn(b.y, b.y + b.height - h)

            window.setSize(w, h)
            window.setLocation(x, y)
        }
    }

    fun capture(window: java.awt.Window) {
        runCatching {
            val gc = window.graphicsConfiguration ?: return
            val b = gc.bounds
            val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            val index = screens.indexOfFirst { it.defaultConfiguration.bounds == b }.coerceAtLeast(0)

            val p = Properties()
            p["screenIndex"] = index.toString()
            p["screenBounds"] = "${b.x},${b.y},${b.width},${b.height}"
            p["offsetX"] = (window.x - b.x).toString()
            p["offsetY"] = (window.y - b.y).toString()
            p["width"] = window.width.toString()
            p["height"] = window.height.toString()

            val f = file()
            f.parentFile?.mkdirs()
            f.outputStream().use { p.store(it, "HalachClock widget placement") }
        }
    }

    private fun signatureOf(device: GraphicsDevice): String =
        device.defaultConfiguration.bounds.let { "${it.x},${it.y},${it.width},${it.height}" }
}
