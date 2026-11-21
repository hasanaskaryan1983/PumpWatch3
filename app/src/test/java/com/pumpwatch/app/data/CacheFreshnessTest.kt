package com.pumpwatch.app.data

import com.pumpwatch.app.testing.FakeClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 P0-1 (C1): تست رگرسیون روی همان تابعی که CoinApi در تولید استفاده می‌کند.
 * این تست «منطق خالصِ بازنویسی‌شده» نیست؛ اگر CacheFreshness بشکند، تولید هم می‌شکند.
 */
class CacheFreshnessTest {

    private val diskTtl = 1_800_000L   // DISK_FRESH_MS
    private val memTtl  = 120_000L     // MEM_CACHE_TTL

    @Test
    fun diskOlderThanTtlIsRejected() {
        val clock = FakeClock()
        val savedAt = WallMs(clock.now)
        clock.advanceHours(2)
        assertFalse(CacheFreshness.isDiskFresh(savedAt, WallMs(clock.now), diskTtl))
    }

    @Test
    fun diskWithinTtlIsAccepted() {
        val clock = FakeClock()
        val savedAt = WallMs(clock.now)
        clock.advanceMinutes(10)
        assertTrue(CacheFreshness.isDiskFresh(savedAt, WallMs(clock.now), diskTtl))
    }

    @Test
    fun diskWithZeroTimestampIsRejected() {
        assertFalse(CacheFreshness.isDiskFresh(WallMs(0L), WallMs(1_700_000_000_000L), diskTtl))
    }

    @Test
    fun memoryExpiresAfterTtl() {
        val clock = FakeClock(startMs = 500_000L)
        val storedAt = ElapsedMs(clock.now)
        clock.advanceMinutes(3)
        assertFalse(CacheFreshness.isMemFresh(storedAt, ElapsedMs(clock.now), memTtl))
    }

    @Test
    fun diskPromotionDoesNotResetMemoryTtl() {
        // دادهٔ دیسک ۹۰ ثانیه عمر دارد → هنوز ۳۰ ثانیه از TTL حافظه باقی است
        val now = ElapsedMs(1_000_000L)
        val backdated = CacheFreshness.backdateElapsed(now, 90_000L)
        assertTrue(CacheFreshness.isMemFresh(backdated, now, memTtl))
        // ولی دادهٔ ۱۳۰ ثانیه‌ای باید بلافاصله منقضی باشد (نه اینکه «تازه» شود)
        val old = CacheFreshness.backdateElapsed(now, 130_000L)
        assertFalse(CacheFreshness.isMemFresh(old, now, memTtl))
    }

    @Test
    fun futureTimestampIsNeverFresh() {
        val clock = FakeClock()
        val savedAt = WallMs(clock.now + 60_000L)
        assertFalse(CacheFreshness.isDiskFresh(savedAt, WallMs(clock.now), diskTtl))
    }
}
