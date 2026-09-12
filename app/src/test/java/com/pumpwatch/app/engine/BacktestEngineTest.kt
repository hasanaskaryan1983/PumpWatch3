package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های موتور بک‌تست (هماهنگ با UnifiedSignalEngine):
 * - تولید معامله با آستانهٔ تستی پایین
 * - no-lookahead: ورود قبل از خروج و داخل داده
 * - سازگاری equity curve و max drawdown
 * - تفکیک in/out-of-sample
 */
class BacktestEngineTest {

    // ۶۰ کندل تخت + ۴۰ کندل صعودی قوی: برای تحریک سیگنال PUMP
    private fun flatThenRise(): List<List<Double>> =
        (0 until 60).map { listOf(100.0, 100.0, 100.0, 100.0, 1000.0) } +
                (0 until 40).map { i ->
                    val base = 100.0 + i * 1.0 // رشد سریع‌تر
                    listOf(base, base + 1.5, base - 0.5, base + 1.0, 2000.0) // حجم بالا
                }

    private fun linearUp(n: Int, step: Double): List<List<Double>> =
        (0 until n).map { i ->
            val base = 100.0 + i * step
            listOf(base, base + step * 1.2, base - step * 0.5, base + step * 0.8, 1500.0)
        }

    @Test
    fun `futures backtest produces trades with lowered threshold`() {
        val (trades, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenRise(), 50, 10, 0.0, signalThreshold = 10
        )
        // با آستانه پایین، باید حداقل یک معامله داشته باشیم
        assertTrue("باید حداقل یک معامله تولید شود", trades.isNotEmpty())
        assertEquals("تعداد معاملات باید با metrics یکی باشد", trades.size, metrics.totalTrades)
        assertEquals("equity curve باید یک نقطه بیشتر از trades داشته باشد", trades.size + 1, metrics.equityCurve.size)
        assertTrue("maxDrawdown باید غیرمنفی باشد", metrics.maxDrawdown >= 0.0)
    }

    @Test
    fun `spot backtest with uptrend produces buys`() {
        val (trades, metrics) = BacktestEngine.runSpot(
            "ETH", linearUp(300, 2.0), 30, scoreThreshold = 10
        )
        // همه معاملات باید BUY باشند (چون روند صعودی است)
        assertTrue("همه معاملات باید BUY باشند", trades.all { it.side == "BUY" || it.side == "PUMP" })
        assertTrue("باید حداقل یک معامله داشته باشیم", metrics.totalTrades > 0)
    }

    @Test
    fun `equity curve starts at 100 and drawdown non-negative`() {
        val (_, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenRise(), 50, 10, 0.0, signalThreshold = 10
        )
        assertEquals("نقطه شروع equity curve باید 100 باشد", 100.0, metrics.equityCurve.first(), 1e-9)
        assertTrue("maxDrawdown باید غیرمنفی باشد", metrics.maxDrawdown >= 0.0)
    }

    @Test
    fun `no lookahead - entry before exit and inside data`() {
        val klines = flatThenRise()
        val (trades, _) = BacktestEngine.runFutures(
            "BTC", klines, 50, 10, 0.0, signalThreshold = 10
        )
        trades.forEach { trade ->
            assertTrue("entryIndex باید داخل داده باشد", trade.entryIndex < klines.size)
            assertTrue("exitIndex باید بعد از entryIndex باشد", trade.exitIndex > trade.entryIndex)
            assertTrue("exitIndex باید داخل یا انتهای داده باشد", trade.exitIndex <= klines.size - 1)
        }
    }

    @Test
    fun `in-sample and out-of-sample split covers all trades`() {
        val (trades, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenRise(), 50, 10, 0.0, signalThreshold = 10
        )
        if (trades.size >= 10) {
            assertTrue("باید in-sample metrics داشته باشیم", metrics.inSampleMetrics != null)
            assertTrue("باید out-of-sample metrics داشته باشیم", metrics.outOfSampleMetrics != null)
            assertEquals(
                "مجموع in-sample و out-of-sample باید کل trades باشد",
                trades.size,
                metrics.inSampleMetrics!!.trades + metrics.outOfSampleMetrics!!.trades
            )
        }
    }
}
