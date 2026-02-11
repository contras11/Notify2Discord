package com.notify2discord.app.notification

import android.app.Notification
import android.content.Context
import com.notify2discord.app.data.PendingQuietItem
import com.notify2discord.app.data.QuietHoursConfig
import com.notify2discord.app.data.RateLimitConfig
import com.notify2discord.app.data.RoutingRule
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.data.SettingsState
import com.notify2discord.app.notification.model.MessageRenderResult
import com.notify2discord.app.notification.model.NotificationPayload
import com.notify2discord.app.worker.DiscordWebhookEnqueuer
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class NotificationDispatchPipeline(
    private val context: Context,
    private val repository: SettingsRepository
) {
    suspend fun process(
        settings: SettingsState,
        payload: NotificationPayload,
        sourceNotification: Notification
    ): Boolean {
        val now = System.currentTimeMillis()
        val aggregateFlushes = flushExpiredAggregates(settings, now)
        aggregateFlushes.forEach { flush ->
            dispatch(
                settings = settings,
                payload = flush.payload,
                webhooks = flush.webhooks,
                sourceNotification = null,
                aggregateCount = flush.aggregateCount
            )
        }

        val destinations = resolveDestinations(settings, payload)
        if (destinations.isEmpty()) return false

        if (!passesAdvancedFilter(settings, payload)) return false

        if (isInQuietHours(settings.quietHoursConfig, now)) {
            enqueueQuietItem(settings, payload, destinations)
            return true
        }

        if (settings.pendingQuietQueue.isNotEmpty()) {
            flushQuietQueue(settings)
        }

        if (shouldDropByDedupe(settings, payload, now)) return false

        val aggregateDecision = registerAggregate(settings, payload, destinations, now)
        when (aggregateDecision) {
            is AggregateDecision.Hold -> return true
            is AggregateDecision.SendNow -> {
                dispatch(
                    settings = settings,
                    payload = payload,
                    webhooks = destinations,
                    sourceNotification = sourceNotification,
                    aggregateCount = 1
                )
                return true
            }
        }
    }

    private suspend fun dispatch(
        settings: SettingsState,
        payload: NotificationPayload,
        webhooks: Set<String>,
        sourceNotification: Notification?,
        aggregateCount: Int
    ) {
        if (webhooks.isEmpty()) return
        if (!allowByRateLimit(settings.rateLimitConfig, payload.packageName, System.currentTimeMillis())) return

        val template = settings.appTemplates[payload.packageName]
            ?.takeIf { it.isNotBlank() }
            ?: settings.defaultTemplate

        val renderedFull = NotificationTemplateEngine.render(template, payload)
        val content = if (settings.embedConfig.enabled) {
            NotificationTemplateEngine.renderShortSummary(payload, aggregateCount)
        } else if (aggregateCount > 1) {
            "$renderedFull\n(直近で ${aggregateCount} 件を集約)"
        } else {
            renderedFull
        }

        val embeds = if (settings.embedConfig.enabled) {
            listOf(
                DiscordEmbedBuilder.build(
                    payload = payload,
                    config = settings.embedConfig,
                    aggregateCount = aggregateCount
                )
            )
        } else {
            emptyList()
        }

        val attachment = if (aggregateCount <= 1 && sourceNotification != null) {
            NotificationAttachmentExtractor.extract(
                context = context,
                notification = sourceNotification,
                payloadId = payload.postTime.toString()
            )
        } else {
            null
        }

        val result = MessageRenderResult(
            content = content,
            embeds = embeds,
            attachment = attachment
        )
        val payloadJson = DiscordPayloadJsonBuilder.build(result)

        webhooks.forEach { webhook ->
            DiscordWebhookEnqueuer.enqueue(
                context = context,
                webhookUrl = webhook,
                payloadJson = payloadJson,
                attachment = attachment
            )
        }
    }

    private fun resolveDestinations(settings: SettingsState, payload: NotificationPayload): Set<String> {
        // 個別Webhookが設定されている場合は、そのWebhookだけを送信先にする
        settings.appWebhooks[payload.packageName]
            ?.takeIf { it.isNotBlank() }
            ?.let { return setOf(it) }

        val destinations = linkedSetOf<String>()
        settings.webhookUrl
            .takeIf { it.isNotBlank() }
            ?.let { destinations += it }

        settings.routingRules
            .filter { it.enabled }
            .filter { matchesRoutingRule(it, payload) }
            .flatMap { it.webhookUrls }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { destinations += it }

        return destinations
    }

    private fun matchesRoutingRule(rule: RoutingRule, payload: NotificationPayload): Boolean {
        if (rule.packageNames.isNotEmpty() && payload.packageName !in rule.packageNames) {
            return false
        }

        val sourceText = buildSearchSource(payload)
        val keywordMatch = if (rule.keywords.isEmpty()) {
            false
        } else {
            rule.keywords.any { sourceText.contains(it, ignoreCase = true) }
        }

        val regexMatch = if (rule.useRegex && rule.regexPattern.isNotBlank()) {
            runCatching {
                Regex(rule.regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(sourceText)
            }.getOrDefault(false)
        } else {
            false
        }

        return when {
            rule.keywords.isNotEmpty() && rule.useRegex -> keywordMatch || regexMatch
            rule.keywords.isNotEmpty() -> keywordMatch
            rule.useRegex -> regexMatch
            else -> true
        }
    }

    private fun passesAdvancedFilter(settings: SettingsState, payload: NotificationPayload): Boolean {
        val filter = settings.filterConfig
        if (!filter.enabled) return true

        if (filter.excludeSummary && payload.isSummary) return false
        if (filter.channelIds.isNotEmpty() && payload.channelId !in filter.channelIds) return false
        if (payload.importance < filter.minImportance) return false

        val sourceText = buildSearchSource(payload)
        val keywordMatch = if (filter.keywords.isEmpty()) {
            false
        } else {
            filter.keywords.any { sourceText.contains(it, ignoreCase = true) }
        }

        val regexMatch = if (filter.useRegex && filter.regexPattern.isNotBlank()) {
            runCatching {
                Regex(filter.regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(sourceText)
            }.getOrDefault(false)
        } else {
            false
        }

        return when {
            filter.keywords.isNotEmpty() && filter.useRegex -> keywordMatch || regexMatch
            filter.keywords.isNotEmpty() -> keywordMatch
            filter.useRegex -> regexMatch
            else -> true
        }
    }

    private fun shouldDropByDedupe(settings: SettingsState, payload: NotificationPayload, now: Long): Boolean {
        val dedupeConfig = settings.dedupeConfig
        if (!dedupeConfig.enabled) return false

        val windowMs = dedupeConfig.windowSeconds.coerceAtLeast(1) * 1000L
        val appKey = payload.packageName

        if (dedupeConfig.contentHashEnabled) {
            val hash = sha256("${payload.title}\n${payload.text}")
            val previous = contentHashCache[appKey]
            if (previous != null && previous.first == hash && now - previous.second <= windowMs) {
                return true
            }
            contentHashCache[appKey] = hash to now
        }

        if (dedupeConfig.titleLatestOnly && payload.title.isNotBlank()) {
            val titleKey = "$appKey::${payload.title.lowercase()}"
            val previousTime = titleCache[titleKey]
            titleCache[titleKey] = now
            if (previousTime != null && now - previousTime <= windowMs) {
                return true
            }
        }

        return false
    }

    private fun registerAggregate(
        settings: SettingsState,
        payload: NotificationPayload,
        destinations: Set<String>,
        now: Long
    ): AggregateDecision {
        val config = settings.rateLimitConfig
        if (!config.enabled || config.aggregateWindowSeconds <= 0) {
            return AggregateDecision.SendNow
        }

        val windowMs = config.aggregateWindowSeconds.coerceAtLeast(1) * 1000L
        val key = payload.packageName
        val state = aggregateStateByApp[key]

        if (state == null || now - state.windowStart > windowMs) {
            aggregateStateByApp[key] = AggregateState(
                windowStart = now,
                count = 1,
                latest = payload,
                webhooks = destinations.toMutableSet()
            )
            return AggregateDecision.SendNow
        }

        // 連投は保持し、ウィンドウ満了時にサマリ化して送る
        state.count += 1
        state.latest = payload
        state.webhooks += destinations
        aggregateStateByApp[key] = state
        return AggregateDecision.Hold
    }

    private fun flushExpiredAggregates(settings: SettingsState, now: Long): List<AggregateFlush> {
        val config = settings.rateLimitConfig
        if (!config.enabled || config.aggregateWindowSeconds <= 0) return emptyList()

        val windowMs = config.aggregateWindowSeconds.coerceAtLeast(1) * 1000L
        val flushed = mutableListOf<AggregateFlush>()
        val keysToRemove = mutableListOf<String>()
        aggregateStateByApp.forEach { (key, state) ->
            if (now - state.windowStart < windowMs) return@forEach

            if (state.count > 1) {
                flushed += AggregateFlush(
                    payload = state.latest,
                    webhooks = state.webhooks,
                    aggregateCount = state.count
                )
            }
            keysToRemove += key
        }
        keysToRemove.forEach { aggregateStateByApp.remove(it) }

        return flushed
    }

    private suspend fun enqueueQuietItem(
        settings: SettingsState,
        payload: NotificationPayload,
        destinations: Set<String>
    ) {
        val updated = (settings.pendingQuietQueue + PendingQuietItem(
            packageName = payload.packageName,
            appName = payload.appName,
            title = payload.title,
            text = payload.text,
            postTime = payload.postTime,
            webhookUrls = destinations.toList()
        )).takeLast(500)

        repository.saveQuietQueue(updated)
    }

    private suspend fun flushQuietQueue(settings: SettingsState) {
        val queue = settings.pendingQuietQueue
        if (queue.isEmpty()) return

        queue.groupBy { it.packageName }.forEach { (packageName, items) ->
            val latest = items.maxByOrNull { it.postTime } ?: return@forEach
            val destinations = items.flatMap { it.webhookUrls }.toSet()
            if (destinations.isEmpty()) return@forEach

            val payload = NotificationPayload(
                packageName = packageName,
                appName = latest.appName,
                title = latest.title,
                text = "サイレント時間中の通知 ${items.size} 件をまとめました。最新: ${latest.text}",
                postTime = latest.postTime,
                channelId = "",
                importance = Int.MAX_VALUE,
                isSummary = false
            )

            dispatch(
                settings = settings,
                payload = payload,
                webhooks = destinations,
                sourceNotification = null,
                aggregateCount = items.size
            )
        }

        repository.saveQuietQueue(emptyList())
    }

    private fun isInQuietHours(config: QuietHoursConfig, nowMillis: Long): Boolean {
        if (!config.enabled) return false

        val now = LocalDateTime.now()
        if (config.daysOfWeek.isNotEmpty()) {
            val today = toLegacyDayIndex(now.dayOfWeek)
            if (today !in config.daysOfWeek) return false
        }

        val nowMinute = now.hour * 60 + now.minute
        val startMinute = config.startHour * 60 + config.startMinute
        val endMinute = config.endHour * 60 + config.endMinute

        return if (startMinute <= endMinute) {
            nowMinute in startMinute until endMinute
        } else {
            nowMinute >= startMinute || nowMinute < endMinute
        }
    }

    private fun toLegacyDayIndex(dayOfWeek: DayOfWeek): Int {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> 2
            DayOfWeek.TUESDAY -> 3
            DayOfWeek.WEDNESDAY -> 4
            DayOfWeek.THURSDAY -> 5
            DayOfWeek.FRIDAY -> 6
            DayOfWeek.SATURDAY -> 7
            DayOfWeek.SUNDAY -> 1
        }
    }

    private fun allowByRateLimit(config: RateLimitConfig, packageName: String, now: Long): Boolean {
        if (!config.enabled) return true

        val windowMs = config.windowSeconds.coerceAtLeast(1) * 1000L
        val deque = sentAtByApp.getOrPut(packageName) { ArrayDeque() }

        while (deque.isNotEmpty() && now - deque.first() > windowMs) {
            deque.removeFirst()
        }

        if (deque.size >= config.maxPerWindow.coerceAtLeast(1)) {
            return false
        }

        deque.addLast(now)
        return true
    }

    private fun buildSearchSource(payload: NotificationPayload): String {
        return listOf(payload.appName, payload.title, payload.text, payload.packageName)
            .joinToString("\n")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class AggregateState(
        val windowStart: Long,
        var count: Int,
        var latest: NotificationPayload,
        val webhooks: MutableSet<String>
    )

    private sealed class AggregateDecision {
        object SendNow : AggregateDecision()
        object Hold : AggregateDecision()
    }

    private data class AggregateFlush(
        val payload: NotificationPayload,
        val webhooks: Set<String>,
        val aggregateCount: Int
    )

    companion object {
        // 連投制御の状態を保持し、サービス再生成時の乱高下を抑える
        private val contentHashCache = ConcurrentHashMap<String, Pair<String, Long>>()
        private val titleCache = ConcurrentHashMap<String, Long>()
        private val sentAtByApp = ConcurrentHashMap<String, ArrayDeque<Long>>()
        private val aggregateStateByApp = ConcurrentHashMap<String, AggregateState>()
    }
}
