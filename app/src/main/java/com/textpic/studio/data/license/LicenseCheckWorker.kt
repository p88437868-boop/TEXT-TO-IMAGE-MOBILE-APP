package com.textpic.studio.data.license

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.textpic.studio.TextPicApplication
import java.util.concurrent.TimeUnit

/**
 * Twice-a-day license check so a block or an extension reaches the phone without
 * the customer opening the app. Failures are silent: a worker problem must never
 * lock anyone out, and the cached signed reply keeps working for a week.
 *
 * It also reports images rendered since the last check.
 */
class LicenseCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val application = applicationContext as TextPicApplication
        runCatching { application.container.licenseService.refresh(online = true) }
        return Result.success()
    }
}

object LicenseCheckScheduler {
    private const val WORK_NAME = "textpic-license-check"

    fun schedule(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<LicenseCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
