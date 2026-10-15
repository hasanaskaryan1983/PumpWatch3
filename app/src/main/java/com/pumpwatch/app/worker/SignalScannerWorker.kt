package com.pumpwatch.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.engine.NotificationGuard
import com.pumpwatch.app.engine.QuickScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignalScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODE = "mode"
        private const val CHANNEL_ID = "signal_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val SIGNAL_THRESHOLD = 5
        // 🚀 Commit 8: پنجرهٔ dedup شش‌ساعته
        private const val DEDUP_WINDOW_MS = 6L * 3_600_000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("pumpwatch_prefs", 0)

        val modeRaw = inputData.getString(KEY_MODE)
            ?: prefs.getString("mode", "SPOT")
            ?: "SPOT"
        val mode = if (modeRaw == "FUT" || modeRaw == "FUTURES") "FUTURES" else "SPOT"

        val report = QuickScanner.scan(
            applicationContext,
            QuickScanner.TOP_SYMBOLS.take(50),
            mode
        )

        prefs.edit()
            .putString("last_scores", "سیگنال: ${report.signalCount}\n" + report.lines.joinToString("\n"))
            .apply()

        if (report.signalCount >= SIGNAL_THRESHOLD) {
            SignalNavigator.markPending(applicationContext)
            // 🚀 Commit 8: dedup — گزارش یکسان داخل پنجره تکرار نمی‌شود
            val key = "scan_${mode}_${report.signalCount}"
            if (NotificationGuard.shouldDeliver(applicationContext, key, DEDUP_WINDOW_MS)) {
                sendSignalNotification(report.signalCount, mode)
                NotificationGuard.recordDelivered(applicationContext, key)
            }
        }

        Result.success()
    }

    private fun sendSignalNotification(count: Int, mode: String) {
        val context = applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "سیگنال‌های قوی",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "هشدار برای سیگنال‌های معاملاتی قوی (اسکن ساعتی)"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_signals_tab", true)
        }

        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val modeLabel = if (mode == "FUTURES") "فیوچرز" else "اسپات"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔥 $count سیگنال قوی جدید")
            .setContentText("حالت: $modeLabel • برای مشاهده کلیک کنید")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("اسکن ساعتی $count سیگنال قوی در حالت $modeLabel پیدا کرد. برای مشاهدهٔ جزئیات و ورود/خروج دقیق کلیک کنید."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
