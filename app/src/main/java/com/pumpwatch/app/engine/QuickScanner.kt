package com.pumpwatch.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.pumpwatch.app.MainActivity
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.data.KlineCache
import com.pumpwatch.app.ui.PaperState
import com.pumpwatch.app.ui.PaperTrade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

data class ScanReport(
    val lines: List<String>,
    val signalCount: Int
)

object QuickScanner {

    val TOP_SYMBOLS = listOf(
        "BTC", "ETH", "BNB", "SOL", "XRP", "DOGE", "ADA", "TRX", "AVAX", "SHIB",
        "DOT", "LINK", "MATIC", "LTC", "BCH", "UNI", "ATOM", "ETC", "XLM", "FIL",
        "APT", "ARB", "OP", "NEAR", "ICP", "STX", "IMX", "INJ", "SUI", "SEI",
        "TIA", "ORDI", "RUNE", "FET", "GRT", "AAVE", "MKR", "SNX", "CRV", "LDO",
        "PEPE", "WIF", "BONK", "FLOKI", "TON", "JUP", "PYTH", "WLD", "RENDER", "TAO"
    )

    // P0-6: فرمت نمایش زمان بسته شدن کندل (HH:mm) برای Notification و لاگ
    private val closeTimeFmt = SimpleDateFormat("HH:mm", Locale.US)

    // 🚀 P1-2: تعداد هم‌روندی — ۵ برای Binance ایمن است (limit ~۱۲۰۰ weight/min)
    private const val PARALLELISM = 5
    private const val CHUNK_DELAY_MS = 200L

    // 🚀 P1-4: آستانهٔ تشخیص «chunk شبکه‌ای» — اگر chunk سریع‌تر از این تمام شد
    // یعنی از KlineCache آمده و delay لازم نیست
    private const val CACHED_CHUNK_THRESHOLD_MS = 100L

    suspend fun scan(ctx: Context, symbols: List<String>, mode: String): ScanReport {
        return if (mode == "FUTURES") scanFutures(ctx, symbols) else scanSpot(ctx, symbols)
    }

    private fun openPaperTrade(
        ctx: Context, symbol: String, entry: Double, stop: Double,
        target: Double, stopPct: Double, score: Int
    ) {
        try {
            val prefs = ctx.getSharedPreferences("pumpwatch_prefs", 0)
            if (!prefs.getBoolean("paper_bot", false)) return
            val gson = Gson()
            val state = try {
                val json = prefs.getString("paper_state", "") ?: ""
                if (json.isEmpty()) PaperState() else gson.fromJson(json, PaperState::class.java) ?: PaperState()
            } catch (_: Exception) { PaperState() }

            if (state.trades.any { it.status == "OPEN" && it.symbol == symbol }) return
            val size = min(50.0, state.cash * 0.1)
            if (size < 10 || entry <= 0) return

            state.trades.add(
                PaperTrade(
                    symbol = symbol, tier = "هشدار 🔔", entry = entry, sizeUsd = size,
                    qty = size / entry, price = entry, stop = stop, stopPct = stopPct,
                    target = target, openTime = System.currentTimeMillis(),
                    score = score, trailing = true
                )
            )
            state.cash -= size
            prefs.edit().putString("paper_state", gson.toJson(state)).apply()
        } catch (_: Exception) { }
    }

