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
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.ui.PaperState
import com.pumpwatch.app.ui.PaperTrade
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

    suspend fun scan(ctx: Context, symbols: List<String>, mode: String): ScanReport {
        return if (mode == "FUTURES") scanFutures(ctx, symbols) else scanSpot(ctx, symbols)
    }

    private fun openPaperTrade(
        ctx: Context, symbol: String, entry: Double, stop: Double,
        target: Double, stopPct: Double, score: Int
    ) {
        try {
            val prefs = ctx.getSharedPreferences("pumpwatch_prefs", 0)
            // اصلاح P0-2: پیش‌فرض false
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

    private suspend fun scanFutures(ctx: Context, symbols: List<String>): ScanReport {
        val lines = mutableListOf<String>()
        var signalCount = 0

        for (symbol in symbols) {
            try {
                val klines = BinanceClient.api.klines("${symbol}USDT", "1h", 100)
                if (klines.size < 100) {
                    lines.add("$symbol: کندل کم (${klines.size})")
                    continue
                }
                
                // تبدیل به فرمت Candle برای UnifiedSignalEngine
                val candles = klines.mapIndexed { index, k -> 
                    Candle(time = 0L, open = k[1].asDouble, high = k[2].asDouble, low = k[3].asDouble, close = k[4].asDouble, volume = k[5].asDouble)
                }

                val funding = try { BinanceFutures.api.premiumIndex("${symbol}USDT").lastFundingRate?.toDoubleOrNull() } catch (_: Exception) { null }

                val signal = UnifiedSignalEngine.analyze(
                    coinId = symbol, symbol = symbol, name = symbol,
                    candles1h = candles, mode = "FUT", funding = funding,
                    params = UnifiedSignalParams(minScore = 70)
                )

                if (signal != null && signal.side != "NONE") {
                    lines.add("$symbol | ${signal.side} | Score: ${signal.score} | ${signal.reasons.firstOrNull() ?: "Setup فعال"}")
                    
                    val logged = SignalLogger.log(
                        ctx,
                        LoggedSignal(
                            symbol = symbol, side = signal.side, score = signal.score,
                            entry = signal.entry, stop = signal.stopLoss, target = signal.target1,
                            time = System.currentTimeMillis(), mode = "FUT"
                        )
                    )
                    
                    if (logged) {
                        signalCount++
                        if (signal.side == "PUMP") {
                            openPaperTrade(ctx, symbol, signal.entry, signal.stopLoss, signal.target1, (signal.entry - signal.stopLoss)/signal.entry * 100, signal.score)
                        }
                    }

                    if (signal.golden) {
                        sendNotification(ctx, symbol, signal.score, signal.side, signal.price, "FUT", signal.reasons)
                    }
                } else {
                    lines.add("$symbol | NONE")
                }
            } catch (e: Exception) {
                lines.add("$symbol خطا: ${e.message}")
            }
        }
        return ScanReport(lines, signalCount)
    }

    private suspend fun scanSpot(ctx: Context, symbols: List<String>): ScanReport {
        val lines = mutableListOf<String>()
        var signalCount = 0

        for (symbol in symbols) {
            try {
                val klines = BinanceClient.api.klines("${symbol}USDT", "1d", 300)
                if (klines.size < 100) {
                    lines.add("$symbol: تاریخچه کم (${klines.size})")
                    continue
                }
                
                val candles = klines.mapIndexed { index, k -> 
                    Candle(time = 0L, open = k[1].asDouble, high = k[2].asDouble, low = k[3].asDouble, close = k[4].asDouble, volume = k[5].asDouble)
                }

                val signal = UnifiedSignalEngine.analyze(
                    coinId = symbol, symbol = symbol, name = symbol,
                    candles1h = candles, mode = "SPOT", funding = null,
                    params = UnifiedSignalParams(minScore = 70)
                )

                if (signal != null && signal.side != "NONE") {
                    lines.add("$symbol | ${signal.side} | Score: ${signal.score} | ${signal.reasons.firstOrNull() ?: "Setup فعال"}")
                    
                    val logged = SignalLogger.log(
                        ctx,
                        LoggedSignal(
                            symbol = symbol, side = signal.side, score = signal.score,
                            entry = signal.entry, stop = signal.stopLoss, target = signal.target1,
                            time = System.currentTimeMillis(), mode = "SPOT"
                        )
                    )
                    
                    if (logged) {
                        signalCount++
                        openPaperTrade(ctx, symbol, signal.entry, signal.stopLoss, signal.target1, (signal.entry - signal.stopLoss)/signal.entry * 100, signal.score)
                    }

                    if (signal.golden) {
                        sendNotification(ctx, symbol, signal.score, signal.side, signal.price, "SPOT", signal.reasons)
                    }
                } else {
                    lines.add("$symbol | NONE")
                }
            } catch (e: Exception) {
                lines.add("$symbol خطا: ${e.message}")
            }
        }
        return ScanReport(lines, signalCount)
    }

    private fun sendNotification(
        ctx: Context, symbol: String, score: Int, side: String, price: Double,
        mode: String, reasons: List<String>
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
        val modeText = if (mode == "FUT") "⚡ فیوچرز" else "🏦 اسپات"

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $action: $symbol $modeText")
            .setContentText("امتیاز: $score/100 | قیمت: $$price")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
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
