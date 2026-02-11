package com.notify2discord.app.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2discord.app.data.SettingsRepository
import com.notify2discord.app.notification.DiscordPayloadJsonBuilder
import com.notify2discord.app.notification.model.DiscordEmbedField
import com.notify2discord.app.notification.model.DiscordEmbedPayload
import com.notify2discord.app.notification.model.MessageRenderResult
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BatteryStatusWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val repository = SettingsRepository(appContext)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

    override suspend fun doWork(): Result {
        return runCatching {
            val settings = repository.getSettingsSnapshot()
            if (!settings.batteryReportConfig.enabled || settings.webhookUrl.isBlank()) {
                return Result.success()
            }

            val batteryIntent = applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
            ) ?: return Result.retry()

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return Result.retry()

            val percent = ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val statusLabel = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "充電中"
                BatteryManager.BATTERY_STATUS_FULL -> "満充電"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "放電中"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "未充電"
                else -> "不明"
            }

            val embed = DiscordEmbedPayload(
                title = "バッテリー残量レポート",
                description = "現在の端末バッテリーは ${percent}% です。",
                color = colorByBatteryLevel(percent, status),
                fields = listOf(
                    DiscordEmbedField(name = "残量", value = "${percent}%", inline = true),
                    DiscordEmbedField(name = "状態", value = statusLabel, inline = true),
                    DiscordEmbedField(
                        name = "取得時刻",
                        value = LocalDateTime.now().format(dateFormatter),
                        inline = false
                    ),
                    DiscordEmbedField(
                        name = "端末",
                        value = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        inline = false
                    )
                ),
                footerText = "Notify2Discord"
            )
            val render = MessageRenderResult(
                content = "🔋 バッテリーレポート",
                embeds = listOf(embed)
            )
            val payloadJson = DiscordPayloadJsonBuilder.build(render)
            DiscordWebhookEnqueuer.enqueue(
                context = applicationContext,
                webhookUrl = settings.webhookUrl,
                payloadJson = payloadJson,
                attachment = null
            )
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun colorByBatteryLevel(percent: Int, status: Int): Int {
        if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
            return 0x2E86DE
        }
        return when {
            percent >= 60 -> 0x27AE60
            percent >= 30 -> 0xF39C12
            else -> 0xE74C3C
        }
    }
}
