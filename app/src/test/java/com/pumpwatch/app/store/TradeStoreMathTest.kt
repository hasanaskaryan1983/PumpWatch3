package com.pumpwatch.app.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 69: تست‌های منطق خالص Drawdown (بدون نیاز به Android/Context).
 *
 * هدف: ثابت کنیم سرمایهٔ پایه دیگر هاردکد ۱۰ نیست و فرمول قله→دره درست است.
 */
class TradeStoreMathTest {

    private val dd = { pnls: List<Double>, base: Double ->
        TradeStore.maxDrawdownFromPnls(pnls, base)
    }

    @Test
    fun `empty trades produce zero drawdown`() {
        assertEquals(0.0, dd(emptyList(), 1000.0), 0.0001)
    }

    @Test
    fun `all winning trades produce zero drawdown`() {
        assertEquals(0.0, dd(listOf(50.0, 50.0, 30.0), 1000.0), 0.0001)
    }

    @Test
    fun `single loss measures drawdown against base capital`() {
        // base=1000, pnl=-200 → equity=800, peak=1000 → dd = 20%
        assertEquals(20.0, dd(listOf(-200.0), 1000.0), 0.01)
    }

    @Test
    fun `drawdown measured from historical peak not base`() {
        // +200 → equity 1200 (peak), -300 → equity 900
        // dd = (1200-900)/1200*100 = 25%
        assertEquals(25.0, dd(listOf(200.0, -300.0), 1000.0), 0.01)
    }

    @Test
    fun `peak updates as equity grows`() {
        // +200 → 1200 peak; -100 → 1100 (dd 8.33); +50 → 1150
        // max dd = 8.3333
        assertEquals(8.3333, dd(listOf(200.0, -100.0, 50.0), 1000.0), 0.01)
    }

    @Test
    fun `base capital is NOT hardcoded to 100 - different bases give different dd`() {
        val pnls = listOf(100.0, -300.0)
        val dd1000 = dd(pnls, 1000.0)   // peak 1100 → equity 800 → dd 27.27%
        val dd100 = dd(pnls, 100.0)     // peak 200 → equity -100 → dd 150%
        assertEquals(27.2727, dd1000, 0.01)
        assertEquals(150.0, dd100, 0.01)
        assertTrue("Drawdown must depend on base capital", dd1000 != dd100)
    }

    @Test
    fun `non-positive base capital falls back to default safely`() {
        // base=0 باید به DEFAULT (1000) fallback کند، نه تقسیم بر صفر
        val result = dd(listOf(-200.0), 0.0)
        assertEquals(20.0, result, 0.01)
    }
}
