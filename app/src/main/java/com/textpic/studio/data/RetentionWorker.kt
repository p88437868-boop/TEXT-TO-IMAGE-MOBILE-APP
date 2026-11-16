package com.textpic.studio.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.textpic.studio.TextPicApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class RetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as TextPicApplication
        val retentionDays = application.container.settingsRepository.settings.first().retentionDays
        if (retentionDays > 0) {
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
            application.container.messageRepository.purgeOlderThan(cutoff)
        }
        return Result.success()
    }
}

object RetentionScheduler {
    private const val WORK_NAME = "textpic-retention"

    fun schedule(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
