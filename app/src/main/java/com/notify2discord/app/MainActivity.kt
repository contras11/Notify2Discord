package com.notify2discord.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = currentBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                Triple(Screen.Settings, "設定", Icons.Default.Settings),
                                Triple(Screen.SelectedApps, "転送アプリ", Icons.Default.Apps),
                                Triple(Screen.NotificationHistory, "履歴", Icons.Default.Notifications)
                            ).forEach { (screen, label, icon) ->
                                NavigationBarItem(
                                    selected = currentDestination == screen.route,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Settings.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                ) { outerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Settings.route,
                        modifier = Modifier.fillMaxSize().padding(outerPadding)
                    ) {
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                state = state.value,
                                onSaveWebhook = viewModel::saveWebhookUrl,
                                onToggleForwarding = viewModel::setForwardingEnabled,
                                onOpenNotificationAccess = {
                                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                onTestSend = viewModel::sendTestNotification,
                                onRequestIgnoreBatteryOptimizations = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
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
                                onSetAppWebhook = viewModel::setAppWebhook
                            )
                        }

                        composable(Screen.NotificationHistory.route) {
                            NotificationHistoryScreen(
                                history = history.value,
                                onDeleteRecord = viewModel::deleteNotificationRecord,
                                onClearAll = viewModel::clearNotificationHistory,
                                onClearByApp = viewModel::clearNotificationHistoryByApp
                            )
                        }
                    }
                }
            }
        }
    }
}
