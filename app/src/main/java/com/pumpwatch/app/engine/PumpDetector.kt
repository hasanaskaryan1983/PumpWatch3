package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * نتیجه تحلیل Sixty Second Trades
 */
data class SixtySecondResult(
    val signal: String, // "BUY", "SELL", "NEUTRAL"
    val strength: Int,  // 0-100
    val adx: Double,
    val stochK: Double,
    val stochD: Double,
    val isFractalHigh: Boolean,
    val isFractalLow: Boolean
)

/**
 * نتیجه تحلیل Zig Zag Hist
 */
data class ZigZagResult(
    val direction: String, // "UP", "DOWN", "REVERSING_UP", "REVERSING_DOWN"
    val lastSwingHigh: Double,
    val lastSwingLow: Double,
    val currentTrend: String, // "BULLISH", "BEARISH"
    val reversalSignal: Boolean,
    val histValue: Double
)

/**
 * نتیجه تحلیل Order Flow (CVD)
 */
data class OrderFlowResult(
    val cvdScore: Int,       // -100 to +100
    val buyVolume: Double,
    val sellVolume: Double,
    val delta: Double,       // buy - sell
    val isAccumulation: Boolean,
    val isDistribution: Boolean
)

object PumpDetector {

    // ==================== SIXTY SECOND TRADES ====================
    fun analyzeSixtySecond(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>
    ): SixtySecondResult {
        if (closes.size < 20) {
            return SixtySecondResult("NEUTRAL", 0, 0.0, 50.0, 50.0, false, false)
        }

        val adx = calculateADX(highs, lows, closes, 14)
        val (stochK, stochD) = calculateStochastic(highs, lows, closes, 14, 3)
        val isFractalHigh = detectFractalHigh(highs, closes.size - 1, 2)
        val isFractalLow = detectFractalLow(lows, closes.size - 1, 2)

        var signal = "NEUTRAL"
        var strength = 0

        if (stochK < 20 && adx > 20 && isFractalLow) {
            signal = "BUY"
            strength = ((20 - stochK) * 2 + (adx - 20) + 30).toInt().coerceIn(0, 100)
        } else if (stochK > 80 && adx > 20 && isFractalHigh) {
            signal = "SELL"
            strength = ((stochK - 80) * 2 + (adx - 20) + 30).toInt().coerceIn(0, 100)
        } else if (adx > 25) {
            strength = (adx * 2).toInt().coerceIn(0, 100)
        }

        return SixtySecondResult(signal, strength, adx, stochK, stochD, isFractalHigh, isFractalLow)
    }

    /**
     * ✅ اصلاح P0-4: ADX واقعی (Wilder's smoothing)
     * قبلاً فقط یک نقطهٔ DX لحظه‌ای محاسبه می‌شد.
     * اکنون: ساخت List<Candle> موقت و delegate به Indicators.adx که Wilder's smoothing صحیح دارد.
     * برای size < period*2 (یعنی < 28)، صفر برمی‌گرداند → signal = NEUTRAL.
     */
    private fun calculateADX(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int
    ): Double {
        if (closes.size < period * 2) return 0.0

        val candles = closes.indices.map { i ->
            Candle(
                time = i.toLong(),
                open = closes[i],
                high = highs[i],
                low = lows[i],
                close = closes[i],
                volume = 0.0
            )
        }
        return Indicators.adx(candles, period)
    }

