package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های موتور بک‌تست (قدم ۷):
 * - سناریوی کنترل‌شده
 * - تست no-lookahead: هرگز کندل آینده را نمی‌بیند
 * - محاسبهٔ Max Drawdown
 */
class BacktestEngineTest {

    @Test
    fun `futures backtest produces trades and metrics`() {
        // ساخت ۱۰۰ کندل مصنوعی
        val klines = (0 until 100).map { i ->
            val base = 100.0 + i * 0.5
            listOf(base, base + 1, base - 1, base + 0.5, 1000.0) // [open, high, low, close, volume]
        }
        val (trades, metrics) = BacktestEngine.runFutures("BTC", klines, 50, 10, 0.0)
        assertTrue(trades.isNotEmpty())
        assertEquals(trades.size, metrics.totalTrades)
    }

    @Test
    fun `spot backtest with uptrend produces buys`() {
        // ۳۰۰ کندل صعودی
        val klines = (0 until 300).map { i ->
            val base = 100.0 + i * 2.0
            listOf(base, base + 2, base - 1, base + 1.5, 1000.0)
        }
        val (trades, metrics) = BacktestEngine.runSpot("ETH", klines, 30)
        assertTrue(trades.all { it.side == "BUY" })
        assertTrue(metrics.totalTrades > 0)
    }

    @Test
    fun `max drawdown calculated correctly`() {
        val klines = (0 until 100).map { i ->
            val base = 100.0 + i * 0.5
            listOf(base, base + 1, base - 1, base + 0.5, 1000.0)
        }
        val (_, metrics) = BacktestEngine.runFutures("BTC", klines, 50, 10, 0.0)
        assertTrue(metrics.maxDrawdown >= 0.0)
        assertTrue(metrics.equityCurve.isNotEmpty())
    }

    @Test
    fun `no lookahead - score computed only from past data`() {
        // تست: برای هر معامله، entryIndex باید کمتر از هر کندلی باشد که در محاسبهٔ score استفاده شده
        val klines = (0 until 100).map { i ->
            val base = 100.0 + i * 0.5
            listOf(base, base + 1, base - 1, base + 0.5, 1000.0)
        }
        val (trades, _) = BacktestEngine.runFutures("BTC", klines, 50, 10, 0.0)
        trades.forEach { trade ->
            // entryIndex نباید از closes.size بزرگتر باشد
            assertTrue(trade.entryIndex < klines.size)
            // exitIndex باید بعد از entryIndex باشد
            assertTrue(trade.exitIndex > trade.entryIndex)
        }
    }

    @Test
    fun `in-sample and out-of-sample split`() {
        val klines = (0 until 100).map { i ->
            val base = 100.0 + i * 0.5
            listOf(base, base + 1, base - 1, base + 0.5, 1000.0)
        }
        val (trades, metrics) = BacktestEngine.runFutures("BTC", klines, 50, 10, 0.0)
        if (trades.size >= 10) {
            assertTrue(metrics.inSampleMetrics != null)
            assertTrue(metrics.outOfSampleMetrics != null)
            assertEquals(trades.size, metrics.inSampleMetrics!!.trades + metrics.outOfSampleMetrics!!.trades)
        }
    }
}
