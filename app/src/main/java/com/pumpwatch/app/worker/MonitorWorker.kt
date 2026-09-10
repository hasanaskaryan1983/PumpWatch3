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

class MonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHANNEL_ID = "pumpwatch_monitor"
        private const val MIN_SCORE = 70
        
        /**
         * کلید inputData برای snapshot کردن mode در زمان enqueue.
         * Scheduler باید هنگام زمان‌بندی، mode فعلی کاربر را در inputData بگذارد
         * تا حتی اگر کاربر بعداً mode را عوض کرد، Worker با mode قبلی اجرا شود.
         */
        const val KEY_MODE = "mode"
    }

    override suspend fun doWork(): Result {
        return try {
            // اولویت ۱: snapshot از inputData (زمان enqueue)
            // اولویت ۲: fallback به prefs (برای backward compatibility)
            val modeRaw = inputData.getString(KEY_MODE)
                ?: applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
                    .getString("mode", "SPOT")
                ?: "SPOT"
            val mode = if (modeRaw == "FUTURES") "FUT" else "SPOT"

            val results = BatchScanner.scan(mode, SignalParams(), limit = 25)
            val hot = results.filter { it.side != "NONE" && it.score >= MIN_SCORE }.take(3)

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
