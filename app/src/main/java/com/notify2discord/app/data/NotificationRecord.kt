package com.notify2discord.app.data

data class NotificationRecord(
    val id: Long,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long
)
