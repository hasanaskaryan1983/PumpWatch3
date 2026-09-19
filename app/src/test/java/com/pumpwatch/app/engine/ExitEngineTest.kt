package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 15 (فاز ۱ / Commit 10): تست‌های pure موتور خروج
 */
class ExitEngineTest {

    private fun rising(n: Int, start: Double = 100.0, step: Double = 1.0): MutableList<Double> =
        MutableList(n) { start + it * step }

    private fun ctx(
        side: String = "PUMP",
        entry: Double = 100.0,
        stop: Double = 90.0,
        target: Double? = 130.0,
        closes: List<Double>,
        live: Double
    ) = ExitContext(side, entry, stop, stop, target, closes, live)

    @Test
    fun hard_stop_closes_at_stop_level() {
        val d = ExitEngine.decide(ctx(closes = rising(25), live = 89.0))
        assertEquals("CLOSE", d.action)
        assertEquals("STOP", d.reason)
        assertEquals(90.0, d.exitPrice!!, 0.0001)
    }

    @Test
    fun target_fills_at_target_not_live() {
        val d = ExitEngine.decide(ctx(closes = rising(25), live = 135.0))
        assertEquals("CLOSE", d.action)
        assertEquals("TARGET", d.reason)
        assertEquals(130.0, d.exitPrice!!, 0.0001)
    }

    @Test
    fun volatility_spike_closes_immediately() {
        val closes = listOf(100.0, 100.2, 100.0, 100.2, 100.0)
        val d = ExitEngine.decide(ctx(closes = closes, live = 95.0))
        assertEquals("CLOSE", d.action)
        assertEquals("VOL_SPIKE", d.reason)
    }

    @Test
    fun dead_trade_hits_time_stop() {
        val closes = MutableList(30) { 100.0 + (if (it % 2 == 0) 0.0 else 0.1) }
        val d = ExitEngine.decide(ctx(closes = closes, live = 105.0))
        assertEquals("CLOSE", d.action)
        assertEquals("TIME", d.reason)
    }

    @Test
    fun trend_and_momentum_confluence_closes_fast() {
        val closes = rising(20) // 100..119
        closes.addAll(listOf(116.0, 113.0, 110.0, 107.0, 104.0, 101.0))
        val d = ExitEngine.decide(ctx(closes = closes, live = 101.0))
        assertEquals("CLOSE", d.action)
        assertEquals("MOMENTUM_RSI", d.reason)
    }

    @Test
    fun single_detector_only_tightens_not_closes() {
        val closes = rising(20)
        closes.add(112.0)
        val d = ExitEngine.decide(ctx(closes = closes, live = 112.0))
        assertEquals("TRAIL", d.action)
        assertEquals("TIGHTEN", d.reason)
        assertTrue(d.newStop!! >= 100.0)
    }

    @Test
    fun breakeven_locked_after_one_r() {
        val closes = rising(30)
        val d = ExitEngine.decide(ctx(closes = closes, live = 115.0))
        assertEquals("TRAIL", d.action)
        assertTrue(d.newStop!! >= 100.0)
    }

    @Test
    fun chandelier_ratchets_tighter_as_profit_grows() {
        val closes = rising(30)
        val d1 = ExitEngine.decide(ctx(closes = closes, live = 112.0))
        val d2 = ExitEngine.decide(ctx(closes = closes, live = 135.0))
        assertTrue(d2.newStop!! > d1.newStop!!)
    }

    @Test
    fun short_side_is_mirror() {
        val closes = MutableList(30) { 100.0 - it * 1.0 }
        val d = ExitEngine.decide(
            ctx(side = "DUMP", entry = 100.0, stop = 110.0, target = 70.0, closes = closes, live = 85.0)
        )
        assertEquals("TRAIL", d.action)
        assertTrue(d.newStop!! <= 100.0)
    }

    @Test
    fun insufficient_history_holds_without_guessing() {
        val d = ExitEngine.decide(ctx(closes = rising(5), live = 103.0))
        assertEquals("HOLD", d.action)
        assertNull(d.newStop)
    }
}
