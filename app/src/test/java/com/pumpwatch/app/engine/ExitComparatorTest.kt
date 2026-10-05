package com.pumpwatch.app.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 125: تست ماشین مقایسهٔ سیاست‌های خروج (A/B testing).
 *
 * چرا مهم؟
 * - منطق خروج، هستهٔ سود/زیان اپ است
 * - تفاوت بین LEGACY و ENGINE در بک‌تست و A/B حیاتی است
 * - R-multiple (risk-adjusted return) باید درست محاسبه شود
 *
 * این تست‌ها:
 * - Edge cases (empty bars, zero risk)
 * - Stop/Target/OPEN_END برای هر دو side
 * - R-multiple correctness
 * - replayEngine basic paths
 */
class ExitComparatorTest {

    // ========== Edge cases ==========

    @Test
    fun `replayLegacy returns NONE for empty bars`() {
        val result = ExitComparator.replayLegacy(emptyList(), "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals(0.0, result.realizedR, 0.0001)
        assertEquals("NONE", result.exitReason)
        assertEquals(0, result.barsHeld)
    }

    @Test
    fun `replayLegacy returns NONE for zero risk`() {
        val bars = listOf(Bar(100.0, 105.0, 95.0, 102.0))
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 100.0, 110.0, 120.0)
        assertEquals("NONE", result.exitReason)
    }

    // ========== Long (PUMP): stop hit ==========

    @Test
    fun `replayLegacy long stops out when low hits stop`() {
        val bars = listOf(
            Bar(100.0, 105.0, 95.0, 102.0),  // low 95 > 90 stop, no trigger
            Bar(102.0, 103.0, 89.0, 91.0)    // low 89 <= 90 stop → STOP
        )
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals("STOP", result.exitReason)
        assertEquals(2, result.barsHeld)
        // R = (90 - 100) / 10 = -1.0
        assertEquals(-1.0, result.realizedR, 0.0001)
    }

    // ========== Long (PUMP): target hit ==========

    @Test
    fun `replayLegacy long exits at target when high reaches t2`() {
        val bars = listOf(
            Bar(100.0, 115.0, 98.0, 110.0),  // high 115 < 120, no target
            Bar(110.0, 122.0, 108.0, 120.0)   // high 122 >= 120 → TARGET
        )
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals("TARGET", result.exitReason)
        assertEquals(2, result.barsHeld)
        // R = (120 - 100) / 10 = 2.0
        assertEquals(2.0, result.realizedR, 0.0001)
    }

    // ========== Long (PUMP): OPEN_END ==========

    @Test
    fun `replayLegacy long returns OPEN_END if no trigger`() {
        val bars = listOf(
            Bar(100.0, 105.0, 95.0, 102.0),  // high 105 < 120, low 95 > 90
            Bar(102.0, 108.0, 96.0, 107.0),  // high 108 < 120, low 96 > 90
            Bar(107.0, 112.0, 101.0, 110.0)  // high 112 < 120, low 101 > 90
        )
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals("OPEN_END", result.exitReason)
        assertEquals(3, result.barsHeld)
        // R = (110 - 100) / 10 = 1.0
        assertEquals(1.0, result.realizedR, 0.0001)
    }

    // ========== Short (DUMP): stop hit ==========

    @Test
    fun `replayLegacy short stops out when high hits stop`() {
        val bars = listOf(
            Bar(100.0, 105.0, 95.0, 98.0),   // high 105 < 110 stop
            Bar(98.0, 112.0, 97.0, 111.0)    // high 112 >= 110 stop → STOP
        )
        val result = ExitComparator.replayLegacy(bars, "DUMP", 100.0, 110.0, 90.0, 80.0)
        assertEquals("STOP", result.exitReason)
        assertEquals(2, result.barsHeld)
        // R = (100 - 110) / 10 = -1.0
        assertEquals(-1.0, result.realizedR, 0.0001)
    }

    // ========== Short (DUMP): target hit ==========

    @Test
    fun `replayLegacy short exits at target when low reaches t2`() {
        val bars = listOf(
            Bar(100.0, 103.0, 85.0, 88.0),   // low 85 > 80, no target
            Bar(88.0, 90.0, 78.0, 80.0)      // low 78 <= 80 → TARGET
        )
        val result = ExitComparator.replayLegacy(bars, "DUMP", 100.0, 110.0, 90.0, 80.0)
        assertEquals("TARGET", result.exitReason)
        assertEquals(2, result.barsHeld)
        // R = (100 - 80) / 10 = 2.0
        assertEquals(2.0, result.realizedR, 0.0001)
    }

    // ========== Short (DUMP): OPEN_END ==========

