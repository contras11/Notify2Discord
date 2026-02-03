package com.notify2discord.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notify2discord.app.data.AppInfo
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

    fun toggleExcluded(packageName: String, excluded: Boolean) {
        viewModelScope.launch {
            repository.toggleExcludedPackage(packageName, excluded)
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

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager
            val apps = pm.getInstalledApplications(0)
                .map { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString()
                    AppInfo(appInfo.packageName, label)
                }
                .sortedBy { it.label.lowercase() }

            // 画面表示用のリストに反映
            _apps.value = apps
        }
    }
}
