package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.TimeUnit

// ============================================
// 🟢 کد موجود (دست‌نخورده) — Binance legacy
// ============================================

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

// ============================================
// 🆕 P0-2: abstraction چندصرافی + کف بدون‌مجوز آن‌چین
// ============================================

data class AggTradeNormalized(
    val price: Double,
    val qty: Double,
    val time: Long,
    val buyerIsMaker: Boolean
) {
    val notional: Double get() = price * qty
}

interface WhaleProvider {
    val name: String
    suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized>
}

// -------- Binance --------

private interface BinanceRawApi {
    @GET("api/v3/aggTrades")
    suspend fun aggTrades(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int
    ): List<AggTrade>
}

object BinanceProvider : WhaleProvider {
    override val name: String = "BINANCE"
    private val api: BinanceRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(sharedOkHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val sym = normalizeSymbol(symbol, "BINANCE")
            api.aggTrades(sym, limit).mapNotNull { t ->
                val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.qty?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.time ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.buyerIsMaker ?: false)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// -------- Bybit --------

private data class BybitResponse<T>(val retCode: Int?, val result: BybitResult<T>?)
private data class BybitResult<T>(val list: List<T>?)
private data class BybitTrade(
    val price: String?,
    val size: String?,
    val time: Long?,
    val side: String?
)

private interface BybitRawApi {
    @GET("v5/market/recent-trade")
    suspend fun recentTrades(
        @Query("category") category: String,
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int
    ): BybitResponse<BybitTrade>
}

object BybitProvider : WhaleProvider {
    override val name: String = "BYBIT"
    private val api: BybitRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bybit.com/")
            .client(sharedOkHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BybitRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val sym = normalizeSymbol(symbol, "BYBIT")
            val resp = api.recentTrades("spot", sym, limit)
            if (resp.retCode != 0) return emptyList()
            resp.result?.list?.mapNotNull { t ->
                val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.size?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.time ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("Sell", true) == true)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// -------- OKX --------

private data class OkxResponse(val code: String?, val data: List<OkxTrade>?)
private data class OkxTrade(
    val instId: String?,
    val px: String?,
    val sz: String?,
    val ts: String?,
    val side: String?
)

private interface OkxRawApi {
    @GET("api/v5/market/trades")
    suspend fun trades(
        @Query("instId") instId: String,
        @Query("limit") limit: Int
    ): OkxResponse
}

object OkxProvider : WhaleProvider {
    override val name: String = "OKX"
    private val api: OkxRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.okx.com/")
            .client(sharedOkHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OkxRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val inst = normalizeSymbol(symbol, "OKX")
            val resp = api.trades(inst, limit.coerceAtMost(100))
            if (resp.code != "0") return emptyList()
            resp.data?.mapNotNull { t ->
                val p = t.px?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.sz?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.ts?.toLongOrNull() ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("sell", true) == true)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// -------- Gate --------

private data class GateTrade(
    val price: String?,
    val amount: String?,
    val create_time_ms: Long?,
    val side: String?
)

private interface GateRawApi {
    @GET("api/v4/spot/trades")
    suspend fun trades(
        @Query("currency_pair") pair: String,
        @Query("limit") limit: Int
    ): List<GateTrade>
}

object GateProvider : WhaleProvider {
    override val name: String = "GATE"
    private val api: GateRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.gateio.ws/")
            .client(sharedOkHttp())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GateRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val pair = normalizeSymbol(symbol, "GATE")
            api.trades(pair, limit).mapNotNull { t ->
                val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.amount?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.create_time_ms ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("sell", true) == true)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// -------- 🟢 کف بدون‌مجوز: GeckoTerminal DEX --------

object GeckoDexProvider : WhaleProvider {
    override val name: String = "GECKO_DEX"

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val pools = GeckoTerminal.api.searchPools(symbol).data?.filter { it.attributes != null } ?: emptyList()
            val pool = pools.maxByOrNull { it.attributes?.volume?.h24 ?: 0.0 } ?: return emptyList()
            val net = pool.relationships?.network?.data?.id ?: return emptyList()
            val poolAddr = pool.id?.substringAfter('_') ?: return emptyList()
            val trades = GeckoPrice.api.poolTrades(net, poolAddr).data ?: emptyList()
            trades.mapNotNull { t ->
                val a = t.attributes ?: return@mapNotNull null
                val vol = a.volume_in_usd?.toDoubleOrNull() ?: return@mapNotNull null
                val px = a.price_in_usd?.toDoubleOrNull() ?: a.price?.toDoubleOrNull() ?: return@mapNotNull null
                if (px <= 0) return@mapNotNull null
                val tsSec = a.block_timestamp?.toDoubleOrNull() ?: return@mapNotNull null
                val qty = vol / px
                AggTradeNormalized(px, qty, tsSec.toLong() * 1000L, (a.type ?: "").equals("sell", true))
            }.take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// -------- Shared helpers (top-level functions) --------

private fun sharedOkHttp(): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
}

private fun normalizeSymbol(symbol: String, exchange: String): String {
    val base = symbol.uppercase(Locale.US)
        .replace("_", "-")
        .replace("USDT", "-USDT")
        .replace("USD", "-USD")
    val withDash = if (!base.contains("-")) "$base-USDT" else base
    val parts = withDash.split("-")
    if (parts.size != 2) return symbol.uppercase(Locale.US)
    val coin = parts[0]
    val quote = parts[1]

    return when (exchange) {
        "BINANCE" -> "$coin$quote"
        "BYBIT" -> "$coin$quote"
        "OKX" -> "$coin-$quote"
        "GATE" -> "${coin}_$quote"
        else -> symbol.uppercase(Locale.US)
    }
}

object WhaleProviders {
    val all: List<WhaleProvider> = listOf(
        BinanceProvider,
        BybitProvider,
        OkxProvider,
        GateProvider,
        GeckoDexProvider
    )
}
