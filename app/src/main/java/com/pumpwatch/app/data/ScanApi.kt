package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------- مدل‌های اسکن ----------

data class ScanMarket(
    val id: String,
    val symbol: String,
    val name: String,
    @SerializedName("market_cap_rank") val rank: Int?,
    @SerializedName("current_price") val price: Double,
    @SerializedName("market_cap") val marketCap: Double?,
    @SerializedName("total_volume") val volume: Double?,
    @SerializedName("high_24h") val high24h: Double?,
    @SerializedName("low_24h") val low24h: Double?,
    @SerializedName("price_change_percentage_24h") val change24h: Double?,
    @SerializedName("price_change_percentage_7d_in_currency") val change7d: Double?
)

data class ScanChart(
    val prices: List<List<Double>>,
    @SerializedName("total_volumes") val volumes: List<List<Double>>?
)

data class Derivative(
    val base: String?,
    val symbol: String?,
    val market: DerivMarket?,
    @SerializedName("funding_rate") val fundingRate: Double?,
    @SerializedName("open_interest_usd") val openInterestUsd: Double?,
    @SerializedName("volume_24h") val volume24h: Double?,
    val basis: Double?
)

data class DerivMarket(val name: String?)

// ---------- سرویس API ----------

interface ScanApiService {

    @GET("coins/markets")
    suspend fun markets(
        @Query("vs_currency") vs: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1,
        @Query("sparkline") spark: Boolean = false,
        @Query("price_change_percentage") pcp: String = "24h,7d"
    ): List<ScanMarket>

    @GET("coins/{id}/market_chart")
    suspend fun chart(
        @Path("id") id: String,
        @Query("vs_currency") vs: String = "usd",
        @Query("days") days: Int = 30,
        @Query("interval") interval: String = "hourly"
    ): ScanChart

    @GET("derivatives")
    suspend fun derivatives(): List<Derivative>
}

// ---------- کلاینت ----------

object ScanClient {
    val api: ScanApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScanApiService::class.java)
    }
}
