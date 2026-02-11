package com.notify2discord.app.data

data class BatteryReportConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60
)

data class SettingsState(
    val webhookUrl: String = "",
    val forwardingEnabled: Boolean = true,
    val selectedPackages: Set<String> = emptySet(),
    val appWebhooks: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val retentionDays: Int = 30,  // -1 = 無制限
    val defaultTemplate: String = DEFAULT_TEMPLATE,
    val appTemplates: Map<String, String> = emptyMap(),
    val embedConfig: EmbedConfig = EmbedConfig(),
    val filterConfig: FilterConfig = FilterConfig(),
    val dedupeConfig: DedupeConfig = DedupeConfig(),
    val rateLimitConfig: RateLimitConfig = RateLimitConfig(),
    val quietHoursConfig: QuietHoursConfig = QuietHoursConfig(),
    val routingRules: List<RoutingRule> = emptyList(),
    val webhookHealthCache: Map<String, WebhookHealthStatus> = emptyMap(),
    val pendingQuietQueue: List<PendingQuietItem> = emptyList(),
    val batteryReportConfig: BatteryReportConfig = BatteryReportConfig(),
    val uiModeRulesSimple: Boolean = true,
    val lastBackupAt: Long? = null,
    val lastManualBackupAt: Long? = null,
    val backupSchemaVersion: Int = BACKUP_SCHEMA_VERSION
) {
    companion object {
        const val DEFAULT_TEMPLATE = "【{app}】\nタイトル: {title}\n本文: {text}\n受信時刻: {time}"
        const val BACKUP_SCHEMA_VERSION = 2
    }
}

enum class ThemeMode {
    LIGHT,   // ライトモード
    DARK,    // ダークモード
    SYSTEM   // システム設定に従う
}
