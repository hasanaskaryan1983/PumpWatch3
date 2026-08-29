package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- فاندامنتال ارز ----------

data class CoinInfo(
    val name: String?,
    @SerializedName("market_cap_rank") val rank: Int?,
    @SerializedName("market_data") val marketData: MarketData?
)

data class MarketData(
    @SerializedName("ath_change_percentage") val athChange: Map<String, Double>?,
    @SerializedName("market_cap") val marketCap: Map<String, Double>?,
    @SerializedName("total_volume") val volume: Map<String, Double>?
)

interface CoinInfoApi {

    @GET("coins/{id}")
    suspend fun info(
        @Path("id") id: String,
        @Query("localization") localization: Boolean = false,
        @Query("tickers") tickers: Boolean = false,
        @Query("market_data") marketData: Boolean = true,
        @Query("community_data") communityData: Boolean = false,
        @Query("developer_data") developerData: Boolean = false,
        @Query("sparkline") sparkline: Boolean = false
    ): CoinInfo
}

object CoinInfoClient {
    val api: CoinInfoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinInfoApi::class.java)
    }
}
