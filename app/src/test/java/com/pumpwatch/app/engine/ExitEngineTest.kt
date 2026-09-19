package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitEngineTest {

    private fun rising(n: Int, start: Double = 100.0, step: Double = 1.0): MutableList<Double> =
        MutableList(n) { start + it * step }

    private fun ctx(
        side: String = "PUMP",
        entry: Double = 100.0,
        stop: Double = 90.0,
        target: Double? = 130.0,
        closes: List<Double>,
        live: Double,
        partialClose: Boolean = false
    ) = ExitContext(side, entry, stop, stop, target, closes, live, partialClose)

    @Test fun hard_stop_closes_at_stop_level() {
        val d = ExitEngine.decide(ctx(closes = rising(25), live = 89.0))
        assertEquals("CLOSE", d.action); assertEquals("STOP", d.reason)
        assertEquals(90.0, d.exitPrice!!, 0.0001)
    }

    @Test fun target_fills_at_target_not_live() {
        val d = ExitEngine.decide(ctx(closes = rising(25), live = 135.0))
        assertEquals("CLOSE", d.action); assertEquals("TARGET", d.reason)
        assertEquals(130.0, d.exitPrice!!, 0.0001)
    }

    @Test fun volatility_spike_closes_immediately() {
        val d = ExitEngine.decide(ctx(closes = listOf(100.0, 100.2, 100.0, 100.2, 100.0), live = 95.0))
        assertEquals("CLOSE", d.action); assertEquals("VOL_SPIKE", d.reason)
    }

    @Test fun dead_trade_hits_time_stop() {
        val closes = MutableList(30) { 100.0 + (if (it % 2 == 0) 0.0 else 0.1) }
        val d = ExitEngine.decide(ctx(closes = closes, live = 105.0))
        assertEquals("CLOSE", d.action); assertEquals("TIME", d.reason)
    }

    @Test fun trend_and_momentum_confluence_closes_fast() {
        val closes = rising(20).apply { addAll(listOf(116.0, 113.0, 110.0, 107.0, 104.0, 101.0)) }
        val d = ExitEngine.decide(ctx(closes = closes, live = 101.0))
        assertEquals("CLOSE", d.action); assertEquals("MOMENTUM_RSI", d.reason)
    }

    @Test fun single_detector_only_tightens_not_closes() {
        val closes = rising(20).apply { add(112.0) }
        val d = ExitEngine.decide(ctx(closes = closes, live = 112.0))
        assertEquals("TRAIL", d.action); assertEquals("TIGHTEN", d.reason)
        assertTrue(d.newStop!! >= 100.0)
    }

    @Test fun breakeven_locked_after_one_r() {
        val d = ExitEngine.decide(ctx(closes = rising(30), live = 129.0))
        assertEquals("TRAIL", d.action)
        assertTrue(d.newStop!! >= 100.0)
    }

    @Test fun chandelier_ratchets_tighter_as_profit_grows() {
        val d1 = ExitEngine.decide(ctx(closes = rising(25), live = 124.0, target = null))
        val d2 = ExitEngine.decide(ctx(closes = rising(31), live = 130.0, target = null))
        assertEquals("TRAIL", d1.action); assertEquals("TRAIL", d2.action)
        assertTrue(d2.newStop!! > d1.newStop!!)
    }

    @Test fun short_side_is_mirror() {
        val d = ExitEngine.decide(
            ctx(side = "DUMP", entry = 100.0, stop = 110.0, target = null,
                closes = MutableList(30) { 100.0 - it * 1.0 }, live = 71.0)
        )
        assertEquals("TRAIL", d.action)
        assertTrue(d.newStop!! <= 100.0)
    }

    @Test fun insufficient_history_holds_without_guessing() {
        val d = ExitEngine.decide(ctx(closes = rising(5), live = 103.0))
        assertEquals("HOLD", d.action); assertNull(d.newStop)
    }

    // ---------- 🚀 Commit 11: PARTIAL (Take-profit پله‌ای) ----------

    @Test fun partial_fires_at_one_r_when_not_yet_partialed() {
        // profitR = 1.2, هنوز partial نشده
        val closes = rising(22) // 100..121, ATR=1
        val d = ExitEngine.decide(ctx(closes = closes, live = 112.0, partialClose = false))
        assertEquals("PARTIAL", d.action)
        assertEquals("TP1", d.reason)
        assertEquals(112.0, d.exitPrice!!, 0.0001)
    }

    @Test fun partial_skipped_if_already_partialed() {
        // همان شرایط ولی partialClose = true => دیگر PARTIAL نمی‌آید، TRAIL می‌آید
        val closes = rising(22)
        val d = ExitEngine.decide(ctx(closes = closes, live = 112.0, partialClose = true))
        assertEquals("TRAIL", d.action)
        assertTrue(d.newStop!! >= 100.0)
    }

    @Test fun vol_spike_beats_partial() {
        // profitR = 1.5 ولی اسپایک بزرگ مخالف
        val closes = listOf(100.0, 100.2, 100.0, 100.2, 115.0)
        val d = ExitEngine.decide(ctx(closes = closes, live = 105.0))
        assertEquals("VOL_SPIKE", d.reason)  // اسپایک اولویت بالاتر از PARTIAL
        assertEquals("CLOSE", d.action)
    }

    @Test fun stop_beats_partial() {
        // profitR = 0.8 و به استاپ می‌رسد
        val closes = rising(22)
        val d = ExitEngine.decide(ctx(closes = closes, live = 89.0))
        assertEquals("CLOSE", d.action); assertEquals("STOP", d.reason)
    }
}
