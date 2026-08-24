package com.pumpwatch.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.ApiClient
import kotlin.math.abs

class MonitoringWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = context.getSharedPreferences("pumpwatch_prefs", 0)
            val threshold = prefs.getFloat("alert_threshold", 5f).toDouble()

            val coins = ApiClient.api.getMarkets(perPage = 100)
            val movers = coins
                .filter { abs(it.price_change_percentage_24h ?: 0.0) >= threshold }
                .sortedByDescending { abs(it.price_change_percentage_24h ?: 0.0) }

            if (movers.isNotEmpty()) {
                val top = movers.first()
                val change = top.price_change_percentage_24h ?: 0.0
                val isPump = change >= 0
                showNotification(
                    title = if (isPump) "🚀 پامپ: ${top.symbol.uppercase()}"
                            else "🩸 دامپ: ${top.symbol.uppercase()}",
                    text = "تغییر ۲۴ساعته: ${String.format("%.1f%%", change)} | قیمت: ${formatUsd(top.current_price)}"
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(title: String, text: String) {
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pump_alerts",
                "هشدارهای پامپ",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, "pump_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun formatUsd(price: Double): String {
        return if (price >= 1) String.format("$%,.2f", price)
        else String.format("$%.6f", price)
    }
}
