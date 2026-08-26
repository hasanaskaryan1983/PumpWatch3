package com.pumpwatch.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.Candle
import com.pumpwatch.app.engine.SignalEngine
import com.pumpwatch.app.engine.SignalParams
import com.pumpwatch.app.engine.SignalResult
import com.pumpwatch.app.store.PicksStore
import java.util.concurrent.TimeUnit

// ---------- ورکر پایش خودکار ----------

class MonitorWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var failures = 0
        for (mode in listOf("SPOT", "FUT")) {
            try {
                val params = PicksStore.loadParams(context, mode)
                val picks = BatchScanner.scan(mode, params)

                val oldKeys = PicksStore.loadToday(context, mode)
                    ?.picks
                    ?.filter { it.golden }
                    ?.map { it.coinId + it.side }
                    ?.toSet()
                    ?: emptySet()

                PicksStore.saveScan(context, mode, picks)

                val newGoldens = picks.filter {
                    it.golden && (it.coinId + it.side) !in oldKeys
                }
                if (newGoldens.isNotEmpty()) notify(newGoldens, mode)
            } catch (_: Exception) {
                failures++
            }
        }

        try {
            AutoOptimizer.optimize(context)
        } catch (_: Exception) { }

        return if (failures == 2) Result.retry() else Result.success()
    }

    // ---------- نوتیفیکیشن سیگنال طلایی ----------

    private fun notify(goldens: List<SignalResult>, mode: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    "pumpdump_golden",
                    "سیگنال‌های طلایی",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val title = if (mode == "SPOT") "🏆 سیگنال طلایی اسپات" else "⚡ سیگنال طلایی فیوچرز"
        val text = buildString {
            goldens.take(3).forEach { g ->
                append("${g.symbol} ${if (g.side == "PUMP") "🚀" else "🩸"} ${g.score} | ")
            }
        }

        val notification = NotificationCompat.Builder(context, "pumpdump_golden")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}

// ---------- بک‌تست خودکار (بهینه‌سازی روزانه) ----------

object AutoOptimizer {

    private const val KEY_LAST_OPT = "last_optimize"

    suspend fun optimize(context: Context) {
        val prefs = context.getSharedPreferences("pumpdump_picks", 0)
        val last = prefs.getLong(KEY_LAST_OPT, 0)
        if (System.currentTimeMillis() - last < 24 * 60 * 60 * 1000L) return

        val presets = listOf(
            SignalParams(goldenScore = 85.0, adxMin = 28.0, volumeMin = 2.0),
            SignalParams(),
            SignalParams(goldenScore = 75.0, adxMin = 22.0, volumeMin = 1.2)
        )

        for (mode in listOf("SPOT", "FUT")) {
            try {
                val markets = ScanClient.api.markets(perPage = 10, page = 1)
                var bestParams: SignalParams? = null
                var bestWin = -1.0
                var bestCount = 0

                for (p in presets) {
                    val (win, count) = evalPreset(markets.map { it.id }, p)
                    if (count >= 3 && win > bestWin) {
                        bestWin = win
                        bestCount = count
                        bestParams = p
                    }
                }
                if (bestParams != null && bestCount >= 3) {
                    PicksStore.saveParams(context, mode, bestParams)
                }
            } catch (_: Exception) { }
        }
        prefs.edit().putLong(KEY_LAST_OPT, System.currentTimeMillis()).apply()
    }

    private suspend fun evalPreset(
        coinIds: List<String>,
        params: SignalParams
    ): Pair<Double, Int> {
        var wins = 0
        var losses = 0

        for (id in coinIds) {
            try {
                val chart = ScanClient.api.chart(id, days = 60, interval = "hourly")
                val candles = toCandles(chart.prices, chart.volumes ?: emptyList())
                val n = candles.size
                if (n < 200) continue

                var i = 120
                while (i < n - 24) {
                    val sig = SignalEngine.analyze(
                        id, "TEST", "TEST",
                        candles.subList(0, i + 1), "SPOT", null, params
                    )
                    if (sig != null && sig.golden) {
                        val future = candles.subList(i + 1, minOf(n, i + 25))
                        val tp = if (sig.side == "PUMP")
                            future.any { it.close >= sig.target1 }
                        else
                            future.any { it.close <= sig.target1 }
                        val sl = if (sig.side == "PUMP")
                            future.any { it.close <= sig.stopLoss }
                        else
                            future.any { it.close >= sig.stopLoss }
                        if (tp && !sl) wins++ else if (sl) losses++
                        i += 24
                    } else {
                        i += 6
                    }
                }
            } catch (_: Exception) { }
        }

        val total = wins + losses
        val winRate = if (total > 0) wins * 100.0 / total else 0.0
        return winRate to total
    }

    private fun toCandles(
        prices: List<List<Double>>,
        volumes: List<List<Double>>
    ): List<Candle> {
        if (prices.size < 2) return emptyList()
        val vols = volumes.associate { it[0].toLong() to it[1] }
        val out = ArrayList<Candle>(prices.size)
        var prev = prices[0][1]
        for (p in prices) {
            val c = p[1]
            out.add(
                Candle(
                    time = p[0].toLong(),
                    open = prev,
                    high = maxOf(prev, c),
                    low = minOf(prev, c),
                    close = c,
                    volume = vols[p[0].toLong()] ?: 0.0
                )
            )
            prev = c
        }
        return out
    }
}

// ---------- زمان‌بندی پایش (هوشمند) ----------

object MonitorScheduler {

    fun start(context: Context) {
        try {
            val wm = WorkManager.getInstance(context)

            // ۱) پایش دوره‌ای هر ۳۰ دقیقه
            val periodic = PeriodicWorkRequestBuilder<MonitorWorker>(30, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                "pumpdump_monitor",
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )

            // ۲) اسکن فوری خودکار (اگه داده‌ها قدیمی‌تر از ۳۰ دقیقه باشن)
            val lastScan = maxOf(
                PicksStore.lastScan(context, "SPOT"),
                PicksStore.lastScan(context, "FUT")
            )
            val isStale = System.currentTimeMillis() - lastScan > 30 * 60 * 1000L
            if (isStale) {
                val oneTime = OneTimeWorkRequestBuilder<MonitorWorker>().build()
                wm.enqueueUniqueWork(
                    "pumpdump_immediate",
                    ExistingWorkPolicy.KEEP,
                    oneTime
                )
            }
        } catch (_: Exception) { }
    }
}
