package com.notify2discord.app.data

data class BatteryReportConfig(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,
    val startHour: Int = 9,
    val startMinute: Int = 0
)

data class BatteryHistoryConfig(
    val enabled: Boolean = true,
    val retentionDays: Int = 180,
    val defaultRangeDays: Int = 30
)

data class BatterySnapshot(
    val capturedAt: Long,
    val levelPercent: Int?,
    val status: Int?,
    val health: Int?,
    val isCharging: Boolean,
    val temperatureC: Float?,
    val voltageMv: Int?,
    val technology: String,
    val chargeCounterUah: Int?,
    val currentNowUa: Int?,
    val currentAverageUa: Int?,
    val energyCounterNwh: Long?,
    val cycleCount: Int?,
    val designCapacityMah: Float?,
    val estimatedFullChargeMah: Float?,
    val estimatedHealthPercent: Float?,
    val estimatedHealthByDesignPercent: Float?
)

data class LineThreadProfile(
    val threadKey: String,
    val displayName: String,
    val iconBase64Png: String?,
    val updatedAt: Long
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
    val batteryHistoryConfig: BatteryHistoryConfig = BatteryHistoryConfig(),
    val batteryHistory: List<BatterySnapshot> = emptyList(),
    val captureHistoryWhenForwardingOff: Boolean = false,
    val historyCapturePackages: Set<String> = emptySet(),
    val batteryNominalCapacityMah: Float? = null,
    val lineThreadProfiles: Map<String, LineThreadProfile> = emptyMap(),
    val uiModeRulesSimple: Boolean = true,
    val lastBackupAt: Long? = null,
    val lastManualBackupAt: Long? = null,
    val backupSchemaVersion: Int = BACKUP_SCHEMA_VERSION
) {
    companion object {
        const val DEFAULT_TEMPLATE = "【{app}】\nタイトル: {title}\n本文: {text}\n受信時刻: {time}"
        const val BACKUP_SCHEMA_VERSION = 4
    }
}

enum class ThemeMode {
    LIGHT,   // ライトモード
    DARK,    // ダークモード
    SYSTEM   // システム設定に従う
}
