package com.pumpwatch.app.wallet.gateway

import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 🚀 Commit 52/53 (فاز ۷): خطاهای نوع‌مند منبع.
 */
sealed class ProviderError {
    data class Http(val code: Int) : ProviderError()
    object RateLimited : ProviderError()
    object Timeout : ProviderError()
    object CircuitOpen : ProviderError()
    data class Unknown(val message: String) : ProviderError()
}

class ProviderException(val error: ProviderError, message: String? = null) : Exception(message)

data class ProviderResult<T>(
    val value: T?,
    val source: String,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
    val error: ProviderError? = null
) {
    val isSuccess: Boolean get() = error == null && value != null
}

/**
 * 🚀 Commit 90 (A3): TtlCache thread-safe با ConcurrentHashMap.
 *
 * تغییر نسبت به Commit 52:
 *   - `mutableMapOf` → `ConcurrentHashMap` (thread-safe بدون قفل سراسری)
 *   - lazy eviction در `get()`: اگر entry منقضی شده، حذف می‌شود
 *   - `size()` برای تست/دیباگ
 *
 * قبلاً در concurrent access (چند coroutine همزمان `put`/`get`):
 *   - ConcurrentModificationException می‌داد
 *   - ممکن بود entry گم شود
 */
internal class TtlCache(private val clock: () -> Long) {
    private data class Entry(val value: Any?, val expiresAt: Long)
    private val map = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = map[key] ?: return null
        if (entry.expiresAt <= clock()) {
            map.remove(key)
            return null
        }
        return entry.value as T?
    }

    fun put(key: String, value: Any?, ttlMs: Long) {
        map[key] = Entry(value, clock() + ttlMs)
    }

    fun clear() = map.clear()

    /** برای تست و دیباگ — تعداد entryهای فعلی */
    fun size(): Int = map.size
}

/**
 * 🚀 Commit 90 (A3): CircuitBreaker thread-safe با AtomicInteger/AtomicReference.
 *
 * تغییر نسبت به Commit 52:
 *   - `consecutiveFailures: Int` → `AtomicInteger`
 *   - `openedAt: Long?` → `AtomicReference<Long?>`
 *   - `compareAndSet` برای انتقال اتمی به حالت open
 *
 * قبلاً:
 *   - چند coroutine همزمان `recordFailure` کنند → counter اشتباه
 *   - `openedAt` ممکن است در race از دست برود
 *
 * حالا:
 *   - `incrementAndGet()` به‌طور اتمی counter را افزایش می‌دهد
 *   - `compareAndSet(null, clock())` تضمین می‌کند فقط یک coroutine
 *     breaker را به حالت open می‌برد
 */
internal class CircuitBreaker(
    private val failureThreshold: Int,
    private val cooldownMs: Long,
    private val clock: () -> Long
) {
    private val consecutiveFailures = AtomicInteger(0)
    private val openedAt = AtomicReference<Long?>(null)

    val isOpen: Boolean get() {
        val at = openedAt.get() ?: return false
        return (clock() - at) < cooldownMs
    }

    val isHalfOpen: Boolean get() = openedAt.get() != null && !isOpen

    fun allowRequest(): Boolean = !isOpen

    fun recordSuccess() {
        consecutiveFailures.set(0)
        openedAt.set(null)
    }

    fun recordFailure() {
        val newCount = consecutiveFailures.incrementAndGet()
        // انتقال به open فقط اگر قبلاً open نبوده (race-safe)
        if (openedAt.get() == null && newCount >= failureThreshold) {
            openedAt.compareAndSet(null, clock())
        } else if (isHalfOpen) {
            openedAt.set(clock())
        }
    }

    /** برای تست و دیباگ */
    fun failureCount(): Int = consecutiveFailures.get()
}

/**
 * 🚀 Commit 53: نسخهٔ suspend برای تماس‌های Retrofit اضافه شد؛
 * نسخهٔ سنکرون Commit 52 بدون تغییر باقی است.
 *
 * 🚀 Commit 90 (A3): thread-safe.
 *   - breakers: ConcurrentHashMap + computeIfAbsent (اتمی)
 *   - cache و breaker هر کدام thread-safe هستند
 */
class ProviderGateway(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val sleepSuspend: suspend (Long) -> Unit = { delay(it) },
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 60_000L
) {
    private val cache = TtlCache(clock)
    private val breakers = ConcurrentHashMap<String, CircuitBreaker>()

    private fun breaker(source: String): CircuitBreaker =
        breakers.computeIfAbsent(source) { CircuitBreaker(failureThreshold, cooldownMs, clock) }

    // ---------- نسخهٔ سنکرون (Commit 52) ----------
    fun <T> call(
        source: String,
        key: String,
        ttlMs: Long,
        attempts: Int = 2,
        fetch: () -> T
    ): ProviderResult<T> {
        cache.get<T>(key)?.let { return ProviderResult(it, source, clock(), true) }
        val b = breaker(source)
        if (!b.allowRequest()) return ProviderResult(null, source, clock(), false, ProviderError.CircuitOpen)
        var lastError: ProviderError? = null
        var attempt = 0
        while (attempt < attempts) {
            attempt++
            try {
                val v = fetch()
                b.recordSuccess(); cache.put(key, v, ttlMs)
                return ProviderResult(v, source, clock(), false)
            } catch (e: Exception) {
                lastError = classify(e); b.recordFailure()
                if (!b.allowRequest()) break
                if (attempt < attempts) sleep(minOf(1000L * attempt, 4000L))
            }
        }
        return ProviderResult(null, source, clock(), false, lastError)
    }

    // ---------- نسخهٔ suspend (Commit 53) ----------
    suspend fun <T> callSuspend(
        source: String,
        key: String,
        ttlMs: Long,
        attempts: Int = 2,
        fetch: suspend () -> T
    ): ProviderResult<T> {
        cache.get<T>(key)?.let { return ProviderResult(it, source, clock(), true) }
        val b = breaker(source)
        if (!b.allowRequest()) return ProviderResult(null, source, clock(), false, ProviderError.CircuitOpen)
        var lastError: ProviderError? = null
        var attempt = 0
        while (attempt < attempts) {
            attempt++
            try {
                val v = fetch()
                b.recordSuccess(); cache.put(key, v, ttlMs)
                return ProviderResult(v, source, clock(), false)
            } catch (e: Exception) {
                lastError = classify(e); b.recordFailure()
                if (!b.allowRequest()) break
                if (attempt < attempts) sleepSuspend(minOf(1000L * attempt, 4000L))
            }
        }
        return ProviderResult(null, source, clock(), false, lastError)
    }

    private fun classify(e: Exception): ProviderError = when (e) {
        is ProviderException -> e.error
        else -> ProviderError.Unknown(e.message ?: e::class.java.simpleName)
    }

    fun breakerState(source: String): String = when {
        breaker(source).isOpen -> "open"
        breaker(source).isHalfOpen -> "half-open"
        else -> "closed"
    }

    fun cacheClear() = cache.clear()
}
