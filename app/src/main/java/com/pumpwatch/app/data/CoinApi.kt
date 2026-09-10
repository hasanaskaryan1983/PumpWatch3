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
    @SerializedName("high_24h") val high24h: Double?,
    @SerializedName("low_24h") val low24h: Double?
)

interface CoinGeckoApi {

    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1,
        @Query("price_change_percentage") pcp: String = "1h,24h,7d"
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
}

/**
 * Rate-limit مشترک برای همه درخواست‌های CoinGecko.
 * 
 * اصلاحات فاز ۲:
 * - MIN_INTERVAL_MS افزایش به 1500ms (CoinGecko free ~10-30 req/min)
 * - خواندن و احترام به Retry-After header
 * - پرتاب RateLimitedException پس از max retries (نه proceed بی‌صدا)
 * - AtomicLong برای thread-safety
 */
object ThrottledHttp {

    // CoinGecko free tier: ~10-30 requests/minute → ~2-6 seconds بین درخواست‌ها
    // 1500ms یک تعادل منطقی است که هم throughput خوب دارد هم rate-limit نمی‌شود
    private const val MIN_INTERVAL_MS = 1500L
    private const val MAX_RETRIES = 5
    private const val BASE_BACKOFF_MS = 3000L
    private const val MAX_BACKOFF_MS = 60_000L // سقف 1 دقیقه

    private val lastRequestMs = AtomicLong(0L)
    private val lock = Any()

    private val interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            // ۱) Throttle: فاصله حداقل بین درخواست‌ها
            synchronized(lock) {
                val wait = lastRequestMs.get() + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                lastRequestMs.set(System.currentTimeMillis())
            }

            // ) Retry با backoff نمایی و احترام به Retry-After
            var retries = 0
            while (retries < MAX_RETRIES) {
                val response = chain.proceed(chain.request())
                if (response.code != 429) return response

                // Rate-limit شد — close کن و backoff
                response.close()
                retries++

                // خواندن Retry-After header (ثانیه)
                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                val backoffMs = if (retryAfter != null && retryAfter > 0) {
                    (retryAfter * 1000L).coerceIn(BASE_BACKOFF_MS, MAX_BACKOFF_MS)
                } else {
                    // Exponential backoff: 3s, 6s, 12s, 24s, 48s
                    (BASE_BACKOFF_MS * (1L shl (retries - 1))).coerceAtMost(MAX_BACKOFF_MS)
                }

                Thread.sleep(backoffMs)
                synchronized(lock) { lastRequestMs.set(System.currentTimeMillis()) }
            }

            // پس از MAX_RETRIES، exception خاص پرتاب کن تا caller بداند rate-limit شده
            throw RateLimitedException("CoinGecko rate limit exceeded after $MAX_RETRIES retries")
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Exception خاص برای rate-limit (HTTP 429).
 * Caller می‌تواند این را از سایر خطاهای شبکه تشخیص دهد.
 */
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

    // Thread-safe cache با AtomicReference/AtomicLong
    private val cache1000Ref = AtomicReference<List<CoinMarket>>(emptyList())
    private val cache1000TimeRef = AtomicLong(0L)
    private val cache100Ref = AtomicReference<List<CoinMarket>>(emptyList())
    private val cache100TimeRef = AtomicLong(0L)

    val api: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    // ---------- نمایش فوری: کش یا فقط ۱ صفحه ----------

    suspend fun getQuickCoins(): List<CoinMarket> {
        cache1000Ref.get().takeIf { it.isNotEmpty() }?.let { return it }
        loadList("m250")?.let { return it }
        val p1 = api.getMarkets(perPage = 250, page = 1)
        OfflineCache.save(app, "m250", gson.toJson(p1))
        return p1
    }

    // ---------- ۱۰۰۰ ارز کامل ----------

    suspend fun getTop1000Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        val cached = cache1000Ref.get()
        val cachedTime = cache1000TimeRef.get()

        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) return cached

        if (!forceRefresh && cached.isEmpty()) {
            val disk = loadList("m1000")
            if (disk != null &&
                System.currentTimeMillis() - OfflineCache.time(app, "m1000") < DISK_FRESH_MS
            ) {
                cache1000Ref.set(disk)
                cache1000TimeRef.set(System.currentTimeMillis())
                return disk
            }
        }

        return try {
            val results = mutableListOf<CoinMarket>()
            for (page in 1..4) {
                results.addAll(api.getMarkets(perPage = 250, page = page))
                // افزایش delay بین صفحات: 2000ms برای CoinGecko free tier
                if (page < 4) delay(2000)
            }
            val sorted = results.sortedBy { it.market_cap_rank ?: 9999 }
            cache1000Ref.set(sorted)
            cache1000TimeRef.set(System.currentTimeMillis())
            OfflineCache.save(app, "m1000", gson.toJson(sorted))
            sorted
        } catch (e: Exception) {
            val disk = loadList("m1000") ?: loadList("m250")
            if (disk != null) {
                cache1000Ref.set(disk)
                cache1000TimeRef.set(System.currentTimeMillis())
                disk
            } else throw e
        }
    }

    // ---------- ۱۰۰ ارز ----------

    suspend fun getTop100Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        val cached = cache100Ref.get()
        val cachedTime = cache100TimeRef.get()

        if (!forceRefresh && cached.isNotEmpty() &&
            System.currentTimeMillis() - cachedTime < MEM_CACHE_TTL
        ) return cached

        if (!forceRefresh && cached.isEmpty()) {
            val disk = loadList("m100")
            if (disk != null &&
                System.currentTimeMillis() - OfflineCache.time(app, "m100") < DISK_FRESH_MS
            ) {
                cache100Ref.set(disk)
                cache100TimeRef.set(System.currentTimeMillis())
                return disk
            }
        }

        return try {
            val fresh = api.getMarkets(perPage = 100, page = 1)
            cache100Ref.set(fresh)
            cache100TimeRef.set(System.currentTimeMillis())
            OfflineCache.save(app, "m100", gson.toJson(fresh))
            fresh
        } catch (e: Exception) {
            val disk = loadList("m100")
            if (disk != null) {
                cache100Ref.set(disk)
                cache100TimeRef.set(System.currentTimeMillis())
                disk
            } else throw e
        }
    }

    // ---------- نمودار با کش ۵ دقیقه ----------

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

    fun clearMemoryCache() {
        cache1000Ref.set(emptyList())
        cache1000TimeRef.set(0L)
        cache100Ref.set(emptyList())
        cache100TimeRef.set(0L)
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
