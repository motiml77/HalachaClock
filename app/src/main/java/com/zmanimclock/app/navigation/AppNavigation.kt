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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zmanimclock.app.feature.alarms.data.AlarmType
import com.zmanimclock.app.feature.alarms.presentation.AlarmEditScreen
import com.zmanimclock.app.feature.alarms.presentation.AlarmsScreen
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
            composable(Screen.Zmanim.route) {
                HomeScreen(
                    onCreateZmanAlarm = { zman ->
                        navController.navigate("alarm_edit?type=ZMAN&zman=$zman")
                    },
                )
            }
            composable(Screen.Alarms.route) {
                AlarmsScreen(
                    onCreateAlarm = { type ->
                        navController.navigate("alarm_edit?type=${type.name}")
                    },
                    onEditAlarm = { id ->
                        navController.navigate("alarm_edit?alarmId=$id")
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onOpenCityPicker = { navController.navigate("city_picker") })
            }
            composable("city_picker") {
                CityPickerScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "alarm_edit?type={type}&alarmId={alarmId}&zman={zman}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = "FIXED" },
                    navArgument("alarmId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("zman") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val args = entry.arguments
                val type = runCatching {
                    AlarmType.valueOf(args?.getString("type") ?: "FIXED")
                }.getOrDefault(AlarmType.FIXED)
                val alarmId = args?.getLong("alarmId")?.takeIf { it >= 0 }
                val zman = args?.getString("zman")?.takeIf { it.isNotBlank() }
                AlarmEditScreen(
                    type = type,
                    alarmId = alarmId,
                    preselectedZman = zman,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
