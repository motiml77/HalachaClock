package com.zmanimclock.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.zmanimclock.app.feature.calendar.presentation.CalendarScreen
import com.zmanimclock.app.feature.omer.OmerPromptDialog
import com.zmanimclock.app.feature.omer.OmerPromptViewModel
import com.zmanimclock.app.feature.onboarding.OnboardingScreen
import com.zmanimclock.app.feature.settings.presentation.CityPickerScreen
import com.zmanimclock.app.feature.settings.presentation.SettingsScreen
import com.zmanimclock.app.feature.womensarea.presentation.WomensAreaGateViewModel
import com.zmanimclock.app.feature.womensarea.presentation.WomensAreaHistoryScreen
import com.zmanimclock.app.feature.womensarea.presentation.WomensAreaHostScreen
import com.zmanimclock.app.feature.zmanim.presentation.HomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // "מצב נשי" — the tab only appears once enabled in Settings (and its own
    // PIN/biometric setup completed); the route itself is always registered
    // below regardless, since WomensAreaHostScreen's own gate is the real
    // access control every time it's entered, not this tab's visibility.
    val womensAreaSecurity: WomensAreaGateViewModel = hiltViewModel()
    val womensAreaEnabled by womensAreaSecurity.security.collectAsStateWithLifecycle()
    val bottomBarScreens = remember(womensAreaEnabled.enabled) {
        if (womensAreaEnabled.enabled) {
            // [Zmanim, Calendar, Alarms] -> appended after Alarms.
            Screen.bottomBarScreens + Screen.WomensArea
        } else {
            Screen.bottomBarScreens
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomBarScreens.forEach { screen ->
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
                        label = { Text(screen.labelHebrew, maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
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
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
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
                    onCreateShabbatAlarm = {
                        navController.navigate("alarm_edit?type=ZMAN&shabbat=true")
                    },
                    onEditAlarm = { id ->
                        navController.navigate("alarm_edit?alarmId=$id")
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenCityPicker = { navController.navigate("city_picker") },
                    onOpenPermissions = { navController.navigate("permissions") },
                    // WomensAreaHostScreen shows its own setup step first
                    // whenever setupComplete is false — same route the tab
                    // itself uses, no separate setup route needed.
                    onOpenWomensAreaSetup = { navController.navigate(Screen.WomensArea.route) },
                )
            }
            composable(Screen.WomensArea.route) {
                WomensAreaHostScreen(onOpenHistory = { navController.navigate("womens_area_history") })
            }
            composable("womens_area_history") {
                WomensAreaHistoryScreen(onBack = { navController.popBackStack() })
            }
            composable("permissions") {
                // The same wizard the first launch shows. Reachable forever,
                // because every grant here is answered outside the app and can
                // be denied on first run or revoked from Android's settings
                // later — and until now that left no way back in.
                OnboardingScreen(
                    onDone = { navController.popBackStack() },
                    isFirstRun = false,
                )
            }
            composable("city_picker") {
                CityPickerScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "alarm_edit?type={type}&alarmId={alarmId}&zman={zman}&shabbat={shabbat}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = "FIXED" },
                    navArgument("alarmId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("zman") { type = NavType.StringType; defaultValue = "" },
                    navArgument("shabbat") { type = NavType.BoolType; defaultValue = false },
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
                    shabbatPreset = args?.getBoolean("shabbat") == true,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // The once-a-season omer offer floats above whatever tab is showing.
        val omerPrompt: OmerPromptViewModel = hiltViewModel()
        val showOmerPrompt by omerPrompt.showPrompt.collectAsStateWithLifecycle()
        if (showOmerPrompt) {
            OmerPromptDialog(
                onEnable = { omerPrompt.enable() },
                onDismiss = { omerPrompt.dismiss() },
            )
        }
    }
}
