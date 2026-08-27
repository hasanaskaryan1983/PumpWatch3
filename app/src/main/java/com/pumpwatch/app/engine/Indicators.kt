package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// ---------- کندل ----------

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

// ---------- نتایج ----------

data class MacdResult(val macd: Double, val signal: Double, val histogram: Double)

data class BollingerResult(
    val upper: Double,
    val middle: Double,
    val lower: Double,
    val widthPct: Double
)

data class SupertrendResult(val direction: Int, val line: Double)

data class VolumeProfileResult(val poc: Double, val vah: Double, val vall: Double)

data class OrderBlock(
    val isBullish: Boolean,
    val top: Double,
    val bottom: Double,
    val time: Long
)

// ---------- موتور محاسبات ----------

object Indicators {

    fun closes(candles: List<Candle>): List<Double> = candles.map { it.close }

    // ---------- EMA ----------

    fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double>(values.size)
        var ema = values.take(period).average()
        for (i in values.indices) {
            ema = if (i < period) ema else values[i] * k + ema * (1 - k)
            out.add(ema)
        }
        return out
    }

    fun emaLast(values: List<Double>, period: Int): Double =
        emaSeries(values, period).lastOrNull() ?: 0.0

    // ---------- RSI (Wilder) ----------

    fun rsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = closes[i] - closes[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        for (i in period + 1 until closes.size) {
            val d = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + max(d, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-d, 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    // ---------- MACD ----------

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, sig: Int = 9): MacdResult {
        if (closes.size < slow + sig) return MacdResult(0.0, 0.0, 0.0)
        val ef = emaSeries(closes, fast)
        val es = emaSeries(closes, slow)
        if (ef.isEmpty() || es.isEmpty()) return MacdResult(0.0, 0.0, 0.0)
        val macdLine = ef.mapIndexed { i, v -> v - es[i] }
        val signalLine = emaSeries(macdLine, sig)
        val m = macdLine.last()
        val s = signalLine.lastOrNull() ?: 0.0
        return MacdResult(m, s, m - s)
    }

    // ---------- ATR ----------

    private fun tr(c: Candle, prevClose: Double): Double =
        maxOf(c.high - c.low, abs(c.high - prevClose), abs(c.low - prevClose))

    fun atr(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= period) return 0.0
        var sum = 0.0
        for (i in 1..period) sum += tr(candles[i], candles[i - 1].close)
        var a = sum / period
        for (i in period + 1 until candles.size) {
            a = (a * (period - 1) + tr(candles[i], candles[i - 1].close)) / period
        }
        return a
    }

    // ---------- ADX ----------

    fun adx(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < period * 2) return 0.0
        val plusDM = ArrayList<Double>()
        val minusDM = ArrayList<Double>()
        val trs = ArrayList<Double>()
        for (i in 1 until candles.size) {
            val up = candles[i].high - candles[i - 1].high
            val dn = candles[i - 1].low - candles[i].low
            plusDM.add(if (up > dn && up > 0) up else 0.0)
            minusDM.add(if (dn > up && dn > 0) dn else 0.0)
            trs.add(tr(candles[i], candles[i - 1].close))
        }
        var sTR = trs.take(period).sum()
        var sP = plusDM.take(period).sum()
        var sM = minusDM.take(period).sum()
        val dxList = ArrayList<Double>()
        for (i in period until trs.size) {
            sTR = sTR - sTR / period + trs[i]
            sP = sP - sP / period + plusDM[i]
            sM = sM - sM / period + minusDM[i]
            val pDI = if (sTR > 0) 100 * sP / sTR else 0.0
            val mDI = if (sTR > 0) 100 * sM / sTR else 0.0
            val d = pDI + mDI
            dxList.add(if (d > 0) 100 * abs(pDI - mDI) / d else 0.0)
        }
        if (dxList.isEmpty()) return 0.0
        if (dxList.size < period) return dxList.last()
        var a = dxList.take(period).average()
        for (i in period until dxList.size) {
            a = (a * (period - 1) + dxList[i]) / period
        }
        return a
    }

    // ---------- بولینگر ----------

    fun bollinger(closes: List<Double>, period: Int = 20, mult: Double = 2.0): BollingerResult {
        if (closes.size < period) return BollingerResult(0.0, 0.0, 0.0, 0.0)
        val win = closes.takeLast(period)
        val mean = win.average()
        val sd = sqrt(win.sumOf { (it - mean) * (it - mean) } / period)
        val upper = mean + mult * sd
        val lower = mean - mult * sd
        val width = if (mean > 0) (upper - lower) / mean * 100 else 0.0
        return BollingerResult(upper, mean, lower, width)
    }

    // ---------- Supertrend ----------

    fun supertrend(
        candles: List<Candle>,
        atrPeriod: Int = 10,
        multiplier: Double = 3.0
    ): SupertrendResult {
        val n = candles.size
        if (n < atrPeriod + 2) return SupertrendResult(1, 0.0)

        val tr = DoubleArray(n)
        tr[0] = candles[0].high - candles[0].low
        for (i in 1 until n) tr[i] = tr(candles[i], candles[i - 1].close)

        val atrArr = DoubleArray(n)
        var sum = 0.0
        for (i in 0 until atrPeriod) {
            sum += tr[i]
            atrArr[i] = sum / (i + 1)
        }
        for (i in atrPeriod until n) {
            atrArr[i] = (atrArr[i - 1] * (atrPeriod - 1) + tr[i]) / atrPeriod
        }

        val upperBand = DoubleArray(n)
        val lowerBand = DoubleArray(n)
        var dir = 1

        for (i in 1 until n) {
            val hl2 = (candles[i].high + candles[i].low) / 2
            val bu = hl2 + multiplier * atrArr[i]
            val bl = hl2 - multiplier * atrArr[i]
            upperBand[i] = if (bu < upperBand[i - 1] || candles[i - 1].close > upperBand[i - 1]) bu else upperBand[i - 1]
            lowerBand[i] = if (bl > lowerBand[i - 1] || candles[i - 1].close < lowerBand[i - 1]) bl else lowerBand[i - 1]
            dir = if (dir == 1) {
                if (candles[i].close < lowerBand[i]) -1 else 1
            } else {
                if (candles[i].close > upperBand[i]) 1 else -1
            }
        }
        return SupertrendResult(dir, if (dir == 1) lowerBand[n - 1] else upperBand[n - 1])
    }

    // ---------- نسبت حجم ----------

    fun volumeRatio(volumes: List<Double>, period: Int = 20): Double {
        if (volumes.size <= period) return 0.0
        val last = volumes.last()
        val avg = volumes.dropLast(1).takeLast(period).average()
        return if (avg > 0) last / avg else 0.0
    }

    // ---------- تجمیع کندل ----------

    fun aggregate(candles: List<Candle>, factor: Int): List<Candle> {
        if (factor <= 1) return candles
        val out = ArrayList<Candle>(candles.size / factor + 1)
        var i = 0
        while (i + factor <= candles.size) {
            val chunk = candles.subList(i, i + factor)
            out.add(
                Candle(
                    time = chunk[0].time,
                    open = chunk[0].open,
                    high = chunk.maxOf { it.high },
                    low = chunk.minOf { it.low },
                    close = chunk.last().close,
                    volume = chunk.sumOf { it.volume }
                )
            )
            i += factor
        }
        return out
    }

    // ---------- Volume Profile (ابزار حرفه‌ای‌ها) ----------

    fun volumeProfile(candles: List<Candle>, period: Int = 96): VolumeProfileResult {
        val win = candles.takeLast(period)
        if (win.isEmpty()) return VolumeProfileResult(0.0, 0.0, 0.0)
        val min = win.minOf { it.low }
        val max = win.maxOf { it.high }
        if (max <= min) return VolumeProfileResult(win.last().close, max, min)

        val bins = 24
        val vol = DoubleArray(bins)
        for (c in win) {
            val mid = (c.high + c.low) / 2
            var idx = ((mid - min) / (max - min) * bins).toInt()
            if (idx >= bins) idx = bins - 1
            if (idx < 0) idx = 0
            vol[idx] += c.volume
        }
        val pocIdx = vol.indices.maxByOrNull { vol[it] } ?: 0
        val binSize = (max - min) / bins
        val poc = min + (pocIdx + 0.5) * binSize

        val total = vol.sum()
        var acc = vol[pocIdx]
        var lo = pocIdx
        var hi = pocIdx
        while (acc < total * 0.7 && (lo > 0 || hi < bins - 1)) {
            val nextLo = if (lo > 0) vol[lo - 1] else -1.0
            val nextHi = if (hi < bins - 1) vol[hi + 1] else -1.0
            if (nextLo >= nextHi) {
                lo--
                acc += vol[lo]
            } else {
                hi++
                acc += vol[hi]
            }
        }
        val vah = min + (hi + 1) * binSize
        val vall = min + lo * binSize
        return VolumeProfileResult(poc, vah, vall)
    }

    // ---------- Order Blocks (Smart Money) ----------

    fun findOrderBlocks(candles: List<Candle>, lookback: Int = 50): List<OrderBlock> {
        val out = mutableListOf<OrderBlock>()
        val n = candles.size
        if (n < 10) return out
        val start = max(1, n - lookback)
        for (i in start until n - 3) {
            val c = candles[i]
            val n1 = candles[i + 1]
            val n2 = candles[i + 2]
            val n3 = candles[i + 3]
            val body = max(c.high - c.low, 0.0000001)

            // اردر بلاک صعودی: آخرین کندل نزولی قبل از حرکت صعودی قوی
            if (c.close < c.open &&
                n1.close > n1.open && n2.close > n2.open &&
                n3.close > c.high &&
                (n3.close - c.low) > body * 1.5
            ) {
                out.add(OrderBlock(true, max(c.open, c.close), min(c.open, c.close), c.time))
            }

            // اردر بلاک نزولی
            if (c.close > c.open &&
                n1.close < n1.open && n2.close < n2.open &&
                n3.close < c.low &&
                (c.high - n3.close) > body * 1.5
            ) {
                out.add(OrderBlock(false, max(c.open, c.close), min(c.open, c.close), c.time))
            }
        }
        return out.takeLast(6)
    }

    // ---------- BOS (شکست ساختار) ----------

    fun detectBOS(candles: List<Candle>, swing: Int = 10): Pair<Boolean, Boolean> {
        if (candles.size < swing * 2 + 1) return Pair(false, false)
        val prevHigh = candles.dropLast(1).takeLast(swing).maxOf { it.high }
        val prevLow = candles.dropLast(1).takeLast(swing).minOf { it.low }
        val last = candles.last()
        return Pair(last.close > prevHigh, last.close < prevLow)
    }

    // ---------- Stochastic RSI ----------

    fun stochRsi(closes: List<Double>, rsiPeriod: Int = 14, stochPeriod: Int = 14): Double {
        if (closes.size < rsiPeriod + stochPeriod) return 50.0
        val series = mutableListOf<Double>()
        for (i in rsiPeriod..closes.size) {
            series.add(rsi(closes.subList(0, i), rsiPeriod))
        }
        val win = series.takeLast(stochPeriod)
        val hh = win.maxOrNull() ?: 50.0
        val ll = win.minOrNull() ?: 50.0
        val last = series.last()
        return if (hh > ll) (last - ll) / (hh - ll) * 100 else 50.0
    }
}
