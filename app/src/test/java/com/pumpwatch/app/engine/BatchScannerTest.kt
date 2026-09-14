package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد برای BatchScanner.buildCandlesChecked
 *
 * این تابع pure است و candleهای ساعتی را از سری زمانی raw CoinGecko می‌سازد.
 *
 * 📌 نکتهٔ حیاتی دربارهٔ unitMs:
 *   - اگر max(ts) < 10^11 → unitMs = 1000 (فرض: ts ثانیه‌ای است، به ms تبدیل می‌شود)
 *   - اگر max(ts) ≥ 10^11 → unitMs = 1.0 (فرض: ts از قبل ms است)
 *   این تست‌ها از timestamp بزرگ (≈ Nov 2023) استفاده می‌کنند تا unitMs=1 بماند.
 *
 * 📌 رفتار candle-production:
 *   - Candle فقط وقتی bucket عوض می‌شود تولید می‌شود
 *   - close candle = آخرین قیمت در bucket قبلی (نه نقطهٔ شروع bucket جدید)
 *   - آخرین bucket هیچ‌وقت تولید نمی‌شود (droppedTrailingIncomplete=true همیشه)
 */
class BatchScannerTest {

    // timestamp واقع‌نما (بزرگتر از 10^11) تا unitMs=1.0 بماند
    private val BASE = 1_700_000_000_000L  // ~Nov 2023
    private val H = 3_600_000L            // یک ساعت به میلی‌ثانیه

    private fun pts(vararg pairs: Pair<Long, Double>): List<List<Double>> =
        pairs.map { (ts, price) -> listOf(ts.toDouble(), price) }

    private fun vols(vararg pairs: Pair<Long, Double>): List<List<Double>> =
        pairs.map { (ts, vol) -> listOf(ts.toDouble(), vol) }

    // ---------- تست ۱: single bucket → drop ----------

    @Test
    fun `single hour bucket produces no closed candle`() {
        // همه در یک ساعت (500_000 < 3_600_000) → یک bucket → هیچ candle تولید نمی‌شود
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + 60_000 to 110.0,
            BASE + 120_000 to 95.0,
            BASE + 300_000 to 120.0,
            BASE + 500_000 to 115.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        assertTrue("همه در یک bucket → هیچ candle بسته‌ای تولید نمی‌شود", build.candles.isEmpty())
        assertTrue("droppedTrailingIncomplete همیشه true است", build.droppedTrailingIncomplete)
    }

    // ---------- تست ۲: three buckets → two candles ----------

