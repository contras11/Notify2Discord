package com.notify2discord.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

object NotificationListenerForegroundHelper {
    private const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "notification_listener_service"

    fun startForeground(service: Service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(service)
        }

        val notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle("Notify2Discord 実行中")
            .setContentText("通知を監視しています")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        service.startForeground(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "通知転送サービス",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "通知をDiscordに転送するサービスの実行状態を表示します"
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}
