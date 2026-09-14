package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد برای BatchScanner.buildCandlesChecked
 *
 * این تابع pure است و candleهای ساعتی را از سری زمانی raw CoinGecko می‌سازد.
 * تست‌ها روی سه منطق حیاتی تمرکز دارند:
 *  1. bucket boundary صحیح (P0-6: time = پایان bucket = close time)
 *  2. gap counting دقیق (گزارش جاهایی که داده از دست رفته)
 *  3. droppedTrailingIncomplete = true (آخرین bucket که هنوز بسته نشده حذف می‌شود)
 *
 * ورودی‌ها از CoinGecko می‌آیند:
 *  - prices: List<List<Double>> = [[timestamp_ms, price], ...]
 *  - volumes: List<List<Double>> = [[timestamp_ms, cumulative_volume], ...]
 */
class BatchScannerTest {

    // Helper: ساخت [[ts, price], ...]
    private fun pts(vararg pairs: Pair<Long, Double>): List<List<Double>> =
        pairs.map { (ts, price) -> listOf(ts.toDouble(), price) }

    // Helper: ساخت [[ts, cumulative_volume], ...]
    private fun vols(vararg pairs: Pair<Long, Double>): List<List<Double>> =
        pairs.map { (ts, vol) -> listOf(ts.toDouble(), vol) }

    // ---------- تست ۱: bucket ساده یک ساعته ----------

