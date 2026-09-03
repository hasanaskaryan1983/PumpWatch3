package com.pumpwatch.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.engine.QuickScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignalScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
        val mode = prefs.getString("mode", "SPOT") ?: "SPOT"

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
