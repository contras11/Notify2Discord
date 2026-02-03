package com.notify2discord.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {
    private val dataStore = context.settingsDataStore

    private val keyWebhookUrl = stringPreferencesKey("webhook_url")
    private val keyForwardingEnabled = booleanPreferencesKey("forwarding_enabled")
    private val keyExcludedPackages = stringSetPreferencesKey("excluded_packages")

    val settingsFlow: Flow<SettingsState> = dataStore.data.map { prefs ->
        SettingsState(
            webhookUrl = prefs[keyWebhookUrl] ?: "",
            forwardingEnabled = prefs[keyForwardingEnabled] ?: true,
            excludedPackages = prefs[keyExcludedPackages] ?: emptySet()
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

    suspend fun setExcludedPackages(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[keyExcludedPackages] = packages
        }
    }

    suspend fun toggleExcludedPackage(packageName: String, excluded: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[keyExcludedPackages] ?: emptySet()
            val updated = if (excluded) {
                current + packageName
            } else {
                current - packageName
            }
            prefs[keyExcludedPackages] = updated
        }
    }
}