    @Test
    fun `single hour bucket builds one candle with close time at bucket end`() {
        // همهٔ نقاط در یک ساعت (bucket_start = 0, hourMs = 3_600_000)
        val ts0 = 0L
        val prices = pts(
            ts0 + 0 to 100.0,
            ts0 + 60_000 to 110.0,
            ts0 + 120_000 to 95.0,    // low
            ts0 + 300_000 to 120.0,   // high
            ts0 + 500_000 to 115.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        // چون همه در یک bucket هستند و droppedTrailingIncomplete=true،
        // bucket باز drop می‌شود → نتیجه empty است.
        // این رفتار صحیح است: کندل‌های هنوز-بسته‌نشده نباید در تحلیل وارد شوند.
        assertTrue("دادهٔ کمتر از یک ساعت کامل باید drop شود", build.candles.isEmpty())
        assertTrue("droppedTrailingIncomplete باید true باشد", build.droppedTrailingIncomplete)
    }

    // ---------- تست ۲: bucket های متوالی با close time صحیح ----------

    @Test
    fun `two consecutive buckets produce two candles with correct close times`() {
        val hourMs = 3_600_000L
        val ts0 = 0L

        // bucket اول: ts0 تا ts0+hourMs (نقاط در این بازه)
        // bucket دوم: ts0+hourMs تا ts0+2*hourMs
        // bucket سوم: ts0+2*hourMs تا ts0+3*hourMs (drop چون هنوز باز است)
        val prices = pts(
            ts0 + 0 to 100.0,              // در bucket اول
            ts0 + 60_000 to 110.0,
            ts0 + 120_000 to 95.0,
            ts0 + hourMs + 0 to 115.0,     // در bucket دوم
            ts0 + hourMs + 60_000 to 125.0,
            ts0 + hourMs + 120_000 to 105.0,
            ts0 + 2 * hourMs + 0 to 120.0, // در bucket سوم (drop)
            ts0 + 2 * hourMs + 60_000 to 130.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        assertEquals("باید ۲ کندل بسته‌شده تولید شود (سومی drop می‌شود)", 2, build.candles.size)

        // P0-6: time = پایان bucket = close time رسمی
        val candle1 = build.candles[0]
        val candle2 = build.candles[1]
        assertEquals("close time کندل اول باید پایان bucket اول باشد", hourMs, candle1.time)
        assertEquals("close time کندل دوم باید پایان bucket دوم باشد", 2 * hourMs, candle2.time)

        // OHLC بررسی: کندل اول
        assertEquals(100.0, candle1.open, 0.001)
        assertEquals(110.0, candle1.high, 0.001)
        assertEquals(95.0, candle1.low, 0.001)
        assertEquals(115.0, candle1.close, 0.001) // آخرین قیمت در bucket اول

        // OHLC بررسی: کندل دوم
        assertEquals(115.0, candle2.open, 0.001)
        assertEquals(125.0, candle2.high, 0.001)
        assertEquals(105.0, candle2.low, 0.001)
        assertEquals(130.0, candle2.close, 0.001) // آخرین قیمت در bucket دوم (نه 120!)
    }

    // ---------- تست ۳: gap counting ----------

    @Test
    fun `gaps between buckets are counted correctly`() {
        val hourMs = 3_600_000L
        val ts0 = 0L

        // bucket اول (ساعت ۰)، bucket دوم (ساعت ۱)، bucket پنجم (ساعت ۴) → ۲ gap (ساعت ۲ و ۳)
        val prices = pts(
            ts0 + 0 to 100.0,                      // bucket 0
            ts0 + hourMs + 0 to 110.0,             // bucket 1
            ts0 + 4 * hourMs + 0 to 120.0          // bucket 4 (drop چون آخرین)
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        assertEquals("باید ۲ کندل بسته‌شده تولید شود (سومی drop)", 2, build.candles.size)
        // gap بین bucket 1 و bucket 4 = 2 ساعت (bucket 2 و 3)
        assertEquals("باید ۲ gap ساعتی گزارش شود", 2, build.gapCount)
    }

    @Test
    fun `no gap when buckets are consecutive`() {
        val hourMs = 3_600_000L
        val ts0 = 0L

        val prices = pts(
            ts0 + 0 to 100.0,
            ts0 + hourMs + 0 to 110.0,
            ts0 + 2 * hourMs + 0 to 120.0,
            ts0 + 3 * hourMs + 0 to 130.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, null)

        assertEquals("باید ۳ کندل بسته‌شده تولید شود (چهارمی drop)", 3, build.candles.size)
        assertEquals("هیچ gap نباید گزارش شود", 0, build.gapCount)
    }

    // ---------- تست ۴: حجم از cumulative استخراج می‌شود ----------

    @Test
    fun `volume is derived from cumulative volume deltas within bucket`() {
        val hourMs = 3_600_000L
        val ts0 = 0L

        // cumulative volume: 1000 → 1500 → 2000 (در یک bucket)
        val prices = pts(
            ts0 + 0 to 100.0,
            ts0 + 60_000 to 110.0,
            ts0 + 120_000 to 115.0
        )
        val volumes = vols(
            ts0 + 0 to 1000.0,
            ts0 + 60_000 to 1500.0,   // delta = 500
            ts0 + 120_000 to 2000.0   // delta = 500
        )

        val build = BatchScanner.buildCandlesChecked(prices, volumes)

        // همه در یک bucket → drop می‌شود
        assertTrue("کمتر از یک ساعت کامل باید drop شود", build.candles.isEmpty())
    }

    @Test
    fun `volume accumulates deltas across two buckets`() {
        val hourMs = 3_600_000L
        val ts0 = 0L

        // bucket 1: cumulative 1000 → 1500 → 2000 → 2500 (دلتا کل ۱۵۰۰)
        // bucket 2: cumulative 2500 → 2800 → 3200 (دلتا کل ۷۰۰)
        // bucket 3: drop
        val prices = pts(
            ts0 + 0 to 100.0,
            ts0 + 60_000 to 110.0,
            ts0 + 120_000 to 115.0,
            ts0 + 180_000 to 118.0,
            ts0 + hourMs + 0 to 120.0,
            ts0 + hourMs + 60_000 to 125.0,
            ts0 + hourMs + 120_000 to 128.0,
            ts0 + 2 * hourMs + 0 to 130.0
        )
        val volumes = vols(
            ts0 + 0 to 1000.0,
            ts0 + 60_000 to 1500.0,
            ts0 + 120_000 to 2000.0,
            ts0 + 180_000 to 2500.0,
            ts0 + hourMs + 0 to 2500.0,
            ts0 + hourMs + 60_000 to 2800.0,
            ts0 + hourMs + 120_000 to 3200.0,
            ts0 + 2 * hourMs + 0 to 3300.0
        )

        val build = BatchScanner.buildCandlesChecked(prices, volumes)

        assertEquals("باید ۲ کندل بسته‌شده تولید شود", 2, build.candles.size)

        // bucket 1: deltas = 500 + 500 + 500 = 1500 (اولین نقطه delta ندارد)
        assertEquals("حجم کندل اول باید ۱۵۰۰ باشد", 1500.0, build.candles[0].volume, 0.001)

        // bucket 2: deltas = 300 + 400 = 700 (اولین نقطه delta ندارد)
        assertEquals("حجم کندل دوم باید ۷۰۰ باشد", 700.0, build.candles[1].volume, 0.001)
    }

    // ---------- تست ۵: input خالی و degenerate ----------

    @Test
    fun `empty input produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(emptyList(), null)
        assertTrue(build.candles.isEmpty())
        assertEquals(0, build.gapCount)
    }

    @Test
    fun `single point input produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(pts(0L to 100.0), null)
        assertTrue("تک‌نقطه باید drop شود", build.candles.isEmpty())
    }

    @Test
    fun `two points in same bucket produces empty build`() {
        val build = BatchScanner.buildCandlesChecked(
            pts(0L to 100.0, 60_000L to 110.0),
            null
        )
        assertTrue("دو نقطه در یک ساعت باید drop شوند", build.candles.isEmpty())
    }

    // ---------- تست ۶: sanity check برای دادهٔ واقعی‌نما ----------

    @Test
    fun `realistic 24-hour input produces 23 closed candles`() {
        val hourMs = 3_600_000L
        // ۲۴ نقطه، یکی در هر ساعت
        val prices = (0..23).map { i -> listOf((i * hourMs).toDouble(), 100.0 + i) }

        val build = BatchScanner.buildCandlesChecked(prices, null)

        // ۲۳ کندل بسته‌شده (بیست‌وچهارمی drop می‌شود)
        assertEquals("باید ۲۳ کندل بسته‌شده تولید شود", 23, build.candles.size)
        assertEquals("هیچ gap نباید باشد (همه ساعت‌ها پشت‌سرهم)", 0, build.gapCount)

        // sanity: close time کندل اول = پایان ساعت اول
        assertEquals(hourMs, build.candles[0].time)
        assertEquals(23 * hourMs, build.candles.last().time)
    }
}
