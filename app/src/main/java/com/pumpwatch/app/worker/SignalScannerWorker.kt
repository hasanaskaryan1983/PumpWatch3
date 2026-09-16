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
import com.pumpwatch.app.engine.QuickScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SignalScannerWorker — اسکنر ساعتی سیگنال‌ها + نوتیفیکیشن
 * 
 * Sprint 11 (C2): اگر اسکن ≥ ۵ سیگنال قوی پیدا کند، نوتیفیکیشن می‌فرستد
 * تا کاربر حتی وقتی اپ بسته است از فرصت‌ها آگاه شود.
 */
class SignalScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODE = "mode"
        private const val CHANNEL_ID = "signal_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val SIGNAL_THRESHOLD = 5 // حداقل ۵ سیگنال قوی برای نوتیفیکیشن
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("pumpwatch_prefs", 0)

        // snapshot mode از inputData یا prefs
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

        // 🚀 Sprint 11 (C2): نوتیفیکیشن برای سیگنال‌های قوی
        if (report.signalCount >= SIGNAL_THRESHOLD) {
            sendSignalNotification(report.signalCount, mode)
        }

        Result.success()
    }

    /**
     * ارسال نوتیفیکیشن برای سیگنال‌های قوی
     * - ساخت NotificationChannel اگر وجود ندارد (Android 8+)
     * - چک کردن POST_NOTIFICATIONS permission قبل از ارسال
     * - Intent برای باز کردن اپ (اگر MainActivity پشتیبانی کند)
     */
    private fun sendSignalNotification(count: Int, mode: String) {
        val context = applicationContext
        
        // چک کردن permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return // کاربر permission نداده، بی‌صدا رد شو
        }

        // ساخت NotificationChannel (Android 8+)
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

        // Intent برای باز کردن اپ
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_signals_tab", true) // MainActivity می‌تواند این را بخواند
        }
        
        val pendingIntent = if (intent != null) {
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val modeLabel = if (mode == "FUTURES") "فیوچرز" else "اسپات"
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // آیکون پیش‌فرض
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
            // permission revoked mid-flight — بی‌صدا رد شو
        }
    }
}