    /**
     * 🚀 P1-2 + P1-4: اسکن موازی با delay هوشمند.
     *
     * - نمادها chunkهای ۵تایی می‌شوند و هم‌زمان روی Dispatchers.IO اجرا می‌شوند
     * - P1-4: زمان هر chunk اندازه‌گیری می‌شود؛ فقط اگر chunk کند بوده
     *   (≥۱۰۰ms = call شبکه واقعی) delay اعمال می‌شود؛ chunkهای cache‌ای بدون صبر رد می‌شوند
     * - side-effectها (لاگ/اعلان/معامله کاغذی) بعداً sequential اجرا می‌شوند (thread-safety)
     *
     * منطق P0-6 (Candle.time=k[6]، candleCloseTs) و P1-1 (KlineCache) دست‌نخورده.
     */
    private suspend fun scanFutures(ctx: Context, symbols: List<String>): ScanReport {
        data class ScanLine(val text: String, val signal: UnifiedSignalResult?)

        val allLines = coroutineScope {
            symbols.chunked(PARALLELISM).flatMap { chunk ->
                // 🚀 P1-4: اندازه‌گیری زمان chunk برای تصمیم هوشمند دربارهٔ delay
                val chunkStart = System.currentTimeMillis()
                val deferreds = chunk.map { symbol ->
                    async(Dispatchers.IO) {
                        try {
                            val klines = KlineCache.klines("${symbol}USDT", "1h", 100)
                            if (klines.size < 100) {
                                return@async ScanLine("$symbol: کندل کم (${klines.size})", null)
                            }

                            val candles = klines.map { k ->
                                Candle(
                                    time = k[6].asLong,
                                    open = k[1].asDouble,
                                    high = k[2].asDouble,
                                    low = k[3].asDouble,
                                    close = k[4].asDouble,
                                    volume = k[5].asDouble
                                )
                            }

                            val funding = try {
                                BinanceFutures.api.premiumIndex("${symbol}USDT").lastFundingRate?.toDoubleOrNull()
                            } catch (_: Exception) { null }

                            val signal = UnifiedSignalEngine.analyze(
                                coinId = symbol, symbol = symbol, name = symbol,
                                candles1h = candles, mode = "FUT", funding = funding,
                                params = UnifiedSignalParams(minScore = 70)
                            )

                            ScanLine(
                                text = if (signal != null && signal.side != "NONE") {
                                    val closeText = if (signal.candleCloseTs > 0L) {
                                        closeTimeFmt.format(Date(signal.candleCloseTs))
                                    } else "?"
                                    "$symbol | ${signal.side} | Score: ${signal.score} | $closeText | ${signal.reasons.firstOrNull() ?: "Setup فعال"}"
                                } else {
                                    "$symbol | NONE"
                                },
                                signal = signal
                            )
                        } catch (e: Exception) {
                            ScanLine("$symbol خطا: ${e.message}", null)
                        }
                    }
                }
                val results = deferreds.awaitAll()
                val chunkElapsed = System.currentTimeMillis() - chunkStart
                if (chunkElapsed >= CACHED_CHUNK_THRESHOLD_MS) {
                    delay(CHUNK_DELAY_MS)
                }
                results
            }
        }

        // مرحلهٔ دوم: side-effects در thread فراخواننده (بدون race روی SharedPreferences)
        val lines = mutableListOf<String>()
        var signalCount = 0
        for (line in allLines) {
            lines.add(line.text)
            val signal = line.signal
            if (signal != null && signal.side != "NONE") {
                val logged = SignalLogger.log(
                    ctx,
                    LoggedSignal(
                        symbol = signal.symbol, side = signal.side, score = signal.score,
                        entry = signal.entry, stop = signal.stopLoss, target = signal.target1,
                        time = if (signal.candleCloseTs > 0L) signal.candleCloseTs else System.currentTimeMillis(),
                        mode = "FUT"
                    )
                )
                if (logged) {
                    signalCount++
                    if (signal.side == "PUMP") {
                        openPaperTrade(
                            ctx, signal.symbol, signal.entry, signal.stopLoss, signal.target1,
                            (signal.entry - signal.stopLoss) / signal.entry * 100, signal.score
                        )
                    }
                }
                if (signal.golden) {
                    sendNotification(ctx, signal.symbol, signal.score, signal.side, signal.price, "FUT", signal.reasons, signal.candleCloseTs)
                }
            }
        }

        return ScanReport(lines, signalCount)
    }

