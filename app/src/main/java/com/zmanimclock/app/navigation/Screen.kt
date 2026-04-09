package com.zmanimclock.app.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Calendar : Screen("calendar")
    data object Alerts : Screen("alerts")
    data object Settings : Screen("settings")
    data object AlertEditor : Screen("alert_editor/{zmanId}") {
        fun createRoute(zmanId: String = "") = "alert_editor/$zmanId"
    }
    data object CityPicker : Screen("city_picker")
}