    @Test
    fun `replayLegacy short returns OPEN_END if no trigger`() {
        val bars = listOf(
            Bar(100.0, 105.0, 95.0, 98.0),   // high 105 < 110, low 95 > 80
            Bar(98.0, 103.0, 90.0, 92.0)     // high 103 < 110, low 90 > 80
        )
        val result = ExitComparator.replayLegacy(bars, "DUMP", 100.0, 110.0, 90.0, 80.0)
        assertEquals("OPEN_END", result.exitReason)
        // R = (100 - 92) / 10 = 0.8
        assertEquals(0.8, result.realizedR, 0.0001)
    }

    // ========== replayEngine ==========

    @Test
    fun `replayEngine returns NONE for empty bars`() {
        val result = ExitComparator.replayEngine(emptyList(), "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals("NONE", result.exitReason)
        assertEquals(0, result.barsHeld)
    }

    @Test
    fun `replayEngine returns NONE for zero risk`() {
        val bars = listOf(Bar(100.0, 105.0, 95.0, 102.0))
        val result = ExitComparator.replayEngine(bars, "PUMP", 100.0, 100.0, 110.0, 120.0)
        assertEquals("NONE", result.exitReason)
    }

    @Test
    fun `replayEngine long stops out on low breach`() {
        // stop0=90, stopLevel = max(90, 90) = 90 initially
        val bars = listOf(
            Bar(100.0, 103.0, 95.0, 98.0),   // low 95 > 90 stopLevel
            Bar(98.0, 99.0, 88.0, 89.0)      // low 88 <= 90 stopLevel → STOP
        )
        val result = ExitComparator.replayEngine(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals("STOP", result.exitReason)
        assertEquals(2, result.barsHeld)
        // R = (90 - 100) / 10 = -1.0 (assuming no partial)
        assertEquals(-1.0, result.realizedR, 0.0001)
    }

    @Test
    fun `replayEngine short stops out on high breach`() {
        val bars = listOf(
            Bar(100.0, 105.0, 95.0, 98.0),   // high 105 < 110 stopLevel
            Bar(98.0, 112.0, 97.0, 111.0)    // high 112 >= 110 → STOP
        )
        val result = ExitComparator.replayEngine(bars, "DUMP", 100.0, 110.0, 90.0, 80.0)
        assertEquals("STOP", result.exitReason)
        assertEquals(2, result.barsHeld)
        assertEquals(-1.0, result.realizedR, 0.0001)
    }

    // ========== R-multiple calculation ==========

    @Test
    fun `R-multiple is positive when profitable long`() {
        val bars = listOf(Bar(100.0, 125.0, 98.0, 122.0))  // high 125 >= t2=120
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        // R = (120 - 100) / 10 = 2.0
        assertTrue("R should be positive", result.realizedR > 0)
        assertEquals(2.0, result.realizedR, 0.0001)
    }

    @Test
    fun `R-multiple is negative when stopped out`() {
        val bars = listOf(Bar(100.0, 105.0, 85.0, 90.0))  // low 85 <= stop=90
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        // R = (90 - 100) / 10 = -1.0
        assertTrue("R should be negative", result.realizedR < 0)
        assertEquals(-1.0, result.realizedR, 0.0001)
    }

    @Test
    fun `R-multiple scales correctly with risk`() {
        // risk = 20 (entry 100, stop 80)
        val bars = listOf(Bar(100.0, 145.0, 95.0, 142.0))  // high 145 >= t2=140
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 80.0, 120.0, 140.0)
        // R = (140 - 100) / 20 = 2.0
        assertEquals(2.0, result.realizedR, 0.0001)
    }

    @Test
    fun `R-multiple for short profitable trade`() {
        val bars = listOf(Bar(100.0, 103.0, 75.0, 78.0))  // low 75 <= t2=80
        val result = ExitComparator.replayLegacy(bars, "DUMP", 100.0, 110.0, 90.0, 80.0)
        // R = (100 - 80) / 10 = 2.0
        assertTrue("Short profitable R should be positive", result.realizedR > 0)
        assertEquals(2.0, result.realizedR, 0.0001)
    }

    @Test
    fun `barsHeld counts correctly`() {
        val bars = listOf(
            Bar(100.0, 102.0, 98.0, 101.0),
            Bar(101.0, 103.0, 99.0, 102.0),
            Bar(102.0, 104.0, 85.0, 90.0)  // low 85 <= stop=90 → 3 bars held
        )
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals(3, result.barsHeld)
    }

    @Test
    fun `first bar stop hit returns barsHeld equals 1`() {
        val bars = listOf(Bar(100.0, 102.0, 85.0, 90.0))  // low 85 <= stop=90
        val result = ExitComparator.replayLegacy(bars, "PUMP", 100.0, 90.0, 110.0, 120.0)
        assertEquals("STOP", result.exitReason)
        assertEquals(1, result.barsHeld)
    }
}
