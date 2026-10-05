package com.pumpwatch.app.engine

import com.google.gson.JsonArray
import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 127: تست‌های pure برای ScoringEngine.
 *
 * توابع تست‌شده:
 * - emaL (EMA)
 * - rsi (RSI)
 * - macdU (MACD uptrend)
 * - scoreFromCandles (امتیاز کلی)
 *
 * توابع network-dependent (score) تست نمی‌شوند چون به KlineCache وابسته‌اند.
 */
class ScoringEngineTest {

    // ========== Helpers ==========

    /** ساخت یک کندل JsonArray با فرمت Binance: [openTime, open, high, low, close, volume, ...] */
    private fun candle(close: Double, volume: Double = 1000.0, open: Double = close): JsonArray =
        JsonArray().apply {
            add(0L)       // openTime
            add(open)     // open
            add(close)    // high (simplified)
            add(open)     // low
            add(close)    // close
            add(volume)   // volume
            add(0L)       // closeTime
        }

    /** ساخت یک سری کندل از روی close prices */
    private fun candlesFromCloses(closes: List<Double>, volume: Double = 1000.0): List<JsonArray> =
        closes.map { candle(it, volume) }

    // ========== emaL tests ==========

    @Test
    fun `emaL with empty list returns zero`() {
        assertEquals(0.0, ScoringEngine.emaL(emptyList(), 5), 0.0001)
    }

    @Test
    fun `emaL with single element returns that element`() {
        assertEquals(5.0, ScoringEngine.emaL(listOf(5.0), 5), 0.0001)
    }

    @Test
    fun `emaL when size less than period returns average`() {
        val data = listOf(2.0, 4.0, 6.0)
        val expected = data.average()  // 4.0
        assertEquals(expected, ScoringEngine.emaL(data, 5), 0.0001)
    }

    @Test
    fun `emaL with constant series returns that constant`() {
        val data = List(20) { 100.0 }
        assertEquals(100.0, ScoringEngine.emaL(data, 10), 0.0001)
    }

    @Test
    fun `emaL with increasing series approaches latest value`() {
        val data = (1..30).map { it.toDouble() }
        val ema = ScoringEngine.emaL(data, 10)
        // EMA of increasing series should be below the latest value but close
        assertTrue("EMA should be below latest", ema < data.last())
        assertTrue("EMA should be reasonably close to latest", ema > data.last() * 0.8)
    }

    @Test
    fun `emaL with period equals size returns simple average`() {
        val data = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val expected = data.average()  // 3.0
        assertEquals(expected, ScoringEngine.emaL(data, 5), 0.0001)
    }

    @Test
    fun `emaL manual calculation for small series`() {
        // period = 3, k = 2/(3+1) = 0.5
        // data = [10, 20, 30, 40]
        // initial EMA (from first 3) = (10+20+30)/3 = 20
        // next EMA = 40*0.5 + 20*0.5 = 30
        val data = listOf(10.0, 20.0, 30.0, 40.0)
        val result = ScoringEngine.emaL(data, 3)
        assertEquals(30.0, result, 0.0001)
    }

    // ========== rsi tests ==========

    @Test
    fun `rsi returns 50 when size equals period`() {
        val data = List(15) { 100.0 + it }
        assertEquals(50.0, ScoringEngine.rsi(data, 14), 0.0001)
    }

    @Test
    fun `rsi returns 50 when size less than period`() {
        val data = List(10) { 100.0 }
        assertEquals(50.0, ScoringEngine.rsi(data, 14), 0.0001)
    }

    @Test
    fun `rsi returns 100 for all-up series`() {
        // هر کندل بالاتر از قبلی → RSI باید نزدیک ۱۰۰ باشد
        val data = (1..50).map { it.toDouble() }
        val r = ScoringEngine.rsi(data, 14)
        assertEquals("All up should give RSI near 100", 100.0, r, 0.0001)
    }

    @Test
    fun `rsi returns 0 for all-down series`() {
        // هر کندل پایین‌تر از قبلی → RSI باید نزدیک ۰ باشد
        val data = (50 downTo 1).map { it.toDouble() }
        val r = ScoringEngine.rsi(data, 14)
        assertEquals("All down should give RSI near 0", 0.0, r, 0.0001)
    }

    @Test
    fun `rsi returns mid-range for balanced series`() {
        // alternates up and down by same amount
        val data = mutableListOf(100.0)
        for (i in 1..60) {
            data.add(data.last() + if (i % 2 == 0) 1.0 else -1.0)
        }
        val r = ScoringEngine.rsi(data, 14)
        assertTrue("Balanced series should have RSI between 30-70", r in 30.0..70.0)
    }

