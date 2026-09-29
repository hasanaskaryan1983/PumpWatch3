package com.pumpwatch.app.wallet.gateway

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 90 (A3): تست thread-safety برای ProviderGateway.
 *
 * هدف: اثبات اینکه در concurrent access:
 *   - ConcurrentModificationException نمی‌دهد
 *   - cache و breaker state گم نمی‌شود
 *   - circuit breaker به‌درستی بعد از threshold باز می‌شود
 */
class ProviderGatewayThreadSafetyTest {

    @Test
    fun `concurrent cache put and get should not throw`() = runBlocking {
        val gateway = ProviderGateway()
        val iterations = 100

        coroutineScope {
            val jobs = (1..iterations).map { i ->
                async {
                    gateway.callSuspend("test", "key:$i", 60_000, attempts = 1) {
                        "value:$i"
                    }
                }
            }
            jobs.awaitAll()
        }
        // اگر اینجا برسیم بدون exception، تست پاس شده
    }

    @Test
    fun `concurrent calls with same key cache value after first fetch`() = runBlocking {
        var fetchCount = 0
        val gateway = ProviderGateway()

        // اول یک بار fetch کن تا cache شود
        gateway.callSuspend("test", "shared-key", 60_000, attempts = 1) {
            fetchCount++
            "shared-value"
        }
        assertEquals("First call should fetch", 1, fetchCount)

        // حالا ۵۰ coroutine همزمان باید همه از cache بخوانند
        coroutineScope {
            val jobs = (1..50).map {
                async {
                    gateway.callSuspend("test", "shared-key", 60_000, attempts = 1) {
                        fetchCount++
                        "shared-value"
                    }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { r ->
                assertTrue("All calls should succeed", r.isSuccess)
                assertEquals("shared-value", r.value)
                assertTrue("All calls should be from cache", r.fromCache)
            }
        }

        // هیچ‌کدام نباید دوباره fetch کرده باشند
        assertEquals("No additional fetches should happen", 1, fetchCount)
    }

    @Test
    fun `circuit breaker opens after threshold failures concurrently`() = runBlocking {
        val gateway = ProviderGateway(failureThreshold = 3, cooldownMs = 60_000)

        coroutineScope {
            val jobs = (1..5).map {
                async {
                    gateway.callSuspend("flaky", "key", 60_000, attempts = 1) {
                        throw RuntimeException("fail")
                    }
                }
            }
            jobs.awaitAll()
        }

        assertEquals("Circuit should open after 3 failures", "open", gateway.breakerState("flaky"))
    }

    @Test
    fun `circuit breaker returns CircuitOpen error when open`() = runBlocking {
        val gateway = ProviderGateway(failureThreshold = 2, cooldownMs = 60_000)

        // ۲ شکست برای open شدن circuit
        repeat(2) {
            gateway.callSuspend("broken", "key", 60_000, attempts = 1) {
                throw RuntimeException("fail")
            }
        }

        // درخواست بعدی باید CircuitOpen برگرداند، بدون اینکه fetch را صدا بزند
        var fetchCalled = false
        val result = gateway.callSuspend("broken", "key", 60_000, attempts = 1) {
            fetchCalled = true
            "should not reach"
        }

        assertTrue("Result should not be success", !result.isSuccess)
        assertEquals("Error should be CircuitOpen", ProviderError.CircuitOpen, result.error)
        assertTrue("Fetch should not be called when circuit open", !fetchCalled)
    }

    @Test
    fun `ttl cache expires entries`() {
        var currentTime = 0L
        val cache = TtlCache({ currentTime })

        cache.put("k1", "v1", 100)
        assertEquals("v1", cache.get<String>("k1"))

        currentTime = 50
        assertEquals("v1", cache.get<String>("k1"))

        currentTime = 150
        assertNull("Expired entry should return null", cache.get<String>("k1"))
    }

    @Test
    fun `circuit breaker resets after successful call`() = runBlocking {
        var failMode = true
        val gateway = ProviderGateway(failureThreshold = 2, cooldownMs = 60_000)

        // ۱ شکست
        gateway.callSuspend("flaky", "key", 60_000, attempts = 1) {
            if (failMode) throw RuntimeException("fail") else "ok"
        }
        assertEquals("closed", gateway.breakerState("flaky"))

        // حالا موفق
        failMode = false
        val success = gateway.callSuspend("flaky", "key", 60_000, attempts = 1) {
            if (failMode) throw RuntimeException("fail") else "ok"
        }
        assertTrue(success.isSuccess)
        assertEquals("closed", gateway.breakerState("flaky"))
    }

    @Test
    fun `multiple sources have independent breakers`() = runBlocking {
        val gateway = ProviderGateway(failureThreshold = 2, cooldownMs = 60_000)

        // source A خراب شود
        repeat(2) {
            gateway.callSuspend("sourceA", "k1", 60_000, attempts = 1) {
                throw RuntimeException("fail A")
            }
        }
        assertEquals("sourceA should be open", "open", gateway.breakerState("sourceA"))

        // source B باید بسته بماند
        val resultB = gateway.callSuspend("sourceB", "k2", 60_000, attempts = 1) {
            "ok B"
        }
        assertTrue("sourceB should still work", resultB.isSuccess)
        assertEquals("sourceB should be closed", "closed", gateway.breakerState("sourceB"))
    }

    @Test
    fun `cache size returns correct count`() {
        var currentTime = 0L
        val cache = TtlCache({ currentTime })

        assertEquals(0, cache.size())
        cache.put("k1", "v1", 100)
        assertEquals(1, cache.size())
        cache.put("k2", "v2", 100)
        assertEquals(2, cache.size())

        // منقضی شدن
        currentTime = 150
        cache.get<String>("k1")  // باید حذف شود
        assertEquals(1, cache.size())
    }

    @Test
    fun `cache clear removes all entries`() {
        var currentTime = 0L
        val cache = TtlCache({ currentTime })

        cache.put("k1", "v1", 100)
        cache.put("k2", "v2", 100)
        assertEquals(2, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get<String>("k1"))
    }
}
