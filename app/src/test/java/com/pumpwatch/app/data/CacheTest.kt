package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * تست‌های واحد برای لایهٔ cache عمومی (TtlCache).
 *
 * P1-1 acceptance criteria:
 *  - TTL lazy expiry: entry منقضی روی get حذف می‌شود
 *  - Thread-safety: چند thread هم‌زمان بدون race یا crash
 *  - prune(): حذف فعال entryهای منقضی
 *  - invalidate/clear: رفتار درست حذف
 *  - getOrPut: load یکبار، cache، برگرداندن
 */
class CacheTest {

    // TTL کوتاه برای تست (۱۰۰ میلی‌ثانیه) — کافی برای اندازه‌گیری در CI
    private val SHORT_TTL = 100L

    @Test
    fun `put then get returns value before expiry`() {
        val cache = TtlCache<String>(SHORT_TTL * 10) // TTL طولانی‌تر برای جلوگیری از انقضا
        cache.put("key1", "value1")
        assertEquals("value1", cache.get("key1"))
    }

    @Test
    fun `get returns null for missing key`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun `get returns null after TTL expires`() {
        val cache = TtlCache<String>(SHORT_TTL)
        cache.put("key1", "value1")
        assertEquals("value1", cache.get("key1"))
        
        Thread.sleep(SHORT_TTL + 50) // صبر بیشتر از TTL
        
        assertNull("entry باید پس از TTL منقضی شود", cache.get("key1"))
    }

    @Test
    fun `getOrPut loads on miss and caches for subsequent calls`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
        val loadCount = AtomicInteger(0)
        
        val loader: () -> String = {
            loadCount.incrementAndGet()
            "loaded_value"
        }
        
        // اولین بار: loader اجرا می‌شود
        val v1 = cache.getOrPut("key1", loader)
        assertEquals("loaded_value", v1)
        assertEquals(1, loadCount.get())
        
        // دومین بار: از cache خوانده می‌شود (loader اجرا نمی‌شود)
        val v2 = cache.getOrPut("key1", loader)
        assertEquals("loaded_value", v2)
        assertEquals("loader باید فقط یکبار اجرا شود", 1, loadCount.get())
    }

    @Test
    fun `getOrPut returns null when loader returns null (no cache)`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
        val loadCount = AtomicInteger(0)
        
        val loader: () -> String? = {
            loadCount.incrementAndGet()
            null
        }
        
        // اولین بار: loader اجرا می‌شود و null برمی‌گرداند
        val v1 = cache.getOrPut("key1", loader)
        assertNull(v1)
        assertEquals(1, loadCount.get())
        
        // دومین بار: چون null cache نشده، loader دوباره اجرا می‌شود
        val v2 = cache.getOrPut("key1", loader)
        assertNull(v2)
        assertEquals("loader باید دوباره اجرا شود چون null cache نمی‌شود", 2, loadCount.get())
    }

    @Test
    fun `invalidate removes entry`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
        cache.put("key1", "value1")
        assertEquals("value1", cache.get("key1"))
        
        cache.invalidate("key1")
        assertNull(cache.get("key1"))
    }

    @Test
    fun `clear empties entire cache`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.put("k3", "v3")
        assertEquals(3, cache.size())
        
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get("k1"))
    }

    @Test
    fun `prune removes expired entries but keeps fresh ones`() {
        val cache = TtlCache<String>(SHORT_TTL)
        cache.put("old", "value1")
        
        Thread.sleep(SHORT_TTL + 50) // صبر برای انقضای "old"
        
        cache.put("fresh", "value2") // این تازه است
        
        cache.prune()
        
        assertNull("old باید حذف شود", cache.get("old"))
        assertEquals("fresh باید باقی بماند", "value2", cache.get("fresh"))
    }

    /**
     * Thread-safety: چند thread هم‌زمان روی یک key یکسان getOrPut می‌کنند.
     * انتظار:
     *  - هیچ exception نمی‌دهد
     *  - همه threadها مقدار یکسان می‌گیرند
     *  - cache اندازه نهایی ۱ است
     *
     * نکتهٔ شناخته‌شده: loader ممکن است چند بار اجرا شود (race بی‌ضرر)،
     * چون TtlCache از lock استفاده نمی‌کند. مهم این است که نتیجه درست است.
     */
    @Test
    fun `concurrent getOrPut on same key is thread-safe`() {
        val cache = TtlCache<String>(SHORT_TTL * 100)
        val pool = Executors.newFixedThreadPool(8)
        val threads = 20
        val latch = CountDownLatch(threads)
        val loadCount = AtomicInteger(0)
        val results = java.util.concurrent.ConcurrentLinkedQueue<String>()
        
        val loader: () -> String = {
            loadCount.incrementAndGet()
            Thread.sleep(10) // شبیه‌سازی کار طولانی (API call)
            "shared_value"
        }
        
        repeat(threads) {
            pool.submit {
                try {
                    val value = cache.getOrPut("shared_key", loader)
                    results.add(value)
                } finally {
                    latch.countDown()
                }
            }
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        pool.shutdown()
        
        // همه threadها مقدار یکسان گرفتند
        assertEquals("همه threadها باید مقدار یکسان بگیرند", 1, results.toSet().size)
        assertEquals("shared_value", results.first())
        
        // loader حداقل یکبار اجرا شده (ممکن است چند بار به‌خاطر race)
        assertTrue("loader حداقل یکبار اجرا شده", loadCount.get() >= 1)
        
        // cache فقط یک entry دارد
        assertEquals(1, cache.size())
    }

    @Test
    fun `concurrent puts on different keys are thread-safe`() {
        val cache = TtlCache<String>(SHORT_TTL * 100)
        val pool = Executors.newFixedThreadPool(8)
        val threads = 100
        val latch = CountDownLatch(threads)
        
        repeat(threads) { i ->
            pool.submit {
                try {
                    cache.put("key_$i", "value_$i")
                } finally {
                    latch.countDown()
                }
            }
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        pool.shutdown()
        
        assertEquals(threads, cache.size())
        repeat(threads) { i ->
            assertEquals("value_$i", cache.get("key_$i"))
        }
    }

    @Test
    fun `size reflects current entries`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
        assertEquals(0, cache.size())
        
        cache.put("k1", "v1")
        assertEquals(1, cache.size())
        
        cache.put("k2", "v2")
        assertEquals(2, cache.size())
        
        cache.invalidate("k1")
        assertEquals(1, cache.size())
    }
}
