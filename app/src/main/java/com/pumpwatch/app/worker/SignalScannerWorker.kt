package com.pumpwatch.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.PumpDetector
import com.pumpwatch.app.engine.SignalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private val TOP_SYMBOLS = listOf(
    "BTC", "ETH", "BNB", "SOL", "XRP", "DOGE", "ADA", "TRX", "AVAX", "SHIB",
    "DOT", "LINK", "MATIC", "LTC", "BCH", "UNI", "ATOM", "ETC", "XLM", "FIL",
    "APT", "ARB", "OP", "NEAR", "ICP", "STX", "IMX", "INJ", "SUI", "SEI",
    "TIA", "ORDI", "RUNE", "FET", "GRT", "AAVE", "MKR", "SNX", "CRV", "LDO",
    "PEPE", "WIF", "BONK", "FLOKI", "TON", "JUP", "PYTH", "WLD", "RENDER", "TAO"
)

class SignalScannerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("pumpwatch_prefs", 0)
        val mode = prefs.getString("mode", "SPOT")

        val signalType = if (mode == "FUTURES") "FUT" else "SPOT"
        val symbolsToScan = TOP_SYMBOLS.take(if (mode == "FUTURES") 50 else 50)

        for (symbol in symbolsToScan) {
            try {
                val klines = BinanceClient.api.klines("${symbol}USDT", "1h", 100)
                val closes = klines.map { it[4].asDouble }
                val volumes = klines.map { it[5].asDouble }

                if (closes.size < 40) continue

                // محاسبه امتیاز پایه
                val baseScore = computeScore(closes, volumes)
                
                // تشخیص پامپ زودهنگام
                val pumpScore = PumpDetector.detectEarlyPump(closes, volumes)
                
                // محاسبه امتیاز نهایی با فیلترهای ضد سقف
                val finalScore = PumpDetector.calculateFinalScore(baseScore, closes, volumes, pumpScore)

                val funding = getFundingRate(symbol)
                val oiUp = getOiTrend(symbol)

                var adjustedScore = finalScore
                funding?.let {
                    if (it <= -0.0003) adjustedScore += 10
                    else if (it >= 0.0005) adjustedScore -= 10
                }
                oiUp?.let {
                    if (closes.last() >= closes.dropLast(1).last()) {
                        adjustedScore += if (it) 10 else -10
                    }
                }
                adjustedScore = adjustedScore.coerceIn(-100, 100)

                val threshold = if (mode == "FUTURES") 65 else 60

                if (adjustedScore >= threshold || adjustedScore <= -threshold) {
                    val price = closes.last()
                    val atr = calculateAtr(closes)
                    val risk = if (atr > 0) atr * 1.5 else price * 0.03
                    val side = if (adjustedScore > 0) "BUY" else "SELL"

                    val (stop, target) = if (mode == "FUTURES") {
                        val shortRisk = risk * 0.7
                        if (side == "BUY") {
                            price - shortRisk to price + shortRisk * 1.5
                        } else {
                            price + shortRisk to price - shortRisk * 1.5
                        }
                    } else {
                        if (side == "BUY") {
                            price - risk to price + risk * 1.5
                        } else {
                            price + risk to price - risk * 1.5
                        }
                    }

                    SignalLogger.log(
                        applicationContext,
                        LoggedSignal(
                            symbol = symbol,
                            side = side,
                            score = adjustedScore,
                            entry = price,
                            stop = stop,
                            target = target,
                            time = System.currentTimeMillis(),
                            mode = signalType
                        )
                    )

                    if (adjustedScore >= 75 || adjustedScore <= -75) {
                        sendNotification(symbol, adjustedScore, side, price, signalType, pumpScore)
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        Result.success()
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
        } catch (_: Exception) { null }
    }

    private suspend fun getOiTrend(symbol: String): Boolean? {
        return try {
            val h = BinanceFutures.api.oiHist("${symbol}USDT", "1h", 24)
            if (h.size >= 2) {
                val first = h.first().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                val lastV = h.last().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                lastV > first
            } else null
        } catch (_: Exception) { null }
    }

    private fun sendNotification(symbol: String, score: Int, side: String, price: Double, mode: String, pumpScore: Int) {
        val channelId = "signal_alerts"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "سیگنال‌های قوی",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val emoji = if (score > 0) "🟢" else "🔴"
        val action = if (side == "BUY") "خرید قوی" else "فروش قوی"
        val modeText = if (mode == "FUT") " فیوچرز" else "🏦 اسپات"
        val pumpText = if (pumpScore >= 60) " پامپ" else ""

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $action: $symbol $modeText$pumpText")
            .setContentText("امتیاز: $score/100 | پامپ: $pumpScore/100 | قیمت: $$price")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify("${symbol}_$mode".hashCode(), notification)
    }
}
