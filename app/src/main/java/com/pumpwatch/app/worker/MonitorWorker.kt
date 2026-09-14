package com.pumpwatch.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.KlineCache
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.ParamsStore
import com.pumpwatch.app.engine.SignalLogger
import java.util.Locale

/**
 * MonitorWorker — نسخهٔ یکپارچه
 * - BatchScanner از UnifiedSignalEngine + Binance klines استفاده می‌کند
 * - گیت واحد Dedup: فقط وقتی SignalLogger.log موفق شود نوتیفیکیشن می‌فرستیم
 * - برچسب منبع در نوتیفیکیشن: 📡 کشف بازار
 *
 * 🚀 P1-4: KlineCache.prune() در ابتدای هر اجرا
 * 🚀 Sprint 3: time = candleCloseTs (نه زمان اسکن)
 * 🚀 Sprint 4: پارامترهای سیگنال از ParamsStore (بهینه‌شده یا default)
 */
class MonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "pumpwatch_monitor"
        private const val MIN_SCORE = 70

        const val KEY_MODE = "mode"
    }

    override suspend fun doWork(): Result {
        return try {
            // 🚀 P1-4: خانه‌تکانی دوره‌ای cache
            KlineCache.prune()

            val modeRaw = inputData.getString(KEY_MODE)
                ?: applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
                    .getString("mode", "SPOT")
                ?: "SPOT"
            val mode = if (modeRaw == "FUTURES") "FUT" else "SPOT"

            // 🚀 Sprint 4: پارامترهای فعال — اگر WalkForwardOptimizer چیزی ذخیره کرده
            // باشد همان استفاده می‌شود، وگرنه default های SignalParams
            val signalParams = ParamsStore.load(applicationContext)

            val results = BatchScanner.scan(mode, signalParams, limit = 25)
            val hot = results.filter { it.side != "NONE" && it.score >= MIN_SCORE }.take(3)

            hot.forEachIndexed { i, r ->
                val logSide = if (r.side == "PUMP") "BUY" else "SELL"

                // 🚀 Sprint 3: timestamp = زمان بسته شدن کندل مولد سیگنال
                val signalTs = if (r.candleCloseTs > 0L) r.candleCloseTs else System.currentTimeMillis()

                // گیت واحد dedup با QuickScanner
                val logged = SignalLogger.log(
                    applicationContext,
                    LoggedSignal(
                        symbol = r.symbol,
                        side = logSide,
                        score = r.score,
                        entry = r.entry,
                        stop = r.stopLoss,
                        target = r.target1,
                        time = signalTs,
                        mode = mode
                    )
                )

                if (logged) {
                    showNotification(
                        id = 1000 + i,
                        title = "${if (r.side == "PUMP") "🚀 پامپ" else "🩸 دامپ"} ${r.symbol} — ${r.score}/100 ${if (r.golden) "🏅" else ""} 📡",
                        text = String.format(
                            Locale.US,
                            "ورود: %.6f | استاپ: %.6f | هدف: %.6f",
                            r.entry, r.stopLoss, r.target1
                        )
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun showNotification(id: Int, title: String, text: String) {
        val nm = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "هشدارهای پامپ/دامپ",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        nm.notify(id, notification)
    }
}
