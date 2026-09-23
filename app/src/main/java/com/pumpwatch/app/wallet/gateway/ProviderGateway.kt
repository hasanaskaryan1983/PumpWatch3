package com.pumpwatch.app.wallet.gateway

import kotlinx.coroutines.delay

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

internal class TtlCache(private val clock: () -> Long) {
    private data class Entry(val value: Any?, val expiresAt: Long)
    private val map = mutableMapOf<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? =
        (map[key]?.takeIf { it.expiresAt > clock() }?.value) as T?

    fun put(key: String, value: Any?, ttlMs: Long) {
        map[key] = Entry(value, clock() + ttlMs)
    }

    fun clear() = map.clear()
}

internal class CircuitBreaker(
    private val failureThreshold: Int,
    private val cooldownMs: Long,
    private val clock: () -> Long
) {
    private var consecutiveFailures = 0
    private var openedAt: Long? = null

    val isOpen: Boolean get() {
        val at = openedAt ?: return false
        return (clock() - at) < cooldownMs
    }
    val isHalfOpen: Boolean get() = openedAt != null && !isOpen

    fun allowRequest(): Boolean = !isOpen

    fun recordSuccess() {
        consecutiveFailures = 0
        openedAt = null
    }

    fun recordFailure() {
        consecutiveFailures++
        if (openedAt == null && consecutiveFailures >= failureThreshold) openedAt = clock()
        else if (isHalfOpen) openedAt = clock()
    }
}

/**
 * 🚀 Commit 53: نسخهٔ suspend برای تماس‌های Retrofit اضافه شد؛
 * نسخهٔ سنکرون Commit 52 بدون تغییر باقی است.
 */
class ProviderGateway(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val sleepSuspend: suspend (Long) -> Unit = { delay(it) },
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 60_000L
) {
    private val cache = TtlCache(clock)
    private val breakers = mutableMapOf<String, CircuitBreaker>()

    private fun breaker(source: String): CircuitBreaker =
        breakers.getOrPut(source) { CircuitBreaker(failureThreshold, cooldownMs, clock) }

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