    @Test
    fun `three consecutive buckets produce two closed candles`() {
        val prices = pts(
            BASE + 0 to 100.0,                    // bucket 0
            BASE + 60_000 to 110.0,               // bucket 0 → high
            BASE + 120_000 to 95.0,               // bucket 0 → low (آخرین در bucket 0)
            BASE + H + 0 to 115.0,                // bucket 1 (شروع)
            BASE + H + 60_000 to 125.0,           // bucket 1 → high
            BASE + H + 120_000 to 105.0,          // bucket 1 (آخرین در bucket 1)
            BASE + 2 * H + 0 to 120.0,            // bucket 2 (شروع)
            BASE + 2 * H + 60_000 to 130.0        // bucket 2 (آخرین، drop)
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        assertEquals("باید ۲ کندل بسته‌شده تولید شود (bucket 2 drop)", 2, build.candles.size)

        // candle 1: time = BASE + H (پایان bucket 0)
        assertEquals(BASE + H, build.candles[0].time)
        assertEquals(100.0, build.candles[0].open, 0.001)
        assertEquals(110.0, build.candles[0].high, 0.001)
        assertEquals(95.0, build.candles[0].low, 0.001)
        // close = آخرین قیمت قبل از تغییر bucket = 95 (نه 115 که در bucket بعدی است)
        assertEquals(95.0, build.candles[0].close, 0.001)

        // candle 2: time = BASE + 2H (پایان bucket 1)
        assertEquals(BASE + 2 * H, build.candles[1].time)
        assertEquals(115.0, build.candles[1].open, 0.001)
        assertEquals(125.0, build.candles[1].high, 0.001)
        assertEquals(105.0, build.candles[1].low, 0.001)
        assertEquals(105.0, build.candles[1].close, 0.001)
    }

    // ---------- تست ۳: gap counting ----------

    @Test
    fun `gaps between buckets are counted correctly`() {
        // bucket 0 → bucket 1 → bucket 4 (gap = bucket 2 و 3)
        val prices = pts(
            BASE + 0 to 100.0,
            BASE + H + 0 to 110.0,
            BASE + 4 * H + 0 to 120.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        assertEquals("باید ۲ کندل بسته‌شده تولید شود", 2, build.candles.size)
        // gap = (4H - H)/H - 1 = 2 (bucket های 2 و 3 خالی)
        assertEquals("باید ۲ gap ساعتی گزارش شود", 2, build.gapCount)
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

        assertEquals("باید ۳ کندل بسته‌شده تولید شود (چهارمی drop)", 3, build.candles.size)
        assertEquals("هیچ gap نباید گزارش شود", 0, build.gapCount)
    }

    // ---------- تست ۴: volume از cumulative deltas ----------

    @Test
    fun `volume is derived from cumulative volume deltas within bucket`() {
        // همه در یک bucket → drop → candles empty
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

        assertTrue("کمتر از یک ساعت کامل باید drop شود", build.candles.isEmpty())
    }

    @Test
    fun `volume accumulates deltas across two buckets`() {
        // bucket 0: BASE → BASE+H
        // bucket 1: BASE+H → BASE+2H
        // bucket 2: BASE+2H (drop)
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
            BASE + 0 to 1000.0,         // lastVol اولیه = 1000
            BASE + 60_000 to 1500.0,    // dv = 500
            BASE + 120_000 to 2000.0,   // dv = 500
            BASE + 180_000 to 2500.0,   // dv = 500 → bucket 0 total = 1500
            BASE + H + 0 to 2500.0,     // dv = 2500-2500 = 0 (reset نشد ولی)
            BASE + H + 60_000 to 2800.0, // dv = 300
            BASE + H + 120_000 to 3200.0, // dv = 400 → bucket 1 total = 700
            BASE + 2 * H + 0 to 3300.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, volumes)

        assertEquals("باید ۲ کندل بسته‌شده تولید شود", 2, build.candles.size)
        assertEquals("bucket 0: 500+500+500 = 1500", 1500.0, build.candles[0].volume, 0.001)
        assertEquals("bucket 1: 0+300+400 = 700", 700.0, build.candles[1].volume, 0.001)
    }

    // ---------- تست ۵: edge cases ----------

    @Test
    fun `empty input produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(emptyList(), null)
        assertTrue(build.candles.isEmpty())
        assertEquals(0, build.gapCount)
    }

    @Test
    fun `single point input produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(pts(BASE to 100.0), null)
        assertTrue("تک‌نقطه (کمتر از ۲) باید empty برگرداند", build.candles.isEmpty())
    }

    @Test
    fun `two points in same bucket produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(
            pts(BASE to 100.0, BASE + 60_000 to 110.0),
            null
        )
        assertTrue("دو نقطه در یک ساعت باید drop شوند", build.candles.isEmpty())
    }

    // ---------- تست ۶: sanity check واقع‌نما ----------

    @Test
    fun `realistic 24-hour input produces 23 closed candles`() {
        // ۲۴ نقطه، یکی در هر ساعت
        val prices = (0..23).map { i -> listOf((BASE + i * H).toDouble(), 100.0 + i) }

        val build = BatchScanner.buildCandlesChecked(prices, null)

        // ۲۳ candle بسته‌شده (نقطهٔ بیست‌وچهارم آخرین bucket است → drop)
        assertEquals("باید ۲۳ کندل بسته‌شده تولید شود", 23, build.candles.size)
        assertEquals("هیچ gap نباید باشد", 0, build.gapCount)
        assertEquals("candle اول پایان ساعت اول است", BASE + H, build.candles[0].time)
        assertEquals("آخرین candle پایان ساعت ۲۳ام است", BASE + 23 * H, build.candles.last().time)
    }
}
