package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های کندل‌سازی CoinGecko (بازبینی دوم، قدم ۳):
 * واحد timestamp، مرتب‌بودن، gap، reset حجم، و حذف کندل ناتمام.
 */
class BuildCandlesTest {

    private val H = 3_600_000.0
    private val HL = 3_600_000L
    private val base = 1_700_000_000_000.0

    private fun p(ts: Double, price: Double) = listOf(ts, price)

    @Test
    fun `complete hourly buckets built and trailing incomplete dropped`() {
        val prices = listOf(
            p(base, 100.0),
            p(base + H / 2, 102.0),
            p(base + H, 101.0),
            p(base + 1.5 * H, 103.0),
            p(base + 2 * H, 104.0)
        )
        val r = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(2, r.candles.size)
        assertEquals(0, r.gapCount)
        assertTrue(r.droppedTrailingIncomplete)

        val c0 = r.candles[0]
        assertEquals(100.0, c0.open, 1e-9)
        assertEquals(102.0, c0.high, 1e-9)
        assertEquals(100.0, c0.low, 1e-9)
        assertEquals(101.0, c0.close, 1e-9)

        val c1 = r.candles[1]
        assertEquals(101.0, c1.open, 1e-9)
        assertEquals(103.0, c1.close, 1e-9)
    }

    @Test
    fun `seconds timestamps normalized to milliseconds`() {
        val pricesSec = listOf(
            p(base / 1000, 100.0),
            p((base + H) / 1000, 101.0),
            p((base + 2 * H) / 1000, 104.0)
        )
        val r = BatchScanner.buildCandlesChecked(pricesSec, null)
        assertEquals(1, r.candles.size)
        assertEquals((base.toLong() / HL) * HL, r.candles[0].time)
    }

    @Test
    fun `gap of two missing hours is counted`() {
        val prices = listOf(
            p(base, 100.0),
            p(base + H / 2, 101.0),
            p(base + 3 * H, 102.0),
            p(base + 3.5 * H, 103.0),
            p(base + 4 * H, 104.0)
        )
        val r = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(2, r.candles.size)
        assertEquals(2, r.gapCount)
    }

    @Test
    fun `unsorted input is sorted before bucketing`() {
        val prices = listOf(
            p(base + H, 101.0),
            p(base, 100.0),
            p(base + 2 * H, 104.0),
            p(base + H / 2, 102.0),
            p(base + 1.5 * H, 103.0)
        )
        val r = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(2, r.candles.size)
        assertEquals(100.0, r.candles[0].open, 1e-9)
    }

    @Test
    fun `volume reset does not produce negative volume`() {
        val prices = listOf(
            p(base, 100.0),
            p(base + H / 2, 102.0),
            p(base + H, 101.0),
            p(base + 1.5 * H, 103.0),
            p(base + 2 * H, 104.0)
        )
        val vols = listOf(
            listOf(base, 500.0),
            listOf(base + H / 2, 900.0),
            listOf(base + H, 120.0),          // reset تجمعی
            listOf(base + 1.5 * H, 300.0),
            listOf(base + 2 * H, 50.0)
        )
        val r = BatchScanner.buildCandlesChecked(prices, vols)
        assertEquals(2, r.candles.size)
        assertTrue(r.candles.all { it.volume >= 0.0 })
        assertEquals(400.0, r.candles[0].volume, 1e-9)   // 900-500
        assertEquals(300.0, r.candles[1].volume, 1e-9)   // reset:120 + 180
    }

    @Test
    fun `single incomplete bucket yields no candles`() {
        val prices = listOf(p(base, 100.0), p(base + H / 2, 101.0))
        val r = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(0, r.candles.size)
        assertTrue(r.droppedTrailingIncomplete)
    }
}
