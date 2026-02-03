package com.notify2discord.app.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import com.notify2discord.app.notification.NotifyNotificationListenerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 再起動後に通知リスナーの再バインドを促す
            val component = ComponentName(context, NotifyNotificationListenerService::class.java)
            NotificationListenerService.requestRebind(component)
        }
    }
}
