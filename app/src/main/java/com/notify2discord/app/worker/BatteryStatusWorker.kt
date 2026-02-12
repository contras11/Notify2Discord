package com.notify2discord.app.worker

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2discord.app.battery.BatteryInfoCollector
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
    private val collector = BatteryInfoCollector(appContext)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")

    override suspend fun doWork(): Result {
        return runCatching {
            val settings = repository.getSettingsSnapshot()
            if (!settings.batteryReportConfig.enabled || settings.webhookUrl.isBlank()) {
                return Result.success()
            }

            val snapshot = collector.collect(
                history = settings.batteryHistory,
                nominalCapacityMah = settings.batteryNominalCapacityMah
            ) ?: return Result.retry()
            val percent = snapshot.levelPercent?.let { "${it}%" } ?: "取得不可"
            val health = snapshot.estimatedHealthByDesignPercent
                ?.let { "${"%.1f".format(it)}%（設計容量基準）" }
                ?: snapshot.estimatedHealthPercent?.let { "${"%.1f".format(it)}%（履歴基準）" }
                ?: "推定不可"

            val embed = DiscordEmbedPayload(
                title = "バッテリー残量レポート",
                description = "現在の端末バッテリーは $percent です。",
                color = colorByBatteryLevel(snapshot.levelPercent, snapshot.isCharging),
                fields = listOf(
                    DiscordEmbedField(name = "残量", value = percent, inline = true),
                    DiscordEmbedField(name = "状態", value = BatteryInfoCollector.statusLabel(snapshot.status), inline = true),
                    DiscordEmbedField(name = "推定劣化", value = health, inline = true),
                    DiscordEmbedField(name = "健康ステータス", value = BatteryInfoCollector.healthLabel(snapshot.health), inline = true),
                    DiscordEmbedField(name = "サイクル", value = snapshot.cycleCount?.toString() ?: "取得不可", inline = true),
                    DiscordEmbedField(name = "取得時刻", value = LocalDateTime.now().format(dateFormatter), inline = false),
                    DiscordEmbedField(name = "端末", value = "${Build.MANUFACTURER} ${Build.MODEL}".trim(), inline = false)
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

    private fun colorByBatteryLevel(levelPercent: Int?, isCharging: Boolean): Int {
        if (isCharging) return 0x2E86DE
        val level = levelPercent ?: return 0x2E86DE
        return when {
            level >= 60 -> 0x27AE60
            level >= 30 -> 0xF39C12
            else -> 0xE74C3C
        }
    }
}
