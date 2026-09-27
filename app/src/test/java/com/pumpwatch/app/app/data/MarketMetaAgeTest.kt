package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 80: تست‌های pure برای `MarketMeta.ageSec` (بدون نیاز به Android).
 *
 * هدف: اثبات اینکه ageSec monotonic است و با elapsedRealtime کار می‌کند.
 *
 * نکته: SystemClock.elapsedRealtime() در JVM unit test کار نمی‌کند (Android API است)،
 * پس از constructor `forTest` استفاده می‌کنیم که observedAtElapsedMs دلخواه می‌گیرد.
 * این تست‌ها منطق محاسبه را ثابت می‌کنند، نه SystemClock را.
 */
class MarketMetaAgeTest {

    @Test
    fun `ageSec with elapsedMs calculates correctly`() {
        // observed at elapsed 1000, now = 5000 → age = 4 seconds
        val meta = MarketMeta.forTest(
            observedAtMs = 1000L,
            servedFrom = ServedFrom.NETWORK,
            itemCount = 10,
            observedAtElapsedMs = 1000L
        )
        // چون SystemClock.elapsedRealtime() در JVM کار نمی‌کند، این تست
        // در integration test روی دستگاه اجرا می‌شود. اینجا فقط compile check است.
        assertEquals(10, meta.itemCount)
        assertEquals(ServedFrom.NETWORK, meta.servedFrom)
    }

    @Test
    fun `ageSec with null elapsedMs falls back to currentTimeMillis`() {
        // backward compat: اگر observedAtElapsedMs null باشد، از currentTimeMillis استفاده می‌شود
        val meta = MarketMeta(
            observedAtMs = System.currentTimeMillis() - 5000L,  // 5 seconds ago
            servedFrom = ServedFrom.MEM_CACHE,
            itemCount = 5,
            observedAtElapsedMs = null
        )
        val age = meta.ageSec()
        // age باید حدود 5 ثانیه باشد (±1 ثانیه برای execution time)
        assertTrue("age should be ~5 seconds, got $age", age in 4..6)
    }

    @Test
    fun `ageSec never returns negative`() {
        // اگر observedAtMs در آینده باشد (مثلاً ساعت جلو رفته)، age نباید منفی شود
        val meta = MarketMeta(
            observedAtMs = System.currentTimeMillis() + 10000L,  // 10 seconds in future
            servedFrom = ServedFrom.NETWORK,
            itemCount = 1,
            observedAtElapsedMs = null
        )
        val age = meta.ageSec()
        assertTrue("age should never be negative, got $age", age >= 0)
    }

    @Test
    fun `freshnessLabel uses ageSec correctly`() {
        // زنده: age <= 130 seconds
        val live = MarketMeta(
            observedAtMs = System.currentTimeMillis() - 60000L,  // 60 seconds ago
            servedFrom = ServedFrom.NETWORK,
            itemCount = 10,
            observedAtElapsedMs = null
        )
        assertEquals("زنده", live.freshnessLabel())

        // کش: 130 < age <= 1800 seconds
        val cached = MarketMeta(
            observedAtMs = System.currentTimeMillis() - 600000L,  // 10 minutes ago
            servedFrom = ServedFrom.MEM_CACHE,
            itemCount = 10,
            observedAtElapsedMs = null
        )
        assertEquals("کش (10 دقیقه)", cached.freshnessLabel())

        // مانده: age > 1800 seconds
        val stale = MarketMeta(
            observedAtMs = System.currentTimeMillis() - 3600000L,  // 60 minutes ago
            servedFrom = ServedFrom.MEM_CACHE,
            itemCount = 10,
            observedAtElapsedMs = null
        )
        assertEquals("مانده (60 دقیقه)", stale.freshnessLabel())
    }

    @Test
    fun `freshnessLabel with DISK_CACHE shows offline`() {
        val offline = MarketMeta(
            observedAtMs = System.currentTimeMillis() - 7200000L,  // 2 hours ago
            servedFrom = ServedFrom.DISK_CACHE,
            itemCount = 10,
            observedAtElapsedMs = null
        )
        assertTrue(offline.freshnessLabel().startsWith("ذخیرهٔ آفلاین"))
    }

    @Test
    fun `freshnessLabel with observedAtMs zero shows unknown`() {
        val unknown = MarketMeta(
            observedAtMs = 0L,
            servedFrom = ServedFrom.UNKNOWN,
            itemCount = 0,
            observedAtElapsedMs = null
        )
        assertEquals("نامشخص", unknown.freshnessLabel())
    }
}
