package com.zmanimclock.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val labelHebrew: String, val icon: ImageVector) {
    data object Zmanim : Screen("zmanim", "זמנים", Icons.Filled.Schedule)
    data object Calendar : Screen("calendar", "לוח שנה", Icons.Filled.CalendarMonth)
    data object Alarms : Screen("alarms", "מעורר", Icons.Filled.Alarm)
    /** Shown only when "מצב נשי" is enabled — see AppNavigation, not in [bottomBarScreens]. */
    data object WomensArea : Screen("womens_area", "איזור נשי", Icons.Filled.Lock)
    data object Settings : Screen("settings", "הגדרות", Icons.Filled.Settings)

    companion object {
        // RTL lays these out right-to-left, so the user reads:
        // זמנים · לוח שנה · מעורר — the tab surfaces. Settings lives as its
        // own icon button at the top of the app (see AppNavigation), not a
        // bottom tab. WomensArea is appended conditionally at the call site,
        // not listed here — this is the fixed, always-present set.
        val bottomBarScreens = listOf(Zmanim, Calendar, Alarms)
    }
}
