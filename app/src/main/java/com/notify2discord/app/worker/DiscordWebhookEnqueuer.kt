package com.notify2discord.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.notify2discord.app.notification.model.AttachmentPayload
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DiscordWebhookEnqueuer {
    const val KEY_WEBHOOK_URL = "webhook_url"
    const val KEY_PAYLOAD_JSON = "payload_json"
    const val KEY_ATTACHMENT_PATH = "attachment_path"
    const val KEY_ATTACHMENT_NAME = "attachment_name"
    const val KEY_ATTACHMENT_CONTENT_TYPE = "attachment_content_type"

    private const val UNIQUE_WORK_NAME = "discord_webhook_queue"

    // 旧呼び出し互換: contentのみ指定されたらJSONへ包む
    fun enqueue(context: Context, webhookUrl: String, content: String) {
        val payloadJson = JSONObject().put("content", content).toString()
        enqueue(context, webhookUrl, payloadJson, null)
    }

    // 直列キューで Discord 送信を積む
    fun enqueue(
        context: Context,
        webhookUrl: String,
        payloadJson: String,
        attachment: AttachmentPayload?
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DiscordWebhookWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(
                workDataOf(
                    KEY_WEBHOOK_URL to webhookUrl,
                    KEY_PAYLOAD_JSON to payloadJson,
                    KEY_ATTACHMENT_PATH to attachment?.filePath,
                    KEY_ATTACHMENT_NAME to attachment?.fileName,
                    KEY_ATTACHMENT_CONTENT_TYPE to attachment?.contentType
                )
            )
            .build()

        WorkManager.getInstance(context)
            .beginUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.APPEND, request)
            .enqueue()
    }
}
