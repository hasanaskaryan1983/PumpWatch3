package com.pumpwatch.app.data

import com.google.gson.JsonArray
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class BinanceCandle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

// ================= Bybit =================
data class BybitKlineResponse(val retCode: Int, val result: BybitKlineResult?)
data class BybitKlineResult(val list: List<List<String>>?)

interface BybitApi {
    @GET("v5/market/kline")
    suspend fun kline(
        @Query("category") category: String,
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): BybitKlineResponse
}

// ================= OKX =================
data class OkxKlineResponse(val code: String, val data: List<List<String>>?)

interface OkxApi {
    @GET("api/v5/market/candles")
    suspend fun candles(
        @Query("instId") instId: String,
        @Query("bar") bar: String,
        @Query("limit") limit: Int
    ): OkxKlineResponse
}

// ================= Gate =================
interface GateApi {
    @GET("api/v4/spot/candlesticks")
    suspend fun candlesticks(
        @Query("currency_pair") pair: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<String>>
}

// ========== کش سراسری — thread-safe با سقف اندازه ==========
private object GlobalKlineCache {
    private val map = mutableMapOf<String, Pair<Long, List<BinanceCandle>>>()
    private val lock = Any()
    private const val MAX_SIZE = 500
    private const val TTL_MS = 5 * 60 * 1000L

    fun get(key: String): List<BinanceCandle>? {
        synchronized(lock) {
            val e = map[key] ?: return null
            if (System.currentTimeMillis() - e.first > TTL_MS) {
                map.remove(key)
                return null
            }
            return e.second
        }
    }

    fun put(key: String, v: List<BinanceCandle>) {
        synchronized(lock) {
            if (map.size >= MAX_SIZE) {
                val oldestKey = map.entries.minByOrNull { it.value.first }?.key
                if (oldestKey != null) map.remove(oldestKey)
            }
            map[key] = System.currentTimeMillis() to v
        }
    }
}

/**
 * قدم ۴ بازبینی دوم: coordinator مشترک درخواست‌های صرافی‌ها.
 * - حداقل فاصله بین درخواست‌ها (جلوگیری از burst coroutineهای موازی)
 * - retry با backoff نمایی روی 429/418 + احترام به Retry-After
 * - پرتاب RateLimitedException به‌جای شکست بی‌صدا
 */
object ExchangeHttp {

    private const val MIN_INTERVAL_MS = 250L
    private const val MAX_RETRIES = 4
    private const val BASE_BACKOFF_MS = 2000L
    private const val MAX_BACKOFF_MS = 30_000L

    private val lastRequestMs = AtomicLong(0L)
    private val lock = Any()

    private val throttleInterceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            // صف مشترک: هیچ دو درخواستی کمتر از MIN_INTERVAL_MS فاصله ندارند
            synchronized(lock) {
                val wait = lastRequestMs.get() + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                lastRequestMs.set(System.currentTimeMillis())
            }

            var retries = 0
            while (retries < MAX_RETRIES) {
                val response = chain.proceed(chain.request())
                if (response.code != 429 && response.code != 418) return response

                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                val host = chain.request().url.host
                response.close()
                retries++

                val backoffMs = if (retryAfter != null && retryAfter > 0) {
                    (retryAfter * 1000L).coerceIn(BASE_BACKOFF_MS, MAX_BACKOFF_MS)
                } else {
                    (BASE_BACKOFF_MS * (1L shl (retries - 1))).coerceAtMost(MAX_BACKOFF_MS)
                }
                Thread.sleep(backoffMs)
                synchronized(lock) { lastRequestMs.set(System.currentTimeMillis()) }
            }
            throw RateLimitedException(
                "Exchange rate limit exceeded after $MAX_RETRIES retries: ${chain.request().url.host}"
            )
        }
    }

    private val headerInterceptor = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "application/json")
                .build()
        )
    }

    fun client(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(throttleInterceptor)
        .addInterceptor(headerInterceptor)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
}

object MultiExchange {

    // همهٔ صرافی‌ها از همان coordinator مشترک استفاده می‌کنند
    private fun client(): OkHttpClient = ExchangeHttp.client()

    private fun <T> create(baseUrl: String, cls: Class<T>): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(cls)

    val bybit: BybitApi by lazy { create("https://api.bybit.com/", BybitApi::class.java) }
    val okx: OkxApi by lazy { create("https://www.okx.com/", OkxApi::class.java) }
    val gate: GateApi by lazy { create("https://api.gateio.ws/", GateApi::class.java) }

