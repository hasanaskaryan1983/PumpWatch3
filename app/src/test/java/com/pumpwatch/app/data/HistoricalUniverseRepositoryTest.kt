package com.pumpwatch.app.data

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

/**
 * 🚀 Commit 128: تست‌های pure برای HistoricalUniverseRepository.
 *
 * 🎯 استراتژی:
 * - توابع اصلی (recordSnapshot, resolve) نیاز به Context دارند و
 *   بدون Robolectric تست نمی‌شوند (در integration test ها پوشش داده می‌شوند).
 * - در اینجا فقط بخش‌های pure و data classes تست می‌شوند:
 *   • UniverseSource enum (3 values)
 *   • UniverseDay و ResolvedUniverse (data classes)
 *   • dayStart (تابع pure با UTC timezone) — از طریق reflection
 *   • ثابت‌های MAX_DAYS, MAX_RANK, DAY_MS, NEAREST_WINDOW_DAYS
 *
 * چرا مهم؟
 * - dayStart اگر اشتباه محاسبه شود، snapshot ها در روز اشتباه ذخیره می‌شوند
 *   و point-in-time query از کار می‌افتد → سوگیری بک‌تست
 * - enum ها و data classes اگر تغییر کنند، API می‌شکند
 */
class HistoricalUniverseRepositoryTest {

    // ========== UniverseSource enum ==========

    @Test
    fun `UniverseSource has exactly 3 values`() {
        assertEquals(3, HistoricalUniverseRepository.UniverseSource.values().size)
    }

    @Test
    fun `UniverseSource contains POINT_IN_TIME`() {
        assertNotNull(HistoricalUniverseRepository.UniverseSource.valueOf("POINT_IN_TIME"))
    }

    @Test
    fun `UniverseSource contains NEAREST_SNAPSHOT`() {
        assertNotNull(HistoricalUniverseRepository.UniverseSource.valueOf("NEAREST_SNAPSHOT"))
    }

    @Test
    fun `UniverseSource contains TODAY_BIASED`() {
        assertNotNull(HistoricalUniverseRepository.UniverseSource.valueOf("TODAY_BIASED"))
    }

    // ========== UniverseDay data class ==========

    @Test
    fun `UniverseDay equality works`() {
        val d1 = HistoricalUniverseRepository.UniverseDay(1000L, listOf("btc", "eth"))
        val d2 = HistoricalUniverseRepository.UniverseDay(1000L, listOf("btc", "eth"))
        assertEquals(d1, d2)
    }

    @Test
    fun `UniverseDay inequality on different day`() {
        val d1 = HistoricalUniverseRepository.UniverseDay(1000L, listOf("btc"))
        val d2 = HistoricalUniverseRepository.UniverseDay(2000L, listOf("btc"))
        assertNotEquals(d1, d2)
    }

    @Test
    fun `UniverseDay inequality on different ids`() {
        val d1 = HistoricalUniverseRepository.UniverseDay(1000L, listOf("btc"))
        val d2 = HistoricalUniverseRepository.UniverseDay(1000L, listOf("eth"))
        assertNotEquals(d1, d2)
    }

