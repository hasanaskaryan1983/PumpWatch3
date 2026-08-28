package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// ---------- مدل‌های DexScreener ----------

data class BoostedToken(
    val chainId: String?,
    val tokenAddress: String?,
    val tokenSymbol: String?,
    val tokenName: String?,
    val totalBoosts: Double?
)

data class DexToken(
    val address: String?,
    val name: String?,
    val symbol: String?
)

data class DexBuySell(val buys: Double?, val sells: Double?)

data class DexTxns(
    val h1: DexBuySell?,
    val h6: DexBuySell?,
    val h24: DexBuySell?
)

data class DexVolume(
    val h1: Double?,
    val h6: Double?,
    val h24: Double?
)

data class DexPriceChange(
    val h1: Double?,
    val h6: Double?,
    val h24: Double?
)

data class DexLiquidity(val usd: Double?)

data class DexPair(
    val chainId: String?,
    val dexId: String?,
    val pairAddress: String?,
    val baseToken: DexToken?,
    val priceUsd: String?,
    val txns: DexTxns?,
    val volume: DexVolume?,
    val priceChange: DexPriceChange?,
    val liquidity: DexLiquidity?,
    val fdv: Double?,
    val pairCreatedAt: Long?
)

// ---------- سرویس API ----------

interface DexScreenerApi {

    @GET("token-boosts/top/v1")
    suspend fun topBoosts(): List<BoostedToken>

    @GET("latest/dex/tokens/{addresses}")
    suspend fun tokens(@Path("addresses") addresses: String): List<DexPair>
}

// ---------- کلاینت (با ترافیک‌شکن مشترک) ----------

object DexClient {
    val api: DexScreenerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.dexscreener.com/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DexScreenerApi::class.java)
    }
}
