package com.notify2discord.app.data

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean = false
)
