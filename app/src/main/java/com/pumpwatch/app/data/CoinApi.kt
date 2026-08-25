package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------- مدل‌ها ----------

data class CoinMarket(
    val id: String,
    val symbol: String,
    val name: String,
    @SerializedName("current_price")
    val current_price: Double,
    @SerializedName("price_change_percentage_24h")
    val price_change_percentage_24h: Double?
)

data class MarketChart(
    val prices: List<List<Double>>
)

// ---------- API ----------

interface CoinApi {

    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false
    ): List<CoinMarket>
}

interface ChartApi {

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int
    ): MarketChart
}

// ---------- کلاینت‌ها ----------

private fun httpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

object ApiClient {
    val api: CoinApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(httpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinApi::class.java)
    }
}

object ChartClient {
    val api: ChartApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(httpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChartApi::class.java)
    }
}
