package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private val futClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

data class PremiumIndex(
    @SerializedName("lastFundingRate") val lastFundingRate: String?
)

data class OiHist(
    @SerializedName("sumOpenInterestValue") val sumOpenInterestValue: String?,
    val timestamp: Long?
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
}
