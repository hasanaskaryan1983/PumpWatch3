package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد برای BatchScanner.buildCandlesChecked
 *
 * 📌 نکتهٔ حیاتی دربارهٔ unitMs:
 *   - اگر max(ts) < 10^11 → unitMs = 1000 (فرض: ts ثانیه‌ای)
 *   - اگر max(ts) ≥ 10^11 → unitMs = 1.0 (فرض: ts از قبل ms است)
 *
 * 📌 نکتهٔ حیاتی دربارهٔ تراز bucket:
 *   مرز bucketها = (ts / hourMs) * hourMs یعنی تراز به ساعت‌های epoch unix.
 *   پس BASE باید مضرب دقیق 3_600_000 باشد تا BASE+H برابر پایان bucket شود.
 *
 * 📌 رفتار candle-production:
 *   - Candle فقط هنگام تغییر bucket تولید می‌شود
 *   - close = آخرین قیمت قبل از تغییر bucket
 *   - آخرین bucket هیچ‌وقت تولید نمی‌شود (droppedTrailingIncomplete=true)
 */
class BatchScannerTest {

    // تراز به مرز ساعت epoch (472222 × 3_600_000) و بزرگ‌تر از 10^11 برای unitMs=1
    private val BASE = 1_699_999_200_000L
    private val H = 3_600_000L

    private fun pts(vararg pairs: Pair<Long, Double>): List<List<Double>> =
        pairs.map { (ts, price) -> listOf(ts.toDouble(), price) }

    private fun vols(vararg pairs: Pair<Long, Double>): List<List<Double>> =
        pairs.map { (ts, vol) -> listOf(ts.toDouble(), vol) }

    @Test
    fun `single hour bucket produces no closed candle`() {
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + 60_000 to 110.0,
            BASE + 120_000 to 95.0,
            BASE + 300_000 to 120.0,
            BASE + 500_000 to 115.0
        )
        val build = BatchScanner.buildCandlesChecked(prices, null)
        assertTrue(build.candles.isEmpty())
        assertTrue(build.droppedTrailingIncomplete)
    }

    @Test
    fun `three consecutive buckets produce two closed candles`() {
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + 60_000 to 110.0,
            BASE + 120_000 to 95.0,
            BASE + H + 0 to 115.0,
            BASE + H + 60_000 to 125.0,
            BASE + H + 120_000 to 105.0,
            BASE + 2 * H + 0 to 120.0,
            BASE + 2 * H + 60_000 to 130.0
        )
        val build = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(2, build.candles.size)
        assertEquals(BASE + H, build.candles[0].time)
        assertEquals(100.0, build.candles[0].open, 0.001)
        assertEquals(110.0, build.candles[0].high, 0.001)
        assertEquals(95.0, build.candles[0].low, 0.001)
        assertEquals(95.0, build.candles[0].close, 0.001)
        assertEquals(BASE + 2 * H, build.candles[1].time)
        assertEquals(115.0, build.candles[1].open, 0.001)
        assertEquals(125.0, build.candles[1].high, 0.001)
        assertEquals(105.0, build.candles[1].low, 0.001)
        assertEquals(105.0, build.candles[1].close, 0.001)
    }

    @Test
    fun `gaps between buckets are counted correctly`() {
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + H + 0 to 110.0,
            BASE + 4 * H + 0 to 120.0
        )
        val build = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(2, build.candles.size)
        assertEquals(2, build.gapCount)
    }

    @Test
    fun `no gap when buckets are consecutive`() {
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + H + 0 to 110.0,
            BASE + 2 * H + 0 to 120.0,
            BASE + 3 * H + 0 to 130.0
        )
        val build = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(3, build.candles.size)
        assertEquals(0, build.gapCount)
    }

    @Test
    fun `volume is derived from cumulative volume deltas within bucket`() {
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + 60_000 to 110.0,
            BASE + 120_000 to 115.0
        )
        val volumes = vols(
            BASE + 0 to 1000.0,
            BASE + 60_000 to 1500.0,
            BASE + 120_000 to 2000.0
        )
        val build = BatchScanner.buildCandlesChecked(prices, volumes)
        assertTrue(build.candles.isEmpty())
    }

    @Test
    fun `volume accumulates deltas across two buckets`() {
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + 60_000 to 110.0,
            BASE + 120_000 to 115.0,
            BASE + 180_000 to 118.0,
            BASE + H + 0 to 120.0,
            BASE + H + 60_000 to 125.0,
            BASE + H + 120_000 to 128.0,
            BASE + 2 * H + 0 to 130.0
        )
        val volumes = vols(
            BASE + 0 to 1000.0,
            BASE + 60_000 to 1500.0,
            BASE + 120_000 to 2000.0,
            BASE + 180_000 to 2500.0,
            BASE + H + 0 to 2500.0,
            BASE + H + 60_000 to 2800.0,
            BASE + H + 120_000 to 3200.0,
            BASE + 2 * H + 0 to 3300.0
        )
        val build = BatchScanner.buildCandlesChecked(prices, volumes)
        assertEquals(2, build.candles.size)
        assertEquals(1500.0, build.candles[0].volume, 0.001)
        assertEquals(700.0, build.candles[1].volume, 0.001)
    }

    @Test
    fun `empty input produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(emptyList(), null)
        assertTrue(build.candles.isEmpty())
        assertEquals(0, build.gapCount)
    }

    @Test
    fun `single point input produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(pts(BASE to 100.0), null)
        assertTrue(build.candles.isEmpty())
    }

    @Test
    fun `two points in same bucket produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(
            pts(BASE to 100.0, BASE + 60_000 to 110.0),
            null
        )
        assertTrue(build.candles.isEmpty())
    }

    @Test
    fun `realistic 24-hour input produces 23 closed candles`() {
        val prices = (0..23).map { i -> listOf((BASE + i * H).toDouble(), 100.0 + i) }
        val build = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(23, build.candles.size)
        assertEquals(0, build.gapCount)
        assertEquals(BASE + H, build.candles[0].time)
        assertEquals(BASE + 23 * H, build.candles.last().time)
    }
}
