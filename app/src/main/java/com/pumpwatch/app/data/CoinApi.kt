package com.pumpwatch.app.data

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.pumpwatch.app.store.OfflineCache
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class CoinMarket(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    val current_price: Double,
    val market_cap: Double,
    val total_volume: Double,
    val price_change_percentage_24h: Double?,
    val market_cap_rank: Int?,
    @SerializedName("price_change_percentage_1h_in_currency") val change1h: Double?,
    @SerializedName("price_change_percentage_7d_in_currency") val change7d: Double?,
    @SerializedName("price_change_percentage_1y_in_currency") val change1y: Double?,
    val ath: Double?,
    val atl: Double?,
    val ath_change_percentage: Double?,
    val atl_change_percentage: Double?,
    @SerializedName("high_24h") val high24h: Double?,
    @SerializedName("low_24h") val low24h: Double?
)

data class CoinListItem(
    val id: String,
    val symbol: String?,
    val name: String?,
    val platforms: Map<String, String?>?
)

interface CoinGeckoApi {

    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1,
        @Query("price_change_percentage") pcp: String = "1h,24h,7d,1y"
    ): List<CoinMarket>

    @GET("coins/{id}/ohlc")
    suspend fun getOhlc(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String
    ): List<List<Double>>

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int
    ): MarketChart

    @GET("coins/list")
    suspend fun getCoinsList(
        @Query("include_platform") includePlatform: Boolean = true
    ): List<CoinListItem>
}

/**
 * 🚀 Commit 88 (A1): محدودکنندهٔ نرخ به‌ازای هاست.
 */
class HostLimiter(
    private val minIntervalMs: Map<String, Long>,
    private val defaultIntervalMs: Long = 1000L
) {
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val lastRequestMs = ConcurrentHashMap<String, Long>()

    suspend fun acquire(host: String) {
        val mutex = mutexes.getOrPut(host) { Mutex() }
        mutex.withLock {
            val interval = minIntervalMs[host] ?: defaultIntervalMs
            val lastMs = lastRequestMs[host] ?: 0L
            val waitMs = lastMs + interval - System.currentTimeMillis()
            if (waitMs > 0) {
                delay(waitMs)
            }
            lastRequestMs[host] = System.currentTimeMillis()
        }
    }

    fun clear() {
        mutexes.clear()
        lastRequestMs.clear()
    }
}

/**
 * 🚀 Commit 88 (A1): HostLimiter سراسری با تنظیمات بهینه برای هر هاست.
 */
val GlobalHostLimiter = HostLimiter(
    minIntervalMs = mapOf(
        "api.binance.com" to 100L,
        "api.coingecko.com" to 1500L,
        "api.geckoterminal.com" to 2000L,
        "api.gopluslabs.io" to 1000L,
        "api.bybit.com" to 200L,
        "www.okx.com" to 200L,
        "api.gateio.ws" to 200L,
        "api.dexscreener.com" to 500L,
        "tonapi.io" to 600L,
        "fullnode.mainnet.sui.io" to 500L,
        "rpc.mainnet.arc.io" to 500L
    ),
    defaultIntervalMs = 1000L
)

/**
 * 🚀 Commit 88 (A1): ThrottledHttp با HostLimiter به‌ازای هاست.
 */
object ThrottledHttp {

    private const val MAX_RETRIES = 5
    private const val BASE_BACKOFF_MS = 3000L
    private const val MAX_BACKOFF_MS = 60_000L

