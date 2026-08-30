package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

private val radarClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

// ---------- GeckoTerminal (استخرهای داغ میم‌کوین) ----------

data class GeckoPriceChange(val h1: Double?, val h6: Double?, val h24: Double?)
data class GeckoBuysSells(val buys: Double?, val sells: Double?)
data class GeckoTxns(val h1: GeckoBuysSells?, val h6: GeckoBuysSells?, val h24: GeckoBuysSells?)
data class GeckoVolume(val h1: Double?, val h6: Double?, val h24: Double?)

data class GeckoPoolAttributes(
    @SerializedName("base_token_price_usd") val priceUsd: String?,
    val name: String?,
    @SerializedName("pool_created_at") val createdAt: String?,
    @SerializedName("fdv_usd") val fdvUsd: Double?,
    @SerializedName("price_change_percentage") val priceChange: GeckoPriceChange?,
    val transactions: GeckoTxns?,
    @SerializedName("volume_usd") val volume: GeckoVolume?,
    @SerializedName("reserve_in_usd") val reserveUsd: String?
)

data class GeckoRelData(val id: String?)
data class GeckoRel(val data: GeckoRelData?)
data class GeckoRelationships(val network: GeckoRel?, val dex: GeckoRel?)

data class GeckoPool(
    val id: String?,
    val type: String?,
    val attributes: GeckoPoolAttributes?,
    val relationships: GeckoRelationships?
)

data class GeckoPoolsResponse(val data: List<GeckoPool>?)

interface GeckoApi {
    @GET("api/v2/networks/{network}/trending_pools")
    suspend fun trendingPools(@Path("network") network: String): GeckoPoolsResponse
}

object GeckoTerminal {
    val api: GeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.geckoterminal.com/")
            .client(radarClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeckoApi::class.java)
    }
}

// ---------- بایننس (حالت پشتیبان: حرکات سریع) ----------

data class Ticker24(
    val symbol: String?,
    @SerializedName("lastPrice") val lastPrice: String?,
    @SerializedName("priceChangePercent") val changePercent: String?,
    @SerializedName("quoteVolume") val quoteVolume: String?
)

interface RadarBinanceApi {
    @GET("api/v3/ticker/24hr")
    suspend fun tickers(): List<Ticker24>
}

object RadarBinance {
    val api: RadarBinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(radarClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadarBinanceApi::class.java)
    }
}
