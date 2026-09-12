package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های موتور بک‌تست — نسخهٔ قطعی با حاشیهٔ امن دوطرفه:
 * کندل شکست U = 5a انتخاب شده تا RSI با هر روش محاسبه‌ای (Wilder یا ساده)
 * داخل پنجرهٔ 50..70 بماند (Wilder≈58، ساده≈63).
 * حجم شکست ۱۰ برابر است تا volumeRatio با هر پنجره‌ای >= 1.5 شود.
 */
class BacktestEngineTest {

    // ۶۰ کندل نوسان متقارن ±0.2 + ۱ کندل شکست +1.0 با حجم ۱۰ برابر + ۳۹ کندل ملایم
    private fun flatThenBreak(): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        for (i in 0 until 60) {
            val c = 100.0 + 0.2 * (i % 2)
            out.add(listOf(c, c + 0.05, c - 0.05, c, 1000.0))
        }
        out.add(listOf(100.2, 101.25, 100.0, 101.2, 10000.0)) // کندل شکست
        for (i in 61 until 100) {
            val c = 101.2 + 0.1 * (i - 60)
            out.add(listOf(c - 0.1, c + 0.05, c - 0.05, c, 1000.0))
        }
        return out
    }

    // ۳۰۰ کندل نوسان متقارن + اسپایک شکست هر ۳۰ کندل (تا ایندکس 240)
    private fun oscillatingWithSpikes(): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var base = 100.0
        for (i in 0 until 300) {
            val isSpike = i >= 90 && i % 30 == 0
            if (isSpike) base += 1.0
            val c = base + 0.2 * (i % 2)
            val vol = if (isSpike) 10000.0 else 1000.0
            val low = if (isSpike) c - 1.05 else c - 0.05
            out.add(listOf(c - 0.2, c + 0.05, low, c, vol))
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
            "ETH", oscillatingWithSpikes(), 30, scoreThreshold = 10
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
