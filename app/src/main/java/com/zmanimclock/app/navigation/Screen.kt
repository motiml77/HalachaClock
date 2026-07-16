package com.zmanimclock.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val labelHebrew: String, val icon: ImageVector) {
    data object Zmanim : Screen("zmanim", "זמנים", Icons.Filled.AccessTime)
    data object Alerts : Screen("alerts", "התראות", Icons.Filled.Notifications)
    data object Settings : Screen("settings", "הגדרות", Icons.Filled.Settings)

    companion object {
        val bottomBarScreens = listOf(Zmanim, Alerts, Settings)
    }
}
