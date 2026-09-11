package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * قدم ۷: موتور pure بک‌تست با metrics حرفه‌ای.
 * - Max Drawdown، Equity Curve، Win Rate، Profit Factor
 * - Slippage + Fee + Funding
 * - تفکیک In-Sample / Out-of-Sample
 * - no-lookahead: همهٔ محاسبات فقط از دادهٔ تا نقطهٔ ورود
 * - signalThreshold/scoreThreshold پارامتر اختیاری (برای تست‌پذیری؛ پیش‌فرض واقعی)
 */
object BacktestEngine {

    private const val FEE_RATE = 0.001 // 0.1% per side
    private const val SLIPPAGE_RATE = 0.0005 // 0.05% per side

    data class Trade(
        val symbol: String,
        val entryIndex: Int,
        val exitIndex: Int,
        val side: String,
        val entryPrice: Double,
        val exitPrice: Double,
        val pnl: Double,
        val result: String,
        val score: Int
    )

    data class BacktestMetrics(
        val totalTrades: Int,
        val wins: Int,
        val losses: Int,
        val expired: Int,
        val winRate: Double,
        val profitFactor: Double,
        val avgPnl: Double,
        val totalPnl: Double,
        val maxDrawdown: Double,
        val equityCurve: List<Double>,
        val inSampleMetrics: SampleMetrics?,
        val outOfSampleMetrics: SampleMetrics?
    )

    data class SampleMetrics(
        val trades: Int,
        val winRate: Double,
        val totalPnl: Double
    )

    fun runFutures(
        symbol: String,
        klines: List<List<Double>>, // [open, high, low, close, volume]
        evalLast: Int,
        hold: Int,
        fundingRate: Double = 0.0,
        signalThreshold: Int = 60
    ): Pair<List<Trade>, BacktestMetrics> {
        if (klines.size < 60) return emptyList<Trade>() to emptyMetrics()

        val highs = klines.map { it[1] }
        val lows = klines.map { it[2] }
        val closes = klines.map { it[3] }
        val volumes = klines.map { it[4] }

        val start = max(48, closes.size - evalLast)
        val end = closes.size - hold
        val trades = mutableListOf<Trade>()
        var prev = 0

        for (i in start until end) {
            val closesSlice = closes.subList(0, i + 1)
            val volumesSlice = volumes.subList(0, i + 1)
            val score = computeScore(closesSlice, volumesSlice)
            val freshBuy = score >= signalThreshold && prev < signalThreshold
            val freshSell = score <= -signalThreshold && prev > -signalThreshold

            if (freshBuy || freshSell) {
                val entryPrice = closes[i]
                val side = if (score > 0) "BUY" else "SELL"

                val entryReal = if (side == "BUY") {
                    entryPrice * (1 + FEE_RATE + SLIPPAGE_RATE)
                } else {
                    entryPrice * (1 - FEE_RATE - SLIPPAGE_RATE)
                }

                val atr = atrAt(closes, i)
                val risk = if (atr > 0) atr * 2.5 else entryPrice * 0.05
                val stop = if (side == "BUY") entryPrice - risk else entryPrice + risk
                val target = if (side == "BUY") entryPrice + risk * 1.5 else entryPrice - risk * 1.5

                var result = "EXP"
                var exitPrice = closes[min(i + hold, closes.size - 1)]
                var exitIndex = min(i + hold, closes.size - 1)

                for (j in (i + 1)..min(i + hold, closes.size - 1)) {
                    val hj = highs[j]
                    val lj = lows[j]
                    if (side == "BUY") {
                        val hitStop = lj <= stop
                        val hitTarget = hj >= target
                        when {
                            hitStop -> {
                                result = "LOSS"
                                exitPrice = stop
                                exitIndex = j
                                break
                            }
                            hitTarget -> {
                                result = "WIN"
                                exitPrice = target
                                exitIndex = j
                                break
                            }
                        }
                    } else {
                        val hitStop = hj >= stop
                        val hitTarget = lj <= target
                        when {
                            hitStop -> {
                                result = "LOSS"
                                exitPrice = stop
                                exitIndex = j
                                break
                            }
                            hitTarget -> {
                                result = "WIN"
                                exitPrice = target
                                exitIndex = j
                                break
                            }
                        }
                    }
                }

                val exitReal = if (side == "BUY") {
                    exitPrice * (1 - FEE_RATE - SLIPPAGE_RATE)
                } else {
                    exitPrice * (1 + FEE_RATE + SLIPPAGE_RATE)
                }

                val fundingCost = if (side == "BUY") {
                    -fundingRate * (exitIndex - i) / 8.0 * entryPrice
                } else {
                    fundingRate * (exitIndex - i) / 8.0 * entryPrice
                }

                val pnl = if (side == "BUY") {
                    ((exitReal - entryReal) / entryReal * 100) + (fundingCost / entryPrice * 100)
                } else {
                    ((entryReal - exitReal) / entryReal * 100) + (fundingCost / entryPrice * 100)
                }

                trades.add(Trade(symbol, i, exitIndex, side, entryPrice, exitPrice, pnl, result, score))
            }
            prev = score
        }

        return trades to computeMetrics(trades)
    }

