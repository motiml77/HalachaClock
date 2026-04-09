package com.zmanimclock.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zmanimclock.app.feature.alerts.presentation.AlertsScreen
import com.zmanimclock.app.feature.calendar.presentation.CalendarScreen
import com.zmanimclock.app.feature.settings.presentation.CityPickerScreen
import com.zmanimclock.app.feature.settings.presentation.SettingsScreen
import com.zmanimclock.app.feature.zmanim.presentation.HomeScreen

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    val bottomNavItems = listOf(
        BottomNavItem("זמנים", Icons.Default.Schedule, Screen.Home.route),
        BottomNavItem("לוח", Icons.Default.CalendarMonth, Screen.Calendar.route),
        BottomNavItem("התראות", Icons.Default.Notifications, Screen.Alerts.route),
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val showBottomBar = bottomNavItems.any { item ->
                currentDestination?.hierarchy?.any { it.route == item.route } == true
            }

            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToAlertEditor = { zmanId ->
                        navController.navigate(Screen.AlertEditor.createRoute(zmanId))
                    },
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen() // ViewModel injected via hiltViewModel()
            }
            composable(Screen.Alerts.route) {
                AlertsScreen(
                    onNavigateToAlertEditor = { zmanId ->
                        navController.navigate(Screen.AlertEditor.createRoute(zmanId))
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCityPicker = { navController.navigate(Screen.CityPicker.route) },
                )
            }
            composable(Screen.CityPicker.route) {
                CityPickerScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}