    /**
     * ✅ اصلاح P0-4: Stochastic استاندارد
     * - %K: (close - lowestLow) / (highestHigh - lowestLow) * 100 روی پنجرهٔ kPeriod
     * - %D: SMA(dPeriod) از سری %K (قبلاً حلقهٔ step‌دار غیراستاندارد بود)
     */
    private fun calculateStochastic(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int,
        dPeriod: Int
    ): Pair<Double, Double> {
        if (closes.size < kPeriod) return Pair(50.0, 50.0)

        // محاسبه %K برای آخرین max(dPeriod, 1) نقطه (تا میانگین SMA درست شود)
        val kValues = mutableListOf<Double>()
        val startIdx = max(kPeriod - 1, closes.size - dPeriod)
        for (i in startIdx until closes.size) {
            val windowHigh = highs.subList(i - kPeriod + 1, i + 1).maxOrNull() ?: closes[i]
            val windowLow = lows.subList(i - kPeriod + 1, i + 1).minOrNull() ?: closes[i]
            val k = if (windowHigh != windowLow) {
                (closes[i] - windowLow) / (windowHigh - windowLow) * 100
            } else 50.0
            kValues.add(k)
        }

        val currentK = kValues.lastOrNull() ?: 50.0
        val currentD = if (kValues.size >= dPeriod) {
            kValues.takeLast(dPeriod).average()
        } else {
            currentK
        }

        return Pair(currentK, currentD)
    }

    private fun detectFractalHigh(highs: List<Double>, index: Int, bars: Int): Boolean {
        if (index < bars || index >= highs.size - bars) return false
        val current = highs[index]
        for (i in 1..bars) {
            if (highs[index - i] >= current || highs[index + i] >= current) return false
        }
        return true
    }

    private fun detectFractalLow(lows: List<Double>, index: Int, bars: Int): Boolean {
        if (index < bars || index >= lows.size - bars) return false
        val current = lows[index]
        for (i in 1..bars) {
            if (lows[index - i] <= current || lows[index + i] <= current) return false
        }
        return true
    }

    // ==================== ZIG ZAG HIST ====================
    // ⚠️ این تابع کد مرده است (هیچ‌جا استفاده نمی‌شود) — برای Commit پاک‌سازی بعدی نگه داشته شده
    fun analyzeZigZag(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        depth: Int = 12,
        deviation: Int = 5,
        backstep: Int = 3
    ): ZigZagResult {
        if (closes.size < depth * 2) {
            return ZigZagResult("UP", 0.0, 0.0, "BULLISH", false, 0.0)
        }

        val swingPoints = findSwingPoints(highs, lows, depth, deviation, backstep)

        if (swingPoints.size < 2) {
            return ZigZagResult("UP", highs.last(), lows.last(), "BULLISH", false, 0.0)
        }

        val lastSwing = swingPoints.last()
        val prevSwing = swingPoints[swingPoints.size - 2]

        val currentTrend = if (lastSwing.second > prevSwing.second) "BULLISH" else "BEARISH"
        val reversalSignal = detectZigZagReversal(swingPoints, closes.last())

        val histValue = if (currentTrend == "BULLISH") {
            (closes.last() - lastSwing.second) / lastSwing.second * 100
        } else {
            (lastSwing.second - closes.last()) / lastSwing.second * 100
        }

        val direction = when {
            reversalSignal && currentTrend == "BEARISH" -> "REVERSING_UP"
            reversalSignal && currentTrend == "BULLISH" -> "REVERSING_DOWN"
            currentTrend == "BULLISH" -> "UP"
            else -> "DOWN"
        }

        return ZigZagResult(
            direction = direction,
            lastSwingHigh = swingPoints.filter { it.first == "HIGH" }.lastOrNull()?.second ?: highs.last(),
            lastSwingLow = swingPoints.filter { it.first == "LOW" }.lastOrNull()?.second ?: lows.last(),
            currentTrend = currentTrend,
            reversalSignal = reversalSignal,
            histValue = histValue
        )
    }

    private fun findSwingPoints(
        highs: List<Double>,
        lows: List<Double>,
        depth: Int,
        deviation: Int,
        backstep: Int
    ): List<Pair<String, Double>> {
        val swings = mutableListOf<Pair<String, Double>>()
        var lastHighIdx = 0
        var lastLowIdx = 0

        for (i in depth until highs.size - depth) {
            var isHigh = true
            for (j in 1..depth) {
                if (highs[i] <= highs[i - j] || highs[i] <= highs[i + j]) {
                    isHigh = false
                    break
                }
            }
            if (isHigh && i - lastHighIdx >= backstep) {
                swings.add(Pair("HIGH", highs[i]))
                lastHighIdx = i
            }

            var isLow = true
            for (j in 1..depth) {
                if (lows[i] >= lows[i - j] || lows[i] >= lows[i + j]) {
                    isLow = false
                    break
                }
            }
            if (isLow && i - lastLowIdx >= backstep) {
                swings.add(Pair("LOW", lows[i]))
                lastLowIdx = i
            }
        }

        return swings
    }

