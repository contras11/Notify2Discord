package com.notify2discord.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notify2discord.app.data.BatteryReportConfig
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object BatteryStatusScheduler {
    private const val UNIQUE_WORK_NAME = "notify2discord_battery_report"

    fun sync(context: Context, config: BatteryReportConfig, webhookUrl: String) {
        val workManager = WorkManager.getInstance(context)
        if (!config.enabled || webhookUrl.isBlank()) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val intervalMinutes = config.intervalMinutes.coerceIn(15, 1440).toLong()
        val initialDelayMinutes = computeInitialDelayMinutes(
            startHour = config.startHour,
            startMinute = config.startMinute
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BatteryStatusWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun computeInitialDelayMinutes(startHour: Int, startMinute: Int): Long {
        val now = LocalDateTime.now()
        val normalizedHour = startHour.coerceIn(0, 23)
        val normalizedMinute = startMinute.coerceIn(0, 59)

        // 指定時刻を次回開始時刻として固定し、その後は周期送信へ移行する
        var nextRun = now
            .withHour(normalizedHour)
            .withMinute(normalizedMinute)
            .withSecond(0)
            .withNano(0)
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1)
        }

        return Duration.between(now, nextRun)
            .toMinutes()
            .coerceAtLeast(0L)
    }
}
