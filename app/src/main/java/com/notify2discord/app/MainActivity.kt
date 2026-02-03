package com.notify2discord.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notify2discord.app.ui.SettingsScreen
import com.notify2discord.app.ui.SettingsViewModel
import com.notify2discord.app.ui.theme.Notify2DiscordTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val state = viewModel.state.collectAsStateWithLifecycle()
            val apps = viewModel.apps.collectAsStateWithLifecycle()

            Notify2DiscordTheme {
                SettingsScreen(
                    state = state.value,
                    apps = apps.value,
                    onSaveWebhook = viewModel::saveWebhookUrl,
                    onToggleForwarding = viewModel::setForwardingEnabled,
                    onOpenNotificationAccess = {
                        // 通知アクセス設定画面を開く
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onTestSend = viewModel::sendTestNotification,
                    onToggleExclude = viewModel::toggleExcluded
                )
            }
        }
    }
}
