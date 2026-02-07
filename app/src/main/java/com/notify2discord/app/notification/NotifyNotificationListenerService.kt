package com.notify2discord.app.notification

import android.app.Notification
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notify2discord.app.data.NotificationRecord
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.notification.model.NotificationPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NotifyNotificationListenerService : NotificationListenerService() {

    private lateinit var repository: SettingsRepository
    private lateinit var pipeline: NotificationDispatchPipeline
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            if (!settings.forwardingEnabled) return@launch

            // ホワイトリスト判定：空なら全アプリ転送、非空なら選択したアプリのみ転送
            if (settings.selectedPackages.isNotEmpty() && sbn.packageName !in settings.selectedPackages) {
                return@launch
            }

            val appName = resolveAppName(sbn.packageName)
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

            val payload = NotificationPayload(
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                postTime = sbn.postTime,
                channelId = sbn.notification.channelId.orEmpty(),
                importance = sbn.notification.priority,
                isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            )

            val accepted = pipeline.process(
                settings = settings,
                payload = payload,
                sourceNotification = notification
            )
            if (!accepted) return@launch

            // 通知履歴は実際に送信対象となったもののみ保存
            val record = NotificationRecord(
                id = System.nanoTime(),
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                postTime = sbn.postTime
            )
            repository.saveNotificationRecord(record)
        }
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
}
