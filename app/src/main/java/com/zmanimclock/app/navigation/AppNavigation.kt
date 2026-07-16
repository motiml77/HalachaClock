package com.zmanimclock.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zmanimclock.app.feature.alerts.presentation.AlertsScreen
import com.zmanimclock.app.feature.settings.presentation.CityPickerScreen
import com.zmanimclock.app.feature.settings.presentation.SettingsScreen
import com.zmanimclock.app.feature.zmanim.presentation.HomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.bottomBarScreens.forEach { screen ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.labelHebrew) },
                        label = { Text(screen.labelHebrew) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Zmanim.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Zmanim.route) { HomeScreen() }
            composable(Screen.Alerts.route) { AlertsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(onOpenCityPicker = { navController.navigate("city_picker") })
            }
            composable("city_picker") {
                CityPickerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
