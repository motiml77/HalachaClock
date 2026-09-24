package com.zmanimclock.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val labelHebrew: String, val icon: ImageVector) {
    data object Zmanim : Screen("zmanim", "זמנים", Icons.Filled.Schedule)
    data object Calendar : Screen("calendar", "לוח שנה", Icons.Filled.CalendarMonth)
    data object Alarms : Screen("alarms", "מעורר", Icons.Filled.Alarm)
    data object Settings : Screen("settings", "הגדרות", Icons.Filled.Settings)

    companion object {
        // RTL lays these out right-to-left, so the user reads:
        // זמנים · לוח שנה · מעורר — the tab surfaces. Settings lives as its
        // own icon button inside the Zmanim tab's hero (see HomeScreen), not
        // a bottom tab.
        val bottomBarScreens = listOf(Zmanim, Calendar, Alarms)
    }
}
