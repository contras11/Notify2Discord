package com.notify2discord.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.notify2discord.app.ui.NotificationHistoryScreen
import com.notify2discord.app.ui.SelectedAppsScreen
import com.notify2discord.app.ui.SettingsScreen
import com.notify2discord.app.ui.SettingsViewModel
import com.notify2discord.app.ui.navigation.Screen
import com.notify2discord.app.ui.theme.Notify2DiscordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val state = viewModel.state.collectAsStateWithLifecycle()
            val apps = viewModel.apps.collectAsStateWithLifecycle()
            val history = viewModel.notificationHistory.collectAsStateWithLifecycle()

            Notify2DiscordTheme(themeMode = state.value.themeMode) {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Settings.route
                ) {
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            state = state.value,
                            onSaveWebhook = viewModel::saveWebhookUrl,
                            onToggleForwarding = viewModel::setForwardingEnabled,
                            onOpenNotificationAccess = {
                                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onTestSend = viewModel::sendTestNotification,
                            onNavigateToSelectedApps = {
                                navController.navigate(Screen.SelectedApps.route)
                            },
                            onNavigateToHistory = {
                                navController.navigate(Screen.NotificationHistory.route)
                            },
                            onRequestIgnoreBatteryOptimizations = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                    intent.data = Uri.parse("package:$packageName")
                                    startActivity(intent)
                                }
                            },
                            onSetThemeMode = viewModel::setThemeMode
                        )
                    }

                    composable(Screen.SelectedApps.route) {
                        SelectedAppsScreen(
                            state = state.value,
                            apps = apps.value,
                            onToggleSelected = viewModel::toggleSelected,
                            onSetAppWebhook = viewModel::setAppWebhook,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable(Screen.NotificationHistory.route) {
                        NotificationHistoryScreen(
                            history = history.value,
                            onDeleteRecord = viewModel::deleteNotificationRecord,
                            onClearAll = viewModel::clearNotificationHistory,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
