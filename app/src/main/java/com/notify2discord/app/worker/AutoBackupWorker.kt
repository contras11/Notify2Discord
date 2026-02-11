package com.notify2discord.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notify2discord.app.data.SettingsRepository

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val repository = SettingsRepository(appContext)

    override suspend fun doWork(): Result {
        // 定期バックアップは失敗時に再試行し、成功時のみ完了扱いにする
        val result = repository.exportSettingsToDefaultFolder()
        return if (result.success) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
