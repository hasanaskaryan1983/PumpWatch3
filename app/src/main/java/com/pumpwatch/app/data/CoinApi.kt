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
import java.util.concurrent.TimeUnit

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

// ---------- ترافیک‌شکن با تلاش مجدد هوشمند ----------

object ThrottledHttp {

    private const val MIN_INTERVAL_MS = 1200L
    private var lastRequestMs = 0L
    private val lock = Any()

    private val interceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            synchronized(lock) {
                val wait = lastRequestMs + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) Thread.sleep(wait)
                lastRequestMs = System.currentTimeMillis()
            }
            var retries = 0
            while (retries < 4) {
                val response = chain.proceed(chain.request())
                if (response.code != 429) return response
                response.close()
                retries++
                // انتظار نمایی: ۳، ۶، ۱۲، ۲۴ ثانیه
                Thread.sleep(3000L * (1L shl (retries - 1)))
                synchronized(lock) { lastRequestMs = System.currentTimeMillis() }
            }
            return chain.proceed(chain.request())
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

// ---------- کلاینت با کش زنده و آفلاین ----------

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

    // کش کوتاه‌تر برای داده‌های زنده
    private const val CACHE_TTL = 30_000L
    private var cache1000: List<CoinMarket> = emptyList()
    private var cache1000Time = 0L
    private var cache100: List<CoinMarket> = emptyList()
    private var cache100Time = 0L

    val api: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    suspend fun getTop1000Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        if (!forceRefresh && cache1000.isNotEmpty() &&
            System.currentTimeMillis() - cache1000Time < CACHE_TTL
        ) return cache1000

        return try {
            val results = mutableListOf<CoinMarket>()
            for (page in 1..4) {
                results.addAll(api.getMarkets(perPage = 250, page = page))
                if (page < 4) delay(1300)
            }
            cache1000 = results.sortedBy { it.market_cap_rank ?: 9999 }
            cache1000Time = System.currentTimeMillis()
            OfflineCache.save(app, "m1000", gson.toJson(cache1000))
            cache1000
        } catch (e: Exception) {
            val disk = loadList("m1000")
            if (disk != null) {
                cache1000 = disk
                disk
            } else throw e
        }
    }

    suspend fun getTop100Coins(forceRefresh: Boolean = false): List<CoinMarket> {
        if (!forceRefresh && cache100.isNotEmpty() &&
            System.currentTimeMillis() - cache100Time < CACHE_TTL
        ) return cache100

        return try {
            cache100 = api.getMarkets(perPage = 100, page = 1)
            cache100Time = System.currentTimeMillis()
            OfflineCache.save(app, "m100", gson.toJson(cache100))
            cache100
        } catch (e: Exception) {
            val disk = loadList("m100")
            if (disk != null) {
                cache100 = disk
                disk
            } else throw e
        }
    }

    suspend fun getCoinChart(id: String, days: Int = 90): MarketChart {
        val key = "chart_${id}_$days"
        return try {
            val chart = api.getMarketChart(id, days = days)
            OfflineCache.save(app, key, gson.toJson(chart))
            chart
        } catch (e: Exception) {
            val json = OfflineCache.load(app, key)
            if (json != null) {
                try {
                    gson.fromJson(json, MarketChart::class.java)
                } catch (_: Exception) {
                    throw e
                }
            } else throw e
        }
    }

    // ---------- پاک کردن کش (برای refresh دستی) ----------

    fun clearMemoryCache() {
        cache1000 = emptyList()
        cache1000Time = 0L
        cache100 = emptyList()
        cache100Time = 0L
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
