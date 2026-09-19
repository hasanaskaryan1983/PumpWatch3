package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 15 (فاز ۱ / Commit 10): تست‌های pure موتور خروج
 *
 * درس: live باید نزدیک به closes.last() باشد مگر اینکه واقعاً
 * در حال تست اسپایک باشیم. وگرنه آشکارساز VOL_SPIKE شلیک می‌کند.
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

    // ---------- ۱) استاپ سخت ----------

    @Test
    fun hard_stop_closes_at_stop_level() {
        val d = ExitEngine.decide(ctx(closes = rising(25), live = 89.0))
        assertEquals("CLOSE", d.action)
        assertEquals("STOP", d.reason)
        assertEquals(90.0, d.exitPrice!!, 0.0001)
    }

    // ---------- ۲) تارگت صادقانه ----------

    @Test
    fun target_fills_at_target_not_live() {
        val d = ExitEngine.decide(ctx(closes = rising(25), live = 135.0))
        assertEquals("CLOSE", d.action)
        assertEquals("TARGET", d.reason)
        assertEquals(130.0, d.exitPrice!!, 0.0001)
    }

    // ---------- ۳) اسپایک نوسان (این تست *خودش* اسپایک را چک می‌کند) ----------

    @Test
    fun volatility_spike_closes_immediately() {
        val closes = listOf(100.0, 100.2, 100.0, 100.2, 100.0)
        val d = ExitEngine.decide(ctx(closes = closes, live = 95.0))
        assertEquals("CLOSE", d.action)
        assertEquals("VOL_SPIKE", d.reason)
    }

    // ---------- ۴) توقف زمانی ----------

    @Test
    fun dead_trade_hits_time_stop() {
        val closes = MutableList(30) { 100.0 + (if (it % 2 == 0) 0.0 else 0.1) }
        val d = ExitEngine.decide(ctx(closes = closes, live = 105.0))
        assertEquals("CLOSE", d.action)
        assertEquals("TIME", d.reason)
    }

    // ---------- ۵) تغییر جهت = بستن فوری ----------

    @Test
    fun trend_and_momentum_confluence_closes_fast() {
        val closes = rising(20) // 100..119
        closes.addAll(listOf(116.0, 113.0, 110.0, 107.0, 104.0, 101.0))
        val d = ExitEngine.decide(ctx(closes = closes, live = 101.0))
        assertEquals("CLOSE", d.action)
        assertEquals("MOMENTUM_RSI", d.reason)
    }

    // ---------- ۶) یک آشکارساز = فقط سفت‌کردن ----------

    @Test
    fun single_detector_only_tightens_not_closes() {
        val closes = rising(20)
        closes.add(112.0)
        val d = ExitEngine.decide(ctx(closes = closes, live = 112.0))
        assertEquals("TRAIL", d.action)
        assertEquals("TIGHTEN", d.reason)
        assertTrue(d.newStop!! >= 100.0)
    }

    // ---------- ۷) BE و نردبان تریل (اصلاح‌شده) ----------

    @Test
    fun breakeven_locked_after_one_r() {
        // 🚀 fix: live = closes.last() تا اسپایک شلیک نکند
        val closes = rising(30)  // 100..129
        val d = ExitEngine.decide(ctx(closes = closes, live = 129.0))
        // profitR = 2.9, atr = 1.0 => TRAIL, stop >= 100 (BE)
        assertEquals("TRAIL", d.action)
        assertTrue("after +1R stop must be >= entry", d.newStop!! >= 100.0)
    }

    @Test
    fun chandelier_ratchets_tighter_as_profit_grows() {
        // 🚀 fix: live = closes.last() برای هر دو سناریو
        val closes1 = rising(25)  // 100..124
        val d1 = ExitEngine.decide(ctx(closes = closes1, live = 124.0))
        // profitR = 2.4 => k = 2.0
        val closes2 = rising(36)  // 100..135
        val d2 = ExitEngine.decide(ctx(closes = closes2, live = 135.0))
        // profitR = 3.5 => k = 1.5 (تریل سفت‌تر)
        assertEquals("TRAIL", d1.action)
        assertEquals("TRAIL", d2.action)
        assertTrue("higher profit must give tighter (higher) stop for longs", d2.newStop!! > d1.newStop!!)
    }

    // ---------- ۸) تقارن شورت (اصلاح‌شده) ----------

    @Test
    fun short_side_is_mirror() {
        val closes = MutableList(30) { 100.0 - it * 1.0 }  // 100..71
        // 🚀 fix: live = 71 (آخرین close)، نه 85 (که اسپایک بود)
        val d = ExitEngine.decide(
            ctx(side = "DUMP", entry = 100.0, stop = 110.0, target = 70.0, closes = closes, live = 71.0)
        )
        // profitR = 2.9 => BE, stop <= 100
        assertEquals("TRAIL", d.action)
        assertTrue("short stop must ratchet down to <= entry", d.newStop!! <= 100.0)
    }

    @Test
    fun insufficient_history_holds_without_guessing() {
        val d = ExitEngine.decide(ctx(closes = rising(5), live = 103.0))
        assertEquals("HOLD", d.action)
        assertNull(d.newStop)
    }
}
