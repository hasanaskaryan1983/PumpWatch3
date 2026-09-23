package com.pumpwatch.app.wallet.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 52: تست‌های JVM برای Gateway (معیار قبولی فاز ۷).
 */
class ProviderGatewayTest {

    private var now = 1_000_000L
    private val gateway = ProviderGateway(clock = { now }, sleep = { })

    @Test
    fun secondCallWithinTtl_isServedFromCache() {
        var fetchCount = 0
        val r1 = gateway.call("src", "k1", ttlMs = 60_000) { fetchCount++; "data" }
        val r2 = gateway.call("src", "k1", ttlMs = 60_000) { fetchCount++; "data" }
        assertTrue(r1.isSuccess); assertFalse(r1.fromCache)
        assertTrue(r2.isSuccess); assertTrue(r2.fromCache)
        assertEquals(1, fetchCount)   // فقط یک تماس واقعی
    }

    @Test
    fun cacheExpiresAfterTtl() {
        var fetchCount = 0
        gateway.call("src", "k2", ttlMs = 10_000) { fetchCount++; "d" }
        now += 10_001
        gateway.call("src", "k2", ttlMs = 10_000) { fetchCount++; "d" }
        assertEquals(2, fetchCount)
    }

    @Test
    fun breakerOpensAfterThreshold_andBlocksWithoutFetch() {
        var fetchCount = 0
        fun failing() = gateway.call<String>("bad", "k", 1000, attempts = 1) {
            fetchCount++
            throw ProviderException(ProviderError.RateLimited)
        }
        failing(); failing(); failing()          // ۳ شکست پیاپی
        assertEquals("open", gateway.breakerState("bad"))
        val blocked = failing()                  // نباید fetch صدا زده شود
        assertEquals(ProviderError.CircuitOpen, blocked.error)
        assertEquals(3, fetchCount)
    }

    @Test
    fun breakerHalfOpenAfterCooldown_thenClosesOnSuccess() {
        var shouldFail = true
        fun probe() = gateway.call<String>("bad2", "k", 1000, attempts = 1) {
            if (shouldFail) throw ProviderException(ProviderError.Timeout) else "ok"
        }
        probe(); probe(); probe()
        assertEquals("open", gateway.breakerState("bad2"))
        now += 61_000                            // پایان cooldown
        assertEquals("half-open", gateway.breakerState("bad2"))
        shouldFail = false
        val r = probe()                          // probe موفق
        assertTrue(r.isSuccess)
        assertEquals("closed", gateway.breakerState("bad2"))
    }

    @Test
    fun retrySucceedsOnSecondAttempt() {
        var calls = 0
        val r = gateway.call("src3", "k", 1000, attempts = 2) {
            calls++
            if (calls == 1) throw ProviderException(ProviderError.Unknown("flaky")) else "ok"
        }
        assertTrue(r.isSuccess)
        assertEquals(2, calls)
    }

    @Test
    fun failedCall_returnsTypedError_notNull_silently() {
        val r = gateway.call<String>("src4", "k", 1000, attempts = 1) {
            throw ProviderException(ProviderError.Http(500))
        }
        assertFalse(r.isSuccess)
        assertEquals(ProviderError.Http(500), r.error)
        assertNull(r.value)
    }
}
