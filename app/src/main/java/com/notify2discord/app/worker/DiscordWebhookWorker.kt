package com.notify2discord.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2discord.app.data.SettingsRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class DiscordWebhookWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()
    private val repository = SettingsRepository(appContext)

    override suspend fun doWork(): Result {
        val webhookUrl = inputData.getString(DiscordWebhookEnqueuer.KEY_WEBHOOK_URL).orEmpty()
        val payloadJson = inputData.getString(DiscordWebhookEnqueuer.KEY_PAYLOAD_JSON).orEmpty()
        val attachmentPath = inputData.getString(DiscordWebhookEnqueuer.KEY_ATTACHMENT_PATH).orEmpty()
        val attachmentName = inputData.getString(DiscordWebhookEnqueuer.KEY_ATTACHMENT_NAME).orEmpty()
        val attachmentContentType = inputData.getString(DiscordWebhookEnqueuer.KEY_ATTACHMENT_CONTENT_TYPE).orEmpty()

        if (webhookUrl.isBlank() || payloadJson.isBlank()) {
            // 必須情報が無ければ失敗にする
            return Result.failure()
        }

        val request = buildRequest(
            webhookUrl = webhookUrl,
            payloadJson = payloadJson,
            attachmentPath = attachmentPath,
            attachmentName = attachmentName,
            attachmentContentType = attachmentContentType
        )

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        repository.recordWebhookDeliveryResult(
                            url = webhookUrl,
                            success = true,
                            statusCode = response.code,
                            message = "送信成功"
                        )
                        Result.success()
                    }
                    response.code in 400..499 && response.code != 429 -> {
                        // 4xx は設定ミスの可能性が高いのでリトライしない
                        repository.recordWebhookDeliveryResult(
                            url = webhookUrl,
                            success = false,
                            statusCode = response.code,
                            message = "送信失敗: ${response.code}"
                        )
                        Result.failure()
                    }
                    else -> {
                        repository.recordWebhookDeliveryResult(
                            url = webhookUrl,
                            success = false,
                            statusCode = response.code,
                            message = "一時的な送信失敗: ${response.code}"
                        )
                        Result.retry()
                    }
                }
            }
        } catch (error: Exception) {
            // ネットワーク失敗などはリトライ
            repository.recordWebhookDeliveryResult(
                url = webhookUrl,
                success = false,
                statusCode = null,
                message = "通信失敗: ${error.message ?: "不明なエラー"}"
            )
            Result.retry()
        }
    }

    private fun buildRequest(
        webhookUrl: String,
        payloadJson: String,
        attachmentPath: String,
        attachmentName: String,
        attachmentContentType: String
    ): Request {
        val hasAttachment = attachmentPath.isNotBlank() && File(attachmentPath).exists()

        if (!hasAttachment) {
            val requestBody = payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            return Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .build()
        }

        val file = File(attachmentPath)
        val fileBody = file.asRequestBody(
            attachmentContentType.ifBlank { "application/octet-stream" }.toMediaTypeOrNull()
        )
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("payload_json", payloadJson)
            .addFormDataPart(
                "files[0]",
                attachmentName.ifBlank { file.name },
                fileBody
            )
            .build()

        return Request.Builder()
            .url(webhookUrl)
            .post(multipartBody)
            .build()
    }
}