    @Test
    fun `UniverseDay copy preserves values`() {
        val original = HistoricalUniverseRepository.UniverseDay(1000L, listOf("btc", "eth"))
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun `UniverseDay copy with modified field`() {
        val original = HistoricalUniverseRepository.UniverseDay(1000L, listOf("btc"))
        val modified = original.copy(dayMs = 2000L)
        assertEquals(2000L, modified.dayMs)
        assertEquals(listOf("btc"), modified.rankedIds)
    }

    // ========== ResolvedUniverse data class ==========

    @Test
    fun `ResolvedUniverse has all required fields`() {
        val r = HistoricalUniverseRepository.ResolvedUniverse(
            rankedIds = listOf("btc", "eth"),
            source = HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME,
            snapshotDayMs = 1000L,
            coveragePct = 85
        )
        assertEquals(listOf("btc", "eth"), r.rankedIds)
        assertEquals(HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME, r.source)
        assertEquals(1000L, r.snapshotDayMs)
        assertEquals(85, r.coveragePct)
    }

    @Test
    fun `ResolvedUniverse equality works`() {
        val r1 = HistoricalUniverseRepository.ResolvedUniverse(
            listOf("btc"),
            HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME,
            1000L, 100
        )
        val r2 = HistoricalUniverseRepository.ResolvedUniverse(
            listOf("btc"),
            HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME,
            1000L, 100
        )
        assertEquals(r1, r2)
    }

    @Test
    fun `ResolvedUniverse inequality on different source`() {
        val r1 = HistoricalUniverseRepository.ResolvedUniverse(
            listOf("btc"),
            HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME,
            1000L, 100
        )
        val r2 = HistoricalUniverseRepository.ResolvedUniverse(
            listOf("btc"),
            HistoricalUniverseRepository.UniverseSource.TODAY_BIASED,
            1000L, 100
        )
        assertNotEquals(r1, r2)
    }

    @Test
    fun `ResolvedUniverse with all three sources`() {
        val sources = HistoricalUniverseRepository.UniverseSource.values()
        val results = sources.map { src ->
            HistoricalUniverseRepository.ResolvedUniverse(
                listOf("btc"), src, 1000L, 50
            )
        }
        assertEquals(3, results.size)
        assertEquals(HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME, results[0].source)
        assertEquals(HistoricalUniverseRepository.UniverseSource.NEAREST_SNAPSHOT, results[1].source)
        assertEquals(HistoricalUniverseRepository.UniverseSource.TODAY_BIASED, results[2].source)
    }

    // ========== dayStart via reflection ==========

    /**
     * call the private `dayStart` function via reflection.
     * In Kotlin `object`, non-@JvmStatic functions are instance methods on the singleton INSTANCE.
     */
    private fun invokeDayStart(ms: Long): Long {
        val clazz = HistoricalUniverseRepository::class.java
        val instanceField = clazz.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val instance = instanceField.get(null)
        val method: Method = clazz.getDeclaredMethod("dayStart", Long::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(instance, ms) as Long
    }

    @Test
    fun `dayStart at midnight UTC returns same timestamp`() {
        // Nov 14, 2023 00:00:00 UTC = 1699920000000 ms
        val midnight = 1699920000000L
        assertEquals(midnight, invokeDayStart(midnight))
    }

    @Test
    fun `dayStart at noon UTC returns previous midnight`() {
        // Nov 14, 2023 12:00:00 UTC = 1699963200000 ms
        // Expected: Nov 14, 2023 00:00:00 UTC = 1699920000000 ms
        val noon = 1699963200000L
        val expectedMidnight = 1699920000000L
        assertEquals(expectedMidnight, invokeDayStart(noon))
    }

    @Test
    fun `dayStart near end of day returns same day midnight`() {
        // Nov 14, 2023 23:59:59 UTC = 1700006399000 ms
        // Expected: Nov 14, 2023 00:00:00 UTC = 1699920000000 ms
        val late = 1700006399000L
        val expectedMidnight = 1699920000000L
        assertEquals(expectedMidnight, invokeDayStart(late))
    }

    @Test
    fun `dayStart always returns multiple of DAY_MS`() {
        val arbitrary = 1700000000000L
        val result = invokeDayStart(arbitrary)
        val dayMs = 86_400_000L
        assertEquals("dayStart must return UTC midnight (multiple of 24h)",
            0L, result % dayMs)
    }

    @Test
    fun `dayStart is deterministic across calls`() {
        val ts = 1700000000000L
        val r1 = invokeDayStart(ts)
        val r2 = invokeDayStart(ts)
        val r3 = invokeDayStart(ts)
        assertEquals(r1, r2)
        assertEquals(r2, r3)
    }

    @Test
    fun `dayStart difference between two days is exactly DAY_MS`() {
        val day1Midnight = 1699920000000L  // Nov 14, 2023 00:00 UTC
        val day2Noon = day1Midnight + 86_400_000L + 43_200_000L  // Nov 15, 12:00 UTC
        val d1 = invokeDayStart(day1Midnight)
        val d2 = invokeDayStart(day2Noon)
        assertEquals(86_400_000L, d2 - d1)
    }

    // ========== Constants via reflection ==========

    @Test
    fun `MAX_DAYS is 120`() {
        val field = HistoricalUniverseRepository::class.java.getDeclaredField("MAX_DAYS")
        field.isAccessible = true
        assertEquals(120, field.getInt(null))
    }

    @Test
    fun `MAX_RANK is 300`() {
        val field = HistoricalUniverseRepository::class.java.getDeclaredField("MAX_RANK")
        field.isAccessible = true
        assertEquals(300, field.getInt(null))
    }

    @Test
    fun `DAY_MS is 86400000`() {
        val field = HistoricalUniverseRepository::class.java.getDeclaredField("DAY_MS")
        field.isAccessible = true
        assertEquals(86_400_000L, field.getLong(null))
    }

    @Test
    fun `NEAREST_WINDOW_DAYS is 7`() {
        val field = HistoricalUniverseRepository::class.java.getDeclaredField("NEAREST_WINDOW_DAYS")
        field.isAccessible = true
        assertEquals(7L, field.getLong(null))
    }

    // ========== Integration smoke test ==========

    @Test
    fun `PREFS name is pumpwatch_universe`() {
        val field = HistoricalUniverseRepository::class.java.getDeclaredField("PREFS")
        field.isAccessible = true
        assertEquals("pumpwatch_universe", field.get(null))
    }

    @Test
    fun `KEY_DAYS name is universe_days`() {
        val field = HistoricalUniverseRepository::class.java.getDeclaredField("KEY_DAYS")
        field.isAccessible = true
        assertEquals("universe_days", field.get(null))
    }
}
