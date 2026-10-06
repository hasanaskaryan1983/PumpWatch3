package com.pumpwatch.app.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 142: گارد رگرسیون عملکرد برای candle builder و exit replay.
 *
 * این‌ها پرهزینه‌ترین مسیرهای اپ‌اند:
 * - buildCandlesChecked: هر اسکن fallback روی دادهٔ CoinGecko
 * - replayLegacy/replayEngine: هر ردیف بک‌تست و A/B
 * اگر کسی حلقهٔ تویتو یا کپی لیست اضافه کند، این تست‌ها فریاد می‌زنند.
 */
class EnginePerfTest {

    private fun prices(points: Int): List<List<Double>> {
        val base = 1_700_000_000_000.0
        return (0 until points).map { i -> listOf(base + i * 600_000.0, 100.0 + i % 5) }
    }

    private fun bars(count: Int): List<Bar> =
        (1..count).map { i -> Bar(100.0 + i % 5, 105.0 + i % 5, 95.0 + i % 5, 102.0 + i % 5) }

    @Test
    fun `buildCandlesChecked 720 points x50 stays under 1000ms`() {
        val p = prices(720)
        BatchScanner.buildCandlesChecked(p, null)
        val start = System.nanoTime()
        repeat(50) { BatchScanner.buildCandlesChecked(p, null) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("50 builds took ${ms}ms (limit 1000ms)", ms < 1000)
    }

    @Test
    fun `replayLegacy 500 bars x100 stays under 1000ms`() {
        val b = bars(500)
        ExitComparator.replayLegacy(b, "PUMP", 100.0, 90.0, 110.0, 120.0)
        val start = System.nanoTime()
        repeat(100) { ExitComparator.replayLegacy(b, "PUMP", 100.0, 90.0, 110.0, 120.0) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("100 legacy replays took ${ms}ms (limit 1000ms)", ms < 1000)
    }

    @Test
    fun `replayEngine 500 bars x100 stays under 2000ms`() {
        val b = bars(500)
        ExitComparator.replayEngine(b, "PUMP", 100.0, 90.0, 110.0, 120.0)
        val start = System.nanoTime()
        repeat(100) { ExitComparator.replayEngine(b, "PUMP", 100.0, 90.0, 110.0, 120.0) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("100 engine replays took ${ms}ms (limit 2000ms)", ms < 2000)
    }
}
