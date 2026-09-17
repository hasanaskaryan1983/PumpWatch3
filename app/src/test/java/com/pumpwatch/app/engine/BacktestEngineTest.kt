package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Sprint 13 — تست‌های واحد BacktestEngine
 *
 * این فایل بازنویسی‌شدهٔ BacktestEngineTest قدیمی است که interface منسوخ
 * (signalThreshold, entryIndex/exitIndex, .trades field) را فرض می‌کرد.
 * تست‌های زیر با interface فعلی سازگارند:
 *   - runSpot(symbol, klines, holdDays) → Pair<List<Trade>, BacktestMetrics>
 *   - runFutures(symbol, klines, evalLast, hold, feeRate) → Pair<List<Trade>, BacktestMetrics>
 *   - Trade(symbol, side, result, pnl, score)
 *   - BacktestMetrics(totalTrades, wins, losses, expired, ...)
 */
class BacktestEngineTest {

    // ---------- Helpers برای ساخت دادهٔ مصنوعی ----------

    /**
     * تولید کندل‌های [open, high, low, close, volume]
     * روند: خطی با noise
     */
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

    /**
     * تولید کندل‌های صعودی قدرتمند (بدون noise برای قابل پیش‌بینی بودن)
     */
    private fun makeBullishCandles(count: Int, start: Double = 100.0): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var price = start
        for (i in 0 until count) {
            val open = price
            val close = price * 1.01  // 1% رشد در هر کندل
            val high = close * 1.002
            val low = open * 0.998
            out.add(listOf(open, high, low, close, 1_000_000.0))
            price = close
        }
        return out
    }

    /**
     * تولید کندل‌های نزولی قدرتمند
     */
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

    // ---------- تست‌های helper های pure ----------

    @Test
    fun test_empty_input_returns_empty() {
        val (trades, metrics) = BacktestEngine.runSpot("BTC", emptyList(), 30)
        assertTrue("empty input should yield 0 trades", trades.isEmpty())
        assertEquals(0, metrics.totalTrades)
    }

    @Test
    fun test_insufficient_data_returns_empty() {
        // کمتر از ۲۵۰ کندل برای اسپات کافی نیست
        val candles = makeCandles(100)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        assertTrue("insufficient data should yield 0 trades", trades.isEmpty())
        assertEquals(0, metrics.totalTrades)
    }

    @Test
    fun test_runSpot_bullish_run_produces_trades() {
        // ۴۰۰ کندل روزانهٔ صعودی قوی → باید چندین سیگنال خرید تولید کند
        val candles = makeBullishCandles(400)
        val (trades, metrics) = BacktestEngine.runSpot("BTC", candles, 30)
        assertTrue(
            "bullish run should produce at least 1 trade, got ${trades.size}",
            trades.size >= 1
        )
        assertEquals(trades.size, metrics.totalTrades)
        // همهٔ معاملات باید BUY باشند
        assertTrue("all trades should be BUY", trades.all { it.side == "BUY" })
    }

    @Test
    fun test_runSpot_strong_bullish_should_be_profitable() {
        // در روند صعودی قوی، اکثر معاملات باید WIN باشند
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
        // در روند نزولی قوی، امتیاز منفی است و هیچ خریدی نباید باز شود
        val candles = makeBearishCandles(400)
        val (trades, _) = BacktestEngine.runSpot("BTC", candles, 30)
        // ممکن است در ابتدا چند خرید داشته باشد (چون EMA200 هنوز در حال ساخت است)،
        // اما بعد از تثبیت EMA200 باید خریدها متوقف شوند
        // انتظار: تعداد خریدها خیلی کمتر از حالت صعودی
        assertTrue(
            "bearish run should have very few buys, got ${trades.size}",
            trades.size <= 3
        )
    }

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
    fun test_runFutures_bullish_produces_buy_signals() {
        val candles = makeBullishCandles(200)
        val (trades, _) = BacktestEngine.runFutures("BTC", candles, 80, 8, 0.0001)
        // در روند صعودی، بیشتر معاملات باید BUY باشند
        val buys = trades.count { it.side == "BUY" }
        val sells = trades.count { it.side == "SELL" }
        assertTrue(
            "bullish should have more buys than sells (got buys=$buys sells=$sells)",
            buys >= sells
        )
    }

    @Test
    fun test_runFutures_bearish_produces_sell_signals() {
        val candles = makeBearishCandles(200)
        val (trades, _) = BacktestEngine.runFutures("BTC", candles, 80, 8, 0.0001)
        val buys = trades.count { it.side == "BUY" }
        val sells = trades.count { it.side == "SELL" }
        assertTrue(
            "bearish should have more sells than buys (got buys=$buys sells=$sells)",
            sells >= buys
        )
    }

    // ---------- تست‌های ساختار Trade ----------

    @Test
    fun test_trade_fields_populated() {
        val candles = makeBullishCandles(400)
        val (trades, _) = BacktestEngine.runSpot("BTC", candles, 30)
        if (trades.isEmpty()) return  // skip اگر سیگنالی نبود

        val t = trades.first()
        assertEquals("BTC", t.symbol)
        assertEquals("BUY", t.side)
        assertTrue(
            "result should be WIN/LOSS/EXP",
            t.result in listOf("WIN", "LOSS", "EXP")
        )
        // pnl یک مقدار واقعی (ممکن است منفی یا مثبت)
        assertTrue("pnl should be finite", t.pnl.isFinite())
        // score باید در بازهٔ معنی‌دار باشد
        assertTrue("score should be in [-100, 100]", t.score in -100..100)
    }

    // ---------- تست‌های metrics ----------

    @Test
    fun test_metrics_consistency() {
        val candles = makeBullishCandles(600)
        val (_, metrics) = BacktestEngine.runSpot("BTC", candles, 30)

        if (metrics.totalTrades == 0) return  // skip

        assertEquals(
            "wins + losses + expired = totalTrades",
            metrics.totalTrades,
            metrics.wins + metrics.losses + metrics.expired
        )

        if (metrics.wins + metrics.losses > 0) {
            val expected = metrics.wins * 100.0 / (metrics.wins + metrics.losses)
            assertTrue(
                "winRate should match wins/decided",
                abs(metrics.winRate - expected) < 0.01
            )
        }

        assertTrue("equityCurve should have trades+1 points",
            metrics.equityCurve.size == metrics.totalTrades + 1)
        assertTrue("equityCurve starts at 100",
            abs(metrics.equityCurve[0] - 100.0) < 0.01)

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

        // بازسازی maxDD از equityCurve
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

    // ---------- تست‌های edge case ----------

    @Test
    fun test_holdDays_zero_does_not_crash() {
        val candles = makeBullishCandles(400)
        val (_, metrics) = BacktestEngine.runSpot("BTC", candles, 0)
        assertNotNull("should not crash with holdDays=0", metrics)
    }

    @Test
    fun test_flat_market_few_signals() {
        // بازار تخت (بدون روند) → امتیاز نزدیک به ۰ → تقریباً هیچ سیگنالی
        val candles = makeCandles(400, trendPerBar = 0.0, noise = 0.5)
        val (trades, _) = BacktestEngine.runSpot("BTC", candles, 30)
        assertTrue(
            "flat market should have few signals (got ${trades.size})",
            trades.size <= 5
        )
    }
}
