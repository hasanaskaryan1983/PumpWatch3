package com.pumpwatch.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 132: integration test واقعی برای HistoricalUniverseRepository.
 *
 * 🎯 استراتژی (بدون ساختن CoinMarket):
 * - recordSnapshot با لیست خالی = no-op (تست ایمنی)
 * - snapshot ها را مستقیم با JSON در prefs seed می‌کنیم (همان فرمت Gson داخلی)
 * - resolve / coveragePct را روی سناریوهای واقعی تست می‌کنیم:
 *   POINT_IN_TIME / NEAREST_SNAPSHOT / TODAY_BIASED / ضد نشتی / پوشش
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoricalUniverseRepositoryRobolectricTest {

    private lateinit var ctx: Context
    private val DAY_MS = 86_400_000L
    private val DAY0 = 1_699_920_000_000L  // یک midnight UTC ثابت

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("pumpwatch_universe", 0).edit().clear().apply()
    }

    private fun seedDays(json: String) {
        ctx.getSharedPreferences("pumpwatch_universe", 0)
            .edit().putString("universe_days", json).apply()
    }

    // ========== recordSnapshot ==========

    @Test
    fun `recordSnapshot with empty list is a no-op`() {
        HistoricalUniverseRepository.recordSnapshot(ctx, emptyList())
        val stored = ctx.getSharedPreferences("pumpwatch_universe", 0)
            .getString("universe_days", "")
        assertTrue("Empty snapshot must not create garbage",
            stored.isNullOrEmpty() || stored == "[]")
    }

    // ========== resolve: سه منبع ==========

    @Test
    fun `resolve with empty store falls back to TODAY_BIASED`() {
        val r = HistoricalUniverseRepository.resolve(ctx, DAY0, emptyList())
        assertEquals(HistoricalUniverseRepository.UniverseSource.TODAY_BIASED, r.source)
        assertTrue(r.rankedIds.isEmpty())
    }

    @Test
    fun `resolve exact day returns POINT_IN_TIME`() {
        seedDays("""[{"dayMs":$DAY0,"rankedIds":["btc","eth","sol"]}]""")
        val r = HistoricalUniverseRepository.resolve(ctx, DAY0, emptyList())
        assertEquals(HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME, r.source)
        assertEquals(listOf("btc", "eth", "sol"), r.rankedIds)
        assertEquals(DAY0, r.snapshotDayMs)
    }

    @Test
    fun `resolve within 7 days returns NEAREST_SNAPSHOT`() {
        seedDays("""[{"dayMs":$DAY0,"rankedIds":["btc","eth"]}]""")
        val start = DAY0 + 3 * DAY_MS  // ۳ روز بعد، بدون snapshot دقیق
        val r = HistoricalUniverseRepository.resolve(ctx, start, emptyList())
        assertEquals(HistoricalUniverseRepository.UniverseSource.NEAREST_SNAPSHOT, r.source)
        assertEquals(DAY0, r.snapshotDayMs)
        assertEquals(listOf("btc", "eth"), r.rankedIds)
    }

    @Test
    fun `resolve picks the closest snapshot when several exist`() {
        seedDays(
            """[
                {"dayMs":${DAY0 + 2 * DAY_MS},"rankedIds":["new"]},
                {"dayMs":$DAY0,"rankedIds":["old"]}
            ]"""
        )
        val start = DAY0 + 4 * DAY_MS
        val r = HistoricalUniverseRepository.resolve(ctx, start, emptyList())
        assertEquals(HistoricalUniverseRepository.UniverseSource.NEAREST_SNAPSHOT, r.source)
        assertEquals("Nearest (not oldest) snapshot must win",
            DAY0 + 2 * DAY_MS, r.snapshotDayMs)
    }

    @Test
    fun `resolve beyond 7 day window falls back to TODAY_BIASED`() {
        seedDays("""[{"dayMs":$DAY0,"rankedIds":["btc"]}]""")
        val start = DAY0 + 10 * DAY_MS  // خارج از پنجرهٔ ۷ روزه
        val r = HistoricalUniverseRepository.resolve(ctx, start, emptyList())
        assertEquals(HistoricalUniverseRepository.UniverseSource.TODAY_BIASED, r.source)
    }

    // ========== ضد نشتی (مهم‌ترین تست) ==========

    @Test
    fun `ANTI-LEAK future snapshots are never used`() {
        // فقط snapshot آینده وجود دارد؛ resolve برای گذشته نباید از آن استفاده کند
        val future = DAY0 + 5 * DAY_MS
        seedDays("""[{"dayMs":$future,"rankedIds":["future-coin"]}]""")
        val r = HistoricalUniverseRepository.resolve(ctx, DAY0, emptyList())
        assertNotEquals("Future snapshot must not leak into past",
            HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME, r.source)
        assertFalse("Future membership must never appear",
            r.rankedIds.contains("future-coin"))
    }

    // ========== coveragePct ==========

    @Test
    fun `coveragePct counts seeded days correctly`() {
        seedDays("""[{"dayMs":$DAY0,"rankedIds":["btc"]}]""")
        // بازهٔ ۴ روزه که فقط ۱ روزش snapshot دارد → ۲۵٪
        val pct = HistoricalUniverseRepository.coveragePct(ctx, DAY0, DAY0 + 3 * DAY_MS)
        assertEquals(25, pct)
    }

    @Test
    fun `coveragePct full coverage is 100`() {
        seedDays(
            """[
                {"dayMs":$DAY0,"rankedIds":["a"]},
                {"dayMs":${DAY0 + DAY_MS},"rankedIds":["a"]},
                {"dayMs":${DAY0 + 2 * DAY_MS},"rankedIds":["a"]}
            ]"""
        )
        val pct = HistoricalUniverseRepository.coveragePct(ctx, DAY0, DAY0 + 2 * DAY_MS)
        assertEquals(100, pct)
    }

    @Test
    fun `coveragePct on empty store is zero`() {
        val pct = HistoricalUniverseRepository.coveragePct(ctx, DAY0, DAY0 + 3 * DAY_MS)
        assertEquals(0, pct)
    }
}
