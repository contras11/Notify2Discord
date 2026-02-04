package com.notify2discord.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(private val context: Context) {
    private val dataStore = context.settingsDataStore

    private val keyWebhookUrl = stringPreferencesKey("webhook_url")
    private val keyForwardingEnabled = booleanPreferencesKey("forwarding_enabled")
    private val keyExcludedPackages = stringSetPreferencesKey("excluded_packages")
    private val keyAppWebhooks = stringPreferencesKey("app_webhooks")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyNotificationHistory = stringPreferencesKey("notification_history")
    private val keyRetentionDays = intPreferencesKey("retention_days")

    val settingsFlow: Flow<SettingsState> = dataStore.data.map { prefs ->
        SettingsState(
            webhookUrl = prefs[keyWebhookUrl] ?: "",
            forwardingEnabled = prefs[keyForwardingEnabled] ?: true,
            selectedPackages = prefs[keyExcludedPackages] ?: emptySet(),
            appWebhooks = deserializeAppWebhooks(prefs[keyAppWebhooks] ?: ""),
            themeMode = ThemeMode.valueOf(prefs[keyThemeMode] ?: ThemeMode.SYSTEM.name),
            retentionDays = prefs[keyRetentionDays] ?: 30
        )
    }

    suspend fun getSettingsSnapshot(): SettingsState = settingsFlow.first()

    suspend fun setWebhookUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[keyWebhookUrl] = url.trim()
        }
    }

    suspend fun setForwardingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[keyForwardingEnabled] = enabled
        }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[keyExcludedPackages] = packages
        }
    }

    suspend fun toggleSelectedPackage(packageName: String, selected: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[keyExcludedPackages] ?: emptySet()
            val updated = if (selected) {
                current + packageName
            } else {
                current - packageName
            }
            prefs[keyExcludedPackages] = updated
        }
    }

    suspend fun setAppWebhook(packageName: String, webhookUrl: String) {
        dataStore.edit { prefs ->
            val current = deserializeAppWebhooks(prefs[keyAppWebhooks] ?: "")
            val updated = if (webhookUrl.isBlank()) {
                current - packageName
            } else {
                current + (packageName to webhookUrl.trim())
            }
            prefs[keyAppWebhooks] = serializeAppWebhooks(updated)
        }
    }

    private fun serializeAppWebhooks(map: Map<String, String>): String {
        return map.entries.joinToString(";") { "${it.key}::${it.value}" }
    }

    private fun deserializeAppWebhooks(str: String): Map<String, String> {
        if (str.isBlank()) return emptyMap()
        return str.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("::", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[keyThemeMode] = mode.name
        }
    }

    // --- 通知履歴 ---

    val notificationHistoryFlow: Flow<List<NotificationRecord>> = dataStore.data.map { prefs ->
        deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
    }

    suspend fun saveNotificationRecord(record: NotificationRecord) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            var updated = (listOf(record) + current).take(500)
            val retentionDays = prefs[keyRetentionDays] ?: 30
            if (retentionDays != -1) {
                val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
                updated = updated.filter { it.postTime >= cutoff }
            }
            prefs[keyNotificationHistory] = serializeNotificationHistory(updated)
        }
    }

    suspend fun deleteNotificationRecord(id: Long) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            prefs[keyNotificationHistory] = serializeNotificationHistory(current.filter { it.id != id })
        }
    }

    suspend fun clearNotificationHistory() {
        dataStore.edit { prefs ->
            prefs[keyNotificationHistory] = ""
        }
    }

    suspend fun setRetentionDays(days: Int) {
        dataStore.edit { prefs -> prefs[keyRetentionDays] = days }
    }

    suspend fun cleanupExpiredRecords() {
        dataStore.edit { prefs ->
            val retentionDays = prefs[keyRetentionDays] ?: 30
            if (retentionDays == -1) return@edit
            val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            prefs[keyNotificationHistory] = serializeNotificationHistory(current.filter { it.postTime >= cutoff })
        }
    }

    suspend fun deleteNotificationRecords(ids: Set<Long>) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            prefs[keyNotificationHistory] = serializeNotificationHistory(current.filter { it.id !in ids })
        }
    }

    suspend fun clearNotificationHistoryByApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            prefs[keyNotificationHistory] = serializeNotificationHistory(current.filter { it.packageName != packageName })
        }
    }

    private fun serializeNotificationHistory(records: List<NotificationRecord>): String {
        val array = JSONArray()
        for (r in records) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("packageName", r.packageName)
            obj.put("appName", r.appName)
            obj.put("title", r.title)
            obj.put("text", r.text)
            obj.put("postTime", r.postTime)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeNotificationHistory(str: String): List<NotificationRecord> {
        if (str.isBlank()) return emptyList()
        return try {
            val array = JSONArray(str)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NotificationRecord(
                    id = obj.getLong("id"),
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("appName"),
                    title = obj.getString("title"),
                    text = obj.getString("text"),
                    postTime = obj.getLong("postTime")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
