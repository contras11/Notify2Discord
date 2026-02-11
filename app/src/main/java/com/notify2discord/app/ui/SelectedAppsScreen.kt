package com.notify2discord.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.notify2discord.app.data.AppInfo
import com.notify2discord.app.data.SettingsState

private enum class AppSortOrder {
    NAME_ASC,
    NAME_DESC,
    SELECTED_FIRST,
    WEBHOOK_FIRST
}

private fun sortLabel(order: AppSortOrder): String = when (order) {
    AppSortOrder.NAME_ASC -> "アルファベット順 (A→Z)"
    AppSortOrder.NAME_DESC -> "アルファベット逆順 (Z→A)"
    AppSortOrder.SELECTED_FIRST -> "選択済みが先"
    AppSortOrder.WEBHOOK_FIRST -> "個別Webhook設定済みが先"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedAppsScreen(
    state: SettingsState,
    apps: List<AppInfo>,
    onToggleSelected: (String, Boolean) -> Unit,
    onSetAppWebhook: (String, String) -> Unit,
    onSetAppTemplate: (String, String) -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(AppSortOrder.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showUsageDialog by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val headerCardColor = if (isDarkTheme) Color(0xFF143247) else Color(0xFFEAF6FF)
    val headerTextColor = if (isDarkTheme) Color(0xFFD6EDFF) else MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(sortOrder) {
        listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("転送アプリ設定") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "ソート")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            AppSortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(sortLabel(order)) },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (sortOrder == order) {
                                            Icon(Icons.Default.Check, null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val appsWithCustomWebhook = apps.filter { state.appWebhooks.containsKey(it.packageName) }.size
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 説明セクション
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = headerCardColor
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "転送対象と個別Webhookをここで管理できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = headerTextColor
                    )
                    AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
                        Surface(
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "転送中: ${state.selectedPackages.size} 個",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "個別Webhook: $appsWithCustomWebhook 個",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    AdaptiveActionGroup(maxItemsInRow = 2) { _ ->
                        TextButton(
                            onClick = { showUsageDialog = true }
                        ) {
                            Text("使い方など")
                        }
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                sortOrder = AppSortOrder.NAME_ASC
                            }
                        ) {
                            Text("検索と並び順リセット")
                        }
                    }
                }
            }

            // 検索バー
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("アプリを検索") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            // 検索 → ソート の順に適用
            val filteredApps = remember(apps, searchQuery) {
                apps.filter { app ->
                    searchQuery.isBlank() ||
                    app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
                }
            }

            val sortedApps = remember(filteredApps, sortOrder, state.selectedPackages, state.appWebhooks) {
                when (sortOrder) {
                    AppSortOrder.NAME_ASC ->
                        filteredApps.sortedBy { it.label.lowercase() }
                    AppSortOrder.NAME_DESC ->
                        filteredApps.sortedByDescending { it.label.lowercase() }
                    AppSortOrder.SELECTED_FIRST ->
                        filteredApps.sortedWith(
                            compareByDescending<AppInfo> { state.selectedPackages.contains(it.packageName) }
                                .thenBy { it.label.lowercase() }
                        )
                    AppSortOrder.WEBHOOK_FIRST ->
                        filteredApps.sortedWith(
                            compareByDescending<AppInfo> { state.appWebhooks.containsKey(it.packageName) }
                                .thenBy { it.label.lowercase() }
                        )
                }
            }

            // アプリリスト
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sortedApps, key = { it.packageName }) { app ->
                    val selected = state.selectedPackages.contains(app.packageName)
                    val hasCustomWebhook = state.appWebhooks.containsKey(app.packageName)

                    AppListItem(
                        app = app,
                        selected = selected,
                        hasCustomWebhook = hasCustomWebhook,
                        onToggleSelected = { onToggleSelected(app.packageName, it) },
                        onSetWebhook = {
                            selectedApp = app
                            showWebhookDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showUsageDialog) {
        AlertDialog(
            onDismissRequest = { showUsageDialog = false },
            title = { Text("使い方など") },
            text = {
                Text(
                    text = "・チェックを入れたアプリだけ転送します\n" +
                        "・何も選ばない場合は全アプリを転送します\n" +
                        "・右端の追加/編集ボタンで個別Webhookを設定できます\n" +
                        "・個別Webhookが未設定なら共通Webhookを使います",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showUsageDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    // Webhook設定ダイアログ
    if (showWebhookDialog && selectedApp != null) {
        WebhookConfigDialog(
            app = selectedApp!!,
            currentWebhook = state.appWebhooks[selectedApp!!.packageName] ?: "",
            currentTemplate = state.appTemplates[selectedApp!!.packageName] ?: "",
            defaultWebhook = state.webhookUrl,
            onDismiss = {
                showWebhookDialog = false
                selectedApp = null
            },
            onSave = { webhook, template ->
                onSetAppWebhook(selectedApp!!.packageName, webhook)
                onSetAppTemplate(selectedApp!!.packageName, template)
                showWebhookDialog = false
                selectedApp = null
            }
        )
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    selected: Boolean,
    hasCustomWebhook: Boolean,
    onToggleSelected: (Boolean) -> Unit,
    onSetWebhook: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasCustomWebhook)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggleSelected
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (hasCustomWebhook) FontWeight.Bold else FontWeight.Normal
                    )
                    if (hasCustomWebhook) {
                        Text(
                            text = "\uD83D\uDD17",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasCustomWebhook) {
                    Text(
                        text = "個別Webhook設定済み",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(onClick = onSetWebhook) {
                Icon(
                    imageVector = if (hasCustomWebhook) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = if (hasCustomWebhook) "Webhook編集" else "Webhook追加",
                    tint = if (hasCustomWebhook)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WebhookConfigDialog(
    app: AppInfo,
    currentWebhook: String,
    currentTemplate: String,
    defaultWebhook: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var webhookText by remember { mutableStateOf(currentWebhook) }
    var templateText by remember { mutableStateOf(currentTemplate) }
    var showWebhookDetails by remember { mutableStateOf(false) }
    var showTemplateDetails by remember { mutableStateOf(false) }

    val isWebhookValid = remember(webhookText) {
        webhookText.isBlank() ||
        webhookText.startsWith("https://discord.com/api/webhooks/") ||
        webhookText.startsWith("https://discordapp.com/api/webhooks/")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Webhook設定")
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Webhook詳細",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showWebhookDetails = !showWebhookDetails }) {
                                Text(if (showWebhookDetails) "閉じる" else "開く")
                                Icon(
                                    imageVector = if (showWebhookDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }
                        if (showWebhookDetails) {
                            Text(
                                text = "このアプリの通知を特定のWebhook URLに送信できます。",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "• 空欄 → デフォルトのWebhookを使用\n• 入力 → このアプリ専用のWebhookを使用",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (defaultWebhook.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "デフォルト:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = maskWebhookUrl(defaultWebhook),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = webhookText,
                    onValueChange = { webhookText = it },
                    label = { Text("個別Webhook URL（オプション）") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") },
                    isError = !isWebhookValid,
                    supportingText = {
                        if (!isWebhookValid) {
                            Text("無効なWebhook URLです", color = MaterialTheme.colorScheme.error)
                        } else if (webhookText.isBlank()) {
                            Text("空欄でデフォルトを使用")
                        } else {
                            Text("このアプリ専用のWebhookを使用", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "テンプレート詳細",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showTemplateDetails = !showTemplateDetails }) {
                                Text(if (showTemplateDetails) "閉じる" else "開く")
                                Icon(
                                    imageVector = if (showTemplateDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }
                        if (showTemplateDetails) {
                            Text(
                                text = "未入力の場合は共通テンプレートを使用します。",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "利用可能: {app} {title} {text} {time} {package}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = templateText,
                    onValueChange = { templateText = it },
                    label = { Text("アプリ別テンプレート（空欄で共通テンプレート）") },
                    placeholder = {
                        Text(
                            "[アプリ] {app}\n[タイトル] {title}\n[本文] {text}"
                        )
                    },
                    supportingText = {
                        Text("未入力時は共通テンプレートを利用")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(webhookText, templateText) },
                enabled = isWebhookValid
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

private fun maskWebhookUrl(url: String): String {
    val lastPart = url.substringAfterLast('/')
    return if (lastPart.length > 6) {
        "****${lastPart.takeLast(6)}"
    } else {
        "****"
    }
}
