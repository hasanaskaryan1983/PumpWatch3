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
import kotlin.math.abs
import kotlin.math.max
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
            // ✅ اصلاح P0-2: پیش‌فرض false — فقط با opt-in صریح کاربر فعال می‌شود
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
                if (klines.size < 100) continue
                
                val candles = klines.mapIndexed { idx, k ->
                    Candle(time = 0L, open = k[1].asDouble, high = k[2].asDouble, low = k[3].asDouble, close = k[4].asDouble, volume = k[5].asDouble)
                }

                val funding = try { BinanceFutures.api.premiumIndex("${symbol}USDT").lastFundingRate?.toDoubleOrNull() } catch (_: Exception) { null }
                
                val signal = UnifiedSignalEngine.analyze(
                    coinId = symbol, symbol = symbol, name = symbol,
                    candles1h = candles, mode = "FUT", funding = funding
                )

                if (signal != null && signal.side != "NONE") {
                    lines.add("$symbol | ${signal.side} | Score: ${signal.score} | ${signal.reasons.firstOrNull() ?: ""}")
                    
                    if (signal.score >= 70) {
                        val logged = SignalLogger.log(
                            ctx, LoggedSignal(
                                symbol = symbol, side = if (signal.side == "PUMP") "BUY" else "SELL", 
                                score = signal.score, entry = signal.entry, stop = signal.stopLoss, 
                                target = signal.target1, time = System.currentTimeMillis(), mode = "FUT"
                            )
                        )
                        if (logged) {
                            signalCount++
                            if (signal.side == "PUMP") {
                                openPaperTrade(ctx, symbol, signal.entry, signal.stopLoss, signal.target1, (signal.entry - signal.stopLoss)/signal.entry * 100, signal.score)
                            }
                        }
                    }
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
                if (klines.size < 100) continue
                
                val candles = klines.mapIndexed { idx, k ->
                    Candle(time = 0L, open = k[1].asDouble, high = k[2].asDouble, low = k[3].asDouble, close = k[4].asDouble, volume = k[5].asDouble)
                }

                val signal = UnifiedSignalEngine.analyze(
                    coinId = symbol, symbol = symbol, name = symbol,
                    candles1h = candles, mode = "SPOT", funding = null
                )

                if (signal != null && signal.side == "PUMP") {
                    lines.add("$symbol | BUY | Score: ${signal.score} | ${signal.reasons.firstOrNull() ?: ""}")
                    
                    if (signal.score >= 70) {
                        val logged = SignalLogger.log(
                            ctx, LoggedSignal(
                                symbol = symbol, side = "BUY", score = signal.score, 
                                entry = signal.entry, stop = signal.stopLoss, target = signal.target1, 
                                time = System.currentTimeMillis(), mode = "SPOT"
                            )
                        )
                        if (logged) {
                            signalCount++
                            openPaperTrade(ctx, symbol, signal.entry, signal.stopLoss, signal.target1, (signal.entry - signal.stopLoss)/signal.entry * 100, signal.score)
                        }
                    }
                }
            } catch (e: Exception) {
                lines.add("$symbol خطا: ${e.message}")
            }
        }
        return ScanReport(lines, signalCount)
    }
}
