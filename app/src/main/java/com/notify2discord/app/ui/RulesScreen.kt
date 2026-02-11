package com.notify2discord.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notify2discord.app.data.DedupeConfig
import com.notify2discord.app.data.EmbedConfig
import com.notify2discord.app.data.FilterConfig
import com.notify2discord.app.data.QuietHoursConfig
import com.notify2discord.app.data.RateLimitConfig
import com.notify2discord.app.data.RoutingRule
import com.notify2discord.app.data.SettingsState
import java.util.UUID

private enum class SimpleFilterPreset {
    ALL,
    IMPORTANT,
    KEYWORD
}

private enum class DedupePreset {
    WEAK,
    STANDARD,
    STRONG
}

private enum class QuietPreset {
    OFF,
    NIGHT,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: SettingsState,
    onSaveDefaultTemplate: (String) -> Unit,
    onSaveRuleConfig: (EmbedConfig, FilterConfig, DedupeConfig, RateLimitConfig, QuietHoursConfig) -> Unit,
    onSaveRoutingRules: (List<RoutingRule>) -> Unit,
    onSetRulesSimpleMode: (Boolean) -> Unit
) {
    var templateText by remember(state.defaultTemplate) { mutableStateOf(state.defaultTemplate) }

    var embedEnabled by remember(state.embedConfig.enabled) { mutableStateOf(state.embedConfig.enabled) }
    var includePackageField by remember(state.embedConfig.includePackageField) { mutableStateOf(state.embedConfig.includePackageField) }
    var includeTimeField by remember(state.embedConfig.includeTimeField) { mutableStateOf(state.embedConfig.includeTimeField) }
    var maxFieldLengthText by remember(state.embedConfig.maxFieldLength) { mutableStateOf(state.embedConfig.maxFieldLength.toString()) }

    var filterEnabled by remember(state.filterConfig.enabled) { mutableStateOf(state.filterConfig.enabled) }
    var filterKeywordsText by remember(state.filterConfig.keywords) { mutableStateOf(state.filterConfig.keywords.joinToString(", ")) }
    var filterUseRegex by remember(state.filterConfig.useRegex) { mutableStateOf(state.filterConfig.useRegex) }
    var filterRegexPattern by remember(state.filterConfig.regexPattern) { mutableStateOf(state.filterConfig.regexPattern) }
    var filterChannelIdsText by remember(state.filterConfig.channelIds) { mutableStateOf(state.filterConfig.channelIds.joinToString(", ")) }
    var filterMinImportanceText by remember(state.filterConfig.minImportance) { mutableStateOf(state.filterConfig.minImportance.toString()) }
    var excludeSummary by remember(state.filterConfig.excludeSummary) { mutableStateOf(state.filterConfig.excludeSummary) }

    var dedupeEnabled by remember(state.dedupeConfig.enabled) { mutableStateOf(state.dedupeConfig.enabled) }
    var contentHashEnabled by remember(state.dedupeConfig.contentHashEnabled) { mutableStateOf(state.dedupeConfig.contentHashEnabled) }
    var titleLatestOnly by remember(state.dedupeConfig.titleLatestOnly) { mutableStateOf(state.dedupeConfig.titleLatestOnly) }
    var dedupeWindowText by remember(state.dedupeConfig.windowSeconds) { mutableStateOf(state.dedupeConfig.windowSeconds.toString()) }

    var rateEnabled by remember(state.rateLimitConfig.enabled) { mutableStateOf(state.rateLimitConfig.enabled) }
    var rateMaxText by remember(state.rateLimitConfig.maxPerWindow) { mutableStateOf(state.rateLimitConfig.maxPerWindow.toString()) }
    var rateWindowText by remember(state.rateLimitConfig.windowSeconds) { mutableStateOf(state.rateLimitConfig.windowSeconds.toString()) }
    var aggregateWindowText by remember(state.rateLimitConfig.aggregateWindowSeconds) { mutableStateOf(state.rateLimitConfig.aggregateWindowSeconds.toString()) }

    var quietEnabled by remember(state.quietHoursConfig.enabled) { mutableStateOf(state.quietHoursConfig.enabled) }
    var quietStartHourText by remember(state.quietHoursConfig.startHour) { mutableStateOf(state.quietHoursConfig.startHour.toString()) }
    var quietStartMinuteText by remember(state.quietHoursConfig.startMinute) { mutableStateOf(state.quietHoursConfig.startMinute.toString()) }
    var quietEndHourText by remember(state.quietHoursConfig.endHour) { mutableStateOf(state.quietHoursConfig.endHour.toString()) }
    var quietEndMinuteText by remember(state.quietHoursConfig.endMinute) { mutableStateOf(state.quietHoursConfig.endMinute.toString()) }
    var quietDaysText by remember(state.quietHoursConfig.daysOfWeek) { mutableStateOf(state.quietHoursConfig.daysOfWeek.joinToString(",")) }

    var isSimpleMode by remember(state.uiModeRulesSimple) { mutableStateOf(state.uiModeRulesSimple) }
    var simpleFilterPreset by remember(state.filterConfig) { mutableStateOf(resolveFilterPreset(state.filterConfig)) }
    var dedupePreset by remember(state.dedupeConfig) { mutableStateOf(resolveDedupePreset(state.dedupeConfig)) }
    var quietPreset by remember(state.quietHoursConfig) { mutableStateOf(resolveQuietPreset(state.quietHoursConfig)) }

    val routingRules = remember(state.routingRules) {
        mutableStateListOf<RoutingRule>().apply {
            addAll(state.routingRules)
        }
    }

    val placeholders = listOf(
        "アプリ名" to "{app}",
        "タイトル" to "{title}",
        "本文" to "{text}",
        "時刻" to "{time}",
        "パッケージ名" to "{package}"
    )
    val previewText = buildTemplatePreview(templateText)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ルール設定") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
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
                text = "使い方に合わせて設定モードを切り替えできます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
                Button(
                    onClick = {
                        isSimpleMode = true
                        onSetRulesSimpleMode(true)
                    },
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                    colors = if (isSimpleMode) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("初心者モード")
                }
                Button(
                    onClick = {
                        isSimpleMode = false
                        onSetRulesSimpleMode(false)
                    },
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                    colors = if (!isSimpleMode) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("詳細モード")
                }
            }

            Divider()

            SectionTitle(title = "テンプレート")
            Text(
                text = "通知本文を自由に決められます。改行はそのまま入力してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(placeholders) { (label, token) ->
                    AssistChip(
                        onClick = {
                            templateText = insertToken(templateText, token)
                        },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = templateText,
                onValueChange = { templateText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("通知テンプレート") },
                minLines = 4
            )
            if (!containsAnyPlaceholder(templateText)) {
                Text(
                    text = "ヒント: アプリ名や本文を入れるには上のチップをタップしてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "プレビュー",
                style = MaterialTheme.typography.labelLarge
            )
            OutlinedTextField(
                value = previewText,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                minLines = 4,
                label = { Text("送信イメージ") }
            )
            Button(onClick = { onSaveDefaultTemplate(templateText) }) {
                Text("テンプレート保存")
            }

            Divider()

            SectionTitle(title = "Embed表示")
            SwitchRow("Embedを有効", embedEnabled) { embedEnabled = it }
            if (!isSimpleMode) {
                SwitchRow("パッケージ欄を表示", includePackageField) { includePackageField = it }
                SwitchRow("時刻欄を表示", includeTimeField) { includeTimeField = it }
                OutlinedTextField(
                    value = maxFieldLengthText,
                    onValueChange = { maxFieldLengthText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本文フィールド最大文字数") }
                )
            }

            Divider()

            SectionTitle(title = "フィルタ")
            if (isSimpleMode) {
                PresetSelector(
                    options = listOf("すべて転送", "重要通知のみ", "キーワード一致"),
                    selectedIndex = simpleFilterPreset.ordinal,
                    onSelect = {
                        simpleFilterPreset = SimpleFilterPreset.values()[it]
                    }
                )
                if (simpleFilterPreset == SimpleFilterPreset.KEYWORD) {
                    OutlinedTextField(
                        value = filterKeywordsText,
                        onValueChange = { filterKeywordsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("キーワード (例: 銀行, 認証, 重要)") }
                    )
                }
            } else {
                SwitchRow("高度フィルタを有効", filterEnabled) { filterEnabled = it }
                OutlinedTextField(
                    value = filterKeywordsText,
                    onValueChange = { filterKeywordsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("キーワード (カンマ区切り)") }
                )
                SwitchRow("正規表現を有効", filterUseRegex) { filterUseRegex = it }
                OutlinedTextField(
                    value = filterRegexPattern,
                    onValueChange = { filterRegexPattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("正規表現パターン") }
                )
                OutlinedTextField(
                    value = filterChannelIdsText,
                    onValueChange = { filterChannelIdsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("通知チャンネルID (カンマ区切り)") }
                )
                OutlinedTextField(
                    value = filterMinImportanceText,
                    onValueChange = { filterMinImportanceText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("最小重要度") }
                )
                SwitchRow("サマリー通知を除外", excludeSummary) { excludeSummary = it }
            }

            Divider()

            SectionTitle(title = "重複抑制 / 集約")
            if (isSimpleMode) {
                PresetSelector(
                    options = listOf("弱", "標準", "強"),
                    selectedIndex = dedupePreset.ordinal,
                    onSelect = {
                        dedupePreset = DedupePreset.values()[it]
                    }
                )
            } else {
                SwitchRow("重複抑制を有効", dedupeEnabled) { dedupeEnabled = it }
                SwitchRow("本文ハッシュ重複を抑制", contentHashEnabled) { contentHashEnabled = it }
                SwitchRow("同一タイトルは最新のみ", titleLatestOnly) { titleLatestOnly = it }
                OutlinedTextField(
                    value = dedupeWindowText,
                    onValueChange = { dedupeWindowText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("重複判定ウィンドウ秒") }
                )
                SwitchRow("レート制限を有効", rateEnabled) { rateEnabled = it }
                OutlinedTextField(
                    value = rateMaxText,
                    onValueChange = { rateMaxText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ウィンドウ内の最大送信数") }
                )
                OutlinedTextField(
                    value = rateWindowText,
                    onValueChange = { rateWindowText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("レート制限ウィンドウ秒") }
                )
                OutlinedTextField(
                    value = aggregateWindowText,
                    onValueChange = { aggregateWindowText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("連投集約ウィンドウ秒") }
                )
            }

            Divider()

            SectionTitle(title = "時間帯")
            if (isSimpleMode) {
                PresetSelector(
                    options = listOf("OFF", "夜間(22:00-07:00)", "カスタム"),
                    selectedIndex = quietPreset.ordinal,
                    onSelect = {
                        quietPreset = QuietPreset.values()[it]
                    }
                )
                if (quietPreset == QuietPreset.CUSTOM) {
                    TimeRangeEditor(
                        startHour = quietStartHourText,
                        startMinute = quietStartMinuteText,
                        endHour = quietEndHourText,
                        endMinute = quietEndMinuteText,
                        onStartHourChange = { quietStartHourText = it },
                        onStartMinuteChange = { quietStartMinuteText = it },
                        onEndHourChange = { quietEndHourText = it },
                        onEndMinuteChange = { quietEndMinuteText = it }
                    )
                }
            } else {
                SwitchRow("サイレント時間を有効", quietEnabled) { quietEnabled = it }
                TimeRangeEditor(
                    startHour = quietStartHourText,
                    startMinute = quietStartMinuteText,
                    endHour = quietEndHourText,
                    endMinute = quietEndMinuteText,
                    onStartHourChange = { quietStartHourText = it },
                    onStartMinuteChange = { quietStartMinuteText = it },
                    onEndHourChange = { quietEndHourText = it },
                    onEndMinuteChange = { quietEndMinuteText = it }
                )
                OutlinedTextField(
                    value = quietDaysText,
                    onValueChange = { quietDaysText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("曜日 (1=日 ... 7=土, 空欄で毎日)") }
                )
            }

            Divider()

            SectionTitle(title = "ルーティング")
            Text(
                text = "一致したルールのWebhookすべてへ送信します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSimpleMode) {
                Text(
                    text = "登録済み: ${routingRules.size} ルール（詳細モードで編集）",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                routingRules.forEach { rule ->
                    RoutingRuleEditor(
                        rule = rule,
                        onChange = { updated ->
                            val index = routingRules.indexOfFirst { it.id == updated.id }
                            if (index >= 0) {
                                routingRules[index] = updated
                            }
                        },
                        onDelete = {
                            routingRules.removeAll { it.id == rule.id }
                        }
                    )
                }

                TextButton(onClick = {
                    routingRules += RoutingRule(
                        id = UUID.randomUUID().toString(),
                        name = "新しいルール",
                        enabled = true
                    )
                }) {
                    Text("ルールを追加")
                }
                Button(onClick = { onSaveRoutingRules(routingRules.toList()) }) {
                    Text("ルーティング保存")
                }
            }

            val resolvedFilter = resolveFilterConfig(
                isSimpleMode = isSimpleMode,
                preset = simpleFilterPreset,
                keywordsText = filterKeywordsText,
                original = FilterConfig(
                    enabled = filterEnabled,
                    keywords = parseCsv(filterKeywordsText),
                    useRegex = filterUseRegex,
                    regexPattern = filterRegexPattern,
                    channelIds = parseCsv(filterChannelIdsText).toSet(),
                    minImportance = filterMinImportanceText.toIntOrNull() ?: Int.MIN_VALUE,
                    excludeSummary = excludeSummary
                )
            )
            val resolvedDedupe = resolveDedupeConfig(
                isSimpleMode = isSimpleMode,
                preset = dedupePreset,
                original = DedupeConfig(
                    enabled = dedupeEnabled,
                    contentHashEnabled = contentHashEnabled,
                    titleLatestOnly = titleLatestOnly,
                    windowSeconds = dedupeWindowText.toIntOrNull() ?: state.dedupeConfig.windowSeconds
                )
            )
            val resolvedRateLimit = resolveRateLimitConfig(
                isSimpleMode = isSimpleMode,
                preset = dedupePreset,
                original = RateLimitConfig(
                    enabled = rateEnabled,
                    maxPerWindow = rateMaxText.toIntOrNull() ?: state.rateLimitConfig.maxPerWindow,
                    windowSeconds = rateWindowText.toIntOrNull() ?: state.rateLimitConfig.windowSeconds,
                    aggregateWindowSeconds = aggregateWindowText.toIntOrNull() ?: state.rateLimitConfig.aggregateWindowSeconds
                )
            )
            val resolvedQuiet = resolveQuietConfig(
                isSimpleMode = isSimpleMode,
                preset = quietPreset,
                startHour = quietStartHourText,
                startMinute = quietStartMinuteText,
                endHour = quietEndHourText,
                endMinute = quietEndMinuteText,
                daysText = quietDaysText,
                original = QuietHoursConfig(
                    enabled = quietEnabled,
                    startHour = quietStartHourText.toIntOrNull() ?: state.quietHoursConfig.startHour,
                    startMinute = quietStartMinuteText.toIntOrNull() ?: state.quietHoursConfig.startMinute,
                    endHour = quietEndHourText.toIntOrNull() ?: state.quietHoursConfig.endHour,
                    endMinute = quietEndMinuteText.toIntOrNull() ?: state.quietHoursConfig.endMinute,
                    daysOfWeek = parseIntCsv(quietDaysText).toSet()
                )
            )

            Button(
                onClick = {
                    // モードに応じて決定済み設定を確定し、保存は1回でまとめて反映する
                    onSaveRuleConfig(
                        EmbedConfig(
                            enabled = embedEnabled,
                            includePackageField = includePackageField,
                            includeTimeField = includeTimeField,
                            maxFieldLength = maxFieldLengthText.toIntOrNull() ?: state.embedConfig.maxFieldLength
                        ),
                        resolvedFilter,
                        resolvedDedupe,
                        resolvedRateLimit,
                        resolvedQuiet
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ルール設定を保存")
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun PresetSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val label = options[index]
            Row(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Button(
                    onClick = { onSelect(index) },
                    colors = if (selectedIndex == index) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimeRangeEditor(
    startHour: String,
    startMinute: String,
    endHour: String,
    endMinute: String,
    onStartHourChange: (String) -> Unit,
    onStartMinuteChange: (String) -> Unit,
    onEndHourChange: (String) -> Unit,
    onEndMinuteChange: (String) -> Unit
) {
    AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
        OutlinedTextField(
            value = startHour,
            onValueChange = onStartHourChange,
            modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.48f),
            label = { Text("開始時") }
        )
        OutlinedTextField(
            value = startMinute,
            onValueChange = onStartMinuteChange,
            modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.48f),
            label = { Text("開始分") }
        )
    }
    AdaptiveActionGroup(maxItemsInRow = 2) { compact ->
        OutlinedTextField(
            value = endHour,
            onValueChange = onEndHourChange,
            modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.48f),
            label = { Text("終了時") }
        )
        OutlinedTextField(
            value = endMinute,
            onValueChange = onEndMinuteChange,
            modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.48f),
            label = { Text("終了分") }
        )
    }
}

@Composable
private fun RoutingRuleEditor(
    rule: RoutingRule,
    onChange: (RoutingRule) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var enabled by remember(rule.id + "_enabled") { mutableStateOf(rule.enabled) }
    var packageNamesText by remember(rule.id + "_packages") { mutableStateOf(rule.packageNames.joinToString(",")) }
    var keywordsText by remember(rule.id + "_keywords") { mutableStateOf(rule.keywords.joinToString(",")) }
    var useRegex by remember(rule.id + "_regex") { mutableStateOf(rule.useRegex) }
    var regexPattern by remember(rule.id + "_pattern") { mutableStateOf(rule.regexPattern) }
    var webhookUrlsText by remember(rule.id + "_webhooks") { mutableStateOf(rule.webhookUrls.joinToString(",")) }

    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            onChange(rule.copy(name = it, enabled = enabled))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("ルール名") }
    )
    SwitchRow("有効", enabled) {
        enabled = it
        onChange(
            rule.copy(
                name = name,
                enabled = it,
                packageNames = parseCsv(packageNamesText).toSet(),
                keywords = parseCsv(keywordsText),
                useRegex = useRegex,
                regexPattern = regexPattern,
                webhookUrls = parseCsv(webhookUrlsText)
            )
        )
    }
    OutlinedTextField(
        value = packageNamesText,
        onValueChange = {
            packageNamesText = it
            onChange(
                rule.copy(
                    name = name,
                    enabled = enabled,
                    packageNames = parseCsv(it).toSet(),
                    keywords = parseCsv(keywordsText),
                    useRegex = useRegex,
                    regexPattern = regexPattern,
                    webhookUrls = parseCsv(webhookUrlsText)
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("対象パッケージ (カンマ区切り)") }
    )
    OutlinedTextField(
        value = keywordsText,
        onValueChange = {
            keywordsText = it
            onChange(
                rule.copy(
                    name = name,
                    enabled = enabled,
                    packageNames = parseCsv(packageNamesText).toSet(),
                    keywords = parseCsv(it),
                    useRegex = useRegex,
                    regexPattern = regexPattern,
                    webhookUrls = parseCsv(webhookUrlsText)
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("キーワード (カンマ区切り)") }
    )
    SwitchRow("正規表現を使用", useRegex) {
        useRegex = it
        onChange(
            rule.copy(
                name = name,
                enabled = enabled,
                packageNames = parseCsv(packageNamesText).toSet(),
                keywords = parseCsv(keywordsText),
                useRegex = it,
                regexPattern = regexPattern,
                webhookUrls = parseCsv(webhookUrlsText)
            )
        )
    }
    OutlinedTextField(
        value = regexPattern,
        onValueChange = {
            regexPattern = it
            onChange(
                rule.copy(
                    name = name,
                    enabled = enabled,
                    packageNames = parseCsv(packageNamesText).toSet(),
                    keywords = parseCsv(keywordsText),
                    useRegex = useRegex,
                    regexPattern = it,
                    webhookUrls = parseCsv(webhookUrlsText)
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("正規表現") }
    )
    OutlinedTextField(
        value = webhookUrlsText,
        onValueChange = {
            webhookUrlsText = it
            onChange(
                rule.copy(
                    name = name,
                    enabled = enabled,
                    packageNames = parseCsv(packageNamesText).toSet(),
                    keywords = parseCsv(keywordsText),
                    useRegex = useRegex,
                    regexPattern = regexPattern,
                    webhookUrls = parseCsv(it)
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("送信先Webhook (カンマ区切り)") }
    )
    TextButton(onClick = onDelete) {
        Text("このルールを削除")
    }
    Divider()
}

private fun insertToken(current: String, token: String): String {
    return if (current.isBlank()) {
        token
    } else {
        "$current $token"
    }
}

private fun containsAnyPlaceholder(template: String): Boolean {
    return listOf("{app}", "{title}", "{text}", "{time}", "{package}")
        .any { template.contains(it) }
}

private fun buildTemplatePreview(template: String): String {
    return template
        .replace("{app}", "メッセージ")
        .replace("{title}", "新着メッセージ")
        .replace("{text}", "こんにちは、これはプレビューです")
        .replace("{time}", "2026/02/08 10:30:00")
        .replace("{package}", "com.example.app")
}

private fun resolveFilterPreset(config: FilterConfig): SimpleFilterPreset {
    if (!config.enabled) return SimpleFilterPreset.ALL
    if (config.keywords.isNotEmpty()) return SimpleFilterPreset.KEYWORD
    return if (config.minImportance > Int.MIN_VALUE) {
        SimpleFilterPreset.IMPORTANT
    } else {
        SimpleFilterPreset.ALL
    }
}

private fun resolveDedupePreset(config: DedupeConfig): DedupePreset {
    if (!config.enabled || (!config.contentHashEnabled && !config.titleLatestOnly)) {
        return DedupePreset.WEAK
    }
    if (!config.titleLatestOnly || config.windowSeconds <= 20) {
        return DedupePreset.WEAK
    }
    return when {
        config.windowSeconds >= 120 -> DedupePreset.STRONG
        else -> DedupePreset.STANDARD
    }
}

private fun resolveQuietPreset(config: QuietHoursConfig): QuietPreset {
    if (!config.enabled) return QuietPreset.OFF
    return if (
        config.startHour == 22 &&
        config.startMinute == 0 &&
        config.endHour == 7 &&
        config.endMinute == 0 &&
        config.daysOfWeek.isEmpty()
    ) {
        QuietPreset.NIGHT
    } else {
        QuietPreset.CUSTOM
    }
}

private fun resolveFilterConfig(
    isSimpleMode: Boolean,
    preset: SimpleFilterPreset,
    keywordsText: String,
    original: FilterConfig
): FilterConfig {
    if (!isSimpleMode) return original

    return when (preset) {
        SimpleFilterPreset.ALL -> FilterConfig(enabled = false)
        SimpleFilterPreset.IMPORTANT -> FilterConfig(
            enabled = true,
            minImportance = 1,
            excludeSummary = true
        )
        SimpleFilterPreset.KEYWORD -> FilterConfig(
            enabled = true,
            keywords = parseCsv(keywordsText),
            excludeSummary = true
        )
    }
}

private fun resolveDedupeConfig(
    isSimpleMode: Boolean,
    preset: DedupePreset,
    original: DedupeConfig
): DedupeConfig {
    if (!isSimpleMode) return original

    return when (preset) {
        DedupePreset.WEAK -> DedupeConfig(
            enabled = true,
            contentHashEnabled = true,
            titleLatestOnly = false,
            windowSeconds = 20
        )
        DedupePreset.STANDARD -> DedupeConfig(
            enabled = true,
            contentHashEnabled = true,
            titleLatestOnly = true,
            windowSeconds = 45
        )
        DedupePreset.STRONG -> DedupeConfig(
            enabled = true,
            contentHashEnabled = true,
            titleLatestOnly = true,
            windowSeconds = 120
        )
    }
}

private fun resolveRateLimitConfig(
    isSimpleMode: Boolean,
    preset: DedupePreset,
    original: RateLimitConfig
): RateLimitConfig {
    if (!isSimpleMode) return original

    return when (preset) {
        DedupePreset.WEAK -> RateLimitConfig(enabled = true, maxPerWindow = 8, windowSeconds = 30, aggregateWindowSeconds = 5)
        DedupePreset.STANDARD -> RateLimitConfig(enabled = true, maxPerWindow = 5, windowSeconds = 30, aggregateWindowSeconds = 10)
        DedupePreset.STRONG -> RateLimitConfig(enabled = true, maxPerWindow = 3, windowSeconds = 30, aggregateWindowSeconds = 20)
    }
}

private fun resolveQuietConfig(
    isSimpleMode: Boolean,
    preset: QuietPreset,
    startHour: String,
    startMinute: String,
    endHour: String,
    endMinute: String,
    daysText: String,
    original: QuietHoursConfig
): QuietHoursConfig {
    if (!isSimpleMode) return original

    return when (preset) {
        QuietPreset.OFF -> QuietHoursConfig(enabled = false)
        QuietPreset.NIGHT -> QuietHoursConfig(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        QuietPreset.CUSTOM -> QuietHoursConfig(
            enabled = true,
            startHour = startHour.toIntOrNull() ?: 22,
            startMinute = startMinute.toIntOrNull() ?: 0,
            endHour = endHour.toIntOrNull() ?: 7,
            endMinute = endMinute.toIntOrNull() ?: 0,
            daysOfWeek = parseIntCsv(daysText).toSet()
        )
    }
}

private fun parseCsv(value: String): List<String> {
    return value.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun parseIntCsv(value: String): List<Int> {
    return parseCsv(value)
        .mapNotNull { it.toIntOrNull() }
        .filter { it in 1..7 }
}
