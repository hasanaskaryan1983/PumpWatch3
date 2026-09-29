package com.pumpwatch.app.wallet.gateway

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGatewayThreadSafetyTest {

    @Test
    fun testConcurrentCachePutAndGet() = runBlocking {
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
    }

    @Test
    fun testConcurrentCallsWithSameKeyUseCache() = runBlocking {
        var fetchCount = 0
        val gateway = ProviderGateway()

        gateway.callSuspend("test", "shared-key", 60_000, attempts = 1) {
            fetchCount++
            "shared-value"
        }
        assertEquals(1, fetchCount)

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
                assertTrue(r.isSuccess)
                assertEquals("shared-value", r.value)
                assertTrue(r.fromCache)
            }
        }
        assertEquals(1, fetchCount)
    }

    @Test
    fun testCircuitBreakerOpensAfterThreshold() = runBlocking {
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

        assertEquals("open", gateway.breakerState("flaky"))
    }

    @Test
    fun testCircuitBreakerReturnsCircuitOpenError() = runBlocking {
        val gateway = ProviderGateway(failureThreshold = 2, cooldownMs = 60_000)

        repeat(2) {
            gateway.callSuspend("broken", "key", 60_000, attempts = 1) {
                throw RuntimeException("fail")
            }
        }

        var fetchCalled = false
        val result = gateway.callSuspend("broken", "key", 60_000, attempts = 1) {
            fetchCalled = true
            "should not reach"
        }

        assertTrue(!result.isSuccess)
        assertEquals(ProviderError.CircuitOpen, result.error)
        assertTrue(!fetchCalled)
    }

    @Test
    fun testTtlCacheExpiresEntries() {
        var currentTime = 0L
        val cache = TtlCache({ currentTime })

        cache.put("k1", "v1", 100)
        assertEquals("v1", cache.get<String>("k1"))

        currentTime = 50
        assertEquals("v1", cache.get<String>("k1"))

        currentTime = 150
        assertNull(cache.get<String>("k1"))
    }

    @Test
    fun testCircuitBreakerResetsAfterSuccess() = runBlocking {
        var failMode = true
        val gateway = ProviderGateway(failureThreshold = 2, cooldownMs = 60_000)

        gateway.callSuspend("flaky", "key", 60_000, attempts = 1) {
            if (failMode) throw RuntimeException("fail") else "ok"
        }
        assertEquals("closed", gateway.breakerState("flaky"))

        failMode = false
        val success = gateway.callSuspend("flaky", "key", 60_000, attempts = 1) {
            if (failMode) throw RuntimeException("fail") else "ok"
        }
        assertTrue(success.isSuccess)
        assertEquals("closed", gateway.breakerState("flaky"))
    }

    @Test
    fun testMultipleSourcesHaveIndependentBreakers() = runBlocking {
        val gateway = ProviderGateway(failureThreshold = 2, cooldownMs = 60_000)

        repeat(2) {
            gateway.callSuspend("sourceA", "k1", 60_000, attempts = 1) {
                throw RuntimeException("fail A")
            }
        }
        assertEquals("open", gateway.breakerState("sourceA"))

        val resultB = gateway.callSuspend("sourceB", "k2", 60_000, attempts = 1) {
            "ok B"
        }
        assertTrue(resultB.isSuccess)
        assertEquals("closed", gateway.breakerState("sourceB"))
    }

    @Test
    fun testCacheSizeReturnsCorrectCount() {
        var currentTime = 0L
        val cache = TtlCache({ currentTime })

        assertEquals(0, cache.size())
        cache.put("k1", "v1", 100)
        assertEquals(1, cache.size())
        cache.put("k2", "v2", 100)
        assertEquals(2, cache.size())

        currentTime = 150
        cache.get<String>("k1")
        assertEquals(1, cache.size())
    }

    @Test
    fun testCacheClearRemovesAllEntries() {
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
