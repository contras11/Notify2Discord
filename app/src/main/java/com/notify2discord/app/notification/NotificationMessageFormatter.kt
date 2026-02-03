package com.notify2discord.app.notification

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationMessageFormatter {
    // Discord に送る本文を日本語で整形
    fun format(appName: String, title: String?, text: String?, postTime: Long): String {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: "(タイトルなし)"
        val safeText = text?.takeIf { it.isNotBlank() } ?: "(本文なし)"
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
        val timeText = formatter.format(Date(postTime))

        return buildString {
            append("[アプリ] ").append(appName).append('\n')
            append("[タイトル] ").append(safeTitle).append('\n')
            append("[本文] ").append(safeText).append('\n')
            append("[時刻] ").append(timeText)
        }
    }
}
