package com.notify2discord.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object DiscordWebhookEnqueuer {
    const val KEY_WEBHOOK_URL = "webhook_url"
    const val KEY_CONTENT = "content"
    private const val UNIQUE_WORK_NAME = "discord_webhook_queue"

    // 直列キューで Discord 送信を積む
    fun enqueue(context: Context, webhookUrl: String, content: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DiscordWebhookWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    KEY_WEBHOOK_URL to webhookUrl,
                    KEY_CONTENT to content
                )
            )
            .build()

        WorkManager.getInstance(context)
            .beginUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND, request)
            .enqueue()
    }
}
