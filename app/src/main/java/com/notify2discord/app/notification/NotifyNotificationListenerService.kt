package com.notify2discord.app.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.worker.DiscordWebhookEnqueuer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotifyNotificationListenerService : NotificationListenerService() {

    private lateinit var repository: SettingsRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        serviceScope.launch {
            val settings = repository.getSettingsSnapshot()
            if (!settings.forwardingEnabled) return@launch
            if (settings.webhookUrl.isBlank()) return@launch
            if (settings.excludedPackages.contains(sbn.packageName)) return@launch

            val appName = resolveAppName(sbn.packageName)
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

            val content = NotificationMessageFormatter.format(
                appName = appName,
                title = title,
                text = text,
                postTime = sbn.postTime
            )

            // WorkManager の直列キューに積んで送信する
            DiscordWebhookEnqueuer.enqueue(applicationContext, settings.webhookUrl, content)
        }
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (ex: Exception) {
            // 取得できない場合はパッケージ名で代替
            packageName
        }
    }
}
