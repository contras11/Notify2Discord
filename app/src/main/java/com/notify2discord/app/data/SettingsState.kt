package com.notify2discord.app.data

data class SettingsState(
    val webhookUrl: String = "",
    val forwardingEnabled: Boolean = true,
    val excludedPackages: Set<String> = emptySet()
)