    fun runSpot(
        symbol: String,
        klines: List<List<Double>>,
        holdDays: Int,
        scoreThreshold: Int = 70
    ): Pair<List<Trade>, BacktestMetrics> {
        if (klines.size < 210) return emptyList<Trade>() to emptyMetrics()

        val highs = klines.map { it[1] }
        val lows = klines.map { it[2] }
        val closes = klines.map { it[3] }
        val volumes = klines.map { it[4] }
        val e50s = emaSeries(closes, 50)

        val trades = mutableListOf<Trade>()
        var prevSig = false

        for (i in 200 until closes.size) {
            val closesSlice = closes.subList(0, i + 1)
            val volumesSlice = volumes.subList(0, i + 1)
            val weeklySlice = closesSlice.chunked(7).map { it.last() }
            var score = spotScore(closesSlice, volumesSlice, weeklySlice)

            val sixty = PumpDetector.analyzeSixtySecond(
                highs.subList(0, i + 1),
                lows.subList(0, i + 1),
                closesSlice
            )
            score += when (sixty.signal) {
                "BUY" -> 15
                "SELL" -> -15
                else -> 0
            }
            score = score.coerceIn(-100, 100)

            val wScore = weeklyScore(weeklySlice)
            val oScore = obvScore(closesSlice, volumesSlice)
            val sig = score >= scoreThreshold && wScore > 0 && oScore > 0

            if (sig && !prevSig) {
                val entryPrice = closes[i]
                val entryReal = entryPrice * (1 + FEE_RATE + SLIPPAGE_RATE)
                val atr = atrAt(closes, i)
                val atrPct = if (entryPrice > 0) atr / entryPrice * 100 else 10.0
                val stopPct = (atrPct * 2.5).coerceIn(7.0, 15.0)
                var trail = entryPrice * (1.0 - stopPct / 100.0)
                val target = entryPrice * (1.0 + stopPct * 2.0 / 100.0)

                var result = "EXP"
                var exitPrice = closes[min(i + holdDays, closes.size - 1)]
                var exitIndex = min(i + holdDays, closes.size - 1)

                for (j in (i + 1)..min(i + holdDays, closes.size - 1)) {
                    val hj = highs[j]
                    val lj = lows[j]
                    val hitStop = lj <= trail
                    val hitTarget = hj >= target
                    val hitE50 = closes[j] < e50s[j]
                    when {
                        hitStop -> {
                            result = "LOSS"
                            exitPrice = trail
                            exitIndex = j
                            break
                        }
                        hitTarget -> {
                            result = "WIN"
                            exitPrice = target
                            exitIndex = j
                            break
                        }
                        hitE50 -> {
                            exitPrice = closes[j]
                            exitIndex = j
                            result = if (exitPrice >= entryPrice) "WIN" else "LOSS"
                            break
                        }
                    }
                    val nt = closes[j] * (1.0 - stopPct / 100.0)
                    if (nt > trail) trail = nt
                }

                val exitReal = exitPrice * (1 - FEE_RATE - SLIPPAGE_RATE)
                val pnl = (exitReal - entryReal) / entryReal * 100

                trades.add(Trade(symbol, i, exitIndex, "BUY", entryPrice, exitPrice, pnl, result, score))
            }
            prevSig = sig
        }

        return trades to computeMetrics(trades)
    }

    private fun computeMetrics(trades: List<Trade>): BacktestMetrics {
        if (trades.isEmpty()) return emptyMetrics()

        val wins = trades.count { it.result == "WIN" }
        val losses = trades.count { it.result == "LOSS" }
        val expired = trades.count { it.result == "EXP" }
        val decided = wins + losses
        val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
        val avgPnl = trades.map { it.pnl }.average()
        val totalPnl = trades.sumOf { it.pnl }

        val totalWins = trades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val totalLosses = abs(trades.filter { it.pnl < 0 }.sumOf { it.pnl })
        val profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) Double.POSITIVE_INFINITY else 0.0

        val equityCurve = mutableListOf(100.0)
        var equity = 100.0
        trades.forEach { t ->
            equity *= (1 + t.pnl / 100.0)
            equityCurve.add(equity)
        }

        var maxDrawdown = 0.0
        var peak = equityCurve[0]
        for (e in equityCurve) {
            if (e > peak) peak = e
            val dd = (peak - e) / peak * 100.0
            if (dd > maxDrawdown) maxDrawdown = dd
        }

        val splitIndex = (trades.size * 0.7).toInt()
        val inSampleTrades = trades.subList(0, splitIndex)
        val outOfSampleTrades = trades.subList(splitIndex, trades.size)

