package com.pumpwatch.app.data

import com.google.gson.JsonArray
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/klines")
    suspend fun klines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<JsonArray>
}

object BinanceClient {
    val api: BinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://data-api.binance.vision/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApi::class.java)
    }
}

interface RadarApi {
    @GET("api/v3/ticker/24hr")
    suspend fun tickers(): List<RadarTicker>
}

data class RadarTicker(
    val symbol: String? = null,
    val lastPrice: String? = null,
    val priceChangePercent: String? = null
)

object RadarBinance {
    val api: RadarApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://data-api.binance.vision/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadarApi::class.java)
    }
}
