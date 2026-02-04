package com.notify2discord.app.data

data class SettingsState(
    val webhookUrl: String = "",
    val forwardingEnabled: Boolean = true,
    val selectedPackages: Set<String> = emptySet(),
    val appWebhooks: Map<String, String> = emptyMap(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val retentionDays: Int = 30  // -1 = 無制限
)

enum class ThemeMode {
    LIGHT,   // ライトモード
    DARK,    // ダークモード
    SYSTEM   // システム設定に従う
}
