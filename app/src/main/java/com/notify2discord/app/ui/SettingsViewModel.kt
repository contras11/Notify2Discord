package com.notify2discord.app.ui

import android.app.Application
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notify2discord.app.battery.BatteryInfoCollector
import com.notify2discord.app.data.AppInfo
import com.notify2discord.app.data.BatteryHistoryConfig
import com.notify2discord.app.data.BatteryReportConfig
import com.notify2discord.app.data.BatterySnapshot
import com.notify2discord.app.data.DedupeConfig
import com.notify2discord.app.data.EmbedConfig
import com.notify2discord.app.data.FilterConfig
import com.notify2discord.app.data.NotificationRecord
import com.notify2discord.app.data.QuietHoursConfig
import com.notify2discord.app.data.RateLimitConfig
import com.notify2discord.app.data.RoutingRule
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.data.SettingsState
import com.notify2discord.app.data.ThemeMode
import com.notify2discord.app.data.WebhookHealthStatus
import com.notify2discord.app.worker.AutoBackupScheduler
import com.notify2discord.app.worker.BatterySnapshotScheduler
import com.notify2discord.app.worker.BatteryStatusScheduler
import com.notify2discord.app.worker.DiscordWebhookEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val batteryInfoCollector = BatteryInfoCollector(application)

    val state: StateFlow<SettingsState> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    val notificationHistory: StateFlow<List<NotificationRecord>> = repository.notificationHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val historyReadMarkers: StateFlow<Map<String, Long>> = repository.historyReadMarkersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _currentBatterySnapshot = MutableStateFlow<BatterySnapshot?>(null)
    val currentBatterySnapshot: StateFlow<BatterySnapshot?> = _currentBatterySnapshot

    val batteryGraphRangeDays: StateFlow<Int> = state
        .map { it.batteryHistoryConfig.defaultRangeDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage

    private val _showRestorePrompt = MutableStateFlow(false)
    val showRestorePrompt: StateFlow<Boolean> = _showRestorePrompt

    private val _hasInternalSnapshot = MutableStateFlow(false)
    val hasInternalSnapshot: StateFlow<Boolean> = _hasInternalSnapshot

    init {
        loadInstalledApps()
        checkRestorePrompt()
        cancelScheduledAutoBackup()
        trimBatteryHistoryOnStartup()
        syncBatteryReportScheduler()
        syncBatterySnapshotScheduler()
        refreshBatteryInfo()
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    fun dismissRestorePrompt() {
        _showRestorePrompt.value = false
    }

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            val trimmed = url.trim()
            repository.setWebhookUrl(trimmed)
            if (trimmed.isBlank()) return@launch

            val status = repository.checkWebhookHealth(trimmed)
            repository.saveWebhookHealthStatus(status)
        }
    }

    fun recheckWebhook(url: String) {
        viewModelScope.launch {
            if (url.isBlank()) return@launch
            val status: WebhookHealthStatus = repository.checkWebhookHealth(url)
            repository.saveWebhookHealthStatus(status)
        }
    }

    fun setForwardingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setForwardingEnabled(enabled)
        }
    }

    fun toggleSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            repository.toggleSelectedPackage(packageName, selected)
        }
    }

    fun setCaptureHistoryWhenForwardingOff(enabled: Boolean) {
        viewModelScope.launch {
            repository.setCaptureHistoryWhenForwardingOff(enabled)
        }
    }

    fun toggleHistoryCapturePackage(packageName: String, selected: Boolean) {
        viewModelScope.launch {
            repository.toggleHistoryCapturePackage(packageName, selected)
        }
    }

    fun setAppWebhook(packageName: String, webhookUrl: String) {
        viewModelScope.launch {
            val trimmed = webhookUrl.trim()
            repository.setAppWebhook(packageName, trimmed)
            if (trimmed.isBlank()) return@launch

            val status = repository.checkWebhookHealth(trimmed)
            repository.saveWebhookHealthStatus(status)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setRulesSimpleMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setUiModeRulesSimple(enabled)
        }
    }

    fun saveDefaultTemplate(template: String) {
        viewModelScope.launch {
            repository.saveTemplate(packageName = null, template = template)
            _operationMessage.value = "テンプレートを保存しました"
        }
    }

    fun saveAppTemplate(packageName: String, template: String) {
        viewModelScope.launch {
            repository.saveTemplate(packageName = packageName, template = template)
            _operationMessage.value = "アプリ別テンプレートを保存しました"
        }
    }

    fun saveRuleConfig(
        embedConfig: EmbedConfig,
        filterConfig: FilterConfig,
        dedupeConfig: DedupeConfig,
        rateLimitConfig: RateLimitConfig,
        quietHoursConfig: QuietHoursConfig
    ) {
        viewModelScope.launch {
            repository.saveRuleConfig(
                embedConfig = embedConfig,
                filterConfig = filterConfig,
                dedupeConfig = dedupeConfig,
                rateLimitConfig = rateLimitConfig,
                quietHoursConfig = quietHoursConfig
            )
            _operationMessage.value = "ルール設定を保存しました"
        }
    }

    fun saveRoutingRules(rules: List<RoutingRule>) {
        viewModelScope.launch {
            repository.saveRoutingRules(rules)
            _operationMessage.value = "ルーティングを保存しました"
        }
    }

    fun exportSettingsNow() {
        viewModelScope.launch {
            val result = repository.exportSettingsToDefaultFolder()
            _operationMessage.value = result.message
            if (result.success) {
                _showRestorePrompt.value = false
                _hasInternalSnapshot.value = repository.hasInternalSnapshotCandidate()
            }
        }
    }

    fun exportSettingsToPickedFile(uri: Uri) {
        viewModelScope.launch {
            val result = repository.exportSettingsToUri(uri)
            _operationMessage.value = result.message
            if (result.success) {
                _showRestorePrompt.value = false
                _hasInternalSnapshot.value = repository.hasInternalSnapshotCandidate()
            }
        }
    }

    fun importSettingsFromPickedFile(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importSettingsFromUri(uri, replaceAll = true)
            _operationMessage.value = result.message
            if (result.success) {
                _showRestorePrompt.value = false
                _hasInternalSnapshot.value = repository.hasInternalSnapshotCandidate()
            }
        }
    }

    fun restoreFromInternalSnapshot() {
        viewModelScope.launch {
            val result = repository.restoreFromInternalSnapshot()
            _operationMessage.value = result.message
            if (result.success) {
                _showRestorePrompt.value = false
            }
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val settings = repository.getSettingsSnapshot()
            if (settings.webhookUrl.isBlank()) return@launch

            val content = "[テスト] Notify2Discord の送信テストです"
            DiscordWebhookEnqueuer.enqueue(getApplication(), settings.webhookUrl, content)
        }
    }

    fun deleteNotificationRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteNotificationRecord(id)
        }
    }

    fun clearNotificationHistory() {
        viewModelScope.launch {
            repository.clearNotificationHistory()
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { repository.setRetentionDays(days) }
    }

    fun cleanupExpiredRecords() {
        viewModelScope.launch { repository.cleanupExpiredRecords() }
    }

    fun deleteNotificationRecords(ids: Set<Long>) {
        viewModelScope.launch { repository.deleteNotificationRecords(ids) }
    }

    fun clearNotificationHistoryByApp(packageName: String) {
        viewModelScope.launch {
            repository.clearNotificationHistoryByApp(packageName)
        }
    }

    fun clearNotificationHistoryByApps(packageNames: Set<String>) {
        viewModelScope.launch {
            repository.clearNotificationHistoryByApps(packageNames)
        }
    }

    fun clearNotificationHistoryByGroupKey(historyGroupKey: String) {
        viewModelScope.launch {
            repository.clearNotificationHistoryByGroupKey(historyGroupKey)
        }
    }

    fun markHistoryRead(historyKey: String, readUntilPostTime: Long) {
        viewModelScope.launch {
            repository.markHistoryRead(historyKey, readUntilPostTime)
        }
    }

    fun markAppHistoryRead(packageName: String, readUntilPostTime: Long) {
        viewModelScope.launch {
            repository.markAppHistoryRead(packageName, readUntilPostTime)
        }
    }

    fun saveBatteryReportConfig(
        enabled: Boolean,
        intervalMinutes: Int,
        startHour: Int,
        startMinute: Int
    ) {
        viewModelScope.launch {
            val normalized = intervalMinutes.coerceIn(15, 1440)
            repository.saveBatteryReportConfig(
                BatteryReportConfig(
                    enabled = enabled,
                    intervalMinutes = normalized,
                    startHour = startHour.coerceIn(0, 23),
                    startMinute = startMinute.coerceIn(0, 59)
                )
            )
            _operationMessage.value = if (enabled) {
                "バッテリー残量の定期送信を保存しました"
            } else {
                "バッテリー残量の定期送信を停止しました"
            }
        }
    }

    fun saveBatteryHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettingsSnapshot().batteryHistoryConfig
            repository.saveBatteryHistoryConfig(current.copy(enabled = enabled))
            _operationMessage.value = if (enabled) {
                "バッテリー履歴の収集を有効にしました"
            } else {
                "バッテリー履歴の収集を停止しました"
            }
        }
    }

    fun saveBatteryNominalCapacity(capacityMah: Float?) {
        viewModelScope.launch {
            repository.saveBatteryNominalCapacityMah(capacityMah)
            _operationMessage.value = if (capacityMah == null) {
                "公称容量を未設定に戻しました"
            } else {
                "公称容量を保存しました"
            }
            refreshBatteryInfo()
        }
    }

    fun setBatteryGraphRangeDays(days: Int) {
        viewModelScope.launch {
            val normalized = when (days) {
                7, 30, 90 -> days
                else -> 30
            }
            val current = repository.getSettingsSnapshot().batteryHistoryConfig
            repository.saveBatteryHistoryConfig(current.copy(defaultRangeDays = normalized))
        }
    }

    fun refreshBatteryInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.getSettingsSnapshot()
            // 履歴の最大推定容量を参照して現在の推定劣化率を算出する
            _currentBatterySnapshot.value = batteryInfoCollector.collect(
                history = settings.batteryHistory,
                nominalCapacityMah = settings.batteryNominalCapacityMah
            )
        }
    }

    private fun checkRestorePrompt() {
        viewModelScope.launch {
            val hasSettings = repository.hasAnyMeaningfulSettings()
            val hasInternal = repository.hasInternalSnapshotCandidate()
            _hasInternalSnapshot.value = hasInternal
            _showRestorePrompt.value = !hasSettings
        }
    }

    private fun cancelScheduledAutoBackup() {
        // 既存ユーザー端末で残っている定期バックアップ予約を停止する
        AutoBackupScheduler.cancel(getApplication())
    }

    private fun trimBatteryHistoryOnStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.getSettingsSnapshot().batteryHistoryConfig
            repository.trimBatteryHistory(config.retentionDays)
        }
    }

    private fun syncBatteryReportScheduler() {
        viewModelScope.launch {
            repository.settingsFlow
                .map { settings -> settings.batteryReportConfig to settings.webhookUrl }
                .distinctUntilChanged()
                .collect { (batteryConfig, webhookUrl) ->
                    BatteryStatusScheduler.sync(
                        context = getApplication(),
                        config = batteryConfig,
                        webhookUrl = webhookUrl
                    )
                }
        }
    }

    private fun syncBatterySnapshotScheduler() {
        viewModelScope.launch {
            repository.settingsFlow
                .map { settings -> settings.batteryHistoryConfig }
                .distinctUntilChanged()
                .collect { config: BatteryHistoryConfig ->
                    BatterySnapshotScheduler.sync(
                        context = getApplication(),
                        config = config
                    )
                }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val pm = app.packageManager

            // 個人プロファイルのアプリ。システムアプリ判定も同時に持つ。
            val personalApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        isSystemApp = isSystemApp(appInfo)
                    )
                }
            val personalPackageNames = personalApps.map { it.packageName }.toSet()

            // 仕事領域のアプリを追加（個人プロファイルに存在しないものだけ）
            val workOnlyApps = mutableListOf<AppInfo>()
            try {
                val launcherApps = app.getSystemService(LauncherApps::class.java)
                if (launcherApps != null) {
                    for (profile in launcherApps.getProfiles()) {
                        for (activity in launcherApps.getActivityList(null, profile)) {
                            val pkg = activity.getComponentName().packageName
                            if (!personalPackageNames.contains(pkg) && workOnlyApps.none { it.packageName == pkg }) {
                                val isSystem = runCatching {
                                    isSystemApp(pm.getApplicationInfo(pkg, 0))
                                }.getOrDefault(false)
                                workOnlyApps += AppInfo(
                                    packageName = pkg,
                                    label = "${activity.getLabel()} (仕事領域)",
                                    isSystemApp = isSystem
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // 仕事領域が存在しない場合や取得できない場合は無視
            }

            _apps.value = (personalApps + workOnlyApps)
                .sortedBy { it.label.lowercase() }
        }
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        val flags = appInfo.flags
        return (flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
}