    private fun detectZigZagReversal(
        swings: List<Pair<String, Double>>,
        currentPrice: Double
    ): Boolean {
        if (swings.size < 4) return false

        val last4 = swings.takeLast(4)
        val highs = last4.filter { it.first == "HIGH" }.map { it.second }
        val lows = last4.filter { it.first == "LOW" }.map { it.second }

        if (highs.size >= 2 && lows.size >= 2) {
            val hh = highs.last() > highs.first()
            val hl = lows.last() > lows.first()
            return (hh && !hl) || (!hh && hl)
        }

        return false
    }

    // ==================== ORDER FLOW (CVD) ====================
    // ⚠️ این تابع کد مرده است — برای Commit پاک‌سازی بعدی نگه داشته شده
    fun analyzeOrderFlow(
        opens: List<Double>,
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        volumes: List<Double>
    ): OrderFlowResult {
        if (closes.size < 20 || volumes.size < 20) {
            return OrderFlowResult(0, 0.0, 0.0, 0.0, false, false)
        }

        var buyVolume = 0.0
        var sellVolume = 0.0
        var cvd = 0.0
        val cvdHistory = mutableListOf<Double>()

        val lookback = min(50, closes.size)
        for (i in (closes.size - lookback) until closes.size) {
            val open = opens[i]
            val close = closes[i]
            val high = highs[i]
            val low = lows[i]
            val volume = volumes[i]

            val candleRange = high - low
            val bodySize = abs(close - open)

            if (candleRange > 0) {
                val buyRatio = if (close > open) {
                    0.5 + (bodySize / candleRange) * 0.5
                } else {
                    0.5 - (bodySize / candleRange) * 0.5
                }

                val buyVol = volume * buyRatio
                val sellVol = volume * (1 - buyRatio)

                buyVolume += buyVol
                sellVolume += sellVol
                cvd += (buyVol - sellVol)
                cvdHistory.add(cvd)
            }
        }

        val delta = buyVolume - sellVolume
        val totalVolume = buyVolume + sellVolume

        val cvdScore = if (totalVolume > 0) {
            (delta / totalVolume * 100).toInt().coerceIn(-100, 100)
        } else 0

        val isAccumulation = detectAccumulation(cvdHistory, closes.takeLast(lookback))
        val isDistribution = detectDistribution(cvdHistory, closes.takeLast(lookback))

        return OrderFlowResult(cvdScore, buyVolume, sellVolume, delta, isAccumulation, isDistribution)
    }

    private fun detectAccumulation(cvdHistory: List<Double>, prices: List<Double>): Boolean {
        if (cvdHistory.size < 10 || prices.size < 10) return false

        val cvdTrend = cvdHistory.takeLast(10).let { it.last() > it.first() }
        val priceFlat = prices.takeLast(10).let {
            val change = abs(it.last() - it.first()) / it.first()
            change < 0.03
        }

        return cvdTrend && priceFlat
    }

    private fun detectDistribution(cvdHistory: List<Double>, prices: List<Double>): Boolean {
        if (cvdHistory.size < 10 || prices.size < 10) return false

        val cvdTrend = cvdHistory.takeLast(10).let { it.last() < it.first() }
        val priceFlat = prices.takeLast(10).let {
            val change = abs(it.last() - it.first()) / it.first()
            change < 0.03
        }

        return cvdTrend && priceFlat
    }

