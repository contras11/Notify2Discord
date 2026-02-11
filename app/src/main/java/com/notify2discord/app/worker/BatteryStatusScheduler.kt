package com.notify2discord.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notify2discord.app.data.BatteryReportConfig
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BatteryStatusWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
