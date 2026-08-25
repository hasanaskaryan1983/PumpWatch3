package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
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

interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1
    ): List<CoinMarket>
}

object ApiClient {
    val api: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    // گرفتن 1000 کوین (4 صفحه × 250)
    suspend fun getTop1000Coins(): List<CoinMarket> {
        val results = mutableListOf<CoinMarket>()
        for (page in 1..4) {
            val pageCoins = api.getMarkets(perPage = 250, page = page)
            results.addAll(pageCoins)
        }
        return results.sortedBy { it.market_cap_rank ?: 9999 }
    }
}
