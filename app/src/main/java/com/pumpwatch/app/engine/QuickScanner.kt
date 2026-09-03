package com.pumpwatch.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pumpwatch.app.MainActivity
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.BinanceFutures
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

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
        val signalType = if (mode == "FUTURES") "FUT" else "SPOT"
        val threshold = 60
        val lines = mutableListOf<String>()
        var signalCount = 0

        for (symbol in symbols) {
            try {
                val klines = BinanceClient.api.klines("${symbol}USDT", "1h", 100)
                if (klines.size < 60) {
                    lines.add("$symbol: کندل کم (${klines.size})")
                    continue
                }
                val opens = klines.map { it[1].asDouble }
                val highs = klines.map { it[2].asDouble }
                val lows = klines.map { it[3].asDouble }
                val closes = klines.map { it[4].asDouble }
                val volumes = klines.map { it[5].asDouble }

                val baseScore = computeScore(closes, volumes)
                val pumpScore = PumpDetector.detectEarlyPump(closes, volumes)
                val sixty = PumpDetector.analyzeSixtySecond(highs, lows, closes)
                val zigzag = PumpDetector.analyzeZigZag(highs, lows, closes)
                val of = PumpDetector.analyzeOrderFlow(opens, highs, lows, closes, volumes)

                val finalScore = PumpDetector.calculateFinalScore(
                    baseScore, closes, volumes, pumpScore, sixty, zigzag, of
                )

                val funding = getFundingRate(symbol)
                val oiUp = getOiTrend(symbol)

                var adjusted = finalScore
                funding?.let {
                    if (it <= -0.0003) adjusted += 10
                    else if (it >= 0.0005) adjusted -= 10
                }
                oiUp?.let {
                    if (closes.last() >= closes.dropLast(1).last()) {
                        adjusted += if (it) 10 else -10
                    }
                }
                adjusted = adjusted.coerceIn(-100, 100)

                lines.add(
                    "$symbol | پایه:$baseScore پامپ:$pumpScore 60s:${sixty.signal} zz:${zigzag.direction} of:${of.cvdScore} => $adjusted"
                )

                // اسپات = فقط خرید | فیوچرز = خرید + فروش
                val isBuySignal = adjusted >= threshold
                val isSellSignal = adjusted <= -threshold && mode == "FUTURES"

                if (isBuySignal || isSellSignal) {
                    val price = closes.last()
                    val atr = calculateAtr(closes)
                    val risk = if (atr > 0) atr * 1.5 else price * 0.03
                    val side = if (adjusted > 0) "BUY" else "SELL"

                    val (stop, target) = if (mode == "FUTURES") {
                        val sr = risk * 0.7
                        if (side == "BUY") price - sr to price + sr * 1.5
                        else price + sr to price - sr * 1.5
                    } else {
                        if (side == "BUY") price - risk to price + risk * 1.5
                        else price + risk to price - risk * 1.5
                    }

                    val logged = SignalLogger.log(
                        ctx,
                        LoggedSignal(
                            symbol = symbol,
                            side = side,
                            score = adjusted,
                            entry = price,
                            stop = stop,
                            target = target,
                            time = System.currentTimeMillis(),
                            mode = signalType
                        )
                    )
                    if (logged) signalCount++

                    if (adjusted >= 75 || adjusted <= -75) {
                        sendNotification(ctx, symbol, adjusted, side, price, signalType, pumpScore, sixty, zigzag, of)
                    }
                }
            } catch (e: Exception) {
                lines.add("$symbol خطا: ${e.message}")
            }
        }

        return ScanReport(lines, signalCount)
    }

    private fun emaLast(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
        return ema
    }

    private fun rsiOf(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 50.0
        var g = 0.0
        var l = 0.0
        for (i in 1..period) {
            val d = data[i] - data[i - 1]
            if (d > 0) g += d else l -= d
        }
        var ag = g / period
        var al = l / period
        for (i in period + 1 until data.size) {
            val d = data[i] - data[i - 1]
            ag = (ag * (period - 1) + max(d, 0.0)) / period
            al = (al * (period - 1) + max(-d, 0.0)) / period
        }
        if (al == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + ag / al)
    }

    private fun macdUp(data: List<Double>): Boolean {
        if (data.size < 35) return false
        val prev = data.dropLast(1)
        return (emaLast(data, 12) - emaLast(data, 26)) > (emaLast(prev, 12) - emaLast(prev, 26))
    }

    private fun bollinger(data: List<Double>, period: Int = 20): Pair<Double, Double> {
        if (data.size < period) return Pair(0.0, 0.0)
        val win = data.takeLast(period)
        val m = win.average()
        val sd = sqrt(win.map { (it - m) * (it - m) }.average())
        return Pair(m + 2 * sd, m - 2 * sd)
    }

    private fun calculateAtr(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 0.0
        var s = 0.0
        for (i in 1..period) s += abs(data[i] - data[i - 1])
        return s / period
    }

    private fun computeScore(closes: List<Double>, volumes: List<Double>): Int {
        val price = closes.last()
        val e20 = emaLast(closes, 20)
        val e50 = emaLast(closes, 50)
        val ema = when {
            price > e20 && e20 > e50 -> 25
            price < e20 && e20 < e50 -> -25
            else -> 0
        }
        val r = rsiOf(closes)
        val rsi = when {
            r <= 35 -> 20
            r >= 65 -> -20
            else -> 0
        }
        val macd = if (macdUp(closes)) 25 else -25

        val (bu, bl) = bollinger(closes)
        val prev = closes.dropLast(1)
        val (pbu, pbl) = bollinger(prev)
        val c = price
        val pc = prev.lastOrNull() ?: c
        val boll = when {
            pc <= pbl && c > bl -> 15
            pc >= pbu && c < bu -> -15
            c <= bl * 1.01 -> 15
            c >= bu * 0.99 -> -15
            c > (bu + bl) / 2 && macd == 25 -> 15
            c < (bu + bl) / 2 && macd == -25 -> -15
            else -> 0
        }

        val vol = if (volumes.size > 15) {
            val lv = volumes.last()
            val av = volumes.dropLast(1).takeLast(14).average()
            val bd = if (c >= pc) 15 else -15
            if (av > 0 && lv >= 1.5 * av) bd else 0
        } else 0

        return (ema + rsi + macd + vol + boll).coerceIn(-100, 100)
    }

    private suspend fun getFundingRate(symbol: String): Double? {
        return try {
            BinanceFutures.api.premiumIndex("${symbol}USDT").lastFundingRate?.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getOiTrend(symbol: String): Boolean? {
        return try {
            val h = BinanceFutures.api.oiHist("${symbol}USDT", "1h", 24)
            if (h.size >= 2) {
                val first = h.first().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                val lastV = h.last().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                lastV > first
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun sendNotification(
        ctx: Context,
        symbol: String,
        score: Int,
        side: String,
        price: Double,
        mode: String,
        pumpScore: Int,
        sixty: SixtySecondResult,
        zigzag: ZigZagResult,
        orderFlow: OrderFlowResult
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

        val emoji = if (score > 0) "🟢" else "🔴"
        val action = if (side == "BUY") "خرید قوی" else "فروش قوی"
        val modeText = if (mode == "FUT") "⚡ فیوچرز" else "🏦 اسپات"

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $action: $symbol $modeText")
            .setContentText("امتیاز: $score/100 | پامپ: $pumpScore | قیمت: $$price")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "امتیاز: $score/100\n" +
                            "Sixty Second: ${sixty.signal} (${sixty.strength}%)\n" +
                            "ZigZag: ${zigzag.direction}\n" +
                            "Order Flow: ${orderFlow.cvdScore}\n" +
                            "پامپ: $pumpScore/100\n" +
                            "قیمت: $$price"
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify("${symbol}_$mode".hashCode(), notification)
    }
}
