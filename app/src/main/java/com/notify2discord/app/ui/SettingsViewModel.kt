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
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                .map { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString()
                    AppInfo(appInfo.packageName, label)
                }
                .sortedBy { it.label.lowercase() }

            _apps.value = apps
        }
    }
}
