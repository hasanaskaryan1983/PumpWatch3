package com.pumpwatch.app.testing

/**
 * 🚀 Commit 9: ساعت جعلی برای تست‌های زمانی — بدون هیچ dependency جدید.
 * استفاده: val clock = FakeClock(); clock.advanceHours(6); سپس clock.now را
 * به پارامتر nowMs توابع تزریق کنید.
 */
class FakeClock(startMs: Long = 1_700_000_000_000L) {
    var now: Long = startMs
        private set

    fun advance(ms: Long) { now += ms }
    fun advanceMinutes(m: Long) { advance(m * 60_000L) }
    fun advanceHours(h: Long) { advance(h * 3_600_000L) }
    fun advanceDays(d: Long) { advance(d * 86_400_000L) }
}
