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
import com.pumpwatch.app.engine.NotificationGuard
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit

class TraderMonitorWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "TraderMonitorWorker"
        // 🚀 Commit 8: هر تراکنش فقط یک بار نوتیفیکیشن می‌گیرد
        private const val DEDUP_WINDOW_MS = 24L * 3_600_000L

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
                    // 🚀 Commit 8: dedup بر اساس txId + شمارش پوشش
                    val key = "trader_${t.addr}_${act.txId}"
                    if (NotificationGuard.shouldDeliver(ctx, key, DEDUP_WINDOW_MS)) {
                        TraderStore.notify(ctx, t, act)
                        NotificationGuard.recordDelivered(ctx, key)
                    }
                }
                delay(300)
            }
            Result.success()
        } catch (e: Exception) {
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
