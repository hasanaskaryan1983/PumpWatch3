package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 15 (فاز ۱ / Commit 12): تست‌های مقایسهٔ A/B
 * هر تست یک سناریوی واقعی بازار را replay می‌کند و دو سیاست را می‌سنجد.
 */
class ExitComparatorTest {

    private fun bar(c: Double) = Bar(c, c + 0.2, c - 0.2, c)

    private fun series(values: List<Double>) = values.map { bar(it) }

    // ---------- ۱) پامپ سپس دامپ: موتور سود را قفل می‌کند ----------

    @Test
    fun pump_then_dump_engine_locks_more_profit() {
        val rise = (0..24).map { 100.0 + it * 0.5 }          // 100 → 112
        val dump = (1..10).map { 112.0 - it * 1.7 }          // → 95
        val bars = series(rise + dump)
        val legacy = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 95.0, 105.0, 115.0)
        val engine = ExitComparator.replayEngine(bars, "PUMP", 100.0, 95.0, 105.0, 115.0)
        assertTrue("engine must lock TP1", engine.partialTaken)
        assertTrue(
            "engine ${engine.realizedR} must beat legacy ${legacy.realizedR} on pump-dump",
            engine.realizedR > legacy.realizedR
        )
    }

    // ---------- ۲) استاپ مستقیم: هر دو برابر (صداقت) ----------

    @Test
    fun straight_stop_both_policies_equal() {
        val bars = series(listOf(99.0, 94.0))
        val legacy = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 95.0, 105.0, 115.0)
        val engine = ExitComparator.replayEngine(bars, "PUMP", 100.0, 95.0, 105.0, 115.0)
        assertEquals("STOP", legacy.exitReason)
        assertEquals("STOP", engine.exitReason)
        assertEquals(legacy.realizedR, engine.realizedR, 0.0001)
        assertEquals(-1.0, engine.realizedR, 0.0001)
    }

    // ---------- ۳) چرخش مومنتوم بدون رسیدن به +1R: ضرر کمتر ----------

    @Test
    fun momentum_flip_cuts_loss_faster() {
        val grind = (0..24).map { 100.0 + it * 0.12 }        // → 103 (هرگز +1R)
        val dump = (1..15).map { 103.0 - it * 0.5 }          // → 95.5
        val bars = series(grind + dump)
        val legacy = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 95.0, 110.0, 120.0)
        val engine = ExitComparator.replayEngine(bars, "PUMP", 100.0, 95.0, 110.0, 120.0)
        assertEquals(-1.0, legacy.realizedR, 0.0001)         // legacy تا استاپ کامل می‌خورد
        assertTrue(
            "engine ${engine.realizedR} must cut loss earlier than legacy -1R",
            engine.realizedR > legacy.realizedR
        )
    }

    // ---------- ۴) صداقت: روی روند خالص، TP1 هزینه دارد ولی کف سود تضمین است ----------

    @Test
    fun pure_runner_tp1_costs_but_floors_profit() {
        val bars = series((0..29).map { 100.0 + it * 1.0 })  // 100 → 129 بدون چرخش
        val legacy = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 140.0)
        val engine = ExitComparator.replayEngine(bars, "PUMP", 100.0, 90.0, 110.0, 140.0)
        assertTrue("engine takes TP1 on runner", engine.partialTaken)
        // معاملهٔ هزینه: روی روند خالص legacy جلوتر است
        assertTrue(legacy.realizedR >= engine.realizedR)
        // ولی کف سود موتور تضمین است: هرگز زیر +1R نمی‌افتد
        assertTrue("engine floors at +1R", engine.realizedR >= 1.0)
    }
}