    /**
     * 🚀 P1-2 + P1-4: نسخهٔ parallel و هوشمند scanSpot — همان الگوی scanFutures.
     */
    private suspend fun scanSpot(ctx: Context, symbols: List<String>): ScanReport {
        data class ScanLine(val text: String, val signal: UnifiedSignalResult?)

        val allLines = coroutineScope {
            symbols.chunked(PARALLELISM).flatMap { chunk ->
                val chunkStart = System.currentTimeMillis()
                val deferreds = chunk.map { symbol ->
                    async(Dispatchers.IO) {
                        try {
                            val klines = KlineCache.klines("${symbol}USDT", "1d", 300)
                            if (klines.size < 100) {
                                return@async ScanLine("$symbol: تاریخچه کم (${klines.size})", null)
                            }

                            val candles = klines.map { k ->
                                Candle(
                                    time = k[6].asLong,
                                    open = k[1].asDouble,
                                    high = k[2].asDouble,
                                    low = k[3].asDouble,
                                    close = k[4].asDouble,
                                    volume = k[5].asDouble
                                )
                            }

                            val signal = UnifiedSignalEngine.analyze(
                                coinId = symbol, symbol = symbol, name = symbol,
                                candles1h = candles, mode = "SPOT", funding = null,
                                params = UnifiedSignalParams(minScore = 70)
                            )

                            ScanLine(
                                text = if (signal != null && signal.side != "NONE") {
                                    val closeText = if (signal.candleCloseTs > 0L) {
                                        closeTimeFmt.format(Date(signal.candleCloseTs))
                                    } else "?"
                                    "$symbol | ${signal.side} | Score: ${signal.score} | $closeText | ${signal.reasons.firstOrNull() ?: "Setup فعال"}"
                                } else {
                                    "$symbol | NONE"
                                },
                                signal = signal
                            )
                        } catch (e: Exception) {
                            ScanLine("$symbol خطا: ${e.message}", null)
                        }
                    }
                }
                val results = deferreds.awaitAll()
                val chunkElapsed = System.currentTimeMillis() - chunkStart
                if (chunkElapsed >= CACHED_CHUNK_THRESHOLD_MS) {
                    delay(CHUNK_DELAY_MS)
                }
                results
            }
        }

        val lines = mutableListOf<String>()
        var signalCount = 0
        for (line in allLines) {
            lines.add(line.text)
            val signal = line.signal
            if (signal != null && signal.side != "NONE") {
                val logged = SignalLogger.log(
                    ctx,
                    LoggedSignal(
                        symbol = signal.symbol, side = signal.side, score = signal.score,
                        entry = signal.entry, stop = signal.stopLoss, target = signal.target1,
                        time = if (signal.candleCloseTs > 0L) signal.candleCloseTs else System.currentTimeMillis(),
                        mode = "SPOT"
                    )
                )
                if (logged) {
                    signalCount++
                    openPaperTrade(
                        ctx, signal.symbol, signal.entry, signal.stopLoss, signal.target1,
                        (signal.entry - signal.stopLoss) / signal.entry * 100, signal.score
                    )
                }
                if (signal.golden) {
                    sendNotification(ctx, signal.symbol, signal.score, signal.side, signal.price, "SPOT", signal.reasons, signal.candleCloseTs)
                }
            }
        }

        return ScanReport(lines, signalCount)
    }

    private fun sendNotification(
        ctx: Context, symbol: String, score: Int, side: String, price: Double,
        mode: String, reasons: List<String>, candleCloseTs: Long = 0L
    ) {
        val channelId = "signal_alerts"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "سیگنال‌های قوی", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(ctx, 0, intent, pendingFlags)

        val emoji = if (side == "PUMP") "🟢" else "🔴"
        val action = if (side == "PUMP") "خرید قوی" else "فروش قوی"
        val modeText = if (mode == "FUT") "⚡ فیوچرز" else " اسپات"

        // P0-6: زمان بسته شدن کندل در Notification
        val closeText = if (candleCloseTs > 0L) {
            "کندل: ${closeTimeFmt.format(Date(candleCloseTs))}\n"
        } else ""

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $action: $symbol $modeText")
            .setContentText("امتیاز: $score/100 | قیمت: $$price")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    closeText +
                    "امتیاز: $score/100\n" +
                    reasons.joinToString("\n") + "\nقیمت: $$price"
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify("${symbol}_$mode".hashCode(), notification)
    }
}
