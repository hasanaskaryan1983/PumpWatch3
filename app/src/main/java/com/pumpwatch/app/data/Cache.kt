package com.pumpwatch.app.data

import com.google.gson.JsonArray
import java.util.concurrent.ConcurrentHashMap

/**
 * 🚀 Sprint 2 (P1-1): لایهٔ cache عمومی با TTL.
 *
 * - thread-safe با ConcurrentHashMap (بدون نیاز به lock دستی)
 * - هر entry پس از ttlMillis منقضی می‌شود (تنبل: هنگام get بررسی می‌شود)
 * - prune() برای پاک‌سازی دوره‌ای و جلوگیری از رشد بی‌پایانهٔ حافظه
 *
 * نکتهٔ شناخته‌شده: در getOrPut اگر دو thread هم‌زمان key یکسان بخواهند،
 * هر دو load می‌کنند (race بی‌ضرر برای مصرف ما؛ نتیجه یکسان است).
 */
class TtlCache<V>(private val ttlMillis: Long) {

    private class Entry<V>(val value: V, val storedAt: Long)

    private val map = ConcurrentHashMap<String, Entry<V>>()

    /** مقدار تازه برمی‌گرداند یا null اگر نبود/منقضی شده بود. */
    fun get(key: String): V? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.storedAt >= ttlMillis) {
            map.remove(key)
            return null
        }
        return e.value
    }

    fun put(key: String, value: V) {
        map[key] = Entry(value, System.currentTimeMillis())
    }

    /** اگر cache تازه بود همان را می‌دهد؛ وگرنه load، cache و برگردان. */
    fun getOrPut(key: String, loader: () -> V?): V? {
        get(key)?.let { return it }
        val value = loader() ?: return null
        put(key, value)
        return value
    }

    fun invalidate(key: String) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }

    /** حذف entryهای منقضی (برای صدا زدن دوره‌ای از Worker یا صفحه‌ها). */
    fun prune() {
        val now = System.currentTimeMillis()
        map.entries.removeIf { now - it.value.storedAt >= ttlMillis }
    }

    fun size(): Int = map.size
}

/**
 * کندل‌های Binance با cache شصت‌ثانیه‌ای.
 *
 * چرا ۶۰ ثانیه: کندلِ تایم‌فریم‌های ≥۱ دقیقه فقط با tick/close عوض می‌شود؛
 * اسکن‌های پشت‌سرهم (Worker هر چند دقیقه، UI هر بار باز شدن) نیازی به
 * call تکراری ندارند. این یعنی حذف ~۹۰٪ callهای تکراری klines.
 */
object KlineCache {
    private const val TTL_MS = 60_000L
    private val cache = TtlCache<List<JsonArray>>(TTL_MS)

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
}
