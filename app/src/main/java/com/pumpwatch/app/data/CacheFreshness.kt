package com.pumpwatch.app.data

/**
 * 🚀 P0-1 (C1): پایه‌های زمانی مجزا.
 *
 * قانون ثابت پروژه:
 *  - هر چیزی که روی دیسک ذخیره می‌شود یا بین بوت‌ها معنا دارد → WallMs (currentTimeMillis)
 *  - هر چیزی که فقط TTL حافظه است → ElapsedMs (SystemClock.elapsedRealtime)
 *
 * این دو تایپ با هم سازگار نیستند؛ پس مقایسهٔ متقاطع (ریشهٔ C1) خطای کامپایل می‌شود.
 */
@JvmInline value class WallMs(val value: Long)
@JvmInline value class ElapsedMs(val value: Long)

object CacheFreshness {

    /** تازگی دیسک: هر دو طرف دیواری. */
    fun isDiskFresh(savedAt: WallMs, now: WallMs, ttlMs: Long): Boolean {
        if (savedAt.value <= 0L) return false
        val age = now.value - savedAt.value
        return age in 0 until ttlMs
    }

    /** تازگی حافظه: هر دو طرف مونوتونیک. */
    fun isMemFresh(storedAt: ElapsedMs, now: ElapsedMs, ttlMs: Long): Boolean {
        if (storedAt.value <= 0L) return false
        val age = now.value - storedAt.value
        return age in 0 until ttlMs
    }

    /**
     * هنگام ارتقای دیسک→حافظه، مهر مونوتونیک را به اندازهٔ سن واقعی داده عقب می‌بریم
     * تا TTL حافظه «ریست» نشود و دادهٔ کهنه دوباره تازه جلوه نکند.
     */
    fun backdateElapsed(now: ElapsedMs, dataAgeMs: Long): ElapsedMs =
        ElapsedMs(now.value - dataAgeMs.coerceAtLeast(0L))
}
