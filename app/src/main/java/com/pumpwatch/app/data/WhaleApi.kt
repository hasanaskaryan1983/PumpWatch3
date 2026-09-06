package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class AggTrade(
    @SerializedName("p") val price: String?,
    @SerializedName("q") val qty: String?,
    @SerializedName("T") val time: Long?,
    @SerializedName("m") val buyerIsMaker: Boolean?
)

interface WhaleBinanceApi {
    @GET("api/v3/aggTrades")
    suspend fun aggTrades(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int
    ): List<AggTrade>
}

object WhaleClient {
    val api: WhaleBinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WhaleBinanceApi::class.java)
    }
}
