package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GeckoApi {
    @GET("search/pools")
    suspend fun searchPools(@Query("query") query: String, @Query("page") page: Int = 1): PoolsResponse

    @GET("networks/{network}/trending_pools")
    suspend fun trendingPools(@Path("network") network: String): PoolsResponse

    @GET("networks/{network}/new_pools")
    suspend fun newPools(@Path("network") network: String): PoolsResponse
}

object GeckoTerminal {
    val api: GeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.geckoterminal.com/api/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(GeckoApi::class.java)
    }
}

data class PoolsResponse(val data: List<GeckoPool>?)

data class GeckoPool(
    val id: String?,
    val type: String?,
    val attributes: PoolAttributes?,
    val relationships: PoolRelationships?
)

data class PoolAttributes(
    val name: String?,
    @SerializedName("base_token_price_usd") val priceUsd: String?,
    @SerializedName("total_reserve_in_usd") val reserveUsd: String?,
    @SerializedName("fdv_usd") val fdvUsd: Double?,
    @SerializedName("pool_created_at") val createdAt: String?,
    @SerializedName("volume_usd") val volume: VolumeInfo?,
    val transactions: TxInfo?,
    @SerializedName("price_change_percentage") val priceChange: PriceChangeInfo?
)

data class VolumeInfo(val h1: Double?, val h6: Double?, val h24: Double?)
data class TxInfo(val h1: TxCount?, val h6: TxCount?, val h24: TxCount?)
data class TxCount(val buys: Double?, val sells: Double?)
data class PriceChangeInfo(val h1: Double?, val h6: Double?, val h24: Double?)

data class PoolRelationships(
    val network: NetworkRel?,
    val base_token: NetworkRel?
)

data class NetworkRel(val data: NetworkData?)
data class NetworkData(val id: String?)
