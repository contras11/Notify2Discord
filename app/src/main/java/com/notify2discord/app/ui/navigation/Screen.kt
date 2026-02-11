package com.notify2discord.app.ui.navigation

sealed class Screen(val route: String) {
    object Settings : Screen("settings")
    object SelectedApps : Screen("selected_apps")
    object NotificationHistory : Screen("notification_history")
    object Rules : Screen("rules")
    object Battery : Screen("battery")
}
