package com.notify2discord.app.notification.model

import com.notify2discord.app.data.RoutingRule

data class NotificationPayload(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val channelId: String,
    val importance: Int,
    val isSummary: Boolean
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = false
)

data class DiscordEmbedPayload(
    val title: String,
    val description: String,
    val color: Int,
    val fields: List<DiscordEmbedField>,
    val footerText: String
)

data class AttachmentPayload(
    val filePath: String,
    val fileName: String,
    val contentType: String
)

data class MessageRenderResult(
    val content: String,
    val embeds: List<DiscordEmbedPayload>,
    val attachment: AttachmentPayload? = null
)

data class FilterRule(
    val keywords: List<String>,
    val useRegex: Boolean,
    val regexPattern: String,
    val channelIds: Set<String>,
    val minImportance: Int,
    val excludeSummary: Boolean
)

typealias RoutingRuleSet = List<RoutingRule>
