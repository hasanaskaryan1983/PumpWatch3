package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
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

data class CoinChart(
    val prices: List<List<Double>>,
    @SerializedName("total_volumes") val totalVolumes: List<List<Double>>?
)

data class DexBoostedToken(
    val url: String?,
    val chainId: String?,
    val tokenAddress: String?,
    val amount: Double?,
    val totalAmount: Double?,
    val icon: String?,
    val header: String?,
    val openGraph: String?,
    val description: String?,
    val links: List<DexLink>?
)

data class DexLink(
    val type: String?,
    val url: String?
)

data class DexTokenProfile(
    val url: String?,
    val chainId: String?,
    val tokenAddress: String?,
    val icon: String?,
    val header: String?,
    val openGraph: String?,
    val description: String?,
    val links: List<DexLink>?,
    @SerializedName("topPools") val topPools: List<DexPool>?
)

data class DexPool(
    @SerializedName("poolAddress") val poolAddress: String?,
    val chainId: String?,
    val dexId: String?,
    val baseToken: DexToken?,
    val quoteToken: DexToken?,
    val priceNative: String?,
    val priceUsd: String?,
    val txns: DexTxns?,
    val volume: DexVolume?,
    val liquidity: DexLiquidity?,
    val fdv: Double?,
    val marketCap: Double?,
    val pairCreatedAt: Long?,
    val info: DexPoolInfo?
)

data class DexToken(
    val address: String?,
    val name: String?,
    val symbol: String?,
    val icon: String?
)

data class DexTxns(
    val m5: DexTxnCount?,
    val h1: DexTxnCount?,
    val h6: DexTxnCount?,
    val h24: DexTxnCount?
)

data class DexTxnCount(
    val buys: Int?,
    val sells: Int?
)

data class DexVolume(
    val h24: Double?,
    val h6: Double?,
    val h1: Double?,
    val m5: Double?
)

data class DexLiquidity(
    val usd: Double?,
    val base: Double?,
    val quote: Double?
)

data class DexPoolInfo(
    val imageUrl: String?,
    val header: String?,
    val openGraph: String?,
    val websites: List<DexWebsite>?,
    val socials: List<DexSocial>?
)

data class DexWebsite(
    val label: String?,
    val url: String?
)

data class DexSocial(
    val type: String?,
    val url: String?
)

data class DexSearchResult(
    val pairs: List<DexPool>?
)

interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 250,
        @Query("page") page: Int = 1
    ): List<CoinMarket>

    @GET("coins/{id}/ohlc")
    suspend fun getOhlc(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String
    ): List<List<Double>>

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int
    ): CoinChart
}

interface DexScreenerApi {
    @GET("token-boosts/top/v1")
    suspend fun getTopBoosted(): List<DexBoostedToken>

    @GET("token-profiles/latest/v1")
    suspend fun getLatestProfiles(): List<DexTokenProfile>

    @GET("latest/dex/search")
    suspend fun searchToken(@Query("q") query: String): DexSearchResult
}

object ApiClient {
    val api: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/api/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CoinGeckoApi::class.java)
    }

    private val dexApi: DexScreenerApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.dexscreener.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DexScreenerApi::class.java)
    }

    suspend fun getTop1000Coins(): List<CoinMarket> {
        val results = mutableListOf<CoinMarket>()
        for (page in 1..4) {
            val pageCoins = api.getMarkets(perPage = 250, page = page)
            results.addAll(pageCoins)
        }
        return results.sortedBy { it.market_cap_rank ?: 9999 }
    }

    suspend fun getTop100Coins(): List<CoinMarket> {
        return api.getMarkets(perPage = 100, page = 1)
    }

    suspend fun getCoinChart(id: String, days: Int): CoinChart {
        return api.getMarketChart(id, days = days)
    }

    suspend fun getDexTrending(): List<CoinMarket> {
        return try {
            val profiles = dexApi.getLatestProfiles()
            val allPools = profiles
                .flatMap { it.topPools ?: emptyList() }
                .distinctBy { it.poolAddress }
                .filterViable()
            allPools.map { it.toCoinMarket() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getDexBoosted(): List<CoinMarket> {
        return try {
            val boosted = dexApi.getTopBoosted()
            val boostedPools = boosted.mapNotNull { boost ->
                boost.tokenAddress?.let { addr ->
                    try {
                        val result = dexApi.searchToken(addr)
                        result.pairs?.firstOrNull()
                    } catch (_: Exception) {
                        null
                    }
                }
            }.filterViable()
            boostedPools.map { it.toCoinMarket() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

fun List<DexPool>.filterViable(
    minLiquidityUsd: Double = 10000.0,
    minVolume24h: Double = 5000.0
): List<DexPool> {
    return filter { pool ->
        val liq = pool.liquidity?.usd ?: 0.0
        val vol = pool.volume?.h24 ?: 0.0
        liq >= minLiquidityUsd && vol >= minVolume24h
    }
}

fun DexPool.toCoinMarket(priceChange24h: Double? = null): CoinMarket {
    val token = baseToken
    return CoinMarket(
        id = token?.address ?: poolAddress ?: "unknown",
        symbol = token?.symbol ?: "???",
        name = token?.name ?: "Unknown",
        image = token?.icon ?: info?.imageUrl ?: "",
        current_price = priceUsd?.toDoubleOrNull() ?: 0.0,
        market_cap = marketCap ?: fdv ?: 0.0,
        total_volume = volume?.h24 ?: 0.0,
        price_change_percentage_24h = priceChange24h,
        market_cap_rank = null
    )
}
