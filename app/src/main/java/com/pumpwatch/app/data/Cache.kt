package com.pumpwatch.app.data

import com.google.gson.JsonArray

/**
 * 🚀 Sprint 2 (P1-1 + P1-4): لایهٔ cache عمومی با TTL و سقف تعداد.
 *
 * پیاده‌سازی با LinkedHashMap + @Synchronized:
 *  - LinkedHashMap به‌طور طبیعی insertion order را حفظ می‌کند
 *  - put روی key موجود ابتدا remove می‌کند تا entry به انتهای order برود (LRU-like)
 *  - eviction با `keys.firstOrNull()` ساده و deterministic است
 *  - @Synchronized thread-safety کامل (کمی سربار، ولی قابل‌قبول برای cache)
 *  - ConcurrentHashMap به‌خاطر size() تقریبی برای eviction مناسب نیست
 */
class TtlCache<V>(
    private val ttlMillis: Long,
    private val maxEntries: Int = Int.MAX_VALUE
) {

    private class Entry<V>(val value: V, val storedAtMs: Long)

    private val map = LinkedHashMap<String, Entry<V>>()

    @Synchronized
    fun get(key: String): V? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.storedAtMs >= ttlMillis) {
            map.remove(key)
            return null
        }
        return e.value
    }

    @Synchronized
    fun put(key: String, value: V) {
        // Remove first: if updating existing key, this moves it to the end (most recent)
        map.remove(key)
        map[key] = Entry(value, System.currentTimeMillis())
        // Evict oldest entries if over cap
        while (map.size > maxEntries) {
            val oldest = map.keys.firstOrNull() ?: break
            map.remove(oldest)
        }
    }

    @Synchronized
    fun getOrPut(key: String, loader: () -> V?): V? {
        val existing = get(key)
        if (existing != null) return existing
        val value = loader() ?: return null
        put(key, value)
        return value
    }

    @Synchronized
    fun invalidate(key: String) {
        map.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun prune() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.storedAtMs >= ttlMillis }
    }

    @Synchronized
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
