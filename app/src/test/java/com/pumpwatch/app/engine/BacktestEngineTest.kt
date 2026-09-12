package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestEngineTest {

    /**
     * اصلاح باگ ۳: فاز اول «رنج متقارن» است (＋0.2 / −0.2) تا RSI روی ~50 بماند؛
     * سپس یک کندل شکست +3 با حجم ۳.75 برابر → RSI به ~68 می‌رسد (داخل پنجره 50..70)
     * و شرایط breakout + volumeRatio + RSI همزمان برقرار می‌شود.
     */
    private fun flatThenBreak(): List<List<Double>> {
        val flat = (0 until 60).map { i ->
            val c = 100.0 + 0.2 * (i % 2)
            listOf(c, c + 0.1, c - 0.1, c, 800.0)
        }
        val rise = (0 until 40).map { i ->
            val base = 100.2 + i * 2.5
            val close = if (i == 0) base + 3.0 else base + 2.0
            val vol = if (i < 5) 3000.0 else 1500.0
            listOf(base, close + 0.5, base - 0.5, close, vol)
        }
        return flat + rise
    }

    /**
     * روند ملایم با نوسان داخلی (تا RSI بین 55 تا 68 بماند)
     * + اسپایک حجم هر ۲۵ کندل (ratio ≈ 2.5) → سیگنال‌های BUY متعدد
     */
    private fun mildUptrendWithSpikes(n: Int): List<List<Double>> {
        val gains = listOf(1.0, 0.8, 0.6, -0.9, 0.7)
        val out = mutableListOf<List<Double>>()
        var price = 100.0
        for (i in 0 until n) {
            val g = gains[i % 5]
            val open = price
            price += g
            val vol = if (i % 25 == 0 && i >= 100) 2500.0 else 1000.0
            out.add(listOf(open, maxOf(open, price) + 0.3, minOf(open, price) - 0.3, price, vol))
        }
        return out
    }

    @Test
    fun `futures backtest produces trades with lowered threshold`() {
        val (trades, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenBreak(), 50, 10, 0.0, signalThreshold = 10
        )
        assertTrue("باید حداقل یک معامله تولید شود، تعداد: ${trades.size}", trades.isNotEmpty())
        assertEquals(trades.size, metrics.totalTrades)
        assertEquals(trades.size + 1, metrics.equityCurve.size)
        assertTrue(metrics.maxDrawdown >= 0.0)
    }

    @Test
    fun `spot backtest with uptrend produces buys`() {
        val (trades, metrics) = BacktestEngine.runSpot(
            "ETH", mildUptrendWithSpikes(300), 30, scoreThreshold = 10
        )
        assertTrue("باید حداقل یک معامله BUY تولید شود، تعداد: ${metrics.totalTrades}",
            metrics.totalTrades > 0)
        assertTrue("همه معاملات باید BUY باشند", trades.all { it.side == "BUY" })
    }

    @Test
    fun `equity curve starts at 100 and drawdown non-negative`() {
        val (_, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenBreak(), 50, 10, 0.0, signalThreshold = 10
        )
        assertEquals(100.0, metrics.equityCurve.first(), 1e-9)
        assertTrue(metrics.maxDrawdown >= 0.0)
    }

    @Test
    fun `no lookahead - entry before exit and inside data`() {
        val klines = flatThenBreak()
        val (trades, _) = BacktestEngine.runFutures(
            "BTC", klines, 50, 10, 0.0, signalThreshold = 10
        )
        trades.forEach { t ->
            assertTrue(t.entryIndex < klines.size)
            assertTrue(t.exitIndex > t.entryIndex)
            assertTrue(t.exitIndex <= klines.size - 1)
        }
    }

    @Test
    fun `in-sample and out-of-sample split covers all trades`() {
        val (trades, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenBreak(), 50, 10, 0.0, signalThreshold = 10
        )
        if (trades.size >= 10) {
            assertTrue(metrics.inSampleMetrics != null)
            assertTrue(metrics.outOfSampleMetrics != null)
            assertEquals(trades.size,
                metrics.inSampleMetrics!!.trades + metrics.outOfSampleMetrics!!.trades)
        }
    }
}
