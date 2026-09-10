package com.pumpwatch.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * MonitorScheduler — زمان‌بندی پایش پس‌زمینه
 * هر ۳۰ دقیقه یک‌بار MonitorWorker رو اجرا می‌کنه.
 *
 * اصلاحات فاز ۲:
 * - snapshot از mode در inputData (تا تغییر mode کاربر، Worker قدیمی رو invalidate کنه)
 * - BackoffPolicy.EXPONENTIAL برای مواجهه با 429/network-error (شروع از ۱ دقیقه، دو برابر تا سقف ۵ ساعت)
 * - ExistingPeriodicWorkPolicy.UPDATE تا تغییر پارامترها واقعاً اعمال بشه
 */
object MonitorScheduler {

    private const val WORK_NAME = "pumpwatch_monitor"

    fun start(context: Context) {
        val prefs = context.getSharedPreferences("pumpwatch_prefs", 0)
        val currentMode = prefs.getString("mode", "SPOT") ?: "SPOT"

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // snapshot mode در inputData تا Worker نتیجه‌ای هم‌خوان با انتظار کاربر بدهد
        val inputData = Data.Builder()
            .putString(MonitorWorker.KEY_MODE, currentMode)
            .build()

        val request = PeriodicWorkRequestBuilder<MonitorWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            // backoff نمایی: اگر Worker با Result.retry() شکست خورد (مثلاً 429 یا network)،
            // WorkManager بعد از 1 دقیقه دوباره تلاش می‌کند، سپس 2 دقیقه، 4، 8، ... تا سقف ~5 ساعت
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.MINUTES
            )
            .build()

        // UPDATE: اگر Worker قبلی با mode/پارامتر قدیمی هست، با این جایگزین شود
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun schedule(context: Context) = start(context)

    fun cancel(context: Context) = stop(context)
}
