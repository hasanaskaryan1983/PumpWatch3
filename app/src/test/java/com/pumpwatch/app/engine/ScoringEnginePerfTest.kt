package com.pumpwatch.app.engine

import com.google.gson.JsonArray
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 141: گارد رگرسیون عملکرد برای ScoringEngine.
 *
 * 🎯 هدف: کشف رژیم‌های الگوریتمی (مثلاً O(n²) تصادفی)، نه میکروبنچمارک.
 * سقف‌ها عمداً ۱۰-۵۰ برابر زمان واقعی گرفته شده‌اند تا در CI کند هم سبز بمانند.
 * زمان واقعی روی دستگاه مدرن: هر score حدود ۱-۳ میلی‌ثانیه.
 */
class ScoringEnginePerfTest {

    private fun candles(count: Int): List<JsonArray> = (1..count).map { i ->
        JsonArray().apply {
            add(0L); add(100.0 + i % 7); add(105.0 + i % 7); add(95.0 + i % 7)
            add(102.0 + i % 7); add(1000.0); add(0L)
        }
    }

    @Test
    fun `scoreFromCandles 250 candles x20 stays under 1000ms`() {
        val c = candles(250)
        ScoringEngine.scoreFromCandles(c)  // warm-up
        val start = System.nanoTime()
        repeat(20) { ScoringEngine.scoreFromCandles(c) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("20 scores took ${ms}ms (limit 1000ms) — algorithmic regression?", ms < 1000)
    }

    @Test
    fun `emaL 5000 points x50 stays under 500ms`() {
        val d = (1..5000).map { (it % 97).toDouble() }
        ScoringEngine.emaL(d, 200)
        val start = System.nanoTime()
        repeat(50) { ScoringEngine.emaL(d, 200) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("50 emaL took ${ms}ms (limit 500ms)", ms < 500)
    }

    @Test
    fun `rsi 5000 points x50 stays under 500ms`() {
        val d = (1..5000).map { (it % 97).toDouble() }
        ScoringEngine.rsi(d)
        val start = System.nanoTime()
        repeat(50) { ScoringEngine.rsi(d) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("50 rsi took ${ms}ms (limit 500ms)", ms < 500)
    }
}
