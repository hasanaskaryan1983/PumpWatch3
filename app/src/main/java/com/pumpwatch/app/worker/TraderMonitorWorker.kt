package com.pumpwatch.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.TraderStore
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * 🚀 Sprint 16 — Worker بررسی فعالیت تاپ تریدرها
 * هر ۱۵ دقیقه ولت‌های ردیابی‌شده (با هشدار روشن) را چک می‌کند.
 * اگر فعالیت جدیدی دید: ثبت + نوتیفیکیشن.
 */
class TraderMonitorWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val tracked = TraderStore.list(ctx).filter { it.alertOn }.take(20)
            for (t in tracked) {
                val act = TraderStore.checkNewActivity(ctx, t)
                if (act != null) {
                    TraderStore.markSeen(ctx, t.addr, act.ts, act.txId)
                    TraderStore.notify(ctx, t, act)
                }
                delay(300)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/**
 * 🚀 Sprint 16 — Scheduler ثبت‌نام Worker در WorkManager
 */
object TraderMonitorScheduler {
    private const val NAME = "TraderMonitor"

    fun start(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<TraderMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}
