package com.notify2discord.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.notify2discord.app.R
import com.notify2discord.app.battery.BatteryInfoCollector
import com.notify2discord.app.data.BatterySnapshot
import com.notify2discord.app.data.SettingsState
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(
    state: SettingsState,
    currentSnapshot: BatterySnapshot?,
    graphRangeDays: Int,
    onRefreshBatteryInfo: () -> Unit,
    onSaveBatteryReportConfig: (Boolean, Int, Int, Int) -> Unit,
    onSaveBatteryHistoryEnabled: (Boolean) -> Unit,
    onSetBatteryGraphRangeDays: (Int) -> Unit,
    onSaveBatteryNominalCapacity: (Float?) -> Unit
) {
    LaunchedEffect(Unit) {
        onRefreshBatteryInfo()
    }

    var reportEnabled by rememberSaveable(state.batteryReportConfig.enabled) {
        mutableStateOf(state.batteryReportConfig.enabled)
    }
    var intervalText by rememberSaveable(state.batteryReportConfig.intervalMinutes) {
        mutableStateOf(state.batteryReportConfig.intervalMinutes.toString())
    }
    var startHourText by rememberSaveable(state.batteryReportConfig.startHour) {
        mutableStateOf(state.batteryReportConfig.startHour.toString())
    }
    var startMinuteText by rememberSaveable(state.batteryReportConfig.startMinute) {
        mutableStateOf("%02d".format(state.batteryReportConfig.startMinute))
    }
    var nominalCapacityText by rememberSaveable(state.batteryNominalCapacityMah) {
        mutableStateOf(state.batteryNominalCapacityMah?.let { "%.0f".format(it) }.orEmpty())
    }

    val intervalMinutes = intervalText.toIntOrNull()
    val isIntervalValid = intervalMinutes in 15..1440
    val startHour = startHourText.toIntOrNull()
    val isStartHourValid = startHour in 0..23
    val startMinute = startMinuteText.toIntOrNull()
    val isStartMinuteValid = startMinute in 0..59
    val isStartTimeValid = isStartHourValid && isStartMinuteValid
    val nominalCapacity = nominalCapacityText.trim().toFloatOrNull()
    val isNominalCapacityValid = nominalCapacityText.isBlank() ||
        (nominalCapacity != null && nominalCapacity in 500f..15000f)
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val unavailableLabel = stringResource(id = R.string.battery_data_unavailable)

    val now = System.currentTimeMillis()
    val cutoff = now - graphRangeDays.toLong() * 24 * 60 * 60 * 1000
    val filteredHistory = state.batteryHistory.filter { it.capturedAt >= cutoff }
    val (chargeCount, dischargeCount) = countChargeDischargeTransitions(state.batteryHistory)
    val levelText = currentSnapshot?.levelPercent?.let { "${it}%" } ?: unavailableLabel
    val healthEstimate = currentSnapshot?.estimatedHealthByDesignPercent
        ?.let { "${"%.1f".format(it)}%（設計容量基準）" }
        ?: currentSnapshot?.estimatedHealthPercent?.let { "${"%.1f".format(it)}%（履歴基準）" }
        ?: "推定不可"
    val healthLabel = BatteryInfoCollector.healthLabel(currentSnapshot?.health)
    val statusLabel = BatteryInfoCollector.statusLabel(currentSnapshot?.status)
    val chargeCycleItems = buildChargeCycleItems(
        cycleCount = currentSnapshot?.cycleCount,
        chargeCount = chargeCount,
        dischargeCount = dischargeCount,
        unavailableLabel = unavailableLabel
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("バッテリー管理") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onRefreshBatteryInfo) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "更新")
                    }
                }
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
            BatteryInfoCard(
                title = "基本情報",
                items = listOf(
                    "残量" to levelText,
                    "状態" to statusLabel,
                    "健康" to healthLabel,
                    "推定劣化" to healthEstimate,
                    "技術" to currentSnapshot?.technology.orEmpty().ifBlank { unavailableLabel }
                )
            )
            BatteryInfoCard(
                title = "詳細情報",
                items = listOf(
                    "電流（瞬間）" to formatCurrentUa(currentSnapshot?.currentNowUa),
                    "電流（平均）" to formatCurrentUa(currentSnapshot?.currentAverageUa),
                    "電圧" to formatVoltageMv(currentSnapshot?.voltageMv),
                    "温度" to formatTemperature(currentSnapshot?.temperatureC),
                    "エネルギー" to formatEnergyNwh(currentSnapshot?.energyCounterNwh),
                    "公称容量（手入力）" to formatCapacityMah(state.batteryNominalCapacityMah),
                    "設計容量" to formatCapacityMah(currentSnapshot?.designCapacityMah),
                    "残容量" to formatChargeCounter(currentSnapshot?.chargeCounterUah)
                )
            )
            BatteryInfoCard(
                title = "充放電情報",
                items = chargeCycleItems
            )

            Card(colors = AppCardColors.emphasized()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "劣化推定グラフ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.battery_graph_range_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AdaptiveActionGroup(maxItemsInRow = 3) { compact ->
                        listOf(7, 30, 90).forEach { range ->
                            val selected = range == graphRangeDays
                            if (selected) {
                                Button(
                                    onClick = { onSetBatteryGraphRangeDays(range) },
                                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                                ) {
                                    Text("${range}日")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSetBatteryGraphRangeDays(range) },
                                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                                ) {
                                    Text("${range}日")
                                }
                            }
                        }
                    }
                    BatteryHealthLineChart(
                        snapshots = filteredHistory,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(id = R.string.battery_estimated_value_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(id = R.string.battery_observed_value_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(colors = AppCardColors.normal()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "劣化履歴の収集（1時間ごと）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("履歴収集を有効にする")
                        Switch(
                            checked = state.batteryHistoryConfig.enabled,
                            onCheckedChange = onSaveBatteryHistoryEnabled
                        )
                    }
                    Text(
                        text = "保持期間: ${state.batteryHistoryConfig.retentionDays}日（自動削除）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "※ ${stringResource(id = R.string.battery_data_unavailable)}の項目は端末仕様により未提供です。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(colors = AppCardColors.emphasized()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.battery_report_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(id = R.string.battery_report_toggle))
                        Switch(
                            checked = reportEnabled,
                            onCheckedChange = { reportEnabled = it }
                        )
                    }
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(id = R.string.battery_report_interval_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = reportEnabled && !isIntervalValid
                    )
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = startHourText,
                                onValueChange = { startHourText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(id = R.string.battery_report_start_hour_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = reportEnabled && !isStartHourValid
                            )
                            OutlinedTextField(
                                value = startMinuteText,
                                onValueChange = { startMinuteText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text(stringResource(id = R.string.battery_report_start_minute_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = reportEnabled && !isStartMinuteValid
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startHourText,
                                onValueChange = { startHourText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.battery_report_start_hour_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = reportEnabled && !isStartHourValid
                            )
                            OutlinedTextField(
                                value = startMinuteText,
                                onValueChange = { startMinuteText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(id = R.string.battery_report_start_minute_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = reportEnabled && !isStartMinuteValid
                            )
                        }
                    }
                    if (reportEnabled && !isIntervalValid) {
                        Text(
                            text = stringResource(id = R.string.battery_report_interval_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (reportEnabled && !isStartTimeValid) {
                        Text(
                            text = stringResource(id = R.string.battery_report_start_time_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!reportEnabled || (isIntervalValid && isStartTimeValid)) {
                        Text(
                            text = stringResource(id = R.string.battery_report_interval_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.battery_report_start_time_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            val normalizedMinute = startMinute ?: state.batteryReportConfig.startMinute
                            onSaveBatteryReportConfig(
                                reportEnabled,
                                intervalMinutes ?: state.batteryReportConfig.intervalMinutes,
                                startHour ?: state.batteryReportConfig.startHour,
                                normalizedMinute
                            )
                            // 保存ボタン押下後の表示も2桁表記にそろえる
                            startMinuteText = "%02d".format(normalizedMinute.coerceIn(0, 59))
                        },
                        enabled = !reportEnabled || (isIntervalValid && isStartTimeValid),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.battery_report_save))
                    }
                }
            }

            Card(colors = AppCardColors.normal()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "公称容量 (mAh)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "手入力すると、劣化率の計算で最優先で使用します。空欄なら端末値を使います。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = nominalCapacityText,
                        onValueChange = { nominalCapacityText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("公称容量 (mAh)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = !isNominalCapacityValid
                    )
                    if (!isNominalCapacityValid) {
                        Text(
                            text = "500〜15000 mAh の範囲で入力してください（空欄は未設定）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = {
                            onSaveBatteryNominalCapacity(
                                nominalCapacity?.coerceIn(500f, 15000f)
                            )
                        },
                        enabled = isNominalCapacityValid,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("公称容量を保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryInfoCard(
    title: String,
    items: List<Pair<String, String>>
) {
    Card(colors = AppCardColors.normal()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            items.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (index != items.lastIndex) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }
    }
}

private fun countChargeDischargeTransitions(history: List<BatterySnapshot>): Pair<Int, Int> {
    if (history.size < 2) return 0 to 0
    var charge = 0
    var discharge = 0
    val sorted = history.sortedBy { it.capturedAt }
    var previous = sorted.first().isCharging
    // 充電状態の遷移回数をアプリ観測値として扱う
    sorted.drop(1).forEach { snapshot ->
        val current = snapshot.isCharging
        if (!previous && current) charge += 1
        if (previous && !current) discharge += 1
        previous = current
    }
    return charge to discharge
}

private fun formatCurrentUa(value: Int?): String {
    if (value == null) return "取得不可"
    return "${"%.0f".format(value.absoluteValue / 1000f)} mA"
}

private fun formatVoltageMv(value: Int?): String {
    if (value == null) return "取得不可"
    return "${"%.2f".format(value / 1000f)} V"
}

private fun formatTemperature(value: Float?): String {
    if (value == null) return "取得不可"
    return "${"%.1f".format(value)} ℃"
}

private fun formatEnergyNwh(value: Long?): String {
    if (value == null) return "取得不可"
    return "${"%.1f".format(value / 1_000_000f)} Wh"
}

private fun formatChargeCounter(value: Int?): String {
    if (value == null) return "取得不可"
    return "${"%.0f".format(value / 1000f)} mAh"
}

private fun formatCapacityMah(value: Float?): String {
    if (value == null) return "取得不可"
    return "${"%.0f".format(value)} mAh"
}

private fun buildChargeCycleItems(
    cycleCount: Int?,
    chargeCount: Int,
    dischargeCount: Int,
    unavailableLabel: String
): List<Pair<String, String>> {
    return if (cycleCount != null) {
        listOf("充放電回数（OS値）" to "$cycleCount 回")
    } else {
        listOf(
            "充放電回数（OS値）" to unavailableLabel,
            "充電開始回数（アプリ観測値）" to "$chargeCount 回",
            "放電開始回数（アプリ観測値）" to "$dischargeCount 回"
        )
    }
}
