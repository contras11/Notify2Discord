package com.notify2discord.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notify2discord.app.BuildConfig
import com.notify2discord.app.R
import com.notify2discord.app.data.SettingsState
import com.notify2discord.app.data.ThemeMode
import com.notify2discord.app.data.WebhookHealthLevel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    showRestorePrompt: Boolean,
    hasInternalSnapshot: Boolean,
    onDismissRestorePrompt: () -> Unit,
    onRestoreFromInternalSnapshot: () -> Unit,
    onExportSettingsNow: () -> Unit,
    onExportSettingsToPickedFile: (Uri) -> Unit,
    onImportSettingsFromPickedFile: (Uri) -> Unit,
    onSaveWebhook: (String) -> Unit,
    onRecheckWebhook: (String) -> Unit,
    onToggleForwarding: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onTestSend: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenRules: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    onCleanupExpired: () -> Unit,
    onSaveBatteryReportConfig: (Boolean, Int) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(onExportSettingsToPickedFile)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportSettingsFromPickedFile)
    }
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm") }

    var webhookText by rememberSaveable(state.webhookUrl) {
        mutableStateOf(state.webhookUrl)
    }
    var isEditing by rememberSaveable {
        mutableStateOf(state.webhookUrl.isBlank())
    }
    var pendingSaveUrl by remember { mutableStateOf<String?>(null) }
    var pendingSaveMessage by remember { mutableStateOf<String?>(null) }
    var showWebhookHealthDetails by rememberSaveable { mutableStateOf(false) }
    var showExportOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var batteryReportEnabled by rememberSaveable(state.batteryReportConfig.enabled) {
        mutableStateOf(state.batteryReportConfig.enabled)
    }
    var batteryIntervalText by rememberSaveable(state.batteryReportConfig.intervalMinutes) {
        mutableStateOf(state.batteryReportConfig.intervalMinutes.toString())
    }

    LaunchedEffect(state.webhookUrl) {
        if (webhookText != state.webhookUrl) {
            webhookText = state.webhookUrl
        }
        isEditing = state.webhookUrl.isBlank()
        showWebhookHealthDetails = false
    }
    LaunchedEffect(state.batteryReportConfig) {
        batteryReportEnabled = state.batteryReportConfig.enabled
        batteryIntervalText = state.batteryReportConfig.intervalMinutes.toString()
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
    val webhookHealth = state.webhookHealthCache[state.webhookUrl]
    val batteryInterval = batteryIntervalText.toIntOrNull()
    val isBatteryIntervalValid = batteryInterval in 15..1440
    val isBatteryDirty = batteryReportEnabled != state.batteryReportConfig.enabled ||
        batteryIntervalText != state.batteryReportConfig.intervalMinutes.toString()
    val canSaveBatteryConfig = !batteryReportEnabled || isBatteryIntervalValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings_title)) },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(id = R.string.notification_access_help),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onOpenNotificationAccess,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.open_notification_access))
            }

            Button(
                onClick = onRequestIgnoreBatteryOptimizations,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "バッテリー最適化を無効にする")
            }

            Divider()

            if (!isEditing && state.webhookUrl.isNotBlank()) {
                Text(text = stringResource(id = R.string.webhook_saved_section))
                Text(
                    text = maskWebhookUrl(state.webhookUrl),
                    style = MaterialTheme.typography.bodySmall
                )
                AdaptiveActionGroup(maxItemsInRow = 3) { compact ->
                    Button(onClick = {
                        clipboardManager.setText(AnnotatedString(state.webhookUrl))
                        scope.launch {
                            snackbarHostState.showSnackbar(copiedMessage)
                        }
                    }, modifier = if (compact) Modifier.fillMaxWidth() else Modifier) {
                        Text(text = stringResource(id = R.string.webhook_copy))
                    }
                    Button(
                        onClick = { requestSave("") },
                        modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                    ) {
                        Text(text = stringResource(id = R.string.webhook_clear))
                    }
                    Button(
                        onClick = { isEditing = true },
                        modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                    ) {
                        Text(text = stringResource(id = R.string.webhook_edit))
                    }
                }
                Button(
                    onClick = onTestSend,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.test_send))
                }
                if (state.webhookUrl.isNotBlank()) {
                    TextButton(onClick = { onRecheckWebhook(state.webhookUrl) }) {
                        Text("Webhookを再検証")
                    }
                }
            } else {
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

                AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
                    Button(
                        onClick = { requestSave(webhookText) },
                        modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                    ) {
                        Text(text = stringResource(id = R.string.webhook_save))
                    }
                    if (state.webhookUrl.isNotBlank()) {
                        TextButton(
                            onClick = { isEditing = false },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                        ) {
                            Text(text = "キャンセル")
                        }
                    }
                }
            }

            if (webhookHealth != null) {
                val checkedTime = formatEpochMillis(webhookHealth.checkedAt, dateTimeFormatter)
                val successTime = formatEpochMillis(webhookHealth.lastDeliverySuccessAt, dateTimeFormatter)
                Text(
                    text = "Webhook状態: ${webhookHealth.effectiveState.label()} / 検証: ${webhookHealth.level.label()} (${webhookHealth.statusCode ?: "-"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = webhookHealth.effectiveState.color()
                )
                if (checkedTime != null) {
                    Text(
                        text = "最終検証: $checkedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val hasDetails = webhookHealth.message.isNotBlank() ||
                    webhookHealth.deliveryMessage.isNotBlank() ||
                    successTime != null
                if (hasDetails) {
                    TextButton(onClick = { showWebhookHealthDetails = !showWebhookHealthDetails }) {
                        Text(if (showWebhookHealthDetails) "詳細を隠す" else "詳細を表示")
                    }
                }
                if (showWebhookHealthDetails) {
                    if (webhookHealth.message.isNotBlank()) {
                        Text(
                            text = "検証詳細: ${webhookHealth.message}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (webhookHealth.deliveryMessage.isNotBlank()) {
                        Text(
                            text = "送信詳細: ${webhookHealth.deliveryMessage}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (successTime != null) {
                        Text(
                            text = "最終送信成功: $successTime",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Divider()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "設定バックアップ",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "手動で設定を保存・復元できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "既定の保存先: Documents/Notify2Discord",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
                        Button(
                            onClick = { showExportOptionsDialog = true },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                        ) {
                            Text("エクスポート")
                        }
                        Button(
                            onClick = {
                                importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                            },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                            colors = ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("インポート")
                        }
                    }
                    if (state.lastBackupAt != null) {
                        formatEpochMillis(state.lastBackupAt, dateTimeFormatter)?.let { timeText ->
                            Text(
                                text = "最終バックアップ: $timeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.lastManualBackupAt != null) {
                        formatEpochMillis(state.lastManualBackupAt, dateTimeFormatter)?.let { timeText ->
                            Text(
                                text = "最終手動バックアップ: $timeText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpenRules,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ルール設定を開く")
            }

            Divider()

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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.battery_report_section_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(id = R.string.battery_report_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(id = R.string.battery_report_toggle))
                    Switch(
                        checked = batteryReportEnabled,
                        onCheckedChange = { batteryReportEnabled = it }
                    )
                }
                OutlinedTextField(
                    value = batteryIntervalText,
                    onValueChange = { batteryIntervalText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(id = R.string.battery_report_interval_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = batteryReportEnabled && !isBatteryIntervalValid
                )
                if (batteryReportEnabled && !isBatteryIntervalValid) {
                    Text(
                        text = stringResource(id = R.string.battery_report_interval_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.battery_report_interval_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
                    Button(
                        onClick = {
                            val intervalToSave = batteryInterval ?: state.batteryReportConfig.intervalMinutes
                            onSaveBatteryReportConfig(batteryReportEnabled, intervalToSave)
                        },
                        enabled = canSaveBatteryConfig,
                        modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                    ) {
                        Text(stringResource(id = R.string.battery_report_save))
                    }
                    if (isBatteryDirty) {
                        TextButton(
                            onClick = {
                                batteryReportEnabled = state.batteryReportConfig.enabled
                                batteryIntervalText = state.batteryReportConfig.intervalMinutes.toString()
                            },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                        ) {
                            Text("変更を戻す")
                        }
                    }
                }
            }

            Divider()

            Column {
                Text(
                    text = "テーマ設定",
                    style = MaterialTheme.typography.titleMedium
                )
                AdaptiveActionGroup(maxItemsInRow = 3) { compact ->
                    ThemeMode.values().forEach { mode ->
                        val isSelected = state.themeMode == mode
                        Button(
                            onClick = { onSetThemeMode(mode) },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(
                                text = when (mode) {
                                    ThemeMode.LIGHT -> "ライト"
                                    ThemeMode.DARK -> "ダーク"
                                    ThemeMode.SYSTEM -> "自動"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Divider()

            Column {
                Text(
                    text = "履歴の保持期間",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "設定した日数より古い履歴は自動で削除されます。「無制限」の場合は削除しません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.padding(top = 8.dp))
                val retentionOptions = listOf(
                    7 to "1週間",
                    14 to "2週間",
                    30 to "1ヶ月",
                    90 to "3ヶ月",
                    -1 to "無制限"
                )
                AdaptiveActionGroup(maxItemsInRow = 3, horizontalSpacing = 4.dp) { compact ->
                    retentionOptions.forEach { (days, label) ->
                        val isSelected = state.retentionDays == days
                        Button(
                            onClick = { onSetRetentionDays(days) },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                            colors = if (isSelected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.padding(top = 8.dp))
                Button(
                    onClick = onCleanupExpired,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("古い履歴を今すぐ削除")
                }
            }

            Divider()

            Text(
                text = "リンク",
                style = MaterialTheme.typography.titleMedium
            )
            val links = listOf(
                "GitHubリポジトリ" to "https://github.com/contras11/Notify2Discord",
                "個人開発ポータル" to "https://remudo.com/",
                "X (Twitter)" to "https://x.com/remudo_"
            )
            links.forEach { (label, url) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }

            Spacer(modifier = Modifier.padding(top = 16.dp))
            Text(
                text = "バージョン ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExportOptionsDialog = false },
            title = { Text("エクスポート方法") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "保存方法を選んでください。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        showExportOptionsDialog = false
                        onExportSettingsNow()
                    }) {
                        Text("既定フォルダに保存")
                    }
                    TextButton(onClick = {
                        showExportOptionsDialog = false
                        createBackupLauncher.launch(generateBackupFileName())
                    }) {
                        Text("保存先を選んで保存")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportOptionsDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    if (showRestorePrompt) {
        AlertDialog(
            onDismissRequest = onDismissRestorePrompt,
            title = { Text("設定を復元しますか？") },
            text = {
                Text("現在の設定が空のため、バックアップから復元できます。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRestorePrompt()
                    importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                }) {
                    Text("バックアップファイルを選択")
                }
            },
            dismissButton = {
                AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
                    if (hasInternalSnapshot) {
                        TextButton(
                            onClick = { onRestoreFromInternalSnapshot() },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                        ) {
                            Text("内部スナップショット")
                        }
                    }
                    TextButton(
                        onClick = onDismissRestorePrompt,
                        modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                    ) {
                        Text("後で")
                    }
                }
            }
        )
    }
}

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

private fun generateBackupFileName(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    val timestamp = LocalDateTime.now().format(formatter)
    return "notify2discord_backup_${timestamp}.json"
}

private fun formatEpochMillis(
    epochMillis: Long?,
    formatter: DateTimeFormatter
): String? {
    return epochMillis?.let { millis ->
        runCatching {
            formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        }.getOrNull()
    }
}

private fun WebhookHealthLevel.label(): String {
    return when (this) {
        WebhookHealthLevel.OK -> "正常"
        WebhookHealthLevel.WARNING -> "注意"
        WebhookHealthLevel.ERROR -> "エラー"
    }
}

@Composable
private fun WebhookHealthLevel.color() = when (this) {
    WebhookHealthLevel.OK -> MaterialTheme.colorScheme.primary
    WebhookHealthLevel.WARNING -> MaterialTheme.colorScheme.tertiary
    WebhookHealthLevel.ERROR -> MaterialTheme.colorScheme.error
}
