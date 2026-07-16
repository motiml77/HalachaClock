package com.zmanimclock.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val labelHebrew: String, val icon: ImageVector) {
    data object Zmanim : Screen("zmanim", "זמנים", Icons.Filled.AccessTime)
    data object Alarms : Screen("alarms", "מעורר", Icons.Filled.Alarm)
    data object Settings : Screen("settings", "הגדרות", Icons.Filled.Settings)

    companion object {
        val bottomBarScreens = listOf(Zmanim, Alarms, Settings)
    }
}
