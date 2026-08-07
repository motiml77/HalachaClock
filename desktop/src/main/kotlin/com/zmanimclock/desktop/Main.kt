package com.zmanimclock.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.zmanimclock.desktop.data.DesktopPrefs
import com.zmanimclock.desktop.data.DesktopZmanimService
import com.zmanimclock.desktop.ui.CalendarPane
import com.zmanimclock.desktop.ui.SettingsPane
import com.zmanimclock.desktop.ui.ZmanimPane

/**
 * "שעון זמנים" for Windows — the main window.
 *
 * A wide window rather than a phone-shaped one, which removes a whole
 * mechanism: on Android the calendar has to collapse to a single week row so
 * that a month grid and seventeen zman rows can share a narrow screen. Here
 * they sit side by side and the collapsing machinery is simply not needed.
 *
 * Deliberately a NORMAL, focusable window. The desktop widget (a separate,
 * borderless one) cannot take keyboard input, so anything involving typing or
 * arrow keys — the calendar, city search, settings — belongs here.
 */
fun main() = application {
    val prefs = remember { DesktopPrefs.load() }
    val service = remember { DesktopZmanimService(prefs) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "שעון זמנים",
        icon = painterResource("branding/logo.png"),
        state = rememberWindowState(width = 860.dp, height = 600.dp),
    ) {
        ZmanimDesktopTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MainWindowContent(service)
            }
        }
    }
}

private enum class MainTab(val label: String) {
    ZMANIM("זמני היום"),
    CALENDAR("לוח שנה"),
    SETTINGS("הגדרות"),
}

@Composable
private fun MainWindowContent(service: DesktopZmanimService) {
    var tab by remember { mutableStateOf(MainTab.ZMANIM) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal, modifier = Modifier.fillMaxWidth()) {
            MainTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
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
