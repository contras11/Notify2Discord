package com.notify2discord.app.notification

import android.app.Notification
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import com.notify2discord.app.data.LineThreadProfile
import com.notify2discord.app.data.NotificationRecord
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.notification.model.NotificationPayload
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotifyNotificationListenerService : NotificationListenerService() {

    private lateinit var repository: SettingsRepository
    private lateinit var pipeline: NotificationDispatchPipeline
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val linePackageName = "jp.naver.line.android"

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        pipeline = NotificationDispatchPipeline(applicationContext, repository)

        // フォアグラウンドサービスとして実行
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationListenerForegroundHelper.startForeground(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // サービスが切断された場合、再バインドを要求
        requestRebind(ComponentName(this, NotifyNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        serviceScope.launch {
            val settings = repository.getSettingsSnapshot()
            val appName = resolveAppName(sbn.packageName)
            val parsed = parseNotificationMeta(
                packageName = sbn.packageName,
                notification = notification
            )

            val payload = NotificationPayload(
                packageName = sbn.packageName,
                appName = appName,
                title = parsed.title,
                text = parsed.text,
                postTime = sbn.postTime,
                channelId = sbn.notification.channelId.orEmpty(),
                importance = sbn.notification.priority,
                isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            )

            // selectedPackages が空なら全アプリが転送対象
            val isForwardingTarget = settings.selectedPackages.isEmpty() ||
                sbn.packageName in settings.selectedPackages

            var acceptedForForwarding = false
            if (settings.forwardingEnabled && isForwardingTarget) {
                acceptedForForwarding = pipeline.process(
                    settings = settings,
                    payload = payload,
                    sourceNotification = notification
                )
            }

            val shouldStoreAsUnsentTarget = settings.captureHistoryWhenForwardingOff &&
                sbn.packageName in settings.historyCapturePackages &&
                (!settings.forwardingEnabled || !isForwardingTarget)
            val shouldStoreHistory = acceptedForForwarding || shouldStoreAsUnsentTarget
            if (!shouldStoreHistory) return@launch

            val record = NotificationRecord(
                id = System.nanoTime(),
                packageName = sbn.packageName,
                appName = appName,
                title = parsed.title,
                text = parsed.text,
                postTime = sbn.postTime,
                historyGroupKey = parsed.historyGroupKey,
                conversationName = parsed.conversationName,
                senderName = parsed.senderName
            )
            repository.saveNotificationRecord(record)
            parsed.lineThreadProfile?.let { repository.upsertLineThreadProfile(it) }
        }
    }

    private fun parseNotificationMeta(
        packageName: String,
        notification: Notification
    ): ParsedNotificationMeta {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extractNotificationText(notification)
        if (packageName != linePackageName) {
            return ParsedNotificationMeta(
                title = title,
                text = text,
                historyGroupKey = null,
                conversationName = null,
                senderName = null,
                lineThreadProfile = null
            )
        }

        val messages = extractMessagingMessages(extras)
        val latestMessage = messages.lastOrNull()
        val senderName = latestMessage?.senderPerson?.name?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: latestMessage?.sender?.toString()?.trim()?.takeIf { it.isNotBlank() }

        val conversationName = listOf(
            extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            title
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        val historyGroupKey = conversationName
            ?.let(::normalizeLineConversationKey)
            ?.takeIf { it.isNotBlank() }
            ?.let { "line::$it" }
        val iconBase64 = extractLineIconBase64(notification, latestMessage)
        val lineProfile = historyGroupKey?.let { threadKey ->
            LineThreadProfile(
                threadKey = threadKey,
                displayName = conversationName ?: senderName ?: "LINE",
                iconBase64Png = iconBase64,
                updatedAt = System.currentTimeMillis()
            )
        }

        return ParsedNotificationMeta(
            title = title,
            text = text,
            historyGroupKey = historyGroupKey,
            conversationName = conversationName,
            senderName = senderName,
            lineThreadProfile = lineProfile
        )
    }

    private fun resolveAppName(packageName: String): String {
        // まず個人プロファイルで解決を試みる
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            return packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            // 個人プロファイルにない場合は仕事領域で解決を試みる
        }
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            if (launcherApps != null) {
                for (profile in launcherApps.getProfiles()) {
                    val activities = launcherApps.getActivityList(packageName, profile)
                    if (activities.isNotEmpty()) {
                        return activities[0].getLabel().toString()
                    }
                }
            }
        } catch (_: Exception) {
            // 仕事領域でも解決できない場合はパッケージ名で代替
        }
        return packageName
    }

    private fun extractNotificationText(notification: Notification): String {
        val extras = notification.extras
        val messaging = extractMessagingStyleText(extras)
        if (messaging.isNotBlank()) return messaging

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (bigText.isNotBlank()) return bigText

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it?.toString().orEmpty().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (textLines.isNotEmpty()) return textLines.joinToString(separator = "\n")

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (text.isNotBlank()) return text

        return extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
    }

    private fun extractMessagingMessages(extras: Bundle): List<Notification.MessagingStyle.Message> {
        val raw = extras.get(Notification.EXTRA_MESSAGES) as? Array<*> ?: return emptyList()
        val bundles = raw.mapNotNull { it as? Bundle }.toTypedArray()
        if (bundles.isEmpty()) return emptyList()
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
    }

    private fun extractMessagingStyleText(extras: Bundle): String {
        val lines = extractMessagingMessages(extras)
            .mapNotNull { message ->
                val body = message.text?.toString().orEmpty().trim()
                if (body.isBlank()) return@mapNotNull null
                val sender = message.senderPerson?.name?.toString()?.trim()
                    ?: message.sender?.toString()?.trim().orEmpty()
                if (sender.isBlank()) body else "$sender: $body"
            }
        return lines.joinToString(separator = "\n")
    }

    private fun extractLineIconBase64(
        notification: Notification,
        latestMessage: Notification.MessagingStyle.Message?
    ): String? {
        latestMessage?.senderPerson?.icon
            ?.let(::iconToBase64Png)
            ?.let { return it }

        val extras = notification.extras
        extras.getParcelable(Notification.EXTRA_LARGE_ICON, android.graphics.drawable.Icon::class.java)
            ?.let(::iconToBase64Png)
            ?.let { return it }
        extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, android.graphics.drawable.Icon::class.java)
            ?.let(::iconToBase64Png)
            ?.let { return it }

        extras.getParcelable(Notification.EXTRA_LARGE_ICON, Bitmap::class.java)
            ?.let(::bitmapToBase64Png)
            ?.let { return it }
        extras.getParcelable(Notification.EXTRA_LARGE_ICON_BIG, Bitmap::class.java)
            ?.let(::bitmapToBase64Png)
            ?.let { return it }

        return null
    }

    private fun iconToBase64Png(icon: android.graphics.drawable.Icon): String? {
        val bitmap = runCatching {
            // SDK差異で Icon#getBitmap が参照できない環境があるため Drawable 経由で統一する
            icon.loadDrawable(this)?.toBitmap(128, 128)
        }.getOrNull() ?: return null
        return bitmapToBase64Png(bitmap)
    }

    private fun bitmapToBase64Png(bitmap: Bitmap): String? {
        return runCatching {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun normalizeLineConversationKey(raw: String): String {
        return raw.trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }
}

private data class ParsedNotificationMeta(
    val title: String,
    val text: String,
    val historyGroupKey: String?,
    val conversationName: String?,
    val senderName: String?,
    val lineThreadProfile: LineThreadProfile?
)
