package com.pumpwatch.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.MainActivity
import com.pumpwatch.app.data.FollowedWhalesStore
import com.pumpwatch.app.data.WatchlistStore
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import com.pumpwatch.app.engine.WhaleFlowEngine
import com.pumpwatch.app.engine.WhaleFlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 🚀 Commit 91 (A5) + Commit 93 (7.1): Worker پس‌زمینه برای نهنگ و میم.
 * هر ۱۵ دقیقه اجرا می‌شود و در صورت یافتن سیگنال قوی، نوتیفیکیشن ارسال می‌کند.
 */
class WhaleMemeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "WhaleMemeWorker"
        const val CHANNEL_ID = "whale_meme_alerts"
        const val CHANNEL_NAME = "هشدارهای نهنگ و میم"
        private const val NOTIFICATION_ID_WHALE = 1001
        private const val NOTIFICATION_ID_MEME = 1002
        private const val NOTIFICATION_ID_FOLLOWED = 1003
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            checkWhaleAlerts()
            checkMemeAlerts()
            checkFollowedWhalesAlerts() // 🚀 Commit 93: چک نهنگ‌های دنبال‌شده
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkWhaleAlerts() {
        val watchlist = WatchlistStore.load(applicationContext)
        if (watchlist.isEmpty()) return

        for (entry in watchlist) {
            try {
                val result = WhaleFlowEngine.analyze(
                    symbol = entry.symbol + "USDT",
                    limit = 1000
                ) ?: continue

                if (result.pressure == WhaleFlowEngine.PRESSURE_ACCUMULATION ||
                    result.pressure == WhaleFlowEngine.PRESSURE_DISTRIBUTION
                ) {
                    sendWhaleNotification(entry.symbol, result)
                }
            } catch (_: Exception) {
                // خطای یک توکن نباید بقیه را متوقف کند
            }
        }
    }

    private suspend fun checkMemeAlerts() {
        try {
            val signals = MemeRadar.scan { _, _ -> }
            val highScoreSignals = signals.filter { it.score >= 80 }

            if (highScoreSignals.isNotEmpty()) {
                sendMemeNotification(highScoreSignals.take(3))
            }
        } catch (_: Exception) {
            // خطا در اسکن میم نباید worker را شکست دهد
        }
    }

    // 🚀 Commit 93: چک نهنگ‌های دنبال‌شده (بر اساس فعالیت تجمعی نماد)
    private suspend fun checkFollowedWhalesAlerts() {
        val followedWhales = FollowedWhalesStore.load(applicationContext)
        if (followedWhales.isEmpty()) return

        for (whale in followedWhales) {
            try {
                val result = WhaleFlowEngine.analyze(
                    symbol = whale.symbol + "USDT",
                    limit = 1000
                ) ?: continue

                // اگر فعالیت نهنگی وجود دارد و بزرگ‌ترین معامله از آستانه بیشتر است
                if (result.whaleTrades > 0 && result.largestTrade >= whale.alertThreshold) {
                    sendFollowedWhaleNotification(whale, result)
                }
            } catch (_: Exception) {
                // خطای یک نماد نباید بقیه را متوقف کند
            }
        }
    }

    private fun sendWhaleNotification(symbol: String, result: WhaleFlowResult) {
        val pressureText = when (result.pressure) {
            WhaleFlowEngine.PRESSURE_ACCUMULATION -> "🐳 فشار خرید نهنگ‌ها: $symbol"
            WhaleFlowEngine.PRESSURE_DISTRIBUTION -> "📉 فشار فروش نهنگ‌ها: $symbol"
            else -> return
        }

        val body = "نسبت خرید: ${(result.buyRatio * 100).toInt()}% • " +
                "${result.whaleTrades} معاملهٔ نهنگی • منبع: ${result.source}"

        sendNotification(NOTIFICATION_ID_WHALE, pressureText, body)
    }

    private fun sendMemeNotification(signals: List<MemeSignal>) {
        if (signals.isEmpty()) return
        val first = signals.first()
        val title = "🚀 سیگنال میم جدید: ${first.symbol}"
        val body = buildString {
            append("امتیاز: ${first.score}/100 • ")
            append("نقدینگی: $${(first.liquidity / 1000).toInt()}K • ")
            append("تغییر ۱س: ${String.format(Locale.US, "%.1f", first.changeH1)}%")
            if (signals.size > 1) append("\nو ${signals.size - 1} سیگنال دیگر")
        }
        sendNotification(NOTIFICATION_ID_MEME, title, body)
    }

    // 🚀 Commit 93: نوتیفیکیشن برای فعالیت نهنگی در نماد دنبال‌شده
    private fun sendFollowedWhaleNotification(
        whale: com.pumpwatch.app.data.FollowedWhale, 
        result: WhaleFlowResult
    ) {
        val title = "🐳 فعالیت نهنگی در نماد دنبال‌شده: ${whale.symbol}"
        val body = buildString {
            append("تعداد معاملات نهنگی: ${result.whaleTrades}\n")
            append("بزرگ‌ترین معامله: $${String.format(Locale.US, "%,.0f", result.largestTrade)}\n")
            append("نسبت خرید: ${(result.buyRatio * 100).toInt()}%")
        }
        sendNotification(NOTIFICATION_ID_FOLLOWED, title, body)
    }

    private fun sendNotification(notificationId: Int, title: String, body: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "هشدارهای نهنگ و میم‌کوین"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
