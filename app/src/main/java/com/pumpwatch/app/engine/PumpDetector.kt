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

    // ==================== REVIVED PUMP DETECTION ====================

    /**
     * تشخیص پامپ زودهنگام: افزایش ناگهانی حجم (۲.۵ برابر میانگین) + جهش قیمت (حداقل ۱.۵٪)
     */
    fun detectEarlyPump(candles: List<Candle>, lookback: Int = 20): Boolean {
        if (candles.size < lookback + 3) return false
        val history = candles.take(candles.size - 3)
        val recent = candles.takeLast(3)
        val avgVol = history.map { it.volume }.average()
        val recentVol = recent.sumOf { it.volume }
        val volSpike = avgVol > 0 && recentVol > (avgVol * 2.5)

        val startPrice = history.last().close
        val currentPrice = recent.last().close
        val priceJump = if (startPrice > 0) (currentPrice - startPrice) / startPrice else 0.0

        return volSpike && priceJump >= 0.015
    }

    /**
     * بررسی اینکه آیا قیمت فعلی نزدیک به سقف اخیر است (مثلاً ۹۵٪ بالاترین قیمت)
     */
    fun isAtTop(candles: List<Candle>, lookback: Int = 50, threshold: Double = 0.95): Boolean {
        if (candles.size < lookback) return false
        val recent = candles.takeLast(lookback)
        val highest = recent.maxOf { it.high }
        val current = recent.last().close
        return highest > 0 && current >= (highest * threshold)
    }

    /**
     * امتیاز نهایی پتانسیل پامپ (۰ تا ۱۰۰) - ترکیب EarlyPump، IsAtTop و SixtySecond
     */
    fun analyzePumpPotential(candles: List<Candle>): Int {
        if (candles.size < 20) return 0
        var score = 0
        
        if (detectEarlyPump(candles)) score += 40
        if (isAtTop(candles)) score += 30

        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val closes = candles.map { it.close }
        val sixtyRes = analyzeSixtySecond(highs, lows, closes)
        
        if (sixtyRes.signal == "BUY") {
            score += (sixtyRes.strength / 3).toInt()
        }
        
        return score.coerceIn(0, 100)
    }
}
