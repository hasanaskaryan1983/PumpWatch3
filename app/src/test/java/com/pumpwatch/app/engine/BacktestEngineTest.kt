package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Sprint 13 — تست‌های واحد BacktestEngine
 *
 * بازنویسی‌شده برای interface فعلی موتور:
 *   - runSpot(symbol, klines, holdDays) → Pair<List<Trade>, BacktestMetrics>
 *   - runFutures(symbol, klines, evalLast, hold, feeRate) → Pair<List<Trade>, BacktestMetrics>
 *   - Trade(symbol, side, result, pnl, score)
 *
 * درس شکست CI #702: assertion کمی روی «تعداد سیگنال» در دادهٔ تصادفی
 * شکننده است. تست‌های تصادفی فقط سازگاری metrics را می‌سنجند؛
 * تست‌های کمیِ قطعی فقط روی دادهٔ deterministic (بدون noise) اجرا می‌شوند.
 */
class BacktestEngineTest {

    // ---------- Helpers ساخت دادهٔ مصنوعی ----------

    /** کندل‌های [open, high, low, close, volume] با روند خطی + noise تصادفی */
    private fun makeCandles(
        count: Int,
        startPrice: Double = 100.0,
        trendPerBar: Double = 0.0,
        noise: Double = 1.0,
        volume: Double = 1_000_000.0
    ): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var price = startPrice
        for (i in 0 until count) {
            val open = price
            val close = price + trendPerBar + (Math.random() - 0.5) * noise
            val high = maxOf(open, close) + Math.random() * noise * 0.5
            val low = minOf(open, close) - Math.random() * noise * 0.5
            out.add(listOf(open, high, low, close, volume))
            price = close
        }
        return out
    }

    /** روند صعودی صاف و deterministic (+1% در هر کندل، بدون noise) */
    private fun makeBullishCandles(count: Int, start: Double = 100.0): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var price = start
        for (i in 0 until count) {
            val open = price
            val close = price * 1.01
            val high = close * 1.002
            val low = open * 0.998
            out.add(listOf(open, high, low, close, 1_000_000.0))
            price = close
        }
        return out
    }

    /** روند نزولی صاف و deterministic (-1% در هر کندل، بدون noise) */
    private fun makeBearishCandles(count: Int, start: Double = 100.0): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var price = start
        for (i in 0 until count) {
            val open = price
            val close = price * 0.99
            val high = open * 1.002
            val low = close * 0.998
            out.add(listOf(open, high, low, close, 1_000_000.0))
            price = close
        }
        return out
    }

    // ---------- Edge cases ورودی ----------

    @Test
    fun test_empty_input_returns_empty() {
        val (trades, metrics) = BacktestEngine.runSpot("BTC", emptyList(), 30)
        assertTrue("empty input should yield 0 trades", trades.isEmpty())
        assertEquals(0, metrics.totalTrades)
    }

    @Test
    fun test_insufficient_data_returns_empty() {
        val candles = makeCandles(100)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        assertTrue("insufficient data should yield 0 trades", trades.isEmpty())
        assertEquals(0, metrics.totalTrades)
    }

    // ---------- رفتار اسپات روی روند deterministic ----------

    @Test
    fun test_runSpot_bullish_run_produces_trades() {
        val candles = makeBullishCandles(400)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        assertTrue(
            "bullish run should produce at least 1 trade, got ${trades.size}",
            trades.size >= 1
        )
        assertEquals(trades.size, metrics.totalTrades)
        assertTrue("all spot trades should be BUY", trades.all { it.side == "BUY" })
    }

    @Test
    fun test_runSpot_strong_bullish_should_be_profitable() {
        val candles = makeBullishCandles(600)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        if (trades.isNotEmpty()) {
            assertTrue(
                "strong bull should have > 50% winrate, got ${metrics.winRate}%",
                metrics.winRate > 50.0
            )
        }
    }

    @Test
    fun test_runSpot_bearish_run_no_buys() {
        val candles = makeBearishCandles(400)
        val (trades, _) = BacktestEngine.runSpot("BTC", candles, 30)
        assertTrue(
            "bearish run should have very few buys, got ${trades.size}",
            trades.size <= 3
        )
    }

    // ---------- رفتار فیوچرز روی روند deterministic ----------

    @Test
    fun test_runFutures_empty_input() {
        val (trades, metrics) = BacktestEngine.runFutures("BTC", emptyList(), 60, 8, 0.0001)
        assertTrue(trades.isEmpty())
        assertEquals(0, metrics.totalTrades)
    }

    @Test
    fun test_runFutures_insufficient_data() {
        val candles = makeCandles(50)
        val (trades, _) = BacktestEngine.runFutures("BTC", candles, 40, 8, 0.0001)
        assertTrue(trades.isEmpty())
    }

    @Test
    fun test_runFutures_overextended_bull_no_signals() {
        // روند صافِ بدون pullback: RSI اشباع + مومنتوم overextended
        // → موتور صادقانه صبر می‌کند و سیگنالی نمی‌دهد (deterministic)
        val candles = makeBullishCandles(200)
        val (trades, _) = BacktestEngine.runFutures("BTC", candles, 80, 8, 0.0001)
        assertTrue(
            "overextended bull without pullback should yield no futures signal, got ${trades.size}",
            trades.isEmpty()
        )
    }

    @Test
    fun test_runFutures_overextended_bear_no_signals() {
        val candles = makeBearishCandles(200)
        val (trades, _) = BacktestEngine.runFutures("BTC", candles, 80, 8, 0.0001)
        assertTrue(
            "overextended bear without pullback should yield no futures signal, got ${trades.size}",
            trades.isEmpty()
        )
    }

    // ---------- ساختار Trade ----------

    @Test
    fun test_trade_fields_populated() {
        val candles = makeBullishCandles(400)
        val (trades, _) = BacktestEngine.runSpot("BTC", candles, 30)
        if (trades.isEmpty()) return

        val t = trades.first()
        assertEquals("BTC", t.symbol)
        assertEquals("BUY", t.side)
        assertTrue("result should be WIN/LOSS/EXP", t.result in listOf("WIN", "LOSS", "EXP"))
        assertTrue("pnl should be finite", t.pnl.isFinite())
        assertTrue("score should be in [-100, 100]", t.score in -100..100)
    }

    // ---------- سازگاری Metrics ----------

    @Test
    fun test_metrics_consistency() {
        val candles = makeBullishCandles(600)
        val (_, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        if (metrics.totalTrades == 0) return

        assertEquals(
            "wins + losses + expired = totalTrades",
            metrics.totalTrades,
            metrics.wins + metrics.losses + metrics.expired
        )
        if (metrics.wins + metrics.losses > 0) {
            val expected = metrics.wins * 100.0 / (metrics.wins + metrics.losses)
            assertTrue("winRate should match wins/decided", abs(metrics.winRate - expected) < 0.01)
        }
        assertTrue(
            "equityCurve should have trades+1 points",
            metrics.equityCurve.size == metrics.totalTrades + 1
        )
        assertTrue("equityCurve starts at 100", abs(metrics.equityCurve[0] - 100.0) < 0.01)
        assertTrue("maxDrawdown should be >= 0", metrics.maxDrawdown >= 0)
        assertTrue("profitFactor should be >= 0", metrics.profitFactor >= 0)
    }

    @Test
    fun test_profitFactor_logic() {
        val candles = makeBullishCandles(600)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        if (trades.isEmpty()) return

        val wins = trades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val losses = abs(trades.filter { it.pnl < 0 }.sumOf { it.pnl })
        if (losses > 0) {
            val expected = wins / losses
            assertTrue(
                "profitFactor should match wins/losses (got ${metrics.profitFactor} vs $expected)",
                abs(metrics.profitFactor - expected) < 0.01
            )
        }
    }

    @Test
    fun test_maxDrawdown_logic() {
        val candles = makeBullishCandles(600)
        val (_, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        if (metrics.totalTrades == 0) return

        var peak = metrics.equityCurve[0]
        var maxDD = 0.0
        for (e in metrics.equityCurve) {
            if (e > peak) peak = e
            val dd = (peak - e) / peak * 100.0
            if (dd > maxDD) maxDD = dd
        }
        assertTrue(
            "maxDrawdown should match recomputation (got ${metrics.maxDrawdown} vs $maxDD)",
            abs(metrics.maxDrawdown - maxDD) < 0.01
        )
    }

    // ---------- Edge cases رفتار ----------

    @Test
    fun test_holdDays_zero_does_not_crash() {
        val candles = makeBullishCandles(400)
        val (_, metrics) = BacktestEngine.runSpot("BTC", candles, 0)
        assertNotNull("should not crash with holdDays=0", metrics)
    }

    @Test
    fun test_flat_market_metrics_still_consistent() {
        // درس CI #702: شمارش سیگنال در دادهٔ تصادفی شکننده است.
        // بازار تخت فقط باید «نسازد» و metrics سازگار بدهد.
        val candles = makeCandles(400, trendPerBar = 0.0, noise = 0.5)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)

        assertEquals(trades.size, metrics.totalTrades)
        assertEquals(
            "wins + losses + expired must equal totalTrades even in flat market",
            metrics.totalTrades,
            metrics.wins + metrics.losses + metrics.expired
        )
        assertTrue("equityCurve length must match", metrics.equityCurve.size == trades.size + 1)
        assertTrue("maxDrawdown must be non-negative", metrics.maxDrawdown >= 0.0)
    }
}
