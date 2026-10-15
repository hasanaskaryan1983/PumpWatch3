package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🚀 Commit 81: تست‌های pure برای `Metrics` (بدون نیاز به Android).
 *
 * هدف: اثبات اینکه counters و P95 latency به‌درستی کار می‌کنند.
 */
class MetricsTest {

    @Before
    fun setup() {
        Metrics.reset()
    }

    @Test
    fun `recordRequest increments totalRequests`() {
        assertEquals(0L, Metrics.snapshot().totalRequests)
        Metrics.recordRequest()
        assertEquals(1L, Metrics.snapshot().totalRequests)
        Metrics.recordRequest()
        assertEquals(2L, Metrics.snapshot().totalRequests)
    }

    @Test
    fun `recordRateLimit increments rateLimitHits`() {
        assertEquals(0L, Metrics.snapshot().rateLimitHits)
        Metrics.recordRateLimit()
        assertEquals(1L, Metrics.snapshot().rateLimitHits)
    }

    @Test
    fun `recordPartialResponse increments partialResponses`() {
        assertEquals(0L, Metrics.snapshot().partialResponses)
        Metrics.recordPartialResponse()
        assertEquals(1L, Metrics.snapshot().partialResponses)
    }

    @Test
    fun `P95 latency with single sample returns that sample`() {
        Metrics.recordLatency(100L)
        val snapshot = Metrics.snapshot()
        assertEquals(100L, snapshot.p95LatencyMs)
    }

    @Test
    fun `P95 latency with multiple samples calculates correctly`() {
        // ۱۰۰ sample: 1ms, 2ms, ..., 100ms
        for (i in 1..100) {
            Metrics.recordLatency(i.toLong())
        }
        val snapshot = Metrics.snapshot()
        // P95 = 95th value = 95ms
        assertEquals(95L, snapshot.p95LatencyMs)
    }

    @Test
    fun `P95 latency with ring buffer wraps correctly`() {
        // ۱۵۰ sample: اول 1-100، بعد 101-150 (که 1-50 را overwrite می‌کند)
        for (i in 1..100) {
            Metrics.recordLatency(i.toLong())
        }
        for (i in 101..150) {
            Metrics.recordLatency(i.toLong())
        }
        // Ring buffer الان شامل 51-150 است (100 sample)
        // P95 = 95th value = 145ms
        val snapshot = Metrics.snapshot()
        assertEquals(145L, snapshot.p95LatencyMs)
    }

    @Test
    fun `P95 latency with zero samples returns zero`() {
        val snapshot = Metrics.snapshot()
        assertEquals(0L, snapshot.p95LatencyMs)
    }

    @Test
    fun `reset clears all metrics`() {
        Metrics.recordRequest()
        Metrics.recordRateLimit()
        Metrics.recordPartialResponse()
        Metrics.recordLatency(100L)

        Metrics.reset()

        val snapshot = Metrics.snapshot()
        assertEquals(0L, snapshot.totalRequests)
        assertEquals(0L, snapshot.rateLimitHits)
        assertEquals(0L, snapshot.partialResponses)
        assertEquals(0L, snapshot.p95LatencyMs)
    }

    @Test
    fun `snapshot is immutable`() {
        Metrics.recordRequest()
        val snapshot1 = Metrics.snapshot()
        Metrics.recordRequest()
        val snapshot2 = Metrics.snapshot()

        // snapshot1 نباید تغییر کند
        assertEquals(1L, snapshot1.totalRequests)
        assertEquals(2L, snapshot2.totalRequests)
    }

    @Test
    fun `MetricsSnapshot data class equality`() {
        val a = MetricsSnapshot(10, 2, 1, 100)
        val b = MetricsSnapshot(10, 2, 1, 100)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
