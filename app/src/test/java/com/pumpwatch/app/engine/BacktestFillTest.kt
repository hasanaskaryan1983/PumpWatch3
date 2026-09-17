package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 14: regression tests for H6 + M7 backtest fill rules.
 */
class BacktestFillTest {

    private fun candle(
        o: Double, h: Double, l: Double, c: Double, v: Double = 1_000_000.0
    ): List<Double> = listOf(o, h, l, c, v)

    private fun baseUptrend(): MutableList<List<Double>> {
        val out = mutableListOf<List<Double>>()
        var prevClose = 100.0 / 1.005
        for (i in 0 until 260) {
            val c = prevClose * 1.005
            out.add(candle(prevClose, c * 1.001, prevClose * 0.999, c))
            prevClose = c
        }
        return out
    }

    private fun closeAt(i: Int): Double = 100.0 * Math.pow(1.005, i.toDouble())

    private fun setEntryBarGap(k: MutableList<List<Double>>) {
        val c210 = closeAt(210)
        k[211] = candle(c210 * 1.002, c210 * 1.004 * 1.001, c210 * 1.002 * 0.999, c210 * 1.004)
    }

    private fun setEntryBarNoGap(k: MutableList<List<Double>>) {
        val c210 = closeAt(210)
        k[211] = candle(c210, c210 * 1.004 * 1.001, c210 * 0.999, c210 * 1.004)
    }

    @Test
    fun both_levels_touched_stop_wins_conservatively() {
        val k = baseUptrend()
        setEntryBarGap(k)
        val c210 = closeAt(210)
        k[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.97, c210 * 1.01)

        val (trades, _) = BacktestEngine.runSpot("TEST", k, 30)
        assertTrue("expected at least one trade", trades.isNotEmpty())
        val t = trades.first()
        assertEquals("BUY", t.side)
        assertTrue("both levels in one candle => STOP wins", t.result == "LOSS")
        assertTrue("stop-out must be negative after fees", t.pnl < 0)
    }

    @Test
    fun target_touch_fills_at_target_not_at_close() {
        val k = baseUptrend()
        setEntryBarGap(k)
        val c210 = closeAt(210)
        k[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val (trades, _) = BacktestEngine.runSpot("TEST", k, 30)
        val t = trades.first()
        assertTrue("target hit => WIN", t.result == "WIN")
        assertTrue("fill at target (intrabar) > close-based fill", t.pnl > 1.0)
        assertTrue("round-trip fee deducted", t.pnl < 1.9)
    }

    @Test
    fun gap_through_target_fills_at_open() {
        val c210 = closeAt(210)

        val kB = baseUptrend()
        setEntryBarGap(kB)
        kB[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val kC = baseUptrend()
        setEntryBarGap(kC)
        kC[212] = candle(c210 * 1.03, c210 * 1.05, c210 * 1.02, c210 * 1.04)

        val tB = BacktestEngine.runSpot("TEST", kB, 30).first.first()
        val tC = BacktestEngine.runSpot("TEST", kC, 30).first.first()

        assertTrue(tB.result == "WIN")
        assertTrue(tC.result == "WIN")
        assertTrue("gap-up open fills at open => better price", tC.pnl > tB.pnl)
    }

    @Test
    fun entry_uses_next_bar_open_not_signal_close() {
        val c210 = closeAt(210)

        val kGap = baseUptrend()
        setEntryBarGap(kGap)
        kGap[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val kNoGap = baseUptrend()
        setEntryBarNoGap(kNoGap)
        kNoGap[212] = candle(c210 * 1.004, c210 * 1.05, c210 * 0.99, c210 * 1.01)

        val tGap = BacktestEngine.runSpot("TEST", kGap, 30).first.first()
        val tNoGap = BacktestEngine.runSpot("TEST", kNoGap, 30).first.first()

        assertTrue("higher entry (next-bar gap open) reduces profit", tGap.pnl < tNoGap.pnl)
    }

    @Test
    fun spot_replay_is_deterministic() {
        val k = baseUptrend()
        val (t1, m1) = BacktestEngine.runSpot("TEST", k, 30)
        val (t2, m2) = BacktestEngine.runSpot("TEST", k, 30)
        assertEquals(t1, t2)
        assertEquals(m1, m2)
    }

    @Test
    fun futures_replay_is_deterministic_even_without_signals() {
        val k = mutableListOf<List<Double>>()
        var prev = 100.0
        for (i in 0 until 200) {
            val c = prev * 1.005
            k.add(candle(prev, c * 1.001, prev * 0.999, c))
            prev = c
        }
        for (i in 0 until 60) {
            val c = if (i % 2 == 0) prev * 1.001 else prev * 0.9995
            k.add(candle(prev, c * 1.001, prev * 0.999, c))
            prev = c
        }

        val (t1, m1) = BacktestEngine.runFutures("TEST", k, 260, 12, 0.0001)
        val (t2, m2) = BacktestEngine.runFutures("TEST", k, 260, 12, 0.0001)
        assertEquals(t1, t2)
        assertEquals(m1, m2)
        assertTrue(t1.all { it.side == "BUY" || it.side == "SELL" })
        assertTrue(t1.all { it.result in listOf("WIN", "LOSS", "EXP") })
    }

    @Test
    fun futures_cross_triggers_signal() {
        val k = mutableListOf<List<Double>>()
        var prev = 100.0
        for (i in 0 until 80) {
            k.add(candle(prev, prev * 1.001, prev * 0.999, prev))
        }
        for (i in 0 until 20) {
            val c = prev * 0.985
            k.add(candle(prev, prev * 1.002, c * 0.998, c))
            prev = c
        }
        for (i in 0 until 30) {
            val c = prev * 1.02
            k.add(candle(prev, c * 1.005, prev * 0.998, c))
            prev = c
        }

        val (trades, _) = BacktestEngine.runFutures("TEST", k, 130, 12, 0.0001)
        assertTrue("real bullish crossover => at least one BUY", trades.any { it.side == "BUY" })
    }
}
