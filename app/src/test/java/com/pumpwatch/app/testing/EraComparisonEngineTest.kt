package com.pumpwatch.app.testing

import com.pumpwatch.app.ui.EraComparisonEngine
import com.pumpwatch.app.ui.PaperTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EraComparisonEngineTest {

    private fun trade(pnl: Double, entry: Double = 100.0, stopPct: Double = 10.0, price: Double? = null): PaperTrade {
        // price طوری تنظیم می‌شود که R برابر pnl/10 باشد (چون risk = 10%)
        val px = price ?: (entry + (pnl / 10.0) * (entry * stopPct / 100.0))
        return PaperTrade(
            symbol = "TEST", tier = "1-10", entry = entry, sizeUsd = 100.0,
            qty = 1.0, price = px, stop = entry * (1 - stopPct / 100), stopPct = stopPct,
            target = 0.0, openTime = 0L, score = 50, status = "CLOSED",
            pnl = pnl, closeTime = 1000L, trailing = true
        )
    }

    @Test
    fun `stats of empty list is zeroed`() {
        val s = EraComparisonEngine.stats(emptyList())
        assertEquals(0, s.count)
        assertEquals(0.0, s.winRate, 0.001)
        assertEquals(0.0, s.avgR, 0.001)
    }

    @Test
    fun `stats computes win rate and avg R`() {
        val trades = listOf(trade(10.0), trade(10.0), trade(-10.0), trade(-10.0))
        val s = EraComparisonEngine.stats(trades)
        assertEquals(4, s.count)
        assertEquals(50.0, s.winRate, 0.001)
        // هر معاملهٔ برد R=+1 و هر باخت R=-1
        assertEquals(0.0, s.avgR, 0.001)
    }

    @Test
    fun `compare reports insufficient data when either side has less than 3`() {
        val before = listOf(trade(10.0), trade(10.0))
        val after = listOf(trade(10.0), trade(10.0), trade(10.0))
        val cmp = EraComparisonEngine.compare(before, after, "تست", 0L)
        assertNull(cmp.verdictPositive)
        assertTrue(cmp.verdict.contains("دادهٔ کافی"))
    }

    @Test
    fun `compare detects improvement`() {
        val before = listOf(trade(-10.0), trade(-10.0), trade(10.0))   // avgR ≈ -0.33
        val after = listOf(trade(20.0), trade(20.0), trade(20.0))       // avgR = +2
        val cmp = EraComparisonEngine.compare(before, after, "تست", 0L)
        assertTrue(cmp.verdictPositive == true)
        assertTrue(cmp.verdict.contains("بهبود"))
    }

    @Test
    fun `compare detects degradation`() {
        val before = listOf(trade(20.0), trade(20.0), trade(20.0))
        val after = listOf(trade(-10.0), trade(-10.0), trade(-10.0))
        val cmp = EraComparisonEngine.compare(before, after, "تست", 0L)
        assertTrue(cmp.verdictPositive == false)
        assertTrue(cmp.verdict.contains("بدتر"))
    }

    @Test
    fun `compare reports neutral when difference is tiny`() {
        val before = listOf(trade(10.0), trade(10.0), trade(10.0))
        val after = listOf(trade(10.0), trade(10.0), trade(10.0))
        val cmp = EraComparisonEngine.compare(before, after, "تست", 0L)
        assertNull(cmp.verdictPositive)
        assertTrue(cmp.verdict.contains("تفاوت معناداری"))
    }

    @Test
    fun `after stats reflect only post-mark trades`() {
        val before = listOf(trade(-10.0), trade(-10.0), trade(-10.0))
        val after = listOf(trade(10.0), trade(10.0))
        val cmp = EraComparisonEngine.compare(before, after, "تست", 0L)
        assertEquals(3, cmp.before.count)
        assertEquals(2, cmp.after.count)
        assertFalse(cmp.after.winRate == cmp.before.winRate)
    }
}
