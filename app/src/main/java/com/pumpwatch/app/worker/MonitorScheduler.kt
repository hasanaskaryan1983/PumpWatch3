package com.pumpwatch.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * MonitorScheduler — زمان‌بندی پایش پس‌زمینه
 * هر ۳۰ دقیقه یک‌بار MonitorWorker رو اجرا می‌کنه
 */
object MonitorScheduler {

    private const val WORK_NAME = "pumpwatch_monitor"

    fun start(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MonitorWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun schedule(context: Context) = start(context)

    fun cancel(context: Context) = stop(context)
}
