@file:OptIn(ExperimentalFoundationApi::class)

package com.notify2discord.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notify2discord.app.data.NotificationRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class AppNotificationSummary(
    val packageName: String,
    val appName: String,
    val count: Int,
    val unreadCount: Int,
    val latest: NotificationRecord
)

private sealed class ChatItem {
    data class DateSeparator(val label: String) : ChatItem()
    data class Message(val record: NotificationRecord) : ChatItem()
}

private enum class HistoryRangeFilter {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    ALL
}

private enum class AppListSortOrder {
    LATEST,
    COUNT,
    APP_NAME
}

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
    readMarkers: Map<String, Long>,
    onDeleteRecord: (Long) -> Unit,
    onDeleteRecords: (Set<Long>) -> Unit,
    onClearAll: () -> Unit,
    onClearByApp: (String) -> Unit,
    onClearByApps: (Set<String>) -> Unit,
    onMarkAppAsRead: (String, Long) -> Unit
) {
    var selectedAppPackage by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedAppPackage != null) {
        selectedAppPackage = null
    }

    if (selectedAppPackage != null) {
        val pkg = selectedAppPackage!!
        val appRecords = history.filter { it.packageName == pkg }
        val appName = appRecords.firstOrNull()?.appName ?: pkg
        val latestPostTime = appRecords.maxOfOrNull { it.postTime }

        LaunchedEffect(pkg, latestPostTime) {
            if (latestPostTime != null) {
                onMarkAppAsRead(pkg, latestPostTime)
            }
        }

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
            readMarkers = readMarkers,
            onClearAll = onClearAll,
            onClearByApps = onClearByApps,
            onSelectApp = { selectedAppPackage = it }
        )
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    history: List<NotificationRecord>,
    readMarkers: Map<String, Long>,
    onClearAll: () -> Unit,
    onClearByApps: (Set<String>) -> Unit,
    onSelectApp: (String) -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var selectedPackages by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var pendingSelectionDelete by remember { mutableStateOf<Set<String>?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var rangeFilter by rememberSaveable { mutableStateOf(HistoryRangeFilter.ALL) }
    var sortOrder by rememberSaveable { mutableStateOf(AppListSortOrder.LATEST) }
    var showRangeMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val rangeFilteredRecords = remember(history, rangeFilter) {
        filterRecordsByRange(history, rangeFilter)
    }
    val summaries = remember(rangeFilteredRecords, searchQuery, sortOrder, readMarkers) {
        val query = searchQuery.trim()
        val source = rangeFilteredRecords
            .groupBy { it.packageName }
            .map { (pkg, records) ->
                val latest = records.maxByOrNull { it.postTime } ?: records.first()
                val readUntil = readMarkers[pkg] ?: 0L
                AppNotificationSummary(
                    packageName = pkg,
                    appName = records.first().appName,
                    count = records.size,
                    unreadCount = records.count { it.postTime > readUntil },
                    latest = latest
                )
            }
            .filter { summary ->
                if (query.isBlank()) {
                    true
                } else {
                    summary.appName.contains(query, ignoreCase = true) ||
                        summary.packageName.contains(query, ignoreCase = true) ||
                        summary.latest.title.contains(query, ignoreCase = true) ||
                        summary.latest.text.contains(query, ignoreCase = true)
                }
            }

        when (sortOrder) {
            AppListSortOrder.LATEST -> source.sortedByDescending { it.latest.postTime }
            AppListSortOrder.COUNT -> source.sortedByDescending { it.count }
            AppListSortOrder.APP_NAME -> source.sortedBy { it.appName.lowercase() }
        }
    }

    LaunchedEffect(summaries) {
        val visiblePackages = summaries.map { it.packageName }.toSet()
        selectedPackages = selectedPackages.intersect(visiblePackages)
        if (visiblePackages.isEmpty()) {
            selectionMode = false
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = {
                        if (selectedPackages.isEmpty()) {
                            Text("選択モード")
                        } else {
                            Text("${selectedPackages.size}件 選択中")
                        }
                    },
                    colors = appBarColors(),
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedPackages = emptySet()
                            selectionMode = false
                        }) {
                            Icon(Icons.Default.Close, "選択キャンセル")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { pendingSelectionDelete = selectedPackages },
                            enabled = selectedPackages.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "選択したアプリ履歴を削除")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("通知履歴") },
                    colors = appBarColors(),
                    actions = {
                        Box {
                            IconButton(onClick = { showRangeMenu = true }) {
                                Icon(Icons.Default.FilterList, "期間フィルタ")
                            }
                            DropdownMenu(
                                expanded = showRangeMenu,
                                onDismissRequest = { showRangeMenu = false }
                            ) {
                                HistoryRangeFilter.values().forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label()) },
                                        onClick = {
                                            rangeFilter = filter
                                            showRangeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "並び替え")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                AppListSortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label()) },
                                        onClick = {
                                            sortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (summaries.isNotEmpty()) {
                            TextButton(onClick = { selectionMode = true }) {
                                Text("編集", color = MaterialTheme.colorScheme.onPrimary)
                            }
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(Icons.Default.Delete, "全削除")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                label = { Text("履歴を検索（アプリ/本文）") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            if (summaries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(summaries, key = { it.packageName }) { summary ->
                        val isSelected = summary.packageName in selectedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            selectedPackages = if (isSelected) {
                                                selectedPackages - summary.packageName
                                            } else {
                                                selectedPackages + summary.packageName
                                            }
                                        } else {
                                            onSelectApp(summary.packageName)
                                        }
                                    },
                                    onLongClick = {
                                        selectionMode = true
                                        selectedPackages = if (isSelected) {
                                            selectedPackages - summary.packageName
                                        } else {
                                            selectedPackages + summary.packageName
                                        }
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(
                                packageName = summary.packageName,
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = summary.appName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val preview = summary.latest.title.ifBlank { summary.latest.text }
                                    .replace("\n", " ")
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
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = formatSummaryTime(summary.latest.postTime),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (summary.unreadCount > 0) {
                                    Badge {
                                        Text(text = summary.unreadCount.toString())
                                    }
                                }
                                Text(
                                    text = "合計 ${summary.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
    }

    if (pendingSelectionDelete != null) {
        val targetPackages = pendingSelectionDelete!!
        AlertDialog(
            onDismissRequest = { pendingSelectionDelete = null },
            title = { Text("選択した履歴を削除するか？") },
            text = { Text("${targetPackages.size}個のアプリ履歴を削除します。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    onClearByApps(targetPackages)
                    selectedPackages = emptySet()
                    selectionMode = false
                    pendingSelectionDelete = null
                }) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSelectionDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

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
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var pendingBatchDelete by remember { mutableStateOf<Set<Long>?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var rangeFilter by rememberSaveable { mutableStateOf(HistoryRangeFilter.ALL) }
    var showRangeMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val bubbleWidthFraction = if (isWideScreen) 0.72f else 0.9f

    val filteredRecords = remember(records, searchQuery, rangeFilter) {
        val byRange = filterRecordsByRange(records, rangeFilter)
        byRange.filter { record ->
            val query = searchQuery.trim()
            if (query.isBlank()) {
                true
            } else {
                record.title.contains(query, ignoreCase = true) ||
                    record.text.contains(query, ignoreCase = true)
            }
        }
    }
    val chatItems = remember(filteredRecords) {
        buildChatItems(filteredRecords)
    }

    LaunchedEffect(filteredRecords) {
        val visibleIds = filteredRecords.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(visibleIds)
    }
    LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) {
            // チャット画面は常に最新が下に来るため、初期表示を最下部へ合わせる
            listState.scrollToItem(chatItems.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            if (selectionMode || selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = {
                        if (selectedIds.isEmpty()) {
                            Text("選択モード")
                        } else {
                            Text("${selectedIds.size}件 選択中")
                        }
                    },
                    colors = appBarColors(),
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedIds = emptySet()
                            selectionMode = false
                        }) {
                            Icon(Icons.Default.Close, "選択キャンセル")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { pendingBatchDelete = selectedIds },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "選択した履歴を削除")
                        }
                    }
                )
            } else {
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
                    colors = appBarColors(),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "戻る")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showRangeMenu = true }) {
                                Icon(Icons.Default.FilterList, "期間フィルタ")
                            }
                            DropdownMenu(
                                expanded = showRangeMenu,
                                onDismissRequest = { showRangeMenu = false }
                            ) {
                                HistoryRangeFilter.values().forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label()) },
                                        onClick = {
                                            rangeFilter = filter
                                            showRangeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        if (records.isNotEmpty()) {
                            TextButton(onClick = { selectionMode = true }) {
                                Text("編集", color = MaterialTheme.colorScheme.onPrimary)
                            }
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(Icons.Default.Delete, "このアプリの履歴を全削除")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                label = { Text("このアプリ内を検索（タイトル/本文）") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                        .fillMaxWidth(bubbleWidthFraction)
                                        .combinedClickable(
                                            onDoubleClick = null,
                                            onClick = {
                                                if (selectionMode || selectedIds.isNotEmpty()) {
                                                    selectedIds = if (isSelected) {
                                                        selectedIds - record.id
                                                    } else {
                                                        selectedIds + record.id
                                                    }
                                                } else {
                                                    recordToDelete = record
                                                }
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedIds = selectedIds + record.id
                                            }
                                        )
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, bubbleShape)
                                            } else {
                                                Modifier
                                            }
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
    }

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
                    selectionMode = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun appBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
)

