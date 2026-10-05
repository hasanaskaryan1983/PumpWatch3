package com.pumpwatch.app.engine

import com.google.gson.JsonArray
import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 127: تست‌های pure برای ScoringEngine.
 *
 * 🚀 Commit 127 fix:
 * - emaL وقتی size < period: lastOrNull() برمی‌گرداند (نه average)
 * - rsi فقط وقتی size <= period برابر 50 است (نه size == period + 1)
 * - macdU نیاز به uptrend شتاب‌دار دارد (EMA ها در uptrend خطی flat می‌شوند)
 * - scoreFromCandles: RSI در uptrend طولانی به >75 می‌رسد → -25 امتیاز، پس uptrend ملایم‌تر
 */
class ScoringEngineTest {

    private fun candle(close: Double, volume: Double = 1000.0, open: Double = close): JsonArray =
        JsonArray().apply {
            add(0L); add(open); add(close); add(open); add(close); add(volume); add(0L)
        }

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
    fun `emaL when size less than period returns last element`() {
        // 🚀 Commit 127 fix: code returns lastOrNull(), not average
        val data = listOf(2.0, 4.0, 6.0)
        assertEquals(6.0, ScoringEngine.emaL(data, 5), 0.0001)
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
        assertTrue("EMA should be below latest", ema < data.last())
        assertTrue("EMA should be reasonably close to latest", ema > data.last() * 0.8)
    }

    @Test
    fun `emaL with period equals size returns simple average`() {
        val data = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val expected = data.average()
        assertEquals(expected, ScoringEngine.emaL(data, 5), 0.0001)
    }

    @Test
    fun `emaL manual calculation for small series`() {
        val data = listOf(10.0, 20.0, 30.0, 40.0)
        val result = ScoringEngine.emaL(data, 3)
        assertEquals(30.0, result, 0.0001)
    }

    // ========== rsi tests ==========

    @Test
    fun `rsi returns 50 when size equals period`() {
        // 🚀 Commit 127 fix: condition is size <= period, so need exactly 14 elements
        val data = List(14) { 100.0 + it }
        assertEquals(50.0, ScoringEngine.rsi(data, 14), 0.0001)
    }

    @Test
    fun `rsi returns 50 when size less than period`() {
        val data = List(10) { 100.0 }
        assertEquals(50.0, ScoringEngine.rsi(data, 14), 0.0001)
    }

    @Test
    fun `rsi returns 100 for all-up series`() {
        val data = (1..50).map { it.toDouble() }
        val r = ScoringEngine.rsi(data, 14)
        assertEquals("All up should give RSI near 100", 100.0, r, 0.0001)
    }

    @Test
    fun `rsi returns 0 for all-down series`() {
        val data = (50 downTo 1).map { it.toDouble() }
        val r = ScoringEngine.rsi(data, 14)
        assertEquals("All down should give RSI near 0", 0.0, r, 0.0001)
    }

    @Test
    fun `rsi returns mid-range for balanced series`() {
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
    fun `macdU returns true for accelerating uptrend`() {
        // 🚀 Commit 127 fix: need ACCELERATING uptrend, not linear
        // (EMA convergence in linear trend makes MACD flat)
        val data = (1..50).map { (it * it).toDouble() }  // quadratic acceleration
        assertTrue("Accelerating uptrend should have MACD rising", ScoringEngine.macdU(data))
    }

    @Test
    fun `macdU returns false for clear downtrend`() {
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
    fun `scoreFromCandles moderate uptrend gives positive score`() {
        // 🚀 Commit 127 fix: use moderate uptrend so RSI stays in 45-65 range (+15)
        // Strong uptrend makes RSI > 75 → -25 points
        val closes = (1..250).map { 100.0 + it * 0.15 }  // gentle slope
        val candles = candlesFromCloses(closes)
        val (score, _) = ScoringEngine.scoreFromCandles(candles)
        assertTrue("Moderate uptrend should score positively, got $score", score > 0)
    }

    @Test
    fun `scoreFromCandles strong downtrend gives negative score`() {
        val closes = (250 downTo 1).map { 100.0 + it * 0.5 }
        val candles = candlesFromCloses(closes)
        val (score, _) = ScoringEngine.scoreFromCandles(candles)
        assertTrue("Strong downtrend should score negatively, got $score", score < -50)
    }

    @Test
    fun `scoreFromCandles is bounded between minus100 and plus100`() {
        val scenarios = listOf(
            (1..250).map { 100.0 + it * 2.0 },
            (250 downTo 1).map { 100.0 + it * 2.0 },
            List(250) { 100.0 + (Math.random() - 0.5) * 10 }
        )
        for (closes in scenarios) {
            val (score, _) = ScoringEngine.scoreFromCandles(candlesFromCloses(closes))
            assertTrue("Score should be bounded", score in -100..100)
        }
    }

    @Test
    fun `scoreFromCandles computes ATR percentage`() {
        val closes = (1..250).map { 100.0 + (it % 3) * 5.0 }
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
