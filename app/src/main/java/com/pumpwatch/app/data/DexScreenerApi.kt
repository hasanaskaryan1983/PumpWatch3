package com.pumpwatch.app.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

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

// ---------- کلاینت مستقل (با تلاش مجدد روی 429) ----------

object DexClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(object : Interceptor {
                override fun intercept(chain: Interceptor.Chain): Response {
                    var response = chain.proceed(chain.request())
                    var retries = 0
                    while (response.code == 429 && retries < 3) {
                        response.close()
                        Thread.sleep(3000L * (retries + 1))
                        response = chain.proceed(chain.request())
                        retries++
                    }
                    return response
                }
            })
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val api: DexScreenerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.dexscreener.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DexScreenerApi::class.java)
    }
}
