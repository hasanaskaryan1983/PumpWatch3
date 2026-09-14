package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * تست‌های واحد برای لایهٔ cache عمومی (TtlCache) و KlineCache.
 *
 * P1-1: TTL lazy expiry، thread-safety، prune، invalidate/clear، getOrPut
 * P1-4: maxEntries با eviction، update بدون eviction، KlineCache bounds
 *
 * ⚠️ نکتهٔ درس‌گرفته: انتظارها باید «مقدار ذخیره‌شده» باشند، نه رشتهٔ کلید!
 */
class CacheTest {

    private val SHORT_TTL = 100L

    @Test
    fun `put then get returns value before expiry`() {
        val cache = TtlCache<String>(SHORT_TTL * 10)
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

        Thread.sleep(SHORT_TTL + 50)

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

        val v1 = cache.getOrPut("key1", loader)
        assertEquals("loaded_value", v1)
        assertEquals(1, loadCount.get())

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

        val v1 = cache.getOrPut("key1", loader)
        assertNull(v1)
        assertEquals(1, loadCount.get())

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

        Thread.sleep(SHORT_TTL + 50)

        cache.put("fresh", "value2")

        cache.prune()

        assertNull("old باید حذف شود", cache.get("old"))
        assertEquals("fresh باید باقی بماند", "value2", cache.get("fresh"))
    }

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
            Thread.sleep(10)
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

        assertEquals("همه threadها باید مقدار یکسان بگیرند", 1, results.toSet().size)
        assertEquals("shared_value", results.first())
        assertTrue("loader حداقل یکبار اجرا شده", loadCount.get() >= 1)
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

    // ================= تست‌های P1-4: سقف حافظه و eviction =================

    /**
     * 🚀 P1-4: وقتی maxEntries پر شد، put باید قدیمی‌ترین entry را حذف کند.
     * توجه: انتظارها «مقدار ذخیره‌شده» هستند: a→"1"، b→"2"، c→"3"، d→"4"
     */
    @Test
    fun `put evicts oldest entry when maxEntries reached`() {
        val cache = TtlCache<String>(SHORT_TTL * 100, maxEntries = 3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")
        assertEquals(3, cache.size())

        cache.put("d", "4")
        assertEquals("size باید روی maxEntries بماند", 3, cache.size())
        assertNull("قدیمی‌ترین (a) باید evict شده باشد", cache.get("a"))
        assertEquals("2", cache.get("b"))
        assertEquals("3", cache.get("c"))
        assertEquals("4", cache.get("d"))
    }

    /**
     * 🚀 P1-4: put روی key موجود eviction نمی‌کند و آن را به انتهای order می‌برد (LRU).
     */
    @Test
    fun `put updates existing key without triggering eviction`() {
        val cache = TtlCache<String>(SHORT_TTL * 100, maxEntries = 3)
        cache.put("a", "1")
        cache.put("b", "2")
        cache.put("c", "3")

        cache.put("a", "1-updated")
        assertEquals("size باید بدون تغییر بماند", 3, cache.size())
        assertEquals("1-updated", cache.get("a"))
        assertEquals("2", cache.get("b"))
        assertEquals("3", cache.get("c"))

        // حالا entry جدید: قدیمی‌ترین در order فعلی = b (چون a تازه‌تر شده)
        cache.put("d", "4")
        assertEquals(3, cache.size())
        assertNull("b باید evict شود (قدیمی‌ترین در order فعلی)", cache.get("b"))
        assertEquals("1-updated", cache.get("a"))
        assertEquals("3", cache.get("c"))
        assertEquals("4", cache.get("d"))
    }

    /**
     * 🚀 P1-4: KlineCache سقف معقول دارد.
     */
    @Test
    fun `KlineCache has sensible max size`() {
        val max = KlineCache.maxSize()
        assertTrue("KlineCache maxSize باید حداقل ۱۰۰ باشد", max >= 100)
        assertTrue("KlineCache maxSize نباید از ۱۰۰۰ بیشتر باشد", max <= 1000)
    }
}
