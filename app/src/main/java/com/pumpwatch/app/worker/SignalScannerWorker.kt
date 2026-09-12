package com.pumpwatch.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.engine.QuickScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SignalScannerWorker — نسخهٔ یکپارچه
 * تغییرات:
 * - snapshot گرفتن mode از inputData (مثل MonitorWorker) تا با تغییر mode کاربر، اجرای جاری به‌هم نریزد
 * - QuickScanner از قبل به UnifiedSignalEngine وصل است؛ اینجا فقط هماهنگ‌سازی رفتار Workerهاست
 */
class SignalScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODE = "mode"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("pumpwatch_prefs", 0)

        // اولویت ۱: snapshot زمان enqueue — اولویت ۲: prefs
        val modeRaw = inputData.getString(KEY_MODE)
            ?: prefs.getString("mode", "SPOT")
            ?: "SPOT"
        // QuickScanner با "FUTURES" کار می‌کند (نه "FUT")
        val mode = if (modeRaw == "FUT" || modeRaw == "FUTURES") "FUTURES" else "SPOT"

        val report = QuickScanner.scan(
            applicationContext,
            QuickScanner.TOP_SYMBOLS.take(50),
            mode
        )

        prefs.edit()
            .putString("last_scores", "سیگنال: ${report.signalCount}\n" + report.lines.joinToString("\n"))
            .apply()

        Result.success()
    }
}
