package com.pumpwatch.app.data

import com.google.gson.JsonArray
import java.util.concurrent.ConcurrentHashMap

/**
 * 🚀 Sprint 2 (P1-1 + P1-4): لایهٔ cache عمومی با TTL و سقف تعداد.
 *
 * - thread-safe با ConcurrentHashMap
 * - هر entry پس از ttlMillis منقضی می‌شود (تنبل: هنگام get بررسی می‌شود)
 * - P1-4: اگر تعداد entryها از maxEntries تجاوز کند، قدیمی‌ترین entry
 *   حذف می‌شود (LRU-like eviction) تا حافظه از کنترل خارج نشود
 * - prune() برای پاک‌سازی دوره‌ای بر اساس زمان
 */
class TtlCache<V>(
    private val ttlMillis: Long,
    private val maxEntries: Int = Int.MAX_VALUE
) {

    private class Entry<V>(val value: V, val storedAt: Long)

    private val map = ConcurrentHashMap<String, Entry<V>>()

    fun get(key: String): V? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.storedAt >= ttlMillis) {
            map.remove(key)
            return null
        }
        return e.value
    }

    fun put(key: String, value: V) {
        // P1-4: اگر سقف فعال است و پر شده، قدیمی‌ترین entry را حذف کن
        if (maxEntries < Int.MAX_VALUE && map.size >= maxEntries && !map.containsKey(key)) {
            evictOldest()
        }
        map[key] = Entry(value, System.currentTimeMillis())
    }

    /**
     * حذف قدیمی‌ترین entry بر اساس storedAt (LRU-like).
     * O(n) است ولی فقط هنگام پر شدن سقف فراخوانی می‌شود.
     */
    private fun evictOldest() {
        var oldestKey: String? = null
        var oldestTs = Long.MAX_VALUE
        for ((k, e) in map) {
            if (e.storedAt < oldestTs) {
                oldestTs = e.storedAt
                oldestKey = k
            }
        }
        oldestKey?.let { map.remove(it) }
    }

    fun getOrPut(key: String, loader: () -> V?): V? {
        get(key)?.let { return it }
        val value = loader() ?: return null
        put(key, value)
        return value
    }

    fun invalidate(key: String) { map.remove(key) }
    fun clear() { map.clear() }

    fun prune() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.storedAt >= ttlMillis }
    }

    fun size(): Int = map.size
    fun maxSize(): Int = maxEntries
}

/**
 * کندل‌های Binance با cache شصت‌ثانیه‌ای و سقف ۳۰۰ entry.
 *
 * چرا ۳۰۰: TOP_SYMBOLS=۵۰ + چندین تایم‌فریم در TradesScreen + buffer برای
 * BatchScanner که تا ۱۰۰ ارز را با ۱h/۱d می‌خواند. در بک‌تست طولانی،
 * قدیمی‌ترین entryها خودکار evict می‌شوند تا حافظه از کنترل خارج نشود.
 */
object KlineCache {
    private const val TTL_MS = 60_000L
    // 🚀 P1-4: سقف ۳۰۰ entry — جلوگیری از رشد بی‌پایان حافظه در اسکن‌های مکرر
    private const val MAX_ENTRIES = 300
    private val cache = TtlCache<List<JsonArray>>(TTL_MS, MAX_ENTRIES)

    suspend fun klines(symbol: String, interval: String, limit: Int): List<JsonArray> {
        val key = "$symbol|$interval|$limit"
        cache.get(key)?.let { return it }
        val fresh = BinanceClient.api.klines(symbol, interval, limit)
        cache.put(key, fresh)
        return fresh
    }

    fun invalidate(symbol: String, interval: String, limit: Int) {
        cache.invalidate("$symbol|$interval|$limit")
    }

    fun clear() = cache.clear()
    fun size(): Int = cache.size()
    fun maxSize(): Int = MAX_ENTRIES
}
