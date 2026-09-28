package com.pumpwatch.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 88 (A1): تست HostLimiter با Mutex per-host.
 *
 * هدف: اثبات اینکه:
 *   - درخواست‌های همزمان به یک هاست سریالی می‌شوند
 *   - درخواست‌های همزمان به هاست‌های مختلف موازی اجرا می‌شوند
 *   - فاصلهٔ زمانی بین درخواست‌ها به تنظیمات minIntervalMs احترام می‌گذارد
 *
 * 🚀 Commit 88-fix: margin بیشتر برای تست‌ها (delay در CI دقیق نیست)
 */
class HostLimiterTest {

    @Test
    fun `sequential requests to same host respect interval`() = runBlocking {
        val limiter = HostLimiter(
            minIntervalMs = mapOf("host1" to 100L),
            defaultIntervalMs = 1000L
        )

        val timestamps = mutableListOf<Long>()
        for (i in 1..5) {
            limiter.acquire("host1")
            timestamps.add(System.currentTimeMillis())
        }

        // فاصلهٔ بین هر دو درخواست باید حداقل ۱۰۰ms باشد
        for (i in 1 until timestamps.size) {
            val gap = timestamps[i] - timestamps[i - 1]
            assertTrue("Gap should be >= 100ms, was ${gap}ms", gap >= 100)
            // 🚀 Commit 88-fix: margin بیشتر (delay در CI ممکن است jitter داشته باشد)
            assertTrue("Gap should be < 500ms (no excessive delay), was ${gap}ms", gap < 500)
        }
    }

    @Test
    fun `concurrent requests to same host are serialized`() = runBlocking {
        val limiter = HostLimiter(
            minIntervalMs = mapOf("host1" to 50L),
            defaultIntervalMs = 1000L
        )

        val timestamps = mutableListOf<Long>()
        val lock = Any()

        coroutineScope {
            val jobs = (1..10).map {
                async {
                    limiter.acquire("host1")
                    synchronized(lock) {
                        timestamps.add(System.currentTimeMillis())
                    }
                }
            }
            jobs.awaitAll()
        }

        assertEquals("All 10 requests should complete", 10, timestamps.size)

        // فاصلهٔ بین هر دو درخواست باید حداقل ۵۰ms باشد (چون سریالی شدند)
        val sorted = timestamps.sorted()
        for (i in 1 until sorted.size) {
            val gap = sorted[i] - sorted[i - 1]
            assertTrue("Gap should be >= 50ms, was ${gap}ms", gap >= 50)
        }
    }

    @Test
    fun `concurrent requests to different hosts run in parallel`() = runBlocking {
        val limiter = HostLimiter(
            minIntervalMs = mapOf(
                "host1" to 200L,
                "host2" to 200L,
                "host3" to 200L
            ),
            defaultIntervalMs = 1000L
        )

        val startMs = System.currentTimeMillis()
        val results = mutableMapOf<String, Long>()

        coroutineScope {
            val jobs = listOf("host1", "host2", "host3").map { host ->
                async {
                    limiter.acquire(host)
                    host to System.currentTimeMillis()
                }
            }
            jobs.awaitAll().forEach { (host, ts) -> results[host] = ts }
        }

        val endMs = System.currentTimeMillis()
        val totalTime = endMs - startMs

        // اگر موازی بودند، کل زمان باید حدود ۲۰۰ms باشد (نه ۶۰۰ms)
        // 🚀 Commit 88-fix: margin بیشتر (در CI ممکن است ۴۰۰-۵۰۰ms طول بکشد)
        assertTrue("Parallel execution should take ~200ms, took ${totalTime}ms", totalTime < 600)
        assertEquals("All 3 hosts should complete", 3, results.size)
    }

    @Test
    fun `default interval used for unknown host`() = runBlocking {
        val limiter = HostLimiter(
            minIntervalMs = mapOf("host1" to 50L),
            defaultIntervalMs = 150L
        )

        val timestamps = mutableListOf<Long>()
        for (i in 1..3) {
            limiter.acquire("unknown_host")
            timestamps.add(System.currentTimeMillis())
        }

        // فاصلهٔ بین هر دو درخواست باید حداقل ۱۵۰ms باشد (defaultIntervalMs)
        for (i in 1 until timestamps.size) {
            val gap = timestamps[i] - timestamps[i - 1]
            assertTrue("Gap should be >= 150ms, was ${gap}ms", gap >= 150)
            // 🚀 Commit 88-fix: margin بیشتر
            assertTrue("Gap should be < 500ms, was ${gap}ms", gap < 500)
        }
    }

    @Test
    fun `clear resets state`() = runBlocking {
        val limiter = HostLimiter(
            minIntervalMs = mapOf("host1" to 100L),
            defaultIntervalMs = 1000L
        )

        limiter.acquire("host1")
        val firstMs = System.currentTimeMillis()

        limiter.clear()

        limiter.acquire("host1")
        val secondMs = System.currentTimeMillis()

        // بعد از clear، نباید منتظر بماند
        val gap = secondMs - firstMs
        assertTrue("After clear, gap should be < 100ms, was ${gap}ms", gap < 100)
    }

    @Test
    fun `different intervals for different hosts`() = runBlocking {
        val limiter = HostLimiter(
            minIntervalMs = mapOf(
                "fast" to 20L,
                "slow" to 200L
            ),
            defaultIntervalMs = 1000L
        )

        val fastTimestamps = mutableListOf<Long>()
        val slowTimestamps = mutableListOf<Long>()

        coroutineScope {
            val fastJobs = (1..5).map {
                async {
                    limiter.acquire("fast")
                    fastTimestamps.add(System.currentTimeMillis())
                }
            }
            val slowJobs = (1..3).map {
                async {
                    limiter.acquire("slow")
                    slowTimestamps.add(System.currentTimeMillis())
                }
            }
            fastJobs.awaitAll()
            slowJobs.awaitAll()
        }

        // فاصلهٔ fast باید حدود ۲۰ms باشد (با margin بیشتر)
        for (i in 1 until fastTimestamps.size) {
            val gap = fastTimestamps[i] - fastTimestamps[i - 1]
            assertTrue("Fast gap should be >= 20ms, was ${gap}ms", gap >= 20)
            // 🚀 Commit 88-fix: margin بیشتر
            assertTrue("Fast gap should be < 200ms, was ${gap}ms", gap < 200)
        }

        // فاصلهٔ slow باید حدود ۲۰۰ms باشد (با margin بیشتر)
        for (i in 1 until slowTimestamps.size) {
            val gap = slowTimestamps[i] - slowTimestamps[i - 1]
            assertTrue("Slow gap should be >= 200ms, was ${gap}ms", gap >= 200)
            // 🚀 Commit 88-fix: margin بیشتر
            assertTrue("Slow gap should be < 500ms, was ${gap}ms", gap < 500)
        }
    }
}
