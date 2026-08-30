package com.pumpwatch.app.data

import com.google.gson.JsonArray
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class BinanceCandle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

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
            .baseUrl("https://api.binance.com/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceApi::class.java)
    }

    suspend fun candles(symbol: String, tf: String): List<BinanceCandle> {
        val pair = symbol.uppercase() + "USDT"
        val interval = when (tf) {
            "15m" -> "15m"
            "1h" -> "1h"
            "4h" -> "4h"
            "1d" -> "1d"
            else -> "1w"
        }
        return api.klines(pair, interval, 200).map { a ->
            BinanceCandle(
                time = a[0].asLong,
                open = a[1].asDouble,
                high = a[2].asDouble,
                low = a[3].asDouble,
                close = a[4].asDouble,
                volume = a[5].asDouble
            )
        }
    }
}
