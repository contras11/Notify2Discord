package com.notify2discord.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2discord.app.battery.BatteryInfoCollector
import com.notify2discord.app.data.SettingsRepository

class BatterySnapshotWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val repository = SettingsRepository(appContext)
    private val collector = BatteryInfoCollector(appContext)

    override suspend fun doWork(): Result {
        val settings = repository.getSettingsSnapshot()
        if (!settings.batteryHistoryConfig.enabled) {
            return Result.success()
        }

        val snapshot = collector.collect(
            history = settings.batteryHistory,
            nominalCapacityMah = settings.batteryNominalCapacityMah
        ) ?: return Result.retry()
        repository.appendBatterySnapshot(snapshot)
        return Result.success()
    }
}
