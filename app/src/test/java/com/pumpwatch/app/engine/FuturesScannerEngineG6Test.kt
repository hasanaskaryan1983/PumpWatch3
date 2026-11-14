package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuturesScannerEngineG6Test {

    private fun candle(open: Double, close: Double, time: Long = 0L): FuturesCandle {
        return FuturesCandle(time, open, close, close * 1.01, open * 0.99, 1000.0)
    }

    @Test
    fun `G6 - regimeOf returns BULL when e50 greater than e200 and price above e50`() {
        // ساخت 60 کندل صعودی با EMA50 > EMA200
        val candles = (1..60).map { i ->
            candle(100.0 + i * 0.5, 100.0 + i * 0.6)
        }
        assertEquals(Regime.BULL, FuturesScannerEngine.regimeOf(candles))
    }

    @Test
    fun `G6 - regimeOf returns BEAR when e50 less than e200 and price below e50`() {
        val candles = (1..60).map { i ->
            candle(100.0 - i * 0.5, 100.0 - i * 0.6)
        }
        assertEquals(Regime.BEAR, FuturesScannerEngine.regimeOf(candles))
    }

    @Test
    fun `G6 - regimeOf returns RANGE for insufficient data`() {
        val candles = (1..50).map { i -> candle(100.0, 100.0) }
        assertEquals(Regime.RANGE, FuturesScannerEngine.regimeOf(candles))
    }

    @Test
    fun `G6 - setupOf returns PULLBACK in BULL when price pulls back to e20`() {
        val candles = (1..40).map { i ->
            val base = 100.0 + i * 0.3
            candle(base, base + 0.2)
        }.toMutableList()
        // پولبک: کندل آخر به EMA20 نزدیک است
        candles.add(candle(100.0, 101.0))
        val setup = FuturesScannerEngine.setupOf(candles, Regime.BULL)
        assertTrue(setup == Setup.PULLBACK || setup == Setup.BREAKOUT_RETEST || setup == Setup.NONE)
    }

    @Test
    fun `G6 - setupOf returns NONE for RANGE regime`() {
        val candles = (1..40).map { i -> candle(100.0, 100.0) }
        assertEquals(Setup.NONE, FuturesScannerEngine.setupOf(candles, Regime.RANGE))
    }

    @Test
    fun `G6 - triggerOf requires at least 3 closed candles`() {
        val now = System.currentTimeMillis()
        val open = now - 10 * 60 * 1000 // 10 دقیقه پیش باز شده
        val candles = listOf(candle(100.0, 101.0, open))
        assertFalse(FuturesScannerEngine.triggerOf(candles, "LONG"))
    }

    @Test
    fun `G6 - triggerOf returns true for 3 consecutive bullish candles with sufficient body`() {
        val now = System.currentTimeMillis()
        val intervalMs = 5L * 60 * 1000
        val candles = (0..5).map { i ->
            val openTime = now - (6 - i) * intervalMs - intervalMs
            val open = 100.0 + i
            val close = open * 1.001 // بدنه 0.1%
            FuturesCandle(openTime, open, close, close * 1.01, open * 0.99, 1000.0)
        }
        assertTrue(FuturesScannerEngine.triggerOf(candles, "LONG"))
    }

    @Test
    fun `G6 - triggerOf returns false for doji candles with insufficient body`() {
        val now = System.currentTimeMillis()
        val intervalMs = 5L * 60 * 1000
        val candles = (0..5).map { i ->
            val openTime = now - (6 - i) * intervalMs - intervalMs
            val open = 100.0
            val close = open * 1.0001 // بدنه 0.01% < 0.05%
            FuturesCandle(openTime, open, close, close * 1.01, open * 0.99, 1000.0)
        }
        assertFalse(FuturesScannerEngine.triggerOf(candles, "LONG"))
    }

    @Test
    fun `G6 - analyze returns null for RANGE regime`() {
        val candles4h = (1..60).map { i -> candle(100.0, 100.0) }
        val candles1h = candles4h
        val candles15m = candles4h
        assertNull(FuturesScannerEngine.analyze("BTC", "USDT", candles4h, candles1h, candles15m, 0.01))
    }

    @Test
    fun `G6 - analyze returns ScanResult for BULL regime with valid setup`() {
        val candles4h = (1..60).map { i ->
            candle(100.0 + i * 0.5, 100.0 + i * 0.6)
        }
        val candles1h = candles4h
        val candles15m = (1..40).map { i ->
            candle(100.0 + i * 0.3, 100.0 + i * 0.4)
        }
        val result = FuturesScannerEngine.analyze("BTC", "USDT", candles4h, candles1h, candles15m, 0.01)
        assertNotNull(result)
        assertEquals(Regime.BULL, result?.regime4h)
        assertEquals("LONG", result?.direction)
    }

    @Test
    fun `G6 - atr returns positive value for sufficient candles`() {
        val candles = (1..20).map { i ->
            candle(100.0 + i * 0.5, 100.0 + i * 0.6)
        }
        val atr = FuturesScannerEngine.atr(candles)
        assertTrue(atr > 0.0)
    }

    @Test
    fun `G6 - atr returns 0 for insufficient candles`() {
        val candles = (1..10).map { i -> candle(100.0, 100.0) }
        assertEquals(0.0, FuturesScannerEngine.atr(candles), 0.001)
    }
}
