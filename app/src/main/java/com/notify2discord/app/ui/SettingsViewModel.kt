package com.notify2discord.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notify2discord.app.data.AppInfo
import com.notify2discord.app.data.NotificationRecord
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.data.SettingsState
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

    init {
        loadInstalledApps()
    }

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            repository.setWebhookUrl(url)
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
            repository.setAppWebhook(packageName, webhookUrl)
        }
    }

    fun setThemeMode(mode: com.notify2discord.app.data.ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
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

    fun clearNotificationHistoryByApp(packageName: String) {
        viewModelScope.launch {
            repository.clearNotificationHistoryByApp(packageName)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val pm = app.packageManager

            // 個人プロファイルのアプリ
            val personalPackages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .associate { appInfo ->
                    appInfo.packageName to pm.getApplicationLabel(appInfo).toString()
                }

            // 仕事領域のアプリを追加（個人プロファイルに存在しないものだけ）
            val workOnlyPackages = mutableMapOf<String, String>()
            try {
                val userManager = app.getSystemService(android.os.UserManager::class.java)
                if (userManager != null) {
                    for (profile in userManager.profiles) {
                        if (!userManager.isManagedProfileUser(profile)) continue
                        val profilePM = app.createContextForUser(profile).packageManager
                        for (appInfo in profilePM.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)) {
                            if (!personalPackages.containsKey(appInfo.packageName) && !workOnlyPackages.containsKey(appInfo.packageName)) {
                                workOnlyPackages[appInfo.packageName] = "${profilePM.getApplicationLabel(appInfo)} (仕事領域)"
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
