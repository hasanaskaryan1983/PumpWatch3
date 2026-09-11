package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های موتور بک‌تست (قدم ۷):
 * - تولید معامله با آستانهٔ تستی (دادهٔ تخت→صعودی)
 * - no-lookahead: ورود قبل از خروج و داخل داده
 * - سازگاری equity curve و max drawdown
 * - تفکیک in/out-of-sample
 */
class BacktestEngineTest {

    // ۶۰ کندل تخت + ۴۰ کندل صعودی: پرش امتیاز در نقطهٔ گذار
    private fun flatThenRise(): List<List<Double>> =
        (0 until 60).map { listOf(100.0, 100.0, 100.0, 100.0, 1000.0) } +
                (0 until 40).map { i ->
                    val base = 100.0 + i * 0.5
                    listOf(base, base + 0.5, base - 0.5, base + 0.25, 1000.0)
                }

    private fun linearUp(n: Int, step: Double): List<List<Double>> =
        (0 until n).map { i ->
            val base = 100.0 + i * step
            listOf(base, base + step, base - step * 0.5, base + step * 0.75, 1000.0)
        }

    @Test
    fun `futures backtest produces trades with lowered threshold`() {
        val (trades, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenRise(), 50, 10, 0.0, signalThreshold = 10
        )
        assertTrue(trades.isNotEmpty())
        assertEquals(trades.size, metrics.totalTrades)
        assertEquals(trades.size + 1, metrics.equityCurve.size)
        assertTrue(metrics.maxDrawdown >= 0.0)
    }

    @Test
    fun `spot backtest with uptrend produces buys`() {
        val (trades, metrics) = BacktestEngine.runSpot(
            "ETH", linearUp(300, 2.0), 30, scoreThreshold = 10
        )
        assertTrue(trades.all { it.side == "BUY" })
        assertTrue(metrics.totalTrades > 0)
    }

    @Test
    fun `equity curve starts at 100 and drawdown non-negative`() {
        val (_, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenRise(), 50, 10, 0.0, signalThreshold = 10
        )
        assertEquals(100.0, metrics.equityCurve.first(), 1e-9)
        assertTrue(metrics.maxDrawdown >= 0.0)
    }

    @Test
    fun `no lookahead - entry before exit and inside data`() {
        val klines = flatThenRise()
        val (trades, _) = BacktestEngine.runFutures(
            "BTC", klines, 50, 10, 0.0, signalThreshold = 10
        )
        trades.forEach { trade ->
            assertTrue(trade.entryIndex < klines.size)
            assertTrue(trade.exitIndex > trade.entryIndex)
            assertTrue(trade.exitIndex <= klines.size - 1)
        }
    }

    @Test
    fun `in-sample and out-of-sample split covers all trades`() {
        val (trades, metrics) = BacktestEngine.runFutures(
            "BTC", flatThenRise(), 50, 10, 0.0, signalThreshold = 10
        )
        if (trades.size >= 10) {
            assertTrue(metrics.inSampleMetrics != null)
            assertTrue(metrics.outOfSampleMetrics != null)
            assertEquals(
                trades.size,
                metrics.inSampleMetrics!!.trades + metrics.outOfSampleMetrics!!.trades
            )
        }
    }
}
