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
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.engine.SignalParams
import java.util.Locale

/**
 * MonitorWorker — نسخهٔ یکپارچه
 * تغییرات:
 * - BatchScanner حالا از UnifiedSignalEngine + Binance klines استفاده می‌کند (همان منبع QuickScanner)
 * - گیت واحد Dedup: فقط وقتی SignalLogger.log موفق شود نوتیفیکیشن می‌فرستیم
 *   (این یعنی اگر QuickScanner همان سیگنال را زودتر ثبت کرده باشد، نوتیف تکراری/متناقض نمی‌فرستیم)
 * - برچسب منبع در نوتیفیکیشن: 📡 کشف بازار
 *
 * 🚀 P1-4: KlineCache.prune() در ابتدای هر بار اجرا صدا می‌شود تا entryهای منقضی
 *      (کندل‌های قدیمی‌تر از ۶۰ ثانیه) قبل از scan آزاد شوند.
 *      این باعث می‌شود در استفادهٔ طولانی‌مدت (چندین روز)، سقف ۳۰۰ entry هیچ‌وقت
 *      فعال نشود و eviction LRU بی‌مورد فعال نگردد.
 *
 * 🚀 Sprint 3 (P2-1): timestamp ثبت سیگنال = زمان بسته شدن کندل مولد آن
 *      (candleCloseTs) به جای زمان اجرای Worker. این هم‌راستا با فلسفهٔ P0-6
 *      است که timestamp سیگنال باید به کندلِ تحلیل‌شده مربوط باشد، نه به لحظهٔ
 *      اجرای کد. fallback به زمان اسکن فقط اگر candleCloseTs صفر بود.
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
            // 🚀 P1-4: خانه‌تکانی دوره‌ای cache — حذف entryهای منقضی قبل از scan
            KlineCache.prune()

            val modeRaw = inputData.getString(KEY_MODE)
                ?: applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
                    .getString("mode", "SPOT")
                ?: "SPOT"
            val mode = if (modeRaw == "FUTURES") "FUT" else "SPOT"

            val results = BatchScanner.scan(mode, SignalParams(), limit = 25)
            val hot = results.filter { it.side != "NONE" && it.score >= MIN_SCORE }.take(3)

            hot.forEachIndexed { i, r ->
                val logSide = if (r.side == "PUMP") "BUY" else "SELL"

                // 🚀 Sprint 3: timestamp = زمان بسته شدن کندل مولد سیگنال.
                // fallback به System.currentTimeMillis فقط اگر candleCloseTs صفر بود
                // (برای backward compat با داده‌های legacy یا edge caseهایی که
                // موتور نتوانسته close time را تعیین کند).
                val signalTs = if (r.candleCloseTs > 0L) r.candleCloseTs else System.currentTimeMillis()

                // گیت واحد dedup با QuickScanner: اگر قبلاً ثبت شده، log=false و نوتیف نمی‌فرستیم
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