        val inSampleMetrics = if (inSampleTrades.isNotEmpty()) {
            val inWins = inSampleTrades.count { it.result == "WIN" }
            val inLosses = inSampleTrades.count { it.result == "LOSS" }
            val inDecided = inWins + inLosses
            SampleMetrics(
                trades = inSampleTrades.size,
                winRate = if (inDecided > 0) inWins * 100.0 / inDecided else 0.0,
                totalPnl = inSampleTrades.sumOf { it.pnl }
            )
        } else null

        val outOfSampleMetrics = if (outOfSampleTrades.isNotEmpty()) {
            val outWins = outOfSampleTrades.count { it.result == "WIN" }
            val outLosses = outOfSampleTrades.count { it.result == "LOSS" }
            val outDecided = outWins + outLosses
            SampleMetrics(
                trades = outOfSampleTrades.size,
                winRate = if (outDecided > 0) outWins * 100.0 / outDecided else 0.0,
                totalPnl = outOfSampleTrades.sumOf { it.pnl }
            )
        } else null

        return BacktestMetrics(
            totalTrades = trades.size,
            wins = wins,
            losses = losses,
            expired = expired,
            winRate = winRate,
            profitFactor = profitFactor,
            avgPnl = avgPnl,
            totalPnl = totalPnl,
            maxDrawdown = maxDrawdown,
            equityCurve = equityCurve,
            inSampleMetrics = inSampleMetrics,
            outOfSampleMetrics = outOfSampleMetrics
        )
    }

    private fun emptyMetrics() = BacktestMetrics(
        totalTrades = 0, wins = 0, losses = 0, expired = 0,
        winRate = 0.0, profitFactor = 0.0, avgPnl = 0.0, totalPnl = 0.0,
        maxDrawdown = 0.0, equityCurve = listOf(100.0),
        inSampleMetrics = null, outOfSampleMetrics = null
    )

    // ---------- توابع کمکی ----------

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
        val macd = if (MacdCalc.macdUp(closes)) 25 else -25

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

    private fun spotScore(closes: List<Double>, volumes: List<Double>, weekly: List<Double>): Int {
        if (closes.size < 210) return 0
        val price = closes.last()
        val e50 = emaLast(closes, 50)
        val e200 = emaLast(closes, 200)
        var s = 0
        s += when {
            price > e50 && e50 > e200 -> 50
            price > e50 -> 25
            price < e50 && e50 < e200 -> -50
            else -> -25
        }
        s += if (MacdCalc.macdUp(closes)) 20 else -20
        val r = rsiOf(closes)
        s += when {
            r in 45.0..65.0 -> 15
            r < 35 -> 20
            r > 75 -> -25
            else -> 5
        }
        if (volumes.size > 40) {
            val recent = volumes.takeLast(20).average()
            val prior = volumes.dropLast(20).takeLast(20).average()
            if (prior > 0 && recent > prior * 1.2) s += 10
        }
        s += weeklyScore(weekly)
        s += obvScore(closes, volumes)
        return s.coerceIn(-100, 100)
    }

    private fun weeklyScore(weekly: List<Double>): Int {
        if (weekly.size < 25) return 0
        val w = weekly.last()
        val e10 = emaLast(weekly, 10)
        val e20 = emaLast(weekly, 20)
        return when {
            w > e10 && e10 > e20 -> 15
            w > e10 -> 8
            w < e10 && e10 < e20 -> -20
            else -> -8
        }
    }

    private fun obvScore(closes: List<Double>, volumes: List<Double>): Int {
        if (closes.size < 30) return 0
        var obv = 0.0
        val series = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            obv += when {
                closes[i] > closes[i - 1] -> volumes[i]
                closes[i] < closes[i - 1] -> -volumes[i]
                else -> 0.0
            }
            series.add(obv)
        }
        if (series.size < 21) return 0
        val now = series.last()
        val past = series[series.size - 21]
        return when {
            now > past * 1.05 -> 10
            now > past -> 5
            now < past * 0.95 -> -10
            else -> -5
        }
    }

    private fun atrAt(data: List<Double>, index: Int, period: Int = 14): Double {
        if (index < period) return 0.0
        var s = 0.0
        for (k in (index - period + 1)..index) s += abs(data[k] - data[k - 1])
        return s / period
    }

    private fun emaLast(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
        return ema
    }

    private fun emaSeries(data: List<Double>, period: Int): List<Double> {
        val out = MutableList(data.size) { 0.0 }
        if (data.size < period) return out
        var ema = data.take(period).average()
        out[period - 1] = ema
        val k = 2.0 / (period + 1)
        for (i in period until data.size) {
            ema = data[i] * k + ema * (1 - k)
            out[i] = ema
        }
        return out
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

    private fun bollinger(data: List<Double>, period: Int = 20): Pair<Double, Double> {
        if (data.size < period) return Pair(0.0, 0.0)
        val win = data.takeLast(period)
        val m = win.average()
        val sd = sqrt(win.map { (it - m) * (it - m) }.average())
        return Pair(m + 2 * sd, m - 2 * sd)
    }
}
