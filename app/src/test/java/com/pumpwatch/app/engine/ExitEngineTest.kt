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
