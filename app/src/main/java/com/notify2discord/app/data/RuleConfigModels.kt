package com.notify2discord.app.data

data class EmbedConfig(
    val enabled: Boolean = true,
    val includePackageField: Boolean = true,
    val includeTimeField: Boolean = true,
    val maxFieldLength: Int = 900
)

data class FilterConfig(
    val enabled: Boolean = false,
    val keywords: List<String> = emptyList(),
    val useRegex: Boolean = false,
    val regexPattern: String = "",
    val channelIds: Set<String> = emptySet(),
    val minImportance: Int = Int.MIN_VALUE,
    val excludeSummary: Boolean = true
)

data class DedupeConfig(
    val enabled: Boolean = true,
    val contentHashEnabled: Boolean = true,
    val titleLatestOnly: Boolean = true,
    val windowSeconds: Int = 45
)

enum class AggregationMode {
    NORMAL,
    WAKE_DELAY_ONLY,
    ALWAYS_SEPARATE
}

data class RateLimitConfig(
    val enabled: Boolean = true,
    val maxPerWindow: Int = 5,
    val windowSeconds: Int = 30,
    val aggregateWindowSeconds: Int = 10,
    val aggregationMode: AggregationMode = AggregationMode.NORMAL
)

data class QuietHoursConfig(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val daysOfWeek: Set<Int> = emptySet() // 空なら毎日
)

data class RoutingRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val packageNames: Set<String> = emptySet(),
    val keywords: List<String> = emptyList(),
    val useRegex: Boolean = false,
    val regexPattern: String = "",
    val webhookUrls: List<String> = emptyList()
)

enum class WebhookHealthLevel {
    OK,
    WARNING,
    ERROR
}

data class WebhookHealthStatus(
    val url: String,
    val level: WebhookHealthLevel,
    val effectiveState: WebhookHealthLevel = level,
    val statusCode: Int? = null,
    val message: String = "",
    val deliveryMessage: String = "",
    val channelName: String = "",
    val guildName: String = "",
    val lastDeliverySuccessAt: Long? = null,
    val lastDeliveryStatusCode: Int? = null,
    val checkedAt: Long = System.currentTimeMillis()
)

data class SettingsBackupResult(
    val success: Boolean,
    val message: String
)

data class PendingQuietItem(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val webhookUrls: List<String>
)
