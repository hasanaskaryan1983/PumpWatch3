package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ---------- شاخص ترس و طمع (alternative.me — بدون کلید) ----------

data class FngData(
    val value: String?,
    @SerializedName("value_classification") val classification: String?,
    val timestamp: String?
)

data class FngResponse(val data: List<FngData>?)

interface FngApi {
    @GET("fng/")
    suspend fun index(@Query("limit") limit: Int = 1): FngResponse
}

object FngClient {
    val api: FngApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.alternative.me/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FngApi::class.java)
    }
}

// ---------- داده جهانی + ترندها (CoinGecko — بدون کلید) ----------

data class GlobalData(
    @SerializedName("market_cap_percentage") val marketCapPercentage: Map<String, Double>?,
    @SerializedName("market_cap_change_percentage_24h_usd") val capChange24h: Double?,
    @SerializedName("total_market_cap") val totalMarketCap: Map<String, Double>?
)

data class GlobalResponse(val data: GlobalData?)

data class TrendingItem(
    val id: String?,
    val symbol: String?,
    val name: String?,
    @SerializedName("market_cap_rank") val rank: Int?
)

data class TrendingCoin(val item: TrendingItem?)

data class TrendingResponse(val coins: List<TrendingCoin>?)

interface PulseApi {
    @GET("global")
    suspend fun global(): GlobalResponse

    @GET("search/trending")
    suspend fun trending(): TrendingResponse
}

object PulseClient {
    val api: PulseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PulseApi::class.java)
    }
}
