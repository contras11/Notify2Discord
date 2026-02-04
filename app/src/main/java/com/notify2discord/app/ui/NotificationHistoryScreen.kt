package com.notify2discord.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notify2discord.app.data.NotificationRecord
import java.text.SimpleDateFormat
import java.util.Date

// アプリごとにまとめた要約データ
private data class AppNotificationSummary(
    val packageName: String,
    val appName: String,
    val count: Int,
    val latest: NotificationRecord
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    history: List<NotificationRecord>,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit,
    onClearByApp: (String) -> Unit
) {
    // null = アプリ一覧、非null = そのアプリの履歴詳細
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }

    if (selectedAppPackage != null) {
        val pkg = selectedAppPackage!!
        val appRecords = history.filter { it.packageName == pkg }
        val appName = appRecords.firstOrNull()?.appName ?: pkg

        AppDetailScreen(
            packageName = pkg,
            appName = appName,
            records = appRecords,
            onDeleteRecord = onDeleteRecord,
            onClearByApp = { onClearByApp(pkg) },
            onNavigateBack = { selectedAppPackage = null }
        )
    } else {
        AppListScreen(
            history = history,
            onClearAll = onClearAll,
            onClearByApp = onClearByApp,
            onSelectApp = { selectedAppPackage = it }
        )
    }
}

// アプリアイコン表示
@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageBitmap: ImageBitmap? = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = drawable.intrinsicWidth.coerceIn(1, 512)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- アプリ一覧画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    history: List<NotificationRecord>,
    onClearAll: () -> Unit,
    onClearByApp: (String) -> Unit,
    onSelectApp: (String) -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var deleteConfirmApp by remember { mutableStateOf<AppNotificationSummary?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm") }

    // アプリごとにグループ化し、最新時刻の降順でソート
    val summaries = remember(history) {
        history
            .groupBy { it.packageName }
            .map { (pkg, records) ->
                AppNotificationSummary(
                    packageName = pkg,
                    appName = records.first().appName,
                    count = records.size,
                    latest = records.maxByOrNull { it.postTime } ?: records.first()
                )
            }
            .sortedByDescending { it.latest.postTime }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知履歴") },
                actions = {
                    if (summaries.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, "全削除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (summaries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "履歴がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(summaries, key = { it.packageName }) { summary ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelectApp(summary.packageName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // アプリアイコン
                            AppIcon(
                                packageName = summary.packageName,
                                modifier = Modifier.size(36.dp)
                            )
                            // アプリ名・プレビュー
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = summary.appName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                val preview = summary.latest.title.ifBlank { summary.latest.text }
                                if (preview.isNotBlank()) {
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            // バッジ・時刻
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(text = summary.count.toString())
                                        }
                                    }
                                ) {
                                    Box(modifier = Modifier.padding(end = 8.dp))
                                }
                                Text(
                                    text = dateFormat.format(Date(summary.latest.postTime)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // アプリグループ削除
                            IconButton(onClick = { deleteConfirmApp = summary }) {
                                Icon(Icons.Default.Delete, "「${summary.appName}」の履歴を削除")
                            }
                        }
                    }
                }
            }
        }
    }

    // アプリグループ削除確認ダイアログ
    if (deleteConfirmApp != null) {
        val target = deleteConfirmApp!!
        AlertDialog(
            onDismissRequest = { deleteConfirmApp = null },
            title = { Text("「${target.appName}」の履歴を削除するか？") },
            text = { Text("${target.count}件の履歴を削除します。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearByApp(target.packageName)
                    deleteConfirmApp = null
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmApp = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 全削除確認ダイアログ
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("全履歴を削除するか？") },
            text = { Text("全${history.size}件の履歴を削除します。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearConfirm = false
                }) {
                    Text("全削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

// --- アプリ内履歴画面 ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailScreen(
    packageName: String,
    appName: String,
    records: List<NotificationRecord>,
    onDeleteRecord: (Long) -> Unit,
    onClearByApp: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var recordToDelete by remember { mutableStateOf<NotificationRecord?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(packageName = packageName, modifier = Modifier.size(28.dp))
                        Text(appName)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                    }
                },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, "このアプリの履歴を全削除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "履歴がありません",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { recordToDelete = record }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (record.title.isNotBlank()) {
                                Text(
                                    text = record.title,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (record.text.isNotBlank()) {
                                Text(
                                    text = record.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(record.postTime)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 1件削除確認
    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("履歴を削除するか？") },
            text = { Text("この履歴1件を削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecord(recordToDelete!!.id)
                    recordToDelete = null
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // アプリ内全削除確認
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("「$appName」の履歴を全削除するか？") },
            text = { Text("${records.size}件の履歴を削除します。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearByApp()
                    showClearConfirm = false
                }) {
                    Text("全削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}