    suspend fun fetchKlines(symbolUpper: String, interval: String, limit: Int): List<BinanceCandle> {
        val key = "$symbolUpper|$interval|$limit"
        GlobalKlineCache.get(key)?.let { return it }
        val out = fetchKlinesNetwork(symbolUpper, interval, limit)
        if (out.isNotEmpty()) GlobalKlineCache.put(key, out)
        return out
    }

    private suspend fun fetchKlinesNetwork(symbolUpper: String, interval: String, limit: Int): List<BinanceCandle> {
        // 1) Bybit — ساختار: [ts, open, high, low, close, volume, ...]
        try {
            val r = bybit.kline("spot", "${symbolUpper}USDT", bybitInterval(interval), limit)
            val list = r.result?.list
            if (!list.isNullOrEmpty()) {
                val out = list.reversed().mapNotNull { a -> candle(a, 0, 1, 2, 3, 4, 5, true) }
                if (out.isNotEmpty()) return out
            }
        } catch (_: Exception) { }

        // 2) OKX — ساختار: [ts, open, high, low, close, vol, volCcy, volCcyQuote, confirm]
        try {
            val r = okx.candles("${symbolUpper}-USDT", okxBar(interval), limit)
            val list = r.data
            if (!list.isNullOrEmpty()) {
                val out = list.reversed().mapNotNull { a -> candle(a, 0, 1, 2, 3, 4, 5, true) }
                if (out.isNotEmpty()) return out
            }
        } catch (_: Exception) { }

        // 3) Gate — ساختار: [timestamp, volume, close, high, low, open, quote_volume]
        try {
            val list = gate.candlesticks("${symbolUpper}_USDT", gateInterval(interval), limit)
            if (list.isNotEmpty()) {
                val out = list.mapNotNull { a -> candle(a, 0, 5, 3, 4, 2, 1, false) }
                if (out.isNotEmpty()) return out
            }
        } catch (_: Exception) { }

        return emptyList()
    }

    // internal تا تست GateParserTest بتونه مستقیم صداش بزنه
    internal fun candle(
        a: List<String>,
        t: Int, o: Int, h: Int, l: Int, c: Int, v: Int,
        timeMs: Boolean
    ): BinanceCandle? {
        val close = a.getOrNull(c)?.toDoubleOrNull() ?: return null
        val time = a.getOrNull(t)?.toDoubleOrNull() ?: return null
        return BinanceCandle(
            time = if (timeMs) time.toLong() else (time * 1000).toLong(),
            open = a.getOrNull(o)?.toDoubleOrNull() ?: 0.0,
            high = a.getOrNull(h)?.toDoubleOrNull() ?: 0.0,
            low = a.getOrNull(l)?.toDoubleOrNull() ?: 0.0,
            close = close,
            volume = a.getOrNull(v)?.toDoubleOrNull() ?: 0.0
        )
    }

    private fun bybitInterval(i: String): String = when (i) {
        "1m" -> "1"
        "5m" -> "5"
        "15m" -> "15"
        "30m" -> "30"
        "1h" -> "60"
        "2h" -> "120"
        "4h" -> "240"
        "6h" -> "360"
        "12h" -> "720"
        "1d" -> "D"
        "1w" -> "W"
        else -> "60"
    }

    private fun okxBar(i: String): String = when (i) {
        "1m" -> "1m"
        "5m" -> "5m"
        "15m" -> "15m"
        "30m" -> "30m"
        "1h" -> "1H"
        "2h" -> "2H"
        "4h" -> "4H"
        "6h" -> "6H"
        "12h" -> "12H"
        "1d" -> "1D"
        "1w" -> "1W"
        else -> "1H"
    }

    private fun gateInterval(i: String): String = when (i) {
        "1w" -> "7d"
        else -> i
    }
}

object BinanceClient {

    val api: KlineCompat = KlineCompat

    object KlineCompat {
        suspend fun klines(symbol: String, interval: String, limit: Int): List<JsonArray> {
            val sym = symbol.uppercase().removeSuffix("USDT")
            return MultiExchange.fetchKlines(sym, interval, limit).map { c ->
                JsonArray().apply {
                    add(c.time)
                    add(c.open)
                    add(c.high)
                    add(c.low)
                    add(c.close)
                    add(c.volume)
                }
            }
        }
    }

    suspend fun candles(symbol: String, tf: String): List<BinanceCandle> {
        val sym = symbol.uppercase().removeSuffix("USDT")
        return MultiExchange.fetchKlines(sym, tf, 200)
    }
}
