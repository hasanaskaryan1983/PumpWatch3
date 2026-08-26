package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class CoinMarket(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String,
    val current_price: Double,
    val market_cap: Double,
    val total_volume: Double,
    val price_change_percentage_24h: Double?,
    val market_cap_rank: Int?
)

data class CoinChart(
    val prices: List<List<Double>>,
    @SerializedName("total_volumes") val totalVolumes: List<List<Double>>?
)

interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1
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
    ): CoinChart
}

object ApiClient {
    val api: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    suspend fun getTop1000Coins(): List<CoinMarket> {
        val results = mutableListOf<CoinMarket>()
        for (page in 1..4) {
            val pageCoins = api.getMarkets(perPage = 250, page = page)
            results.addAll(pageCoins)
        }
        return results.sortedBy { it.market_cap_rank ?: 9999 }
    }

    suspend fun getTop100Coins(): List<CoinMarket> {
        return api.getMarkets(perPage = 100, page = 1)
    }

    suspend fun getCoinChart(id: String, days: Int): CoinChart {
        return api.getMarketChart(id, days = days)
    }
}
