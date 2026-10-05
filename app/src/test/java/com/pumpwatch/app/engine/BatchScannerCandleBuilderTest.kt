package com.pumpwatch.app.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * 🚀 Commit 126: تست تابع pure `buildCandlesChecked` در BatchScanner.
 *
 * 🚀 Commit 126 fix:
 * - trailing bucket دور ریخته می‌شود (برای N کندل، N+1 ساعت لازم است)
 * - volume فقط داخل حلقه جمع می‌شود → bucket اول برای volume مثبت
 *   باید حداقل ۲ نقطه داشته باشد
 * - volumes باید با همان واحد زمانی prices باشند (unitMs از prices می‌آید)
 */
class BatchScannerCandleBuilderTest {

    private val hourMs = 3_600_000L

    // ========== Edge cases ==========

    @Test
    fun `empty prices returns empty list`() {
        val result = BatchScanner.buildCandlesChecked(emptyList(), null)
        assertTrue("Should return empty candles", result.candles.isEmpty())
        assertEquals(0, result.gapCount)
    }

    @Test
    fun `single price point returns empty list`() {
        val prices = listOf(listOf(1700000000.0, 100.0))
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertTrue("Single point cannot form a candle", result.candles.isEmpty())
    }

    @Test
    fun `two points in same hour return empty (trailing bucket dropped)`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + 1800_000.0
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertTrue("Trailing bucket is dropped", result.candles.isEmpty())
    }

    // ========== Bucket aggregation ==========

    @Test
    fun `two hours of data produce one candle`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals("Should create 1 candle", 1, result.candles.size)
    }

    @Test
    fun `multiple points in two hours compute OHLC correctly`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + 600_000.0
        val ts3 = ts1 + 1200_000.0
        val ts4 = ts1 + 1800_000.0
        val ts5 = ts1 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),  // open
            listOf(ts2, 115.0),  // high
            listOf(ts3, 95.0),   // low
            listOf(ts4, 110.0),  // close
            listOf(ts5, 112.0)   // triggers close of first bucket
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(1, result.candles.size)
        val c = result.candles[0]
        assertEquals(100.0, c.open, 0.0001)
        assertEquals(115.0, c.high, 0.0001)
        assertEquals(95.0, c.low, 0.0001)
        assertEquals(110.0, c.close, 0.0001)
    }

    @Test
    fun `three hours of data produce two candles`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val ts3 = ts2 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0),
            listOf(ts3, 115.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals("Should create 2 candles", 2, result.candles.size)
    }

    // ========== Gap detection ==========

    @Test
    fun `no gap between consecutive hours`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val ts3 = ts2 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0),
            listOf(ts3, 115.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals("No gaps expected", 0, result.gapCount)
    }

    @Test
    fun `one hour gap detected correctly`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + 2 * hourMs
        val ts3 = ts2 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0),
            listOf(ts3, 112.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals("Should detect 1 gap", 1, result.gapCount)
    }

    @Test
    fun `multiple hour gaps counted correctly`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + 4 * hourMs
        val ts3 = ts2 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0),
            listOf(ts3, 112.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals("Should detect 3 gaps", 3, result.gapCount)
    }

    // ========== P0-6: close time ==========

    @Test
    fun `candle time is close time not open time`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(1, result.candles.size)
        val candle = result.candles[0]
        val bucketStart = (ts1.toLong() / hourMs) * hourMs
        val expectedCloseTime = bucketStart + hourMs
        assertEquals("time should be close time", expectedCloseTime, candle.time)
    }

    // ========== Timestamp unit detection ==========

    @Test
    fun `timestamps in seconds are converted to milliseconds`() {
        val tsSeconds = 1700000000.0
        val tsSeconds2 = tsSeconds + 3600.0
        val prices = listOf(
            listOf(tsSeconds, 100.0),
            listOf(tsSeconds2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(1, result.candles.size)
        val candle = result.candles[0]
        val expectedMs = (tsSeconds * 1000).toLong()
        val bucketStart = (expectedMs / hourMs) * hourMs
        assertEquals("Should convert seconds to ms", bucketStart + hourMs, candle.time)
    }

    @Test
    fun `timestamps in milliseconds used directly`() {
        val tsMs = 1700000000000.0
        val tsMs2 = tsMs + hourMs
        val prices = listOf(
            listOf(tsMs, 100.0),
            listOf(tsMs2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(1, result.candles.size)
        val candle = result.candles[0]
        val bucketStart = (tsMs.toLong() / hourMs) * hourMs
        assertEquals("Should use ms directly", bucketStart + hourMs, candle.time)
    }

    // ========== Volume handling ==========

    @Test
    fun `no volume data results in zero volume`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(1, result.candles.size)
        assertEquals("Volume should be 0", 0.0, result.candles[0].volume, 0.0001)
    }

    @Test
    fun `volume data aggregated correctly`() {
        // 🚀 Commit 126 fix:
        // 1) volumes باید با همان واحد زمانی prices باشند (ms اینجا)
        // 2) bucket اول باید ≥۲ نقطه داشته باشد تا volume جمع شود
        //    (نقطهٔ اول قبل از حلقه پردازش می‌شود و volume اضافه نمی‌کند)
        val ts1 = 1700000000.0 * 1000
        val tsMid = ts1 + 1800_000.0   // same hour as ts1
        val ts2 = ts1 + hourMs         // next hour → closes first bucket
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(tsMid, 105.0),
            listOf(ts2, 110.0)
        )
        val volumes = listOf(
            listOf(ts1, 1000.0),     // cumulative
            listOf(tsMid, 1800.0),   // cumulative → delta = 800
            listOf(ts2, 2500.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, volumes)
        assertEquals(1, result.candles.size)
        // delta بین ts1 و tsMid = 800 → volume کندل اول
        assertEquals("Volume should equal cumulative delta", 800.0, result.candles[0].volume, 0.0001)
    }

    // ========== Dropped trailing ==========

    @Test
    fun `droppedTrailingIncomplete is always true`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val prices = listOf(
            listOf(ts1, 100.0),
            listOf(ts2, 110.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertTrue("droppedTrailingIncomplete should be true", result.droppedTrailingIncomplete)
    }

    // ========== Sorting ==========

    @Test
    fun `unsorted prices are sorted by timestamp`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val prices = listOf(
            listOf(ts2, 110.0),  // out of order
            listOf(ts1, 100.0)
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertEquals(1, result.candles.size)
        val candle = result.candles[0]
        assertEquals("open should be from earlier timestamp", 100.0, candle.open, 0.0001)
    }

    // ========== Invalid input ==========

    @Test
    fun `prices with insufficient fields are filtered`() {
        val ts1 = 1700000000.0 * 1000
        val ts2 = ts1 + hourMs
        val prices = listOf(
            listOf(ts1),        // only 1 field → filtered
            listOf(ts2, 110.0)  // valid
        )
        val result = BatchScanner.buildCandlesChecked(prices, null)
        assertTrue("Should filter invalid entries and drop trailing", result.candles.isEmpty())
    }
}