    private val interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var retries = 0
            while (retries < MAX_RETRIES) {
                val response = chain.proceed(chain.request())
                if (response.code != 429) return response

                response.close()
                retries++

                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                val backoffMs = if (retryAfter != null && retryAfter > 0) {
                    (retryAfter * 1000L).coerceIn(BASE_BACKOFF_MS, MAX_BACKOFF_MS)
                } else {
                    (BASE_BACKOFF_MS * (1L shl (retries - 1))).coerceAtMost(MAX_BACKOFF_MS)
                }

                Thread.sleep(backoffMs)
            }
            throw RateLimitedException("Rate limit exceeded after $MAX_RETRIES retries")
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Metrics.interceptor)
            .addInterceptor(interceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class RateLimitedException(message: String) : IOException(message)

object ApiClient {

    private val app: Context? by lazy {
        try {
            val cl = Class.forName("android.app.ActivityThread")
            cl.getMethod("currentApplication").invoke(null) as? Context
        } catch (_: Exception) {
            null
        }
    }

    private val gson = Gson()

    private const val MEM_CACHE_TTL = 120_000L
    private const val DISK_FRESH_MS = 1_800_000L
    private const val CHART_FRESH_MS = 300_000L
    private const val PLATFORMS_FRESH_MS = 24 * 60 * 60 * 1000L
    private const val PLATFORMS_CACHE_KEY = "platforms_v1"

    private val cache1000Ref = AtomicReference<List<CoinMarket>>(emptyList())
    private val cache1000TimeRef = AtomicLong(0L)
    private val cache1000ElapsedRef = AtomicLong(0L) // 🚀 Commit 102: Monotonic time tracking

    private val cache100Ref = AtomicReference<List<CoinMarket>>(emptyList())
    private val cache100TimeRef = AtomicLong(0L)
    private val cache100ElapsedRef = AtomicLong(0L) // 🚀 Commit 102: Monotonic time tracking

    private val platformsRef = AtomicReference<Map<String, Map<String, String>>?>(null)
    private val platformsTimeRef = AtomicLong(0L)

    private val marketMetaRef = AtomicReference(MarketMeta(0L, ServedFrom.UNKNOWN, 0))

    fun marketMeta(): MarketMeta = marketMetaRef.get()

    private fun setMeta(observedAtMs: Long, from: ServedFrom, count: Int, elapsedMs: Long? = null) {
        marketMetaRef.set(MarketMeta(observedAtMs, from, count, elapsedMs))
    }

    val api: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    suspend fun getQuickCoins(): List<CoinMarket> {
        // 1. Memory cache with TTL check
        val memCache = cache1000Ref.get()
        val memTime = cache1000TimeRef.get()
        if (memCache.isNotEmpty() && System.currentTimeMillis() - memTime < MEM_CACHE_TTL) {
            val elapsed = cache1000ElapsedRef.get().takeIf { it > 0 }
            setMeta(memTime, ServedFrom.MEM_CACHE, memCache.size, elapsed)
            return memCache
        }

        // 2. Disk cache with TTL check
        val diskTime = OfflineCache.time(app, "m250")
        if (diskTime > 0 && System.currentTimeMillis() - diskTime < DISK_FRESH_MS) {
            loadList("m250")?.let { disk ->
                cache1000Ref.set(disk)
                cache1000TimeRef.set(diskTime)
                cache1000ElapsedRef.set(0L) // Elapsed time is lost across reboots
                setMeta(diskTime, ServedFrom.DISK_CACHE, disk.size, null)
                return disk
            }
        }

        // 3. Network fetch
        GlobalHostLimiter.acquire("api.coingecko.com")
        val p1 = api.getMarkets(perPage = 250, page = 1)
        val nowMs = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        cache1000Ref.set(p1)
        cache1000TimeRef.set(nowMs)
        cache1000ElapsedRef.set(nowElapsed)
        setMeta(nowMs, ServedFrom.NETWORK, p1.size, nowElapsed)

        OfflineCache.save(app, "m250", gson.toJson(p1))
        return p1
    }

    suspend fun getTop1000Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        val cached = cache1000Ref.get()
        val cachedTime = cache1000TimeRef.get()
        val cachedElapsed = cache1000ElapsedRef.get().takeIf { it > 0 }

        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) {
            setMeta(cachedTime, ServedFrom.MEM_CACHE, cached.size, cachedElapsed)
            return cached
        }

        if (!forceRefresh && cached.isEmpty()) {
            val disk = loadList("m1000")
            val diskTime = OfflineCache.time(app, "m1000")
            if (disk != null &&
                System.currentTimeMillis() - diskTime < DISK_FRESH_MS
            ) {
                cache1000Ref.set(disk)
                cache1000TimeRef.set(diskTime)
                cache1000ElapsedRef.set(0L)
                setMeta(diskTime, ServedFrom.DISK_CACHE, disk.size, null)
                return disk
            }
        }

        return try {
            val results = mutableListOf<CoinMarket>()
            for (page in 1..4) {
                GlobalHostLimiter.acquire("api.coingecko.com")
                results.addAll(api.getMarkets(perPage = 250, page = page))
                if (page < 4) delay(2000)
            }
            val sorted = results.sortedBy { it.market_cap_rank ?: 9999 }
            val nowMs = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()

            cache1000Ref.set(sorted)
            cache1000TimeRef.set(nowMs)
            cache1000ElapsedRef.set(nowElapsed)
            setMeta(nowMs, ServedFrom.NETWORK, sorted.size, nowElapsed)

            OfflineCache.save(app, "m1000", gson.toJson(sorted))
            sorted
        } catch (e: Exception) {
            var diskKey = "m1000"
            var disk = loadList(diskKey)
            if (disk == null) {
                diskKey = "m250"
                disk = loadList(diskKey)
            }
            if (disk != null) {
                val diskTime = OfflineCache.time(app, diskKey)
                cache1000Ref.set(disk)
                cache1000TimeRef.set(diskTime)
                cache1000ElapsedRef.set(0L)
                setMeta(diskTime, ServedFrom.DISK_CACHE, disk.size, null)
                disk
            } else throw e
        }
    }

    suspend fun getTop100Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        val cached = cache100Ref.get()
        val cachedTime = cache100TimeRef.get()
        val cachedElapsed = cache100ElapsedRef.get().takeIf { it > 0 }

        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) {
            setMeta(cachedTime, ServedFrom.MEM_CACHE, cached.size, cachedElapsed)
            return cached
        }

        if (!forceRefresh && cached.isEmpty()) {
            val disk = loadList("m100")
            val diskTime = OfflineCache.time(app, "m100")
            if (disk != null &&
                System.currentTimeMillis() - diskTime < DISK_FRESH_MS
            ) {
                cache100Ref.set(disk)
                cache100TimeRef.set(diskTime)
                cache100ElapsedRef.set(0L)
                setMeta(diskTime, ServedFrom.DISK_CACHE, disk.size, null)
                return disk
            }
        }

        return try {
            GlobalHostLimiter.acquire("api.coingecko.com")
            val fresh = api.getMarkets(perPage = 100, page = 1)
            val nowMs = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()

            cache100Ref.set(fresh)
            cache100TimeRef.set(nowMs)
            cache100ElapsedRef.set(nowElapsed)
            setMeta(nowMs, ServedFrom.NETWORK, fresh.size, nowElapsed)

            OfflineCache.save(app, "m100", gson.toJson(fresh))
            fresh
        } catch (e: Exception) {
            val disk = loadList("m100")
            val diskTime = OfflineCache.time(app, "m100")
            if (disk != null) {
                cache100Ref.set(disk)
                cache100TimeRef.set(diskTime)
                cache100ElapsedRef.set(0L)
                setMeta(diskTime, ServedFrom.DISK_CACHE, disk.size, null)
                disk
            } else throw e
        }
    }

    suspend fun getCoinChart(id: String, days: Int = 90): MarketChart {
        val key = "chart_${id}_$days"
        val cachedJson = OfflineCache.load(app, key)
        if (cachedJson != null &&
            System.currentTimeMillis() - OfflineCache.time(app, key) < CHART_FRESH_MS
        ) {
            try {
                return gson.fromJson(cachedJson, MarketChart::class.java)
            } catch (_: Exception) { }
        }
        return try {
            GlobalHostLimiter.acquire("api.coingecko.com")
            val chart = api.getMarketChart(id, days = days)
            OfflineCache.save(app, key, gson.toJson(chart))
            chart
        } catch (e: Exception) {
            if (cachedJson != null) {
                try {
                    gson.fromJson(cachedJson, MarketChart::class.java)
                } catch (_: Exception) {
                    throw e
                }
            } else throw e
        }
    }

    suspend fun getPlatformMap(forceRefresh: Boolean = false): Map<String, Map<String, String>> {
        val cached = platformsRef.get()
        val cachedTime = platformsTimeRef.get()
        if (!forceRefresh && cached != null &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) return cached

        if (!forceRefresh && cached == null) {
            val diskJson = OfflineCache.load(app, PLATFORMS_CACHE_KEY)
            val diskTime = OfflineCache.time(app, PLATFORMS_CACHE_KEY)
            if (diskJson != null && System.currentTimeMillis() - diskTime < PLATFORMS_FRESH_MS) {
                try {
                    val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                    val disk: Map<String, Map<String, String>> = gson.fromJson(diskJson, type)
                    platformsRef.set(disk)
                    platformsTimeRef.set(System.currentTimeMillis())
                    return disk
                } catch (_: Exception) { }
            }
        }

        return try {
            GlobalHostLimiter.acquire("api.coingecko.com")
            val list = api.getCoinsList(includePlatform = true)
            val map = HashMap<String, Map<String, String>>()
            for (item in list) {
                val platforms = item.platforms ?: continue
                if (platforms.isEmpty()) continue
                val filtered = HashMap<String, String>()
                for ((chain, addr) in platforms) {
                    val a = addr?.trim().orEmpty()
                    if (a.isNotEmpty()) filtered[chain] = a
                }
                if (filtered.isNotEmpty()) map[item.id] = filtered
            }
            platformsRef.set(map)
            platformsTimeRef.set(System.currentTimeMillis())
            OfflineCache.save(app, PLATFORMS_CACHE_KEY, gson.toJson(map))
            map
        } catch (e: Exception) {
            val diskJson = OfflineCache.load(app, PLATFORMS_CACHE_KEY)
            if (diskJson != null) {
                try {
                    val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                    val disk: Map<String, Map<String, String>> = gson.fromJson(diskJson, type)
                    platformsRef.set(disk)
                    platformsTimeRef.set(System.currentTimeMillis())
                    return disk
                } catch (_: Exception) { }
            }
            throw e
        }
    }

    fun clearMemoryCache() {
        cache1000Ref.set(emptyList())
        cache1000TimeRef.set(0L)
        cache1000ElapsedRef.set(0L)

        cache100Ref.set(emptyList())
        cache100TimeRef.set(0L)
        cache100ElapsedRef.set(0L)

        platformsRef.set(null)
        platformsTimeRef.set(0L)
    }

    private fun loadList(key: String): List<CoinMarket>? {
        val json = OfflineCache.load(app, key) ?: return null
        return try {
            val type = object : TypeToken<List<CoinMarket>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            null
        }
    }
}

