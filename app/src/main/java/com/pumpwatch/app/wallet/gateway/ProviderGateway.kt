package com.pumpwatch.app.wallet.gateway

/**
 * 🚀 Commit 52 (فاز ۷ برنامهٔ اجرایی): خطاهای نوع‌مند منبع.
 * catch-all ممنوع؛ هر خطا باید نوع داشته باشد.
 */
sealed class ProviderError {
    data class Http(val code: Int) : ProviderError()
    object RateLimited : ProviderError()
    object Timeout : ProviderError()
    object CircuitOpen : ProviderError()
    data class Unknown(val message: String) : ProviderError()
}

/** خطایی که providerها پرتاب می‌کنند تا Gateway نوعش را بفهمد */
class ProviderException(val error: ProviderError, message: String? = null) : Exception(message)

/**
 * 🚀 Commit 52: نتیجهٔ هر تماس با provenance کامل.
 */
data class ProviderResult<T>(
    val value: T?,
    val source: String,
    val fetchedAtMs: Long,
    val fromCache: Boolean,
    val error: ProviderError? = null
) {
    val isSuccess: Boolean get() = error == null && value != null
}

/** کش سادهٔ TTL — پایهٔ «جواب تکراری بدون شبکه» */
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

/**
 * 🚀 Commit 52: قطع‌کنندهٔ مدار — بعد از N شکست پیاپی، منبع موقتاً قطع می‌شود.
 * بعد از cooldown یک probe (half-open) مجاز است؛ موفق → بسته، شکست → باز مجدد.
 */
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
        else if (isHalfOpen) openedAt = clock()   // شکست در probe → باز مجدد
    }
}

/**
 * 🚀 Commit 52: درِ واحد دسترسی به داده.
 * ترتیب: کش → breaker → fetch با retry محدود → ثبت provenance.
 */
class ProviderGateway(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 60_000L
) {
    private val cache = TtlCache(clock)
    private val breakers = mutableMapOf<String, CircuitBreaker>()

    private fun breaker(source: String): CircuitBreaker =
        breakers.getOrPut(source) { CircuitBreaker(failureThreshold, cooldownMs, clock) }

    fun <T> call(
        source: String,
        key: String,
        ttlMs: Long,
        attempts: Int = 2,
        fetch: () -> T
    ): ProviderResult<T> {
        cache.get<T>(key)?.let {
            return ProviderResult(it, source, clock(), fromCache = true)
        }

        val b = breaker(source)
        if (!b.allowRequest()) {
            return ProviderResult(null, source, clock(), false, ProviderError.CircuitOpen)
        }

        var lastError: ProviderError? = null
        var attempt = 0
        while (attempt < attempts) {
            attempt++
            try {
                val v = fetch()
                b.recordSuccess()
                cache.put(key, v, ttlMs)
                return ProviderResult(v, source, clock(), fromCache = false)
            } catch (e: Exception) {
                lastError = classify(e)
                b.recordFailure()
                if (!b.allowRequest()) break
                if (attempt < attempts) sleep(minOf(1000L * attempt, 4000L))
            }
        }
        return ProviderResult(null, source, clock(), false, lastError)
    }

    private fun classify(e: Exception): ProviderError = when (e) {
        is ProviderException -> e.error
        else -> ProviderError.Unknown(e.message ?: e::class.java.simpleName)
    }

    /** وضعیت breaker یک منبع — برای نمایش صادقانه در UI/لاگ */
    fun breakerState(source: String): String = when {
        breaker(source).isOpen -> "open"
        breaker(source).isHalfOpen -> "half-open"
        else -> "closed"
    }

    fun cacheClear() = cache.clear()
}
