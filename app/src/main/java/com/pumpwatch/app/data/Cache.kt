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
 *
 * نکته: storedAtNano از System.nanoTime() برای ordering دقیق استفاده می‌کند
 *       (millis precision کافی نیست؛ ممکن است چند entry در یک millis باشند)
 *       ولی storedAtMs از System.currentTimeMillis() برای TTL check استفاده می‌شود.
 */
class TtlCache<V>(
    private val ttlMillis: Long,
    private val maxEntries: Int = Int.MAX_VALUE
) {

    private class Entry<V>(
        val value: V,
        val storedAtMs: Long,      // برای TTL check (wall clock)
        val storedAtNano: Long     // برای ordering دقیق (monotonic)
    )

    private val map = ConcurrentHashMap<String, Entry<V>>()

    fun get(key: String): V? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.storedAtMs >= ttlMillis) {
            map.remove(key)
            return null
        }
        return e.value
    }

    fun put(key: String, value: V) {
        if (maxEntries < Int.MAX_VALUE && map.size >= maxEntries && !map.containsKey(key)) {
            evictOldest()
        }
        map[key] = Entry(value, System.currentTimeMillis(), System.nanoTime())
    }

    private fun evictOldest() {
        var oldestKey: String? = null
        var oldestNano = Long.MAX_VALUE
        for ((k, e) in map) {
            if (e.storedAtNano < oldestNano) {
                oldestNano = e.storedAtNano
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
        map.entries.removeIf { now - it.value.storedAtMs >= ttlMillis }
    }

    fun size(): Int = map.size
    fun maxSize(): Int = maxEntries
}

/**
 * کندل‌های Binance با cache شصت‌ثانیه‌ای و سقف ۳۰۰ entry.
 */
object KlineCache {
    private const val TTL_MS = 60_000L
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