private fun filterRecordsByRange(
    records: List<NotificationRecord>,
    filter: HistoryRangeFilter
): List<NotificationRecord> {
    if (filter == HistoryRangeFilter.ALL) return records
    val now = System.currentTimeMillis()
    val start = when (filter) {
        HistoryRangeFilter.TODAY -> LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        HistoryRangeFilter.LAST_7_DAYS -> now - 7L * 24 * 60 * 60 * 1000
        HistoryRangeFilter.LAST_30_DAYS -> now - 30L * 24 * 60 * 60 * 1000
        HistoryRangeFilter.ALL -> 0L
    }
    return records.filter { it.postTime >= start }
}

private fun buildChatItems(records: List<NotificationRecord>): List<ChatItem> {
    val grouped = records.groupBy {
        Instant.ofEpochMilli(it.postTime).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val sortedDates = grouped.keys.sorted()
    val items = mutableListOf<ChatItem>()
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    sortedDates.forEach { date ->
        val label = when (date) {
            today -> "今日"
            yesterday -> "昨日"
            else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
        }
        items += ChatItem.DateSeparator(label)
        val dayRecords = grouped[date]!!.sortedBy { it.postTime }
        dayRecords.forEach { items += ChatItem.Message(it) }
    }
    return items
}

private fun formatBubbleTime(postTime: Long): String {
    val zdt = Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault())
    return if (zdt.toLocalDate() == LocalDate.now()) {
        zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        zdt.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
    }
}

private fun formatSummaryTime(postTime: Long): String {
    val zdt = Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (zdt.toLocalDate()) {
        today -> zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "昨日 " + zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> zdt.format(DateTimeFormatter.ofPattern("M/d"))
    }
}

private fun HistoryRangeFilter.label(): String = when (this) {
    HistoryRangeFilter.TODAY -> "今日"
    HistoryRangeFilter.LAST_7_DAYS -> "7日以内"
    HistoryRangeFilter.LAST_30_DAYS -> "30日以内"
    HistoryRangeFilter.ALL -> "全期間"
}

private fun AppListSortOrder.label(): String = when (this) {
    AppListSortOrder.LATEST -> "最新順"
    AppListSortOrder.COUNT -> "件数順"
    AppListSortOrder.APP_NAME -> "アプリ名順"
}
