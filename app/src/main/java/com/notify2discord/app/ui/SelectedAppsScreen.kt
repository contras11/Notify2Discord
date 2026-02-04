package com.notify2discord.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSetAppWebhook: (String, String) -> Unit
) {
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var showWebhookDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(AppSortOrder.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(sortOrder) {
        listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("転送アプリとWebhook設定") },
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "使い方",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "• チェックを入れると、そのアプリの通知を転送します\n• 何もチェックしていない場合は全アプリの通知を転送します\n• 右側のアイコンから、アプリごとに個別のWebhook URLを設定できます\n• 個別設定がない場合は、デフォルトのWebhookが使用されます",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "転送中: ${state.selectedPackages.size} 個",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "個別Webhook: $appsWithCustomWebhook 個",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
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

    // Webhook設定ダイアログ
    if (showWebhookDialog && selectedApp != null) {
        WebhookConfigDialog(
            app = selectedApp!!,
            currentWebhook = state.appWebhooks[selectedApp!!.packageName] ?: "",
            defaultWebhook = state.webhookUrl,
            onDismiss = {
                showWebhookDialog = false
                selectedApp = null
            },
            onSave = { webhook ->
                onSetAppWebhook(selectedApp!!.packageName, webhook)
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
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
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
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(onClick = onSetWebhook) {
                Icon(
                    imageVector = if (hasCustomWebhook) Icons.Default.Edit else Icons.Default.Add,
                    contentDescription = if (hasCustomWebhook) "Webhook編集" else "Webhook追加",
                    tint = if (hasCustomWebhook)
                        MaterialTheme.colorScheme.secondary
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
    defaultWebhook: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var webhookText by remember { mutableStateOf(currentWebhook) }

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
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "個別Webhook設定",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
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
                            color = MaterialTheme.colorScheme.secondary
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
                            Text("このアプリ専用のWebhookを使用", color = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(webhookText) },
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
