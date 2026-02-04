@file:OptIn(ExperimentalFoundationApi::class)

package com.notify2discord.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notify2discord.app.data.NotificationRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// アプリごとにまとめた要約データ
private data class AppNotificationSummary(
    val packageName: String,
    val appName: String,
    val count: Int,
    val latest: NotificationRecord
)

// チャット画面用アイテム
private sealed class ChatItem {
    data class DateSeparator(val label: String) : ChatItem()
    data class Message(val record: NotificationRecord) : ChatItem()
}

// バブルの Shape（左下だけ尖らせてLINE風にする）
private val bubbleShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomEnd = 16.dp,
    bottomStart = 4.dp
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(
    history: List<NotificationRecord>,
    onDeleteRecord: (Long) -> Unit,
    onDeleteRecords: (Set<Long>) -> Unit,
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
            onDeleteRecords = onDeleteRecords,
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

// --- アプリ一覧画面 (LINE風コンタクトリスト) ---

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
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(summaries, key = { it.packageName }) { summary ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectApp(summary.packageName) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 円形アプリアイコン 48dp
                        AppIcon(
                            packageName = summary.packageName,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                        // アプリ名・プレビュー
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = summary.appName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            val preview = summary.latest.title.ifBlank { summary.latest.text }
                            if (preview.isNotBlank()) {
                                Text(
                                    text = preview,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // 時刻・バッジ・削除
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = formatSummaryTime(summary.latest.postTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge {
                                    Text(text = summary.count.toString())
                                }
                                IconButton(
                                    onClick = { deleteConfirmApp = summary },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "「${summary.appName}」の履歴を削除",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
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

// --- アプリ内履歴画面 (チャットバブル + 複数選択) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailScreen(
    packageName: String,
    appName: String,
    records: List<NotificationRecord>,
    onDeleteRecord: (Long) -> Unit,
    onDeleteRecords: (Set<Long>) -> Unit,
    onClearByApp: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var recordToDelete by remember { mutableStateOf<NotificationRecord?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var pendingBatchDelete by remember { mutableStateOf<Set<Long>?>(null) }

    val chatItems = remember(records) { buildChatItems(records) }

    Scaffold(
        topBar = {
            if (selectedIds.isNotEmpty()) {
                // 選択モード TopBar
                TopAppBar(
                    title = { Text("${selectedIds.size}件 選択中") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, "選択キャンセル")
                        }
                    },
                    actions = {
                        IconButton(onClick = { pendingBatchDelete = selectedIds }) {
                            Icon(Icons.Default.Delete, "選択した履歴を削除")
                        }
                    }
                )
            } else {
                // 通常 TopBar
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true
            ) {
                items(chatItems, key = {
                    when (it) {
                        is ChatItem.DateSeparator -> "sep_${it.label}"
                        is ChatItem.Message -> "msg_${it.record.id}"
                    }
                }) { item ->
                    when (item) {
                        is ChatItem.DateSeparator -> {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        is ChatItem.Message -> {
                            val record = item.record
                            val isSelected = record.id in selectedIds
                            Surface(
                                shape = bubbleShape,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .combinedClickable(
                                        onDoubleClick = null,
                                        onClick = {
                                            if (selectedIds.isNotEmpty()) {
                                                selectedIds = if (isSelected) selectedIds - record.id
                                                else selectedIds + record.id
                                            } else {
                                                recordToDelete = record
                                            }
                                        },
                                        onLongClick = {
                                            selectedIds = selectedIds + record.id
                                        }
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, bubbleShape)
                                        else Modifier
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (record.title.isNotBlank()) {
                                        Text(
                                            text = record.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
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
                                        text = formatBubbleTime(record.postTime),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
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

    // バッチ削除確認
    if (pendingBatchDelete != null) {
        val count = pendingBatchDelete!!.size
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = null },
            title = { Text("履歴を削除するか？") },
            text = { Text("${count}件の履歴を削除します。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecords(pendingBatchDelete!!)
                    selectedIds = emptySet()
                    pendingBatchDelete = null
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchDelete = null }) {
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

// --- ヘルパー関数 ---

// reverseLayout=true 用のチャットアイテム構築
// 日の降順・メッセージの降順・各日グループの末尾にセパレータを積む
private fun buildChatItems(records: List<NotificationRecord>): List<ChatItem> {
    val grouped = records.groupBy {
        Instant.ofEpochMilli(it.postTime).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val sortedDates = grouped.keys.sortedDescending()
    val items = mutableListOf<ChatItem>()
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    for (date in sortedDates) {
        val dayRecords = grouped[date]!!.sortedByDescending { it.postTime }
        dayRecords.forEach { items.add(ChatItem.Message(it)) }
        val label = when (date) {
            today -> "今日"
            yesterday -> "昨日"
            else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
        }
        items.add(ChatItem.DateSeparator(label))
    }
    return items
}

// バブル内時刻フォーマット
private fun formatBubbleTime(postTime: Long): String {
    val zdt = Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault())
    return if (zdt.toLocalDate() == LocalDate.now())
        zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    else
        zdt.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}

// アプリ一覧の時刻フォーマット
private fun formatSummaryTime(postTime: Long): String {
    val zdt = Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (zdt.toLocalDate()) {
        today -> zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "昨日 " + zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> zdt.format(DateTimeFormatter.ofPattern("M/d"))
    }
}
