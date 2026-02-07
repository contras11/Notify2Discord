package com.notify2discord.app.ui

import android.app.Application
import android.net.Uri
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notify2discord.app.data.AppInfo
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
import com.notify2discord.app.worker.DiscordWebhookEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val state: StateFlow<SettingsState> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    val notificationHistory: StateFlow<List<NotificationRecord>> = repository.notificationHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage

    private val _showRestorePrompt = MutableStateFlow(false)
    val showRestorePrompt: StateFlow<Boolean> = _showRestorePrompt

    private val _hasInternalSnapshot = MutableStateFlow(false)
    val hasInternalSnapshot: StateFlow<Boolean> = _hasInternalSnapshot

    init {
        loadInstalledApps()
        checkRestorePrompt()
        scheduleAutoBackupIfNeeded()
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

    fun importLatestBackup() {
        viewModelScope.launch {
            val result = repository.importLatestSettingsFromDefaultFolder(replaceAll = true)
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

    private fun checkRestorePrompt() {
        viewModelScope.launch {
            val hasSettings = repository.hasAnyMeaningfulSettings()
            val hasInternal = repository.hasInternalSnapshotCandidate()
            _hasInternalSnapshot.value = hasInternal
            _showRestorePrompt.value = !hasSettings
        }
    }

    private fun scheduleAutoBackupIfNeeded() {
        AutoBackupScheduler.schedule(getApplication())
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val pm = app.packageManager

            // 個人プロファイルのアプリ
            val personalPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .associate { appInfo ->
                    appInfo.packageName to pm.getApplicationLabel(appInfo).toString()
                }

            // 仕事領域のアプリを追加（個人プロファイルに存在しないものだけ）
            val workOnlyPackages = mutableMapOf<String, String>()
            try {
                val launcherApps = app.getSystemService(LauncherApps::class.java)
                if (launcherApps != null) {
                    for (profile in launcherApps.getProfiles()) {
                        for (activity in launcherApps.getActivityList(null, profile)) {
                            val pkg = activity.getComponentName().packageName
                            if (!personalPackages.containsKey(pkg) && !workOnlyPackages.containsKey(pkg)) {
                                workOnlyPackages[pkg] = "${activity.getLabel()} (仕事領域)"
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // 仕事領域が存在しない場合や取得できない場合は無視
            }

            _apps.value = (personalPackages.map { (pkg, label) -> AppInfo(pkg, label) } +
                workOnlyPackages.map { (pkg, label) -> AppInfo(pkg, label) })
                .sortedBy { it.label.lowercase() }
        }
    }
}
