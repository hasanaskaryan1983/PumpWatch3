package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست یکپارچگی MACD (بازبینی دوم):
 * چون QuickScanner / CoinDetailScreen / BacktestScreen / Indicators
 * همگی به MacdCalc واگذار شده‌اند، کافی است MacdCalc با یک
 * پیاده‌سازی مرجع مستقل (EMA با seed میانگین) cross-check شود.
 */
class MacdConsistencyTest {

    // سری ثابت: ۴۰ کندل تخت + ۲۰ کندل صعودی
    private val series: List<Double> = List(40) { 100.0 } + List(20) { 100.0 + it * 2.0 }

    // ---------- پیاده‌سازی مرجع مستقل (بدون استفاده از MacdCalc) ----------

    private fun refEma(values: List<Double>, period: Int): List<Double?> {
        if (values.size < period) return List(values.size) { null }
        val k = 2.0 / (period + 1)
        val out = ArrayList<Double?>(values.size)
        var ema = values.take(period).average()
        for (i in values.indices) {
            when {
                i < period - 1 -> out.add(null)
                i == period - 1 -> out.add(ema)
                else -> {
                    ema = values[i] * k + ema * (1 - k)
                    out.add(ema)
                }
            }
        }
        return out
    }

    private fun refMacdUp(closes: List<Double>): Boolean {
        val n = closes.size
        if (n < 26 + 9) return false
        val e12 = refEma(closes, 12)
        val e26 = refEma(closes, 26)
        val macd = ArrayList<Double>(n - 25)
        for (i in 25 until n) macd.add(e12[i]!! - e26[i]!!)
        val sig = refEma(macd, 9)
        val m1 = macd.last()
        val s1 = sig.last()!!
        return m1 > s1
    }

    // ---------- تست‌ها ----------

    @Test
    fun `macdUp matches independent reference on fixed series`() {
        assertEquals(refMacdUp(series), MacdCalc.macdUp(series))
    }

    @Test
    fun `macdUp is deterministic so all screens agree`() {
        val first = MacdCalc.macdUp(series)
        repeat(5) { assertEquals(first, MacdCalc.macdUp(series)) }
    }

    @Test
    fun `uptrend series is bullish regime in reference and engine`() {
        assertTrue(refMacdUp(series))
        assertTrue(MacdCalc.macdUp(series))
    }

    @Test
    fun `crossUp true only on exact cross bar`() {
        val flat = List(60) { 100.0 }
        assertTrue(MacdCalc.macdCrossUp(flat + listOf(102.0)))
        assertFalse(MacdCalc.macdCrossUp(flat + List(20) { 100.0 + it * 2.0 }))
    }
}
