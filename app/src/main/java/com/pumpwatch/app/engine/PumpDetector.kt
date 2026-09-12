package com.pumpwatch.app.engine

import kotlin.math.max

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
 * PumpDetector — نسخهٔ پاک‌سازی‌شده
 * فقط مسیر زنده نگه داشته شده: ChartSignals.sixtyPoints → analyzeSixtySecond
 * کد مرده (ZigZag, OrderFlow, EarlyPump, IsAtTop, FinalScore) حذف شد —
 * تأییدشده با جست‌وجوی سراسری مخزن: بدون ارجاع بیرون از این فایل.
 */
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
     * ADX واقعی (Wilder's smoothing) — delegate به Indicators.adx
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
     * Stochastic استاندارد:
     * - %K: (close - lowestLow) / (highestHigh - lowestLow) * 100 روی پنجرهٔ kPeriod
     * - %D: SMA(dPeriod) از سری %K
     */
    private fun calculateStochastic(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int,
        dPeriod: Int
    ): Pair<Double, Double> {
        if (closes.size < kPeriod) return Pair(50.0, 50.0)

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
}
