package com.notify2discord.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DiscordWebhookWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val webhookUrl = inputData.getString(DiscordWebhookEnqueuer.KEY_WEBHOOK_URL).orEmpty()
        val content = inputData.getString(DiscordWebhookEnqueuer.KEY_CONTENT).orEmpty()

        if (webhookUrl.isBlank() || content.isBlank()) {
            // 必須情報が無ければ失敗にする
            return Result.failure()
        }

        val jsonBody = JSONObject()
            .put("content", content)
            .toString()

        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(webhookUrl)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success()
                    response.code in 400..499 && response.code != 429 -> {
                        // 4xx は設定ミスの可能性が高いのでリトライしない
                        Result.failure()
                    }
                    else -> Result.retry()
                }
            }
        } catch (ex: Exception) {
            // ネットワーク失敗などはリトライ
            Result.retry()
        }
    }
}
