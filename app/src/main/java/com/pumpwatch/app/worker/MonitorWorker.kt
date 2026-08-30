package com.pumpwatch.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.SignalParams
import java.util.Locale

/**
 * MonitorWorker — پایش پس‌زمینه سبک
 * هر بار اجرا: اسکن ۳۰ ارز برتر + نوتیفیکیشن برای سیگنال‌های داغ
 */
class MonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "pumpwatch_monitor"
        private const val MIN_SCORE = 70
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
            val mode = prefs.getString("mode", "SPOT") ?: "SPOT"

            // اسکن سبک در پس‌زمینه (فقط ۳۰ ارز برتر)
            val results = BatchScanner.scan(mode, SignalParams(), limit = 30)
            val hot = results.filter { it.score >= MIN_SCORE }.take(3)

            hot.forEachIndexed { i, r ->
                showNotification(
                    id = 1000 + i,
                    title = "${if (r.side == "PUMP") "🚀 پامپ" else "🩸 دامپ"} ${r.symbol} — ${r.score}/100 ${if (r.golden) "🏅" else ""}",
                    text = String.format(
                        Locale.US,
                        "ورود: %.6f | استاپ: %.6f | هدف: %.6f",
                        r.entry, r.stopLoss, r.target1
                    )
                )
            }

            Result.success()
        } catch (e: Exception) {
            // اگه خطا بود (مثلاً 429)، چند بار تلاش کن
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