    // ==================== تشخیص پامپ زودهنگام ====================
    // ⚠️ این تابع کد مرده است — برای Commit پاک‌سازی بعدی نگه داشته شده
    fun detectEarlyPump(
        closes: List<Double>,
        volumes: List<Double>
    ): Int {
        if (closes.size < 50 || volumes.size < 50) return 0

        val price = closes.last()
        val prevPrice = closes[closes.size - 2]

        val avgVol = volumes.dropLast(1).takeLast(20).average()
        val currentVol = volumes.last()
        val volSpike = if (avgVol > 0) (currentVol / avgVol - 1) * 100 else 0.0

        val priceChange1h = if (prevPrice > 0) (price - prevPrice) / prevPrice * 100 else 0.0

        val high20 = closes.takeLast(20).maxOrNull() ?: price
        val isBreakout = price > high20 * 0.98 && priceChange1h > 2

        val e5 = emaLast(closes, 5)
        val e20 = emaLast(closes, 20)
        val momentum = if (e20 > 0) (e5 - e20) / e20 * 100 else 0.0

        var pumpScore = 0

        if (volSpike > 100) pumpScore += 30
        else if (volSpike > 50) pumpScore += 20
        else if (volSpike > 25) pumpScore += 10

        if (priceChange1h > 5) pumpScore += 30
        else if (priceChange1h > 3) pumpScore += 20
        else if (priceChange1h > 1) pumpScore += 10

        if (isBreakout) pumpScore += 20

        if (momentum > 3) pumpScore += 20
        else if (momentum > 1) pumpScore += 10

        return pumpScore.coerceIn(0, 100)
    }

    // ==================== بررسی سقف قیمتی ====================
    // ⚠️ این تابع کد مرده است — برای Commit پاک‌سازی بعدی نگه داشته شده
    fun isAtTop(
        closes: List<Double>,
        volumes: List<Double>
    ): Boolean {
        if (closes.size < 50) return false

        val price = closes.last()
        val e20 = emaLast(closes, 20)
        val e50 = emaLast(closes, 50)
        val rsi = rsiOf(closes)

        val distanceFromEma = if (e20 > 0) (price - e20) / e20 * 100 else 0.0

        val price24hAgo = closes[closes.size - 24]
        val change24h = if (price24hAgo > 0) (price - price24hAgo) / price24hAgo * 100 else 0.0

        val price4hAgo = closes[closes.size - 4]
        val change4h = if (price4hAgo > 0) (price - price4hAgo) / price4hAgo * 100 else 0.0

        val isOverbought = rsi > 75
        val isPumped = change24h > 20 || change4h > 10
        val isExtended = distanceFromEma > 8
        val belowEma50 = price < e50

        var topSignals = 0
        if (isOverbought) topSignals++
        if (isPumped) topSignals++
        if (isExtended) topSignals++
        if (belowEma50) topSignals++

        return topSignals >= 2
    }

    // ==================== محاسبه امتیاز نهایی ====================
    // ⚠️ این تابع کد مرده است — برای Commit پاک‌سازی بعدی نگه داشته شده
    fun calculateFinalScore(
        baseScore: Int,
        closes: List<Double>,
        volumes: List<Double>,
        pumpScore: Int,
        sixtyResult: SixtySecondResult,
        zigzagResult: ZigZagResult,
        orderFlowResult: OrderFlowResult
    ): Int {
        var score = baseScore

        if (isAtTop(closes, volumes)) {
            score -= 40
        }

        if (pumpScore >= 60) {
            score += 15
        }

        when (sixtyResult.signal) {
            "BUY" -> score += (sixtyResult.strength * 0.25).toInt()
            "SELL" -> score -= (sixtyResult.strength * 0.25).toInt()
        }

        when (zigzagResult.direction) {
            "REVERSING_UP" -> score += 20
            "UP" -> score += 10
            "REVERSING_DOWN" -> score -= 20
            "DOWN" -> score -= 10
        }

        if (orderFlowResult.cvdScore > 30) {
            score += (orderFlowResult.cvdScore * 0.25).toInt()
        } else if (orderFlowResult.cvdScore < -30) {
            score -= (abs(orderFlowResult.cvdScore) * 0.25).toInt()
        }

        if (orderFlowResult.isAccumulation) {
            score += 15
        }

        if (orderFlowResult.isDistribution) {
            score -= 20
        }

        return score.coerceIn(-100, 100)
    }

    // ==================== توابع کمکی ====================
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
}
