package com.pumpwatch.app.data

import com.google.gson.JsonElement
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- GeckoTerminal: قیمت توکن + تریدهای استخر ----------
interface GeckoPriceApi {
    @GET("networks/{network}/tokens/{address}")
    suspend fun tokenInfo(
        @Path("network") network: String,
        @Path("address") address: String
    ): GtTokenInfo

    @GET("networks/{network}/pools/{address}/trades")
    suspend fun poolTrades(
        @Path("network") network: String,
        @Path("address") address: String
    ): GtTrades
}

data class GtTokenInfo(val data: GtTokenData?)
data class GtTokenData(val attributes: GtTokenAttrs?)
data class GtTokenAttrs(val name: String?, val symbol: String?, val price_usd: String?)

data class GtTrades(val data: List<GtTrade>?)
data class GtTrade(val attributes: GtTradeAttrs?)
data class GtTradeAttrs(
    val block_timestamp: Any?,
    val tx_from_address: String?,
    val tx_to_address: String?,
    val volume_in_usd: Any?,
    val price_in_usd: Any?,
    val price: Any?,
    val type: String?
)

object GeckoPrice {
    val api: GeckoPriceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.geckoterminal.com/api/v2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(GeckoPriceApi::class.java)
    }
}

// ---------- Blockscout: موجودی و تراکنش‌های EVM ----------
interface BlockscoutApi {
    @GET("api")
    suspend fun tokenList(
        @Query("module") module: String,
        @Query("action") action: String,
        @Query("address") address: String
    ): BsTokenList

    @GET("api")
    suspend fun tokenTx(
        @Query("module") module: String,
        @Query("action") action: String,
        @Query("address") address: String,
        @Query("sort") sort: String
    ): BsTxList
}

data class BsTokenList(val status: String?, val result: List<BsToken>?)
data class BsToken(
    val symbol: String?,
    val name: String?,
    val contractAddress: String?,
    val balance: String?,
    val decimals: String?
)

data class BsTxList(val status: String?, val result: List<BsTx>?)
data class BsTx(
    val timeStamp: String?,
    val tokenSymbol: String?,
    val value: String?,
    val from: String?,
    val to: String?,
    val contractAddress: String?,
    val tokenDecimal: String?
)

object Blockscout {
    private val cache = mutableMapOf<String, BlockscoutApi>()
    fun api(host: String): BlockscoutApi = cache.getOrPut(host) {
        Retrofit.Builder()
            .baseUrl(host)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(BlockscoutApi::class.java)
    }
}

// ---------- Solana RPC عمومی (دو سرور + فال‌بک خودکار) ----------
interface SolanaRpcApi {
    @POST(".")
    suspend fun rpc(@Body body: Map<String, @JvmSuppressWildcards Any?>): SolanaRpcResponse

    @POST(".")
    suspend fun rpcRaw(@Body body: Map<String, @JvmSuppressWildcards Any?>): SolanaRawResponse
}

data class SolanaRpcResponse(val result: SolanaRpcResult?)
data class SolanaRpcResult(val value: List<SolTokenAccount>?)
data class SolTokenAccount(val pubkey: String?, val account: SolAccount?)
data class SolAccount(val data: SolAccountData?)
data class SolAccountData(val parsed: SolParsed?)
data class SolParsed(val info: SolInfo?)
data class SolInfo(val mint: String?, val tokenAmount: SolAmount?)
data class SolAmount(val uiAmountString: String?)

data class SolanaRawResponse(val result: JsonElement?, val error: SolRpcError? = null)
data class SolRpcError(val code: Int?, val message: String?)

object SolanaRpc {
    val api: SolanaRpcApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mainnet-beta.solana.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(SolanaRpcApi::class.java)
    }
}

object SolanaRpc2 {
    val api: SolanaRpcApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://solana-rpc.publicnode.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(SolanaRpcApi::class.java)
    }
}

suspend fun solanaRaw(body: Map<String, @JvmSuppressWildcards Any?>, preferAlt: Boolean = false): SolanaRawResponse? {
    val first = if (preferAlt) SolanaRpc2.api else SolanaRpc.api
    val second = if (preferAlt) SolanaRpc.api else SolanaRpc2.api
    val r1 = try { first.rpcRaw(body) } catch (_: Exception) { null }
    if (r1 != null && r1.result != null) return r1
    val r2 = try { second.rpcRaw(body) } catch (_: Exception) { null }
    if (r2 != null && r2.result != null) return r2
    return r2 ?: r1
}

suspend fun solanaTyped(body: Map<String, @JvmSuppressWildcards Any?>): SolanaRpcResponse? {
    val r1 = try { SolanaRpc.api.rpc(body) } catch (_: Exception) { null }
    if (r1 != null && r1.result != null) return r1
    return try { SolanaRpc2.api.rpc(body) } catch (_: Exception) { null }
}
