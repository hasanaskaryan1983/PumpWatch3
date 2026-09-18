package com.pumpwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
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
    // 🚀 Sprint 12 (C4a): درصد رشد ۱ ساله از endpoint markets
    @SerializedName("price_change_percentage_1y_in_currency") val change1y: Double?,
    // 🚀 Sprint 12 (C4a): All-Time High/Low (تمام تاریخ)
    val ath: Double?,
    val atl: Double?,
    val ath_change_percentage: Double?,
    val atl_change_percentage: Double?,
    @SerializedName("high_24h") val high24h: Double?,
    @SerializedName("low_24h") val low24h: Double?
)

// دادهٔ خام /coins/list?include_platform=true
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
        // 🚀 Sprint 12 (C4a): +1y برای محاسبهٔ درصد رشد یک‌ساله
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

object ThrottledHttp {

    private const val MIN_INTERVAL_MS = 1500L
    private const val MAX_RETRIES = 5
    private const val BASE_BACKOFF_MS = 3000L
    private const val MAX_BACKOFF_MS = 60_000L

    private val lastRequestMs = AtomicLong(0L)
    private val lock = Any()

    private val interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            synchronized(lock) {
                val wait = lastRequestMs.get() + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                lastRequestMs.set(System.currentTimeMillis())
            }

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
                synchronized(lock) { lastRequestMs.set(System.currentTimeMillis()) }
            }
            throw RateLimitedException("CoinGecko rate limit exceeded after $MAX_RETRIES retries")
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
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
    private val cache100Ref = AtomicReference<List<CoinMarket>>(emptyList())
    private val cache100TimeRef = AtomicLong(0L)
    private val platformsRef = AtomicReference<Map<String, Map<String, String>>?>(null)
    private val platformsTimeRef = AtomicLong(0L)

    // 🚀 Sprint 14 (مرحله ۲ / Commit 5): متادیتای provenance — سن و مبدأ واقعی دادهٔ بازار
    private val marketMetaRef = AtomicReference(MarketMeta(0L, ServedFrom.UNKNOWN, 0))

    /** UI با این تابع بفهمد عددی که نشان می‌دهد زنده است یا کش/دیسک */
    fun marketMeta(): MarketMeta = marketMetaRef.get()

    private fun setMeta(observedAt: Long, from: ServedFrom, count: Int) {
        marketMetaRef.set(MarketMeta(observedAt, from, count))
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
        cache1000Ref.get().takeIf { it.isNotEmpty() }?.let { return it }
        loadList("m250")?.let { return it }
        val p1 = api.getMarkets(perPage = 250, page = 1)
        OfflineCache.save(app, "m250", gson.toJson(p1))
        return p1
    }

    suspend fun getTop1000Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        val cached = cache1000Ref.get()
        val cachedTime = cache1000TimeRef.get()

        // 🚀 Sprint 14 (Commit 5): برچسب مبدأ = کش حافظه
        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) {
            setMeta(cachedTime, ServedFrom.MEM_CACHE, cached.size)
            return cached
        }

        if (!forceRefresh && cached.isEmpty()) {
            val disk = loadList("m1000")
            if (disk != null &&
                System.currentTimeMillis() - OfflineCache.time(app, "m1000") < DISK_FRESH_MS
            ) {
                cache1000Ref.set(disk)
                cache1000TimeRef.set(System.currentTimeMillis())
                // 🚀 Sprint 14 (Commit 5): برچسب مبدأ = دیسک (هرگز «زنده» نیست)
                setMeta(OfflineCache.time(app, "m1000"), ServedFrom.DISK_CACHE, disk.size)
                return disk
            }
        }

        return try {
            val results = mutableListOf<CoinMarket>()
            for (page in 1..4) {
                results.addAll(api.getMarkets(perPage = 250, page = page))
                if (page < 4) delay(2000)
            }
            val sorted = results.sortedBy { it.market_cap_rank ?: 9999 }
            val nowMs = System.currentTimeMillis()
            cache1000Ref.set(sorted)
            cache1000TimeRef.set(nowMs)
            // 🚀 Sprint 14 (Commit 5): برچسب مبدأ = شبکه
            setMeta(nowMs, ServedFrom.NETWORK, sorted.size)
            OfflineCache.save(app, "m1000", gson.toJson(sorted))
            sorted
        } catch (e: Exception) {
            // 🚀 Sprint 14 (Commit 5): کلید دیسک واقعی ثبت می‌شود (m1000 یا m250)
            var diskKey = "m1000"
            var disk = loadList(diskKey)
            if (disk == null) {
                diskKey = "m250"
                disk = loadList(diskKey)
            }
            if (disk != null) {
                cache1000Ref.set(disk)
                cache1000TimeRef.set(System.currentTimeMillis())
                setMeta(OfflineCache.time(app, diskKey), ServedFrom.DISK_CACHE, disk.size)
                disk
            } else throw e
        }
    }

    suspend fun getTop100Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        val cached = cache100Ref.get()
        val cachedTime = cache100TimeRef.get()

        // 🚀 Sprint 14 (Commit 5): برچسب مبدأ = کش حافظه
        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) {
            setMeta(cachedTime, ServedFrom.MEM_CACHE, cached.size)
            return cached
        }

        if (!forceRefresh && cached.isEmpty()) {
            val disk = loadList("m100")
            if (disk != null &&
                System.currentTimeMillis() - OfflineCache.time(app, "m100") < DISK_FRESH_MS
            ) {
                cache100Ref.set(disk)
                cache100TimeRef.set(System.currentTimeMillis())
                // 🚀 Sprint 14 (Commit 5): برچسب مبدأ = دیسک
                setMeta(OfflineCache.time(app, "m100"), ServedFrom.DISK_CACHE, disk.size)
                return disk
            }
        }

        return try {
            val fresh = api.getMarkets(perPage = 100, page = 1)
            val nowMs = System.currentTimeMillis()
            cache100Ref.set(fresh)
            cache100TimeRef.set(nowMs)
            // 🚀 Sprint 14 (Commit 5): برچسب مبدأ = شبکه
            setMeta(nowMs, ServedFrom.NETWORK, fresh.size)
            OfflineCache.save(app, "m100", gson.toJson(fresh))
            fresh
        } catch (e: Exception) {
            val disk = loadList("m100")
            if (disk != null) {
                cache100Ref.set(disk)
                cache100TimeRef.set(System.currentTimeMillis())
                setMeta(OfflineCache.time(app, "m100"), ServedFrom.DISK_CACHE, disk.size)
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
        cache100Ref.set(emptyList())
        cache100TimeRef.set(0L)
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
