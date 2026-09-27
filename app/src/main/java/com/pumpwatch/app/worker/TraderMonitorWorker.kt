package com.pumpwatch.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.TraderStore
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 🚀 Sprint 16 — Worker بررسی فعالیت تاپ تریدرها
 * 🚀 Commit 71 (فاز ۱ — بند ۱۱): تفکیک transient vs terminal errors
 *
 * هر ۱۵ دقیقه ولت‌های ردیابی‌شده (با هشدار روشن) را چک می‌کند.
 * اگر فعالیت جدیدی دید: ثبت + نوتیفیکیشن.
 *
 * ⚠️ `delay(300)` بین هر تریدر عمدی است: Solana RPC rate limit را دور می‌زند.
 *    این ۶ ثانیه worker را می‌گیرد، ولی ANR نمی‌دهد چون background است.
 */
class TraderMonitorWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "TraderMonitorWorker"

        /**
         * 🚀 Commit 71: تشخیص خطای موقتی از قطعی (بند ۱۱).
         * (همان منطق MonitorWorker — کپی شده برای استقلال هر worker)
         */
        internal fun isTransient(e: Throwable): Boolean {
            val name = e::class.java.simpleName
            val msg = (e.message ?: "").lowercase(Locale.US)

            if (name.contains("Timeout", true) ||
                name.contains("Network", true) ||
                name.contains("Socket", true) ||
                name.contains("Connect", true) ||
                name.contains("UnknownHost", true) ||
                name.contains("IOException", true)
            ) return true

            if (msg.contains("timeout") ||
                msg.contains("429") ||
                msg.contains("500") ||
                msg.contains("502") ||
                msg.contains("503") ||
                msg.contains("504") ||
                msg.contains("network") ||
                msg.contains("connection") ||
                msg.contains("unreachable")
            ) return true

            return false
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val tracked = TraderStore.list(ctx).filter { it.alertOn }.take(20)
            for (t in tracked) {
                val act = TraderStore.checkNewActivity(ctx, t)
                if (act != null) {
                    TraderStore.markSeen(ctx, t.addr, act.ts, act.txId)
                    TraderStore.notify(ctx, t, act)
                }
                // ⚠️ delay(300) عمدی: Solana RPC rate limit
                // ۲۰ تریدر × ۳۰۰ms = ۶ ثانیه worker را می‌گیرد
                // ولی ANR نمی‌دهد چون background است و WorkManager محدودیت ۱۰ دقیقه دارد
                delay(300)
            }
            Result.success()
        } catch (e: Exception) {
            // 🚀 Commit 71 (بند ۱۱): تفکیک transient vs terminal
            if (isTransient(e)) {
                Log.w(TAG, "Transient error (network/rate-limit), will retry", e)
                Result.retry()
            } else {
                Log.e(TAG, "Terminal error (code/data bug), failing", e)
                Result.failure()
            }
        }
    }
}

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
