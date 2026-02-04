package com.notify2discord.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.notify2discord.app.R
import com.notify2discord.app.data.SettingsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onSaveWebhook: (String) -> Unit,
    onToggleForwarding: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTestSend: () -> Unit,
    onNavigateToSelectedApps: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onSetThemeMode: (com.notify2discord.app.data.ThemeMode) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var webhookText by rememberSaveable(state.webhookUrl) {
        mutableStateOf(state.webhookUrl)
    }
    // 保存済みの場合は表示モード、未設定・クリア後は入力モード
    var isEditing by rememberSaveable {
        mutableStateOf(state.webhookUrl.isBlank())
    }
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }
    var pendingSaveMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.webhookUrl) {
        if (webhookText != state.webhookUrl) {
            webhookText = state.webhookUrl
        }
        // 空になった時は入力モード、DataStore から読み込んで非空になった時は表示モード
        isEditing = state.webhookUrl.isBlank()
    }

    val savedMessage = stringResource(id = R.string.webhook_saved_message)
    val clearedMessage = stringResource(id = R.string.webhook_cleared_message)
    val copiedMessage = stringResource(id = R.string.webhook_copied_message)

    LaunchedEffect(state.webhookUrl, pendingSaveUrl) {
        val target = pendingSaveUrl ?: return@LaunchedEffect
        if (state.webhookUrl == target) {
            snackbarHostState.showSnackbar(pendingSaveMessage ?: savedMessage)
            pendingSaveUrl = null
            pendingSaveMessage = null
            // 保存成功時に表示モードに切り替え（URLが非空の場合）
            if (state.webhookUrl.isNotBlank()) {
                isEditing = false
            }
        }
    }

    val requestSave: (String) -> Unit = { url ->
        pendingSaveUrl = url
        pendingSaveMessage = if (url.isBlank()) clearedMessage else savedMessage
        onSaveWebhook(url)
    }

    val isWebhookValid = remember(webhookText) {
        webhookText.startsWith("https://discord.com/api/webhooks/") ||
            webhookText.startsWith("https://discordapp.com/api/webhooks/")
    }
    val isDirty = webhookText != state.webhookUrl

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.settings_title)) })
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.notification_access_help),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onOpenNotificationAccess) {
                Text(text = stringResource(id = R.string.open_notification_access))
            }

            Button(onClick = onRequestIgnoreBatteryOptimizations) {
                Text(text = "バッテリー最適化を無効にする")
            }

            Divider()

            // --- Webhook 表示・入力 ---
            if (!isEditing && state.webhookUrl.isNotBlank()) {
                // 表示モード：マスク済みURL + コピー・クリア・編集ボタン
                Text(text = stringResource(id = R.string.webhook_saved_section))
                Text(
                    text = maskWebhookUrl(state.webhookUrl),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(state.webhookUrl))
                        scope.launch {
                            snackbarHostState.showSnackbar(copiedMessage)
                        }
                    }) {
                        Text(text = stringResource(id = R.string.webhook_copy))
                    }
                    Button(onClick = { requestSave("") }) {
                        Text(text = stringResource(id = R.string.webhook_clear))
                    }
                    Button(onClick = { isEditing = true }) {
                        Text(text = stringResource(id = R.string.webhook_edit))
                    }
                }
                Button(onClick = onTestSend) {
                    Text(text = stringResource(id = R.string.test_send))
                }
            } else {
                // 入力モード：テキストフィールド + ステータス + 保存ボタン
                OutlinedTextField(
                    value = webhookText,
                    onValueChange = { webhookText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(id = R.string.webhook_label)) },
                    isError = webhookText.isNotBlank() && !isWebhookValid
                )

                Text(
                    text = when {
                        state.webhookUrl.isBlank() -> stringResource(id = R.string.webhook_status_empty)
                        isDirty -> stringResource(id = R.string.webhook_status_unsaved)
                        else -> stringResource(id = R.string.webhook_status_saved)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isDirty -> MaterialTheme.colorScheme.error
                        state.webhookUrl.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                if (isDirty && state.webhookUrl.isNotBlank()) {
                    TextButton(onClick = { webhookText = state.webhookUrl }) {
                        Text(text = stringResource(id = R.string.webhook_revert))
                    }
                }

                if (webhookText.isBlank()) {
                    Text(
                        text = stringResource(id = R.string.webhook_empty_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (!isWebhookValid) {
                    Text(
                        text = stringResource(id = R.string.webhook_invalid_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { requestSave(webhookText) }) {
                        Text(text = stringResource(id = R.string.webhook_save))
                    }
                    if (state.webhookUrl.isNotBlank()) {
                        TextButton(onClick = { isEditing = false }) {
                            Text(text = "キャンセル")
                        }
                    }
                }
            }

            Divider()

            // 転送ON/OFF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.forwarding_toggle))
                Switch(
                    checked = state.forwardingEnabled,
                    onCheckedChange = onToggleForwarding
                )
            }

            Divider()

            // テーマ選択
            Column {
                Text(
                    text = "テーマ設定",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.notify2discord.app.data.ThemeMode.values().forEach { mode ->
                        val isSelected = state.themeMode == mode
                        Button(
                            onClick = { onSetThemeMode(mode) },
                            modifier = Modifier.weight(1f),
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(
                                text = when (mode) {
                                    com.notify2discord.app.data.ThemeMode.LIGHT -> "ライト"
                                    com.notify2discord.app.data.ThemeMode.DARK -> "ダーク"
                                    com.notify2discord.app.data.ThemeMode.SYSTEM -> "自動"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Divider()

            // 転送アプリ・Webhook設定
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.selected_apps_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.selected_apps_count, state.selectedPackages.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onNavigateToSelectedApps) {
                    Text("設定")
                }
            }

            // 通知履歴
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.notification_history_section),
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = onNavigateToHistory) {
                    Text(text = stringResource(id = R.string.notification_history_button))
                }
            }
        }
    }
}

// 表示用にトークン部分をマスクする
private fun maskWebhookUrl(url: String): String {
    if (url.isBlank()) return ""
    val marker = "/api/webhooks/"
    val markerIndex = url.indexOf(marker)
    return if (markerIndex >= 0) {
        val head = url.substring(0, markerIndex + marker.length)
        val tail = if (url.length > 6) url.takeLast(6) else url
        "$head****$tail"
    } else {
        val head = url.take(16)
        val tail = if (url.length > 6) url.takeLast(6) else ""
        "$head****$tail"
    }
}