    @Test
    fun `rsi default period is 14`() {
        val data = (1..30).map { it.toDouble() }
        // Calling without explicit period should default to 14
        val r1 = ScoringEngine.rsi(data)
        val r2 = ScoringEngine.rsi(data, 14)
        assertEquals(r1, r2, 0.0001)
    }

    // ========== macdU tests ==========

    @Test
    fun `macdU returns false for too-short series`() {
        val data = List(30) { 100.0 }
        assertFalse(ScoringEngine.macdU(data))
    }

    @Test
    fun `macdU returns true for clear uptrend`() {
        // Steadily increasing series → MACD line should be rising
        val data = (1..50).map { it.toDouble() }
        assertTrue("Uptrend should have MACD rising", ScoringEngine.macdU(data))
    }

    @Test
    fun `macdU returns false for clear downtrend`() {
        // Steadily decreasing series → MACD line should be falling
        val data = (50 downTo 1).map { it.toDouble() }
        assertFalse("Downtrend should not have MACD rising", ScoringEngine.macdU(data))
    }

    @Test
    fun `macdU with constant series returns false`() {
        val data = List(50) { 100.0 }
        assertFalse("Flat series should not trigger MACD uptrend", ScoringEngine.macdU(data))
    }

    // ========== scoreFromCandles tests ==========

    @Test
    fun `scoreFromCandles returns 0 for too-short data`() {
        val candles = candlesFromCloses(List(50) { 100.0 })
        val (score, _) = ScoringEngine.scoreFromCandles(candles)
        assertEquals("Short data should score 0", 0, score)
        assertEquals("Default ATR pct should be 12", 12.0, ScoringEngine.scoreFromCandles(candles).second, 0.0001)
    }

    @Test
    fun `scoreFromCandles strong uptrend gives positive score`() {
        // 250 candles of steadily increasing prices
        val closes = (1..250).map { 100.0 + it * 0.5 }
        val candles = candlesFromCloses(closes)
        val (score, _) = ScoringEngine.scoreFromCandles(candles)
        assertTrue("Strong uptrend should score positively", score > 50)
    }

    @Test
    fun `scoreFromCandles strong downtrend gives negative score`() {
        // 250 candles of steadily decreasing prices
        val closes = (250 downTo 1).map { 100.0 + it * 0.5 }
        val candles = candlesFromCloses(closes)
        val (score, _) = ScoringEngine.scoreFromCandles(candles)
        assertTrue("Strong downtrend should score negatively", score < -50)
    }

    @Test
    fun `scoreFromCandles is bounded between minus100 and plus100`() {
        // Try many different scenarios
        val scenarios = listOf(
            (1..250).map { 100.0 + it * 2.0 },  // strong up
            (250 downTo 1).map { 100.0 + it * 2.0 },  // strong down
            List(250) { 100.0 + (Math.random() - 0.5) * 10 }  // random
        )
        for (closes in scenarios) {
            val (score, _) = ScoringEngine.scoreFromCandles(candlesFromCloses(closes))
            assertTrue("Score should be bounded", score in -100..100)
        }
    }

    @Test
    fun `scoreFromCandles computes ATR percentage`() {
        // 250 candles with known volatility
        val closes = (1..250).map { 100.0 + (it % 3) * 5.0 }  // oscillates
        val candles = candlesFromCloses(closes)
        val (_, atrPct) = ScoringEngine.scoreFromCandles(candles)
        assertTrue("ATR pct should be positive", atrPct > 0)
        assertTrue("ATR pct should be reasonable", atrPct < 50)
    }

    @Test
    fun `scoreFromCandles handles missing volume gracefully`() {
        val closes = List(250) { 100.0 + (it % 10).toDouble() }
        val candles = closes.map { candle(it, volume = 0.0) }
        val (score, _) = ScoringEngine.scoreFromCandles(candles)
        // Should not crash; score should be valid
        assertTrue(score in -100..100)
    }

    @Test
    fun `scoreFromCandles deterministic output`() {
        val closes = List(250) { 100.0 + (it * 7 % 13).toDouble() }
        val candles = candlesFromCloses(closes)
        val (score1, atr1) = ScoringEngine.scoreFromCandles(candles)
        val (score2, atr2) = ScoringEngine.scoreFromCandles(candles)
        assertEquals("Score should be deterministic", score1, score2)
        assertEquals("ATR should be deterministic", atr1, atr2, 0.0001)
    }
}