private val NATIVE_COIN_IDS = setOf(
    "bitcoin", "ethereum", "solana", "the-open-network", "sui",
    "cardano", "ripple", "dogecoin", "binancecoin", "polkadot",
    "avalanche-2", "cosmos", "near", "tron", "litecoin",
    "stellar", "internet-computer", "monero", "algorand", "filecoin",
    "hedera-hashgraph", "vechain", "eos", "tezos", "iota",
    "the-graph", "fantom", "aave", "decentraland", "the-sandbox"
)

fun platformContractOf(
    map: Map<String, Map<String, String>>,
    coinId: String,
    chainHint: String? = null
): String? {
    if (coinId in NATIVE_COIN_IDS) return null
    val platforms = map[coinId] ?: return null
    if (chainHint != null) {
        val direct = platforms[chainHint]
        if (!direct.isNullOrBlank()) return direct
        val aliases = when (chainHint) {
            "ethereum" -> listOf("ethereum", "eth")
            "bsc" -> listOf("binance-smart-chain", "bsc")
            "base" -> listOf("base")
            "arbitrum" -> listOf("arbitrum-one", "arbitrum")
            "optimism" -> listOf("optimistic-ethereum", "optimism")
            "polygon" -> listOf("polygon-pos", "polygon")
            "avalanche" -> listOf("avalanche", "avax")
            "solana" -> listOf("solana")
            "ton" -> listOf("the-open-network", "ton")
            "sui" -> listOf("sui")
            else -> listOf(chainHint)
        }
        for (a in aliases) {
            val v = platforms[a]
            if (!v.isNullOrBlank()) return v
        }
        return null
    }
    val preferred = listOf("ethereum", "binance-smart-chain", "base", "arbitrum-one", "polygon-pos")
    for (p in preferred) {
        val v = platforms[p]
        if (!v.isNullOrBlank()) return v
    }
    return platforms.values.firstOrNull { !it.isNullOrBlank() }
}
