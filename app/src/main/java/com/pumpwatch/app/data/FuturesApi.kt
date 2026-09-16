package com.pumpwatch.app.data

import com.google.gson.JsonArray
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * قدم ۴ بازبینی دوم: کلاینت فیوچرز بایننس هم از همان coordinator مشترک
 * (ExchangeHttp) استفاده می‌کند تا:
 * - حداقل فاصله بین درخواست‌ها رعایت شود (جلوگیری از burst)
 * - روی 429/418 با backoff نمایی و Retry-After retry شود
 * - پس از اتمام retryها به‌جای شکست بی‌صدا، RateLimitedException پرتاب شود
 *
 * 🚀 Sprint 12 (F1-ext): گسترش برای داشبورد/اسکنر فیوچرز:
 * - premiumIndexAll / ticker24h / lsRatio / klines اضافه شد
 * - متدها و مدل‌های موجود (premiumIndex/oiHist/PremiumIndex/OiHist)
 *   دست‌نخورده ماندند تا مصرف‌کننده‌های فعلی نشکنند
 * - مدل‌های جدید با نام‌های مجزا تا تصادم redeclaration نداشته باشیم
 */
private val futClient: OkHttpClient by lazy {
    ExchangeHttp.client()
}

data class PremiumIndex(
    @SerializedName("lastFundingRate") val lastFundingRate: String?
)

data class OiHist(
    @SerializedName("sumOpenInterestValue") val sumOpenInterestValue: String?,
    val timestamp: Long?
)

// 🚀 Sprint 12 (F1-ext): ردیف کامل premiumIndex (برای داشبورد فاندینگ)
data class PremiumIndexFull(
    val symbol: String,
    @SerializedName("markPrice") val markPrice: String?,
    @SerializedName("indexPrice") val indexPrice: String?,
    @SerializedName("lastFundingRate") val lastFundingRate: String?,
    @SerializedName("nextFundingTime") val nextFundingTime: Long?,
    val time: Long?
)

// 🚀 Sprint 12 (F1-ext): تیکر ۲۴ ساعته (حجم/تغییر/سقف/کف)
data class Ticker24h(
    val symbol: String,
    @SerializedName("lastPrice") val lastPrice: String?,
    @SerializedName("priceChangePercent") val priceChangePercent: String?,
    @SerializedName("quoteVolume") val quoteVolume: String?,
    @SerializedName("highPrice") val highPrice: String?,
    @SerializedName("lowPrice") val lowPrice: String?
)

// 🚀 Sprint 12 (F1-ext): نسبت حساب‌های لانگ/شورت (احساسات بازار)
data class LsRatio(
    val symbol: String?,
    @SerializedName("longAccount") val longAccount: String?,
    @SerializedName("shortAccount") val shortAccount: String?,
    @SerializedName("longShortRatio") val longShortRatio: String?,
    val timestamp: Long?
)

// 🚀 Sprint 12 (F1-ext): کندل تایپ‌سیف برای موتورهای F3/F4
data class FuturesCandle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val quoteVolume: Double
)

interface BinanceFuturesApi {
    @GET("fapi/v1/premiumIndex")
    suspend fun premiumIndex(@Query("symbol") symbol: String): PremiumIndex

    @GET("futures/data/openInterestHist")
    suspend fun oiHist(
        @Query("symbol") symbol: String,
        @Query("period") period: String,
        @Query("limit") limit: Int
    ): List<OiHist>

    // ---------- 🚀 Sprint 12 (F1-ext) ----------

    /** همهٔ نمادها — یک درخواست برای داشبورد فاندینگ */
    @GET("fapi/v1/premiumIndex")
    suspend fun premiumIndexAll(): List<PremiumIndexFull>

    /** همهٔ نمادها — یک درخواست برای جدول Top Perps */
    @GET("fapi/v1/ticker/24hr")
    suspend fun ticker24h(): List<Ticker24h>

    /** period: 5m,15m,30m,1h,2h,4h,6h,12h,1d */
    @GET("futures/data/topLongShortAccountRatio")
    suspend fun lsRatio(
        @Query("symbol") symbol: String,
        @Query("period") period: String = "1h",
        @Query("limit") limit: Int = 1
    ): List<LsRatio>

    /** interval: 1m,5m,15m,1h,4h,1d,... — خروجی خام JsonArray (الگوی موجود در BinanceClient) */
    @GET("fapi/v1/klines")
    suspend fun klines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 500
    ): List<JsonArray>
}

object BinanceFutures {
    val api: BinanceFuturesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://fapi.binance.com/")
            .client(futClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceFuturesApi::class.java)
    }

    // ---------- 🚀 Sprint 12 (F1-ext): helper ها ----------

    /**
     * پارس یک ردیف klines به FuturesCandle.
     * ساختار Binance: [openTime, open, high, low, close, volume, closeTime, quoteVolume, ...]
     */
    fun parseCandle(k: JsonArray): FuturesCandle? = try {
        FuturesCandle(
            openTime = k[0].asLong,
            open = k[1].asString.toDouble(),
            high = k[2].asString.toDouble(),
            low = k[3].asString.toDouble(),
            close = k[4].asString.toDouble(),
            quoteVolume = k[7].asString.toDoubleOrNull() ?: 0.0
        )
    } catch (_: Exception) {
        null
    }

    /**
     * Top perpetual ها بر اساس حجم quote ۲۴ ساعته (فقط جفت‌های USDT)
     * مصرف‌کننده: داشبورد F2
     */
    suspend fun topPerps(limit: Int = 100): List<Ticker24h> =
        api.ticker24h()
            .filter { it.symbol.endsWith("USDT") }
            .sortedByDescending { it.quoteVolume?.toDoubleOrNull() ?: 0.0 }
            .take(limit)
}
