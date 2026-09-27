package com.pumpwatch.app.data

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * 🚀 Commit 81 (فاز ۲ — observability): متریک‌های runtime برای رصد سلامت شبکه.
 *
 * این متریک‌ها به ما اجازه می‌دهند بفهمیم:
 *   - چند درخواست به هر endpoint فرستادیم؟ (totalRequests)
 *   - چند بار rate-limit شدیم؟ (rateLimitHits)
 *   - ۹۵٪ درخواست‌ها چقدر طول کشید؟ (p95LatencyMs)
 *   - چند پاسخ ناقص بود؟ (partialResponses)
 *
 * بدون این متریک‌ها، نمی‌فهمیم آیا Commit 73 (ThrottledHttp) واقعاً 429 را
 * کم کرد، یا یک endpoint خاص کند است، یا فیلترها زیادی سخت‌گیرانه‌اند.
 *
 * Thread-safe: همهٔ counters از AtomicLong استفاده می‌کنند.
 * P95 latency: ring buffer از ۱۰۰ آخر latency، sort، index 95.
 */
object Metrics {

    private val totalRequests = AtomicLong(0L)
    private val rateLimitHits = AtomicLong(0L)
    private val partialResponses = AtomicLong(0L)

    // Ring buffer برای P95 latency (100 last samples)
    private val latencyBuffer = LongArray(100)
    private val latencyIndex = AtomicLong(0L)
    private val latencyLock = Any()

    /** Interceptor برای جمع‌آوری metrics (به ThrottledHttp.client اضافه می‌شود) */
    val interceptor: Interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val startMs = System.currentTimeMillis()
            val request = chain.request()

            return try {
                val response = chain.proceed(request)
                val latencyMs = System.currentTimeMillis() - startMs

                recordRequest()
                recordLatency(latencyMs)

                if (response.code == 429) {
                    recordRateLimit()
                }

                response
            } catch (e: Exception) {
                // Network error — هنوز request count می‌شود
                recordRequest()
                throw e
            }
        }
    }

    /** ثبت یک درخواست (توسط interceptor صدا زده می‌شود) */
    fun recordRequest() {
        totalRequests.incrementAndGet()
    }

    /** ثبت یک rate-limit hit (429) */
    fun recordRateLimit() {
        rateLimitHits.incrementAndGet()
    }

    /**
     * ثبت latency یک درخواست (milliseconds).
     *
     * 🚀 Commit 81-fix: getAndIncrement به‌جای incrementAndGet.
     *   قبلاً: incrementAndGet() اول افزایش می‌داد (0→1)، بعد index را برمی‌گرداند (1)
     *     → داده در index 1 ذخیره می‌شد، نه 0 → اولین sample از دست می‌رفت
     *   حالا: getAndIncrement() اول index فعلی را برمی‌گرداند (0)، بعد افزایش می‌دهد (0→1)
     *     → داده در index 0 ذخیره می‌شود (درست)
     */
    fun recordLatency(latencyMs: Long) {
        synchronized(latencyLock) {
            val idx = (latencyIndex.getAndIncrement() % 100).toInt()
            latencyBuffer[idx] = latencyMs
        }
    }

    /** ثبت یک پاسخ ناقص (partial response) */
    fun recordPartialResponse() {
        partialResponses.incrementAndGet()
    }

    /**
     * Snapshot از متریک‌ها (برای UI یا debug).
     *
     * P95 latency: sort کردن ring buffer، گرفتن index 95.
     * اگر کمتر از ۱۰۰ sample داشته باشیم، از موجود استفاده می‌کنیم.
     *
     * 🚀 Commit 81-fix: الگوریتم P95 اصلاح شد.
     *   قبلاً: (count * 0.95).toInt() → index 95 برای 100 sample = 96امین مقدار (اشتباه)
     *   حالا: ((count - 1) * 0.95).toInt() → index 94 برای 100 sample = 95امین مقدار (درست)
     */
    fun snapshot(): MetricsSnapshot {
        val p95 = synchronized(latencyLock) {
            val count = minOf(latencyIndex.get(), 100L).toInt()
            if (count == 0) {
                0L
            } else {
                val sorted = latencyBuffer.copyOf(count).sorted()
                // P95 = 95th percentile = index (n-1) * 0.95
                val p95Index = ((count - 1) * 0.95).toInt()
                sorted[p95Index]
            }
        }

        return MetricsSnapshot(
            totalRequests = totalRequests.get(),
            rateLimitHits = rateLimitHits.get(),
            partialResponses = partialResponses.get(),
            p95LatencyMs = p95
        )
    }

    /** ریست کردن همهٔ متریک‌ها (برای تست یا debug) */
    fun reset() {
        totalRequests.set(0L)
        rateLimitHits.set(0L)
        partialResponses.set(0L)
        synchronized(latencyLock) {
            latencyIndex.set(0L)
            latencyBuffer.fill(0L)
        }
    }
}

/**
 * Snapshot از متریک‌ها در یک لحظه.
 *
 * @property totalRequests تعداد کل درخواست‌ها
 * @property rateLimitHits تعداد 429ها
 * @property partialResponses تعداد پاسخ‌های ناقص
 * @property p95LatencyMs ۹۵امین percentile latency (milliseconds)
 */
data class MetricsSnapshot(
    val totalRequests: Long,
    val rateLimitHits: Long,
    val partialResponses: Long,
    val p95LatencyMs: Long
)
