package com.pumpwatch.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.ApiClient
import java.util.Locale
import kotlin.math.abs

class MonitoringWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val coins = ApiClient.api.getMarkets(perPage = 100)
            val top = coins.maxByOrNull { abs(it.price_change_percentage_24h ?: 0.0) }
            val change = top?.price_change_percentage_24h ?: 0.0

            if (top != null && abs(change) >= 10.0) {
                val title = if (change >= 0) {
                    "🚀 پامپ: ${top.symbol.uppercase(Locale.US)}"
                } else {
                    "🩸 دامپ: ${top.symbol.uppercase(Locale.US)}"
                }
                val text = "${top.name}: ${String.format(Locale.US, "%+.2f%%", change)}"
                showNotification(title, text)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pump_alerts",
                "هشدار پامپ/دامپ",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, "pump_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
