package com.notify2discord.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class SettingsRepository(private val context: Context) {
    private val dataStore = context.settingsDataStore
    private val webhookCheckClient = OkHttpClient()
    private val backupManager = SettingsBackupManager(context)
    private val legacyDefaultTemplates = setOf(
        "[アプリ] {app}\\n[タイトル] {title}\\n[本文] {text}\\n[時刻] {time}",
        "[アプリ] {app}\n[タイトル] {title}\n[本文] {text}\n[時刻] {time}"
    )

    private val keyWebhookUrl = stringPreferencesKey("webhook_url")
    private val keyForwardingEnabled = booleanPreferencesKey("forwarding_enabled")
    private val keyExcludedPackages = stringSetPreferencesKey("excluded_packages")
    private val keyAppWebhooks = stringPreferencesKey("app_webhooks")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyNotificationHistory = stringPreferencesKey("notification_history")
    private val keyHistoryReadMarkers = stringPreferencesKey("history_read_markers")
    private val keyRetentionDays = intPreferencesKey("retention_days")

    private val keyDefaultTemplate = stringPreferencesKey("default_template")
    private val keyAppTemplates = stringPreferencesKey("app_templates")
    private val keyEmbedConfig = stringPreferencesKey("embed_config")
    private val keyFilterConfig = stringPreferencesKey("filter_config")
    private val keyDedupeConfig = stringPreferencesKey("dedupe_config")
    private val keyRateLimitConfig = stringPreferencesKey("rate_limit_config")
    private val keyQuietHoursConfig = stringPreferencesKey("quiet_hours_config")
    private val keyRoutingRules = stringPreferencesKey("routing_rules")
    private val keyWebhookHealthCache = stringPreferencesKey("webhook_health_cache")
    private val keyPendingQuietQueue = stringPreferencesKey("pending_quiet_queue")

    private val keyUiModeRulesSimple = booleanPreferencesKey("ui_mode_rules_simple")
    private val keyLastBackupAt = longPreferencesKey("last_backup_at")
    private val keyLastManualBackupAt = longPreferencesKey("last_manual_backup_at")
    private val keyBackupSchemaVersion = intPreferencesKey("backup_schema_version")
    private val keyInternalSettingsSnapshot = stringPreferencesKey("internal_settings_snapshot")

    val settingsFlow: Flow<SettingsState> = dataStore.data.map { prefs ->
        val storedDefaultTemplate = prefs[keyDefaultTemplate]
        val normalizedDefaultTemplate = normalizeLoadedTemplate(
            storedDefaultTemplate ?: SettingsState.DEFAULT_TEMPLATE
        )
        val resolvedDefaultTemplate = migrateLegacyDefaultTemplate(
            storedValue = storedDefaultTemplate,
            normalizedValue = normalizedDefaultTemplate
        )
        val rawHealthCache = deserializeWebhookHealthCache(prefs[keyWebhookHealthCache] ?: "")
        val effectiveHealthCache = rawHealthCache.mapValues { (_, status) ->
            status.copy(effectiveState = computeEffectiveState(status, System.currentTimeMillis()))
        }

        SettingsState(
            webhookUrl = prefs[keyWebhookUrl] ?: "",
            forwardingEnabled = prefs[keyForwardingEnabled] ?: true,
            selectedPackages = prefs[keyExcludedPackages] ?: emptySet(),
            appWebhooks = deserializeAppWebhooks(prefs[keyAppWebhooks] ?: ""),
            themeMode = parseThemeMode(prefs[keyThemeMode]),
            retentionDays = prefs[keyRetentionDays] ?: 30,
            defaultTemplate = resolvedDefaultTemplate,
            appTemplates = deserializeStringMap(prefs[keyAppTemplates] ?: "")
                .mapValues { (_, value) -> normalizeLoadedTemplate(value) },
            embedConfig = deserializeEmbedConfig(prefs[keyEmbedConfig] ?: ""),
            filterConfig = deserializeFilterConfig(prefs[keyFilterConfig] ?: ""),
            dedupeConfig = deserializeDedupeConfig(prefs[keyDedupeConfig] ?: ""),
            rateLimitConfig = deserializeRateLimitConfig(prefs[keyRateLimitConfig] ?: ""),
            quietHoursConfig = deserializeQuietHoursConfig(prefs[keyQuietHoursConfig] ?: ""),
            routingRules = deserializeRoutingRules(prefs[keyRoutingRules] ?: ""),
            webhookHealthCache = effectiveHealthCache,
            pendingQuietQueue = deserializePendingQuietQueue(prefs[keyPendingQuietQueue] ?: ""),
            uiModeRulesSimple = prefs[keyUiModeRulesSimple] ?: true,
            lastBackupAt = prefs[keyLastBackupAt],
            lastManualBackupAt = prefs[keyLastManualBackupAt],
            backupSchemaVersion = prefs[keyBackupSchemaVersion] ?: SettingsState.BACKUP_SCHEMA_VERSION
        )
    }

    suspend fun getSettingsSnapshot(): SettingsState = settingsFlow.first()

    suspend fun setWebhookUrl(url: String) {
        editSettings {
            this[keyWebhookUrl] = url.trim()
        }
    }

    suspend fun setForwardingEnabled(enabled: Boolean) {
        editSettings {
            this[keyForwardingEnabled] = enabled
        }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        editSettings {
            this[keyExcludedPackages] = packages
        }
    }

    suspend fun toggleSelectedPackage(packageName: String, selected: Boolean) {
        editSettings {
            val current = this[keyExcludedPackages] ?: emptySet()
            val updated = if (selected) {
                current + packageName
            } else {
                current - packageName
            }
            this[keyExcludedPackages] = updated
        }
    }

    suspend fun setAppWebhook(packageName: String, webhookUrl: String) {
        editSettings {
            val current = deserializeAppWebhooks(this[keyAppWebhooks] ?: "")
            val updated = if (webhookUrl.isBlank()) {
                current - packageName
            } else {
                current + (packageName to webhookUrl.trim())
            }
            this[keyAppWebhooks] = serializeAppWebhooks(updated)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        editSettings {
            this[keyThemeMode] = mode.name
        }
    }

    suspend fun setUiModeRulesSimple(enabled: Boolean) {
        editSettings {
            this[keyUiModeRulesSimple] = enabled
        }
    }

    suspend fun saveTemplate(packageName: String?, template: String) {
        editSettings {
            val normalized = normalizeTemplateBeforeSave(template)
            if (packageName.isNullOrBlank()) {
                this[keyDefaultTemplate] = normalized.ifBlank { SettingsState.DEFAULT_TEMPLATE }
                return@editSettings
            }

            val current = deserializeStringMap(this[keyAppTemplates] ?: "")
            val updated = if (normalized.isBlank()) {
                current - packageName
            } else {
                current + (packageName to normalized)
            }
            this[keyAppTemplates] = serializeStringMap(updated)
        }
    }

    suspend fun saveRuleConfig(
        embedConfig: EmbedConfig,
        filterConfig: FilterConfig,
        dedupeConfig: DedupeConfig,
        rateLimitConfig: RateLimitConfig,
        quietHoursConfig: QuietHoursConfig
    ) {
        editSettings {
            this[keyEmbedConfig] = serializeEmbedConfig(embedConfig)
            this[keyFilterConfig] = serializeFilterConfig(filterConfig)
            this[keyDedupeConfig] = serializeDedupeConfig(dedupeConfig)
            this[keyRateLimitConfig] = serializeRateLimitConfig(rateLimitConfig)
            this[keyQuietHoursConfig] = serializeQuietHoursConfig(quietHoursConfig)
        }
    }

    suspend fun saveRoutingRules(rules: List<RoutingRule>) {
        editSettings {
            this[keyRoutingRules] = serializeRoutingRules(rules)
        }
    }

    suspend fun saveQuietQueue(items: List<PendingQuietItem>) {
        editSettings {
            this[keyPendingQuietQueue] = serializePendingQuietQueue(items)
        }
    }

    suspend fun saveWebhookHealthStatus(status: WebhookHealthStatus) {
        editSettings(refreshSnapshot = false) {
            val current = deserializeWebhookHealthCache(this[keyWebhookHealthCache] ?: "")
            val previous = current[status.url]
            val merged = status.copy(
                lastDeliverySuccessAt = status.lastDeliverySuccessAt ?: previous?.lastDeliverySuccessAt,
                lastDeliveryStatusCode = status.lastDeliveryStatusCode ?: previous?.lastDeliveryStatusCode,
                deliveryMessage = status.deliveryMessage.ifBlank { previous?.deliveryMessage.orEmpty() }
            )
            val effective = merged.copy(
                effectiveState = computeEffectiveState(merged, System.currentTimeMillis())
            )
            this[keyWebhookHealthCache] = serializeWebhookHealthCache(current + (effective.url to effective))
        }
    }

    suspend fun recordWebhookDeliveryResult(
        url: String,
        success: Boolean,
        statusCode: Int?,
        message: String
    ) {
        if (url.isBlank()) return

        editSettings(refreshSnapshot = false) {
            val current = deserializeWebhookHealthCache(this[keyWebhookHealthCache] ?: "")
            val previous = current[url]
            val updated = (previous ?: WebhookHealthStatus(
                url = url,
                level = WebhookHealthLevel.WARNING,
                message = "送信履歴のみ記録されています"
            )).copy(
                lastDeliverySuccessAt = if (success) System.currentTimeMillis() else previous?.lastDeliverySuccessAt,
                lastDeliveryStatusCode = statusCode,
                deliveryMessage = message,
                effectiveState = computeEffectiveState(
                    (previous ?: WebhookHealthStatus(
                        url = url,
                        level = WebhookHealthLevel.WARNING,
                        message = "送信履歴のみ記録されています"
                    )).copy(
                        lastDeliverySuccessAt = if (success) System.currentTimeMillis() else previous?.lastDeliverySuccessAt,
                        lastDeliveryStatusCode = statusCode,
                        deliveryMessage = message
                    ),
                    System.currentTimeMillis()
                )
            )
            this[keyWebhookHealthCache] = serializeWebhookHealthCache(current + (url to updated))
        }
    }

    suspend fun getEffectiveWebhookHealth(url: String): WebhookHealthStatus? {
        if (url.isBlank()) return null
        val snapshot = getSettingsSnapshot()
        val status = snapshot.webhookHealthCache[url] ?: return null
        return status.copy(
            effectiveState = computeEffectiveState(status, System.currentTimeMillis())
        )
    }

    suspend fun checkWebhookHealth(url: String): WebhookHealthStatus {
        if (url.isBlank()) {
            return WebhookHealthStatus(
                url = url,
                level = WebhookHealthLevel.ERROR,
                effectiveState = WebhookHealthLevel.ERROR,
                message = "Webhook URL が未設定です"
            )
        }

        val previous = getSettingsSnapshot().webhookHealthCache[url]
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val checkedStatus = try {
            webhookCheckClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                val bodyJson = runCatching { JSONObject(bodyString) }.getOrNull()
                val channelName = bodyJson?.optString("name").orEmpty()
                val guildName = bodyJson?.optString("guild_id").orEmpty()

                when {
                    response.isSuccessful -> WebhookHealthStatus(
                        url = url,
                        level = WebhookHealthLevel.OK,
                        statusCode = response.code,
                        message = "Webhook は有効です",
                        channelName = channelName,
                        guildName = guildName
                    )

                    response.code == 429 -> WebhookHealthStatus(
                        url = url,
                        level = WebhookHealthLevel.WARNING,
                        statusCode = response.code,
                        message = "レート制限中です。しばらく待って再試行してください"
                    )

                    response.code == 401 -> WebhookHealthStatus(
                        url = url,
                        level = WebhookHealthLevel.ERROR,
                        statusCode = response.code,
                        message = "401 Unauthorized: Webhookが無効です"
                    )

                    response.code == 404 -> WebhookHealthStatus(
                        url = url,
                        level = WebhookHealthLevel.ERROR,
                        statusCode = response.code,
                        message = "404 Not Found: Webhookが見つかりません"
                    )

                    else -> WebhookHealthStatus(
                        url = url,
                        level = WebhookHealthLevel.WARNING,
                        statusCode = response.code,
                        message = "検証APIがエラーを返しました (${response.code})"
                    )
                }
            }
        } catch (e: Exception) {
            WebhookHealthStatus(
                url = url,
                level = WebhookHealthLevel.WARNING,
                message = mapWebhookCheckException(e)
            )
        }

        val merged = checkedStatus.copy(
            lastDeliverySuccessAt = previous?.lastDeliverySuccessAt,
            lastDeliveryStatusCode = previous?.lastDeliveryStatusCode,
            deliveryMessage = previous?.deliveryMessage.orEmpty(),
            effectiveState = computeEffectiveState(
                checkedStatus.copy(
                    lastDeliverySuccessAt = previous?.lastDeliverySuccessAt,
                    lastDeliveryStatusCode = previous?.lastDeliveryStatusCode,
                    deliveryMessage = previous?.deliveryMessage.orEmpty()
                ),
                System.currentTimeMillis()
            )
        )
        return merged
    }

    suspend fun hasAnyMeaningfulSettings(): Boolean {
        val settings = getSettingsSnapshot()
        return settings.webhookUrl.isNotBlank() ||
            settings.appWebhooks.isNotEmpty() ||
            settings.selectedPackages.isNotEmpty() ||
            settings.appTemplates.isNotEmpty() ||
            settings.routingRules.isNotEmpty() ||
            settings.notificationExists()
    }

    suspend fun hasInternalSnapshotCandidate(): Boolean {
        return dataStore.data.first()[keyInternalSettingsSnapshot].isNullOrBlank().not()
    }

    suspend fun restoreFromInternalSnapshot(): SettingsBackupResult {
        val snapshot = dataStore.data.first()[keyInternalSettingsSnapshot].orEmpty()
        if (snapshot.isBlank()) {
            return SettingsBackupResult(false, "内部スナップショットが見つかりません")
        }

        return applySnapshotJson(snapshot, replaceAll = true)
    }

    suspend fun exportSettingsToDefaultFolder(): SettingsBackupResult {
        val settingsJson = buildSettingsSnapshotJson(getSettingsSnapshot())
        val envelope = backupManager.buildBackupEnvelope(settingsJson)
        val backupFile = backupManager.writeBackupFile(envelope).getOrElse { error ->
            return SettingsBackupResult(false, error.localizedMessage ?: "バックアップ保存に失敗しました")
        }
        pruneOldBackups(maxCount = 30)

        editSettings(refreshSnapshot = false) {
            val now = System.currentTimeMillis()
            this[keyLastBackupAt] = now
            this[keyLastManualBackupAt] = now
            this[keyBackupSchemaVersion] = SettingsState.BACKUP_SCHEMA_VERSION
        }

        return SettingsBackupResult(true, "バックアップを保存しました: ${backupFile.name}")
    }

    suspend fun exportSettingsToUri(uri: Uri): SettingsBackupResult {
        val settingsJson = buildSettingsSnapshotJson(getSettingsSnapshot())
        val envelope = backupManager.buildBackupEnvelope(settingsJson)
        return backupManager.writeBackupToUri(uri, envelope).fold(
            onSuccess = {
                editSettings(refreshSnapshot = false) {
                    val now = System.currentTimeMillis()
                    this[keyLastBackupAt] = now
                    this[keyLastManualBackupAt] = now
                    this[keyBackupSchemaVersion] = SettingsState.BACKUP_SCHEMA_VERSION
                }
                SettingsBackupResult(true, "バックアップファイルを保存しました")
            },
            onFailure = { error ->
                SettingsBackupResult(false, error.localizedMessage ?: "バックアップファイルの保存に失敗しました")
            }
        )
    }

    suspend fun importLatestSettingsFromDefaultFolder(replaceAll: Boolean = true): SettingsBackupResult {
        val payload = backupManager.readLatestSnapshotJson().getOrElse { error ->
            return SettingsBackupResult(false, error.localizedMessage ?: "バックアップファイルの読み込みに失敗しました")
        }
        val settingsJson = validateAndExtractSettingsPayload(payload).getOrElse { error ->
            return SettingsBackupResult(false, error.localizedMessage ?: "バックアップ形式の検証に失敗しました")
        }
        return applySnapshotJson(settingsJson, replaceAll)
    }

    suspend fun importSettingsFromUri(uri: Uri, replaceAll: Boolean = true): SettingsBackupResult {
        val payload = backupManager.readBackupFromUri(uri).getOrElse { error ->
            return SettingsBackupResult(false, error.localizedMessage ?: "バックアップファイルの読み込みに失敗しました")
        }
        val settingsJson = validateAndExtractSettingsPayload(payload).getOrElse { error ->
            return SettingsBackupResult(false, error.localizedMessage ?: "バックアップ形式の検証に失敗しました")
        }
        return applySnapshotJson(settingsJson, replaceAll)
    }

    suspend fun validateBackupPayload(payload: String): SettingsBackupResult {
        return validateAndExtractSettingsPayload(payload).fold(
            onSuccess = {
                SettingsBackupResult(true, "バックアップ検証に成功しました")
            },
            onFailure = { error ->
                SettingsBackupResult(false, error.localizedMessage ?: "バックアップ検証に失敗しました")
            }
        )
    }

    suspend fun pruneOldBackups(maxCount: Int): Int {
        return backupManager.pruneOldBackups(maxCount).getOrDefault(0)
    }

    // --- 通知履歴 ---

    val notificationHistoryFlow: Flow<List<NotificationRecord>> = dataStore.data.map { prefs ->
        deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
    }

    val historyReadMarkersFlow: Flow<Map<String, Long>> = dataStore.data.map { prefs ->
        deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
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
            val markers = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(
                sanitizeHistoryReadMarkers(markers, updated)
            )
        }
    }

    suspend fun deleteNotificationRecord(id: Long) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            val updated = current.filter { it.id != id }
            prefs[keyNotificationHistory] = serializeNotificationHistory(updated)
            val markers = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(
                sanitizeHistoryReadMarkers(markers, updated)
            )
        }
    }

    suspend fun clearNotificationHistory() {
        dataStore.edit { prefs ->
            prefs[keyNotificationHistory] = ""
            prefs[keyHistoryReadMarkers] = ""
        }
    }

    suspend fun setRetentionDays(days: Int) {
        editSettings {
            this[keyRetentionDays] = days
        }
    }

    suspend fun cleanupExpiredRecords() {
        dataStore.edit { prefs ->
            val retentionDays = prefs[keyRetentionDays] ?: 30
            if (retentionDays == -1) return@edit
            val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 24 * 60 * 60 * 1000
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            val updated = current.filter { it.postTime >= cutoff }
            prefs[keyNotificationHistory] = serializeNotificationHistory(updated)
            val markers = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(
                sanitizeHistoryReadMarkers(markers, updated)
            )
        }
    }

    suspend fun deleteNotificationRecords(ids: Set<Long>) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            val updated = current.filter { it.id !in ids }
            prefs[keyNotificationHistory] = serializeNotificationHistory(updated)
            val markers = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(
                sanitizeHistoryReadMarkers(markers, updated)
            )
        }
    }

    suspend fun clearNotificationHistoryByApp(packageName: String) {
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            val updated = current.filter { it.packageName != packageName }
            prefs[keyNotificationHistory] = serializeNotificationHistory(updated)
            val markers = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(
                sanitizeHistoryReadMarkers(markers - packageName, updated)
            )
        }
    }

    suspend fun clearNotificationHistoryByApps(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        dataStore.edit { prefs ->
            val current = deserializeNotificationHistory(prefs[keyNotificationHistory] ?: "")
            val updated = current.filter { it.packageName !in packageNames }
            prefs[keyNotificationHistory] = serializeNotificationHistory(updated)
            val markers = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(
                sanitizeHistoryReadMarkers(markers - packageNames, updated)
            )
        }
    }

    suspend fun markAppHistoryRead(packageName: String, readUntilPostTime: Long) {
        if (packageName.isBlank() || readUntilPostTime <= 0L) return
        dataStore.edit { prefs ->
            val current = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            val previous = current[packageName] ?: 0L
            val updated = current + (packageName to maxOf(previous, readUntilPostTime))
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(updated)
        }
    }

    suspend fun clearHistoryReadMarker(packageName: String) {
        if (packageName.isBlank()) return
        dataStore.edit { prefs ->
            val current = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(current - packageName)
        }
    }

    suspend fun clearHistoryReadMarkers(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        dataStore.edit { prefs ->
            val current = deserializeHistoryReadMarkers(prefs[keyHistoryReadMarkers] ?: "")
            prefs[keyHistoryReadMarkers] = serializeHistoryReadMarkers(current - packageNames)
        }
    }

    private suspend fun editSettings(
        refreshSnapshot: Boolean = true,
        mutator: MutablePreferences.() -> Unit
    ) {
        dataStore.edit { prefs ->
            mutator(prefs)
        }
        if (refreshSnapshot) {
            refreshInternalSnapshot()
        }
    }

    private suspend fun refreshInternalSnapshot() {
        val snapshotJson = buildSettingsSnapshotJson(getSettingsSnapshot())
        dataStore.edit { prefs ->
            prefs[keyInternalSettingsSnapshot] = snapshotJson
        }
    }

    private suspend fun applySnapshotJson(
        snapshotJson: String,
        replaceAll: Boolean
    ): SettingsBackupResult {
        return runCatching {
            val root = JSONObject(snapshotJson)
            val settings = if (root.has("settings")) {
                root.optJSONObject("settings") ?: throw IllegalArgumentException("settings の形式が不正です")
            } else {
                root
            }

            dataStore.edit { prefs ->
                if (replaceAll) {
                    prefs.clear()
                }

                prefs[keyWebhookUrl] = settings.optString("webhookUrl")
                prefs[keyForwardingEnabled] = settings.optBoolean("forwardingEnabled", true)
                prefs[keyExcludedPackages] = settings.optJSONArray("selectedPackages")
                    ?.let { jsonArrayToStringList(it).toSet() }
                    ?: emptySet()
                prefs[keyAppWebhooks] = serializeAppWebhooks(
                    jsonObjectToStringMap(settings.optJSONObject("appWebhooks"))
                )
                prefs[keyThemeMode] = settings.optString("themeMode", ThemeMode.SYSTEM.name)
                prefs[keyRetentionDays] = settings.optInt("retentionDays", 30)

                prefs[keyDefaultTemplate] = normalizeTemplateBeforeSave(
                    settings.optString("defaultTemplate", SettingsState.DEFAULT_TEMPLATE)
                )
                prefs[keyAppTemplates] = serializeStringMap(
                    jsonObjectToStringMap(settings.optJSONObject("appTemplates"))
                )

                prefs[keyEmbedConfig] = settings.optJSONObject("embedConfig")
                    ?.toString()
                    ?: serializeEmbedConfig(EmbedConfig())
                prefs[keyFilterConfig] = settings.optJSONObject("filterConfig")
                    ?.toString()
                    ?: serializeFilterConfig(FilterConfig())
                prefs[keyDedupeConfig] = settings.optJSONObject("dedupeConfig")
                    ?.toString()
                    ?: serializeDedupeConfig(DedupeConfig())
                prefs[keyRateLimitConfig] = settings.optJSONObject("rateLimitConfig")
                    ?.toString()
                    ?: serializeRateLimitConfig(RateLimitConfig())
                prefs[keyQuietHoursConfig] = settings.optJSONObject("quietHoursConfig")
                    ?.toString()
                    ?: serializeQuietHoursConfig(QuietHoursConfig())

                prefs[keyRoutingRules] = settings.optJSONArray("routingRules")
                    ?.toString()
                    ?: "[]"

                prefs[keyWebhookHealthCache] = settings.optJSONArray("webhookHealthCache")
                    ?.toString()
                    ?: "[]"
                prefs[keyPendingQuietQueue] = settings.optJSONArray("pendingQuietQueue")
                    ?.toString()
                    ?: "[]"

                prefs[keyUiModeRulesSimple] = settings.optBoolean("uiModeRulesSimple", true)
                if (settings.has("lastBackupAt") && !settings.isNull("lastBackupAt")) {
                    prefs[keyLastBackupAt] = settings.optLong("lastBackupAt")
                }
                if (settings.has("lastManualBackupAt") && !settings.isNull("lastManualBackupAt")) {
                    prefs[keyLastManualBackupAt] = settings.optLong("lastManualBackupAt")
                }
                prefs[keyBackupSchemaVersion] = settings.optInt(
                    "backupSchemaVersion",
                    SettingsState.BACKUP_SCHEMA_VERSION
                )

                // 復元後も内部スナップショットを更新して再復旧可能にする
                prefs[keyInternalSettingsSnapshot] = snapshotJson
            }

            SettingsBackupResult(true, "バックアップを復元しました")
        }.getOrElse { error ->
            SettingsBackupResult(false, error.localizedMessage ?: "バックアップ復元に失敗しました")
        }
    }

    private fun buildSettingsSnapshotJson(settings: SettingsState): String {
        return JSONObject()
            .put("webhookUrl", settings.webhookUrl)
            .put("forwardingEnabled", settings.forwardingEnabled)
            .put("selectedPackages", JSONArray(settings.selectedPackages.toList()))
            .put("appWebhooks", JSONObject(settings.appWebhooks))
            .put("themeMode", settings.themeMode.name)
            .put("retentionDays", settings.retentionDays)
            .put("defaultTemplate", settings.defaultTemplate)
            .put("appTemplates", JSONObject(settings.appTemplates))
            .put("embedConfig", JSONObject(serializeEmbedConfig(settings.embedConfig)))
            .put("filterConfig", JSONObject(serializeFilterConfig(settings.filterConfig)))
            .put("dedupeConfig", JSONObject(serializeDedupeConfig(settings.dedupeConfig)))
            .put("rateLimitConfig", JSONObject(serializeRateLimitConfig(settings.rateLimitConfig)))
            .put("quietHoursConfig", JSONObject(serializeQuietHoursConfig(settings.quietHoursConfig)))
            .put("routingRules", JSONArray(serializeRoutingRules(settings.routingRules)))
            .put("webhookHealthCache", JSONArray(serializeWebhookHealthCache(settings.webhookHealthCache)))
            .put("pendingQuietQueue", JSONArray(serializePendingQuietQueue(settings.pendingQuietQueue)))
            .put("uiModeRulesSimple", settings.uiModeRulesSimple)
            .put("lastBackupAt", settings.lastBackupAt)
            .put("lastManualBackupAt", settings.lastManualBackupAt)
            .put("backupSchemaVersion", settings.backupSchemaVersion)
            .toString()
    }

    private fun validateAndExtractSettingsPayload(payload: String): Result<String> {
        return runCatching {
            val schemaVersion = runCatching { JSONObject(payload).optInt("schemaVersion", -1) }
                .getOrDefault(-1)
            if (schemaVersion <= 0) {
                throw IllegalArgumentException("バックアップ形式が不正です（schemaVersion がありません）")
            }
            if (schemaVersion > SettingsState.BACKUP_SCHEMA_VERSION) {
                throw IllegalArgumentException("バックアップのバージョンが新しすぎるため復元できません")
            }
            backupManager.verifyBackupEnvelope(payload).getOrElse { error ->
                throw IllegalArgumentException(error.localizedMessage ?: "バックアップ整合性チェックに失敗しました")
            }
        }
    }

    private fun computeEffectiveState(status: WebhookHealthStatus, nowMillis: Long): WebhookHealthLevel {
        // 検証APIの失敗だけで即エラーにせず、送信実績を加味して最終状態を決める
        if (status.level == WebhookHealthLevel.OK) {
            return WebhookHealthLevel.OK
        }

        val successAt = status.lastDeliverySuccessAt
        if (successAt != null) {
            val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
            if (nowMillis - successAt <= sevenDaysMs) {
                return if (status.level == WebhookHealthLevel.ERROR) {
                    WebhookHealthLevel.WARNING
                } else {
                    WebhookHealthLevel.OK
                }
            }
        }

        return status.level
    }

    private fun mapWebhookCheckException(error: Exception): String {
        return when (error) {
            is UnknownHostException -> "DNSエラー: ホストが見つかりません"
            is SSLException -> "TLS/証明書エラー: 安全な接続に失敗しました"
            is SocketTimeoutException -> "タイムアウト: 応答がありません"
            is ConnectException -> "接続エラー: サーバーに接続できません"
            else -> "通信エラー: ${error.message ?: "不明なエラー"}"
        }
    }

    private fun normalizeTemplateBeforeSave(template: String): String {
        // 旧入力の \"\\n\" 記法は実改行へ移し替えて初心者でも扱えるようにする
        return template
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
    }

    private fun normalizeLoadedTemplate(template: String): String {
        return template.replace("\\n", "\n")
    }

    private fun migrateLegacyDefaultTemplate(storedValue: String?, normalizedValue: String): String {
        // 旧既定テンプレのみ新既定へ移行し、ユーザー独自テンプレはそのまま保持する
        return if (storedValue != null && (storedValue in legacyDefaultTemplates || normalizedValue in legacyDefaultTemplates)) {
            SettingsState.DEFAULT_TEMPLATE
        } else {
            normalizedValue
        }
    }

    private fun parseThemeMode(raw: String?): ThemeMode {
        return runCatching {
            ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
    }

    private fun SettingsState.notificationExists(): Boolean {
        return pendingQuietQueue.isNotEmpty() || webhookHealthCache.isNotEmpty()
    }

    private fun serializeAppWebhooks(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    private fun deserializeAppWebhooks(str: String): Map<String, String> {
        if (str.isBlank()) return emptyMap()

        // 旧バージョンの "pkg::url;pkg::url" 形式にも互換対応する
        if (!str.trimStart().startsWith("{")) {
            return str.split(";")
                .mapNotNull { entry ->
                    val parts = entry.split("::", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                .toMap()
        }

        return runCatching {
            val obj = JSONObject(str)
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrElse { emptyMap() }
    }

    private fun serializeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    private fun deserializeStringMap(str: String): Map<String, String> {
        if (str.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(str)
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrElse { emptyMap() }
    }

    private fun serializeEmbedConfig(config: EmbedConfig): String {
        return JSONObject()
            .put("enabled", config.enabled)
            .put("includePackageField", config.includePackageField)
            .put("includeTimeField", config.includeTimeField)
            .put("maxFieldLength", config.maxFieldLength)
            .toString()
    }

    private fun deserializeEmbedConfig(str: String): EmbedConfig {
        if (str.isBlank()) return EmbedConfig()
        return runCatching {
            val obj = JSONObject(str)
            EmbedConfig(
                enabled = obj.optBoolean("enabled", true),
                includePackageField = obj.optBoolean("includePackageField", true),
                includeTimeField = obj.optBoolean("includeTimeField", true),
                maxFieldLength = obj.optInt("maxFieldLength", 900)
            )
        }.getOrDefault(EmbedConfig())
    }

    private fun serializeFilterConfig(config: FilterConfig): String {
        return JSONObject()
            .put("enabled", config.enabled)
            .put("keywords", JSONArray(config.keywords))
            .put("useRegex", config.useRegex)
            .put("regexPattern", config.regexPattern)
            .put("channelIds", JSONArray(config.channelIds.toList()))
            .put("minImportance", config.minImportance)
            .put("excludeSummary", config.excludeSummary)
            .toString()
    }

    private fun deserializeFilterConfig(str: String): FilterConfig {
        if (str.isBlank()) return FilterConfig()
        return runCatching {
            val obj = JSONObject(str)
            FilterConfig(
                enabled = obj.optBoolean("enabled", false),
                keywords = jsonArrayToStringList(obj.optJSONArray("keywords")),
                useRegex = obj.optBoolean("useRegex", false),
                regexPattern = obj.optString("regexPattern"),
                channelIds = jsonArrayToStringList(obj.optJSONArray("channelIds")).toSet(),
                minImportance = obj.optInt("minImportance", Int.MIN_VALUE),
                excludeSummary = obj.optBoolean("excludeSummary", true)
            )
        }.getOrDefault(FilterConfig())
    }

    private fun serializeDedupeConfig(config: DedupeConfig): String {
        return JSONObject()
            .put("enabled", config.enabled)
            .put("contentHashEnabled", config.contentHashEnabled)
            .put("titleLatestOnly", config.titleLatestOnly)
            .put("windowSeconds", config.windowSeconds)
            .toString()
    }

    private fun deserializeDedupeConfig(str: String): DedupeConfig {
        if (str.isBlank()) return DedupeConfig()
        return runCatching {
            val obj = JSONObject(str)
            DedupeConfig(
                enabled = obj.optBoolean("enabled", true),
                contentHashEnabled = obj.optBoolean("contentHashEnabled", true),
                titleLatestOnly = obj.optBoolean("titleLatestOnly", true),
                windowSeconds = obj.optInt("windowSeconds", 45)
            )
        }.getOrDefault(DedupeConfig())
    }

    private fun serializeRateLimitConfig(config: RateLimitConfig): String {
        return JSONObject()
            .put("enabled", config.enabled)
            .put("maxPerWindow", config.maxPerWindow)
            .put("windowSeconds", config.windowSeconds)
            .put("aggregateWindowSeconds", config.aggregateWindowSeconds)
            .toString()
    }

    private fun deserializeRateLimitConfig(str: String): RateLimitConfig {
        if (str.isBlank()) return RateLimitConfig()
        return runCatching {
            val obj = JSONObject(str)
            RateLimitConfig(
                enabled = obj.optBoolean("enabled", true),
                maxPerWindow = obj.optInt("maxPerWindow", 5),
                windowSeconds = obj.optInt("windowSeconds", 30),
                aggregateWindowSeconds = obj.optInt("aggregateWindowSeconds", 10)
            )
        }.getOrDefault(RateLimitConfig())
    }

    private fun serializeQuietHoursConfig(config: QuietHoursConfig): String {
        return JSONObject()
            .put("enabled", config.enabled)
            .put("startHour", config.startHour)
            .put("startMinute", config.startMinute)
            .put("endHour", config.endHour)
            .put("endMinute", config.endMinute)
            .put("daysOfWeek", JSONArray(config.daysOfWeek.toList()))
            .toString()
    }

    private fun deserializeQuietHoursConfig(str: String): QuietHoursConfig {
        if (str.isBlank()) return QuietHoursConfig()
        return runCatching {
            val obj = JSONObject(str)
            QuietHoursConfig(
                enabled = obj.optBoolean("enabled", false),
                startHour = obj.optInt("startHour", 22),
                startMinute = obj.optInt("startMinute", 0),
                endHour = obj.optInt("endHour", 7),
                endMinute = obj.optInt("endMinute", 0),
                daysOfWeek = jsonArrayToIntList(obj.optJSONArray("daysOfWeek")).toSet()
            )
        }.getOrDefault(QuietHoursConfig())
    }

    private fun serializeRoutingRules(rules: List<RoutingRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("enabled", rule.enabled)
                    .put("packageNames", JSONArray(rule.packageNames.toList()))
                    .put("keywords", JSONArray(rule.keywords))
                    .put("useRegex", rule.useRegex)
                    .put("regexPattern", rule.regexPattern)
                    .put("webhookUrls", JSONArray(rule.webhookUrls))
            )
        }
        return array.toString()
    }

    private fun deserializeRoutingRules(str: String): List<RoutingRule> {
        if (str.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(str)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                RoutingRule(
                    id = obj.optString("id", "rule_$index"),
                    name = obj.optString("name", "ルール${index + 1}"),
                    enabled = obj.optBoolean("enabled", true),
                    packageNames = jsonArrayToStringList(obj.optJSONArray("packageNames")).toSet(),
                    keywords = jsonArrayToStringList(obj.optJSONArray("keywords")),
                    useRegex = obj.optBoolean("useRegex", false),
                    regexPattern = obj.optString("regexPattern"),
                    webhookUrls = jsonArrayToStringList(obj.optJSONArray("webhookUrls"))
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun serializeWebhookHealthCache(cache: Map<String, WebhookHealthStatus>): String {
        val array = JSONArray()
        cache.values.forEach { status ->
            array.put(
                JSONObject()
                    .put("url", status.url)
                    .put("level", status.level.name)
                    .put("effectiveState", status.effectiveState.name)
                    .put("statusCode", status.statusCode)
                    .put("message", status.message)
                    .put("deliveryMessage", status.deliveryMessage)
                    .put("channelName", status.channelName)
                    .put("guildName", status.guildName)
                    .put("lastDeliverySuccessAt", status.lastDeliverySuccessAt)
                    .put("lastDeliveryStatusCode", status.lastDeliveryStatusCode)
                    .put("checkedAt", status.checkedAt)
            )
        }
        return array.toString()
    }

    private fun deserializeWebhookHealthCache(str: String): Map<String, WebhookHealthStatus> {
        if (str.isBlank()) return emptyMap()
        return runCatching {
            val array = JSONArray(str)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                WebhookHealthStatus(
                    url = obj.optString("url"),
                    level = WebhookHealthLevel.valueOf(obj.optString("level", WebhookHealthLevel.WARNING.name)),
                    effectiveState = WebhookHealthLevel.valueOf(obj.optString("effectiveState", obj.optString("level", WebhookHealthLevel.WARNING.name))),
                    statusCode = if (obj.isNull("statusCode")) null else obj.optInt("statusCode"),
                    message = obj.optString("message"),
                    deliveryMessage = obj.optString("deliveryMessage"),
                    channelName = obj.optString("channelName"),
                    guildName = obj.optString("guildName"),
                    lastDeliverySuccessAt = if (obj.isNull("lastDeliverySuccessAt")) null else obj.optLong("lastDeliverySuccessAt"),
                    lastDeliveryStatusCode = if (obj.isNull("lastDeliveryStatusCode")) null else obj.optInt("lastDeliveryStatusCode"),
                    checkedAt = obj.optLong("checkedAt", System.currentTimeMillis())
                )
            }.associateBy { it.url }
        }.getOrElse { emptyMap() }
    }

    private fun serializePendingQuietQueue(items: List<PendingQuietItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("packageName", item.packageName)
                    .put("appName", item.appName)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("postTime", item.postTime)
                    .put("webhookUrls", JSONArray(item.webhookUrls))
            )
        }
        return array.toString()
    }

    private fun deserializePendingQuietQueue(str: String): List<PendingQuietItem> {
        if (str.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(str)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                PendingQuietItem(
                    packageName = obj.optString("packageName"),
                    appName = obj.optString("appName"),
                    title = obj.optString("title"),
                    text = obj.optString("text"),
                    postTime = obj.optLong("postTime"),
                    webhookUrls = jsonArrayToStringList(obj.optJSONArray("webhookUrls"))
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun serializeNotificationHistory(records: List<NotificationRecord>): String {
        val array = JSONArray()
        for (record in records) {
            val obj = JSONObject()
            obj.put("id", record.id)
            obj.put("packageName", record.packageName)
            obj.put("appName", record.appName)
            obj.put("title", record.title)
            obj.put("text", record.text)
            obj.put("postTime", record.postTime)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeNotificationHistory(str: String): List<NotificationRecord> {
        if (str.isBlank()) return emptyList()
        return try {
            val array = JSONArray(str)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                NotificationRecord(
                    id = obj.getLong("id"),
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("appName"),
                    title = obj.getString("title"),
                    text = obj.getString("text"),
                    postTime = obj.getLong("postTime")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeHistoryReadMarkers(markers: Map<String, Long>): String {
        val obj = JSONObject()
        markers.forEach { (packageName, readUntilPostTime) ->
            obj.put(packageName, readUntilPostTime)
        }
        return obj.toString()
    }

    private fun deserializeHistoryReadMarkers(str: String): Map<String, Long> {
        if (str.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(str)
            obj.keys().asSequence().associateWith { key ->
                obj.optLong(key, 0L)
            }.filterValues { it > 0L }
        }.getOrElse { emptyMap() }
    }

    private fun sanitizeHistoryReadMarkers(
        markers: Map<String, Long>,
        records: List<NotificationRecord>
    ): Map<String, Long> {
        if (markers.isEmpty()) return emptyMap()
        val existingPackages = records.asSequence().map { it.packageName }.toSet()
        if (existingPackages.isEmpty()) return emptyMap()
        return markers.filterKeys { it in existingPackages }
    }

    private fun jsonArrayToStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun jsonArrayToIntList(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { index -> array.optInt(index) }
    }

    private fun jsonObjectToStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { key -> obj.optString(key) }
    }
}
