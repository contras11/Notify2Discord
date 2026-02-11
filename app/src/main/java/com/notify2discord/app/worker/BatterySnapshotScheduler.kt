package com.notify2discord.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.notify2discord.app.data.BatteryHistoryConfig
import java.util.concurrent.TimeUnit

object BatterySnapshotScheduler {
    private const val UNIQUE_WORK_NAME = "notify2discord_battery_snapshot"

    fun sync(context: Context, config: BatteryHistoryConfig) {
        val manager = WorkManager.getInstance(context)
        if (!config.enabled) {
            manager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<BatterySnapshotWorker>(1, TimeUnit.HOURS)
            .build()

        manager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
