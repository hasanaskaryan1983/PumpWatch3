package com.pumpwatch.app

import android.app.Application
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit

class PumpWatchApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // اگر خطایی رخ داد، توی فایل ذخیره می‌شه
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val log = Log.getStackTraceString(throwable)
                File(filesDir, "crash.log").writeText(log)
            } catch (_: Exception) { }
            throw throwable
        }

        // WorkManager رو اینجا راه‌اندازی می‌کنیم (نه توی Activity)
        try {
            val request = PeriodicWorkRequestBuilder<MonitoringWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "pump_monitoring",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            File(filesDir, "workmanager_error.log").writeText(e.stackTraceToString())
        }
    }
}
