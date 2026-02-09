package com.notify2discord.app.notification

import android.os.Build
import com.notify2discord.app.data.EmbedConfig
import com.notify2discord.app.notification.model.DiscordEmbedField
import com.notify2discord.app.notification.model.DiscordEmbedPayload
import com.notify2discord.app.notification.model.NotificationPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiscordEmbedBuilder {
    private val dateFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
    private const val DESCRIPTION_LIMIT = 4096
    private const val EMBED_TOTAL_LIMIT = 6000

    fun build(
        payload: NotificationPayload,
        config: EmbedConfig,
        aggregateCount: Int = 0
    ): DiscordEmbedPayload {
        val maxLen = config.maxFieldLength.coerceIn(200, 1000)
        val safeTitle = payload.title.ifBlank { "(タイトルなし)" }
        val safeText = payload.text.ifBlank { "(本文なし)" }
        val prefix = if (aggregateCount > 1) "直近 ${aggregateCount} 件を集約\n" else ""
        val availableForDescription = (DESCRIPTION_LIMIT - prefix.length).coerceAtLeast(0)
        val consumedLength = safeText.take(availableForDescription).length
        val description = buildDescription(safeText, aggregateCount)
        val remainder = safeText.drop(consumedLength).trimStart()
        val title = safeTitle.take(256)
        val footerText = "${Build.MODEL} • ${dateFormatter.format(Date(payload.postTime))}"

        val fields = mutableListOf<DiscordEmbedField>()
        fields += DiscordEmbedField(name = "アプリ", value = payload.appName, inline = true)
        if (config.includeTimeField) {
            fields += DiscordEmbedField(
                name = "受信時刻",
                value = dateFormatter.format(Date(payload.postTime)),
                inline = true
            )
        }
        if (config.includePackageField) {
            fields += DiscordEmbedField(name = "パッケージ", value = payload.packageName, inline = false)
        }

        var consumedChars = title.length + description.length + footerText.length
        consumedChars += fields.sumOf { it.name.length + it.value.length }
        splitToFieldChunks(remainder, maxLen).forEachIndexed { index, part ->
            val fieldName = "本文（続き ${index + 1}）"
            val remaining = EMBED_TOTAL_LIMIT - consumedChars - fieldName.length
            if (remaining <= 0) return@forEachIndexed
            val value = part.take(remaining)
            if (value.isBlank()) return@forEachIndexed

            fields += DiscordEmbedField(
                name = fieldName,
                value = value,
                inline = false
            )
            consumedChars += fieldName.length + value.length
        }

        return DiscordEmbedPayload(
            title = title,
            description = description,
            color = stableColor(payload.packageName),
            fields = fields,
            footerText = footerText
        )
    }

    private fun buildDescription(text: String, aggregateCount: Int): String {
        val prefix = if (aggregateCount > 1) "直近 ${aggregateCount} 件を集約\n" else ""
        val base = text.ifBlank { "(本文なし)" }
        val available = (DESCRIPTION_LIMIT - prefix.length).coerceAtLeast(0)
        val descriptionBody = base.take(available)
        return "$prefix$descriptionBody"
    }

    private fun splitToFieldChunks(text: String, maxLen: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var cursor = text
        while (cursor.isNotBlank()) {
            if (cursor.length <= maxLen) {
                chunks += cursor
                break
            }
            // 読みやすさのため、区切り位置は改行境界を優先する
            val splitIndex = cursor.lastIndexOf('\n', startIndex = maxLen).takeIf { it >= 1 } ?: maxLen
            chunks += cursor.substring(0, splitIndex).trimEnd()
            cursor = cursor.substring(splitIndex).trimStart('\n')
        }
        return chunks
    }

    private fun stableColor(input: String): Int {
        val hash = input.hashCode()
        val red = (hash shr 16) and 0x7F
        val green = (hash shr 8) and 0x7F
        val blue = hash and 0x7F
        return ((red + 64) shl 16) or ((green + 64) shl 8) or (blue + 64)
    }
}
