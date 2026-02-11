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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.notify2discord.app.ui.NotificationHistoryScreen
import com.notify2discord.app.ui.SelectedAppsScreen
import com.notify2discord.app.ui.SettingsScreen
import com.notify2discord.app.ui.SettingsViewModel
import com.notify2discord.app.ui.RulesScreen
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
            val historyReadMarkers = viewModel.historyReadMarkers.collectAsStateWithLifecycle()
            val operationMessage = viewModel.operationMessage.collectAsStateWithLifecycle()
            val showRestorePrompt = viewModel.showRestorePrompt.collectAsStateWithLifecycle()
            val hasInternalSnapshot = viewModel.hasInternalSnapshot.collectAsStateWithLifecycle()

            Notify2DiscordTheme(themeMode = state.value.themeMode) {
                val navController = rememberNavController()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val globalSnackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(operationMessage.value) {
                    val message = operationMessage.value ?: return@LaunchedEffect
                    globalSnackbarHostState.showSnackbar(message)
                    viewModel.clearOperationMessage()
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = globalSnackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            listOf(
                                Triple(Screen.NotificationHistory, "履歴", Icons.Default.Notifications),
                                Triple(Screen.SelectedApps, "転送アプリ", Icons.Default.Apps),
                                Triple(Screen.Rules, "ルール", Icons.Default.Tune),
                                Triple(Screen.Settings, "設定", Icons.Default.Settings)
                            ).forEach { (screen, label, icon) ->
                                NavigationBarItem(
                                    selected = currentBackStackEntry
                                        ?.destination
                                        ?.hierarchy
                                        ?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            // どのタブからでも状態を維持しつつ安定遷移させる
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
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
                        startDestination = Screen.NotificationHistory.route,
                        modifier = Modifier.fillMaxSize().padding(outerPadding)
                    ) {
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                state = state.value,
                                showRestorePrompt = showRestorePrompt.value,
                                hasInternalSnapshot = hasInternalSnapshot.value,
                                onDismissRestorePrompt = viewModel::dismissRestorePrompt,
                                onRestoreFromInternalSnapshot = viewModel::restoreFromInternalSnapshot,
                                onExportSettingsNow = viewModel::exportSettingsNow,
                                onExportSettingsToPickedFile = viewModel::exportSettingsToPickedFile,
                                onImportSettingsFromPickedFile = viewModel::importSettingsFromPickedFile,
                                onSaveWebhook = viewModel::saveWebhookUrl,
                                onRecheckWebhook = viewModel::recheckWebhook,
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
                                onOpenRules = { navController.navigate(Screen.Rules.route) },
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetRetentionDays = viewModel::setRetentionDays,
                                onCleanupExpired = viewModel::cleanupExpiredRecords
                            )
                        }

                        composable(Screen.SelectedApps.route) {
                            SelectedAppsScreen(
                                state = state.value,
                                apps = apps.value,
                                onToggleSelected = viewModel::toggleSelected,
                                onSetAppWebhook = viewModel::setAppWebhook,
                                onSetAppTemplate = viewModel::saveAppTemplate
                            )
                        }

                        composable(Screen.Rules.route) {
                            RulesScreen(
                                state = state.value,
                                onSaveDefaultTemplate = viewModel::saveDefaultTemplate,
                                onSaveRuleConfig = viewModel::saveRuleConfig,
                                onSaveRoutingRules = viewModel::saveRoutingRules,
                                onSetRulesSimpleMode = viewModel::setRulesSimpleMode
                            )
                        }

                        composable(Screen.NotificationHistory.route) {
                            NotificationHistoryScreen(
                                history = history.value,
                                readMarkers = historyReadMarkers.value,
                                onDeleteRecord = viewModel::deleteNotificationRecord,
                                onDeleteRecords = viewModel::deleteNotificationRecords,
                                onClearAll = viewModel::clearNotificationHistory,
                                onClearByApp = viewModel::clearNotificationHistoryByApp,
                                onClearByApps = viewModel::clearNotificationHistoryByApps,
                                onMarkAppAsRead = viewModel::markAppHistoryRead
                            )
                        }
                    }
                }
            }
        }
    }
}
