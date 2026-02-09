package com.notify2discord.app.notification

import com.notify2discord.app.notification.model.NotificationPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationTemplateEngine {
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
    private const val CONTENT_LIMIT = 1900

    fun render(template: String, payload: NotificationPayload): String {
        val safeTitle = payload.title.ifBlank { "(タイトルなし)" }
        val safeText = payload.text.ifBlank { "(本文なし)" }
        val timestamp = dateFormatter.format(Date(payload.postTime))

        return template
            .replace("{app}", payload.appName)
            .replace("{title}", safeTitle)
            .replace("{text}", safeText)
            .replace("{time}", timestamp)
            .replace("{package}", payload.packageName)
    }

    fun renderShortSummary(payload: NotificationPayload, aggregateCount: Int = 1): String {
        val title = payload.title.ifBlank { "(タイトルなし)" }
        val raw = if (aggregateCount > 1) {
            "📬 ${payload.appName}: ${aggregateCount}件の通知（最新: $title）"
        } else {
            "📩 ${payload.appName}: $title"
        }
        return raw.take(CONTENT_LIMIT)
    }
}
