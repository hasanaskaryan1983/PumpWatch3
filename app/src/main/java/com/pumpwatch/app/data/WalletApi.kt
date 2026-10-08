package com.pumpwatch.app.data

import android.content.Context
import com.google.gson.JsonElement
import kotlinx.coroutines.delay
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
    suspend fun tokenInfo(@Path("network") network: String, @Path("address") address: String): GtTokenInfo

    @GET("networks/{network}/pools/{address}/trades")
    suspend fun poolTrades(@Path("network") network: String, @Path("address") address: String, @Query("before") before: Long? = null): GtTrades
}

data class GtTokenInfo(val data: GtTokenData?)
data class GtTokenData(val attributes: GtTokenAttrs?)
data class GtTokenAttrs(val name: String?, val symbol: String?, val price_usd: String?)

data class GtTrades(val data: List<GtTrade>?)
data class GtTrade(val attributes: GtTradeAttrs?)

/**
 * 🚀 Commit 82 (فاز ۳ — W1): مدل دقیق مطابق مستندات رسمی GeckoTerminal API v2.
 *
 * تغییرات نسبت به مدل قبلی:
 *   - `type` حذف شد (فیلد ثابت "trade" برای نوع resource، جهت نیست)
 *   - `kind` اضافه شد ("buy" | "sell" از دید base token)
 *   - `block_timestamp` از `Any?` به `String?` (ISO-8601)
 *   - `price_in_usd` → `price_from_in_usd` + `price_to_in_usd`
 *   - `volume_in_usd` از `Any?` به `String?`
 *   - `tx_hash` اضافه شد (برای dedup و commitهای آینده)
 *   - `tx_from_address` / `tx_to_address` حفظ شدند (آدرس کیف نهنگ)
 *   - `token_amount` اضافه شد (حجم توکن)
 */
data class GtTradeAttrs(
    val block_timestamp: String?,      // "2026-09-27T21:10:03Z" ISO-8601 UTC
    val kind: String?,                 // "buy" | "sell"
    val volume_in_usd: String?,        // رشته (مثلاً "1234.56")
    val price_from_in_usd: String?,    // قیمت قبل از معامله
    val price_to_in_usd: String?,      // قیمت بعد از معامله
    val tx_hash: String?,              // هش تراکنش (برای dedup)
    val tx_from_address: String?,      // آدرس واقعی خریدار/فروشنده (نهنگ!)
    val tx_to_address: String?,
    val token_amount: String?          // مقدار توکن
)

/**
 * 🚀 Commit 82: مدل غنی برای Commitهای بعدی (92: ردیابی نهنگ DEX).
 * آدرس کیف و هش تراکنش را نگهداری می‌کند تا در لایه موتور بتوانیم
 * هر آدرسی که ترید بزرگ زده را جمع‌آوری کنیم → لیدربورد نهنگ‌های کشف‌شده.
 */
data class DexTrade(
    val priceUsd: Double,
    val notionalUsd: Double,
    val timeMs: Long,
    val isSell: Boolean,
    val walletAddress: String?,
    val txHash: String?
)

object GeckoPrice {
    val api: GeckoPriceApi by lazy {
        Retrofit.Builder().baseUrl("https://api.geckoterminal.com/api/v2/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(GeckoPriceApi::class.java)
    }
}

// ---------- Blockscout: موجودی و تراکنش‌های EVM ----------
interface BlockscoutApi {
    @GET("api")
    suspend fun tokenList(@Query("module") module: String, @Query("action") action: String, @Query("address") address: String): BsTokenList
    @GET("api")
    suspend fun tokenTx(@Query("module") module: String, @Query("action") action: String, @Query("address") address: String, @Query("sort") sort: String): BsTxList
}

data class BsTokenList(val status: String?, val result: List<BsToken>?)
data class BsToken(
    val symbol: String?, val name: String?, val contractAddress: String?, val balance: String?, val decimals: String?
)

data class BsTxList(val status: String?, val result: List<BsTx>?)
data class BsTx(
    val timeStamp: String?, val tokenSymbol: String?, val value: String?, val from: String?,
    val to: String?, val contractAddress: String?, val tokenDecimal: String?
)

object Blockscout {
    private val cache = mutableMapOf<String, BlockscoutApi>()
    fun api(host: String): BlockscoutApi = cache.getOrPut(host) {
        Retrofit.Builder().baseUrl(host).addConverterFactory(GsonConverterFactory.create()).build().create(BlockscoutApi::class.java)
    }
}

// ---------- Solana RPC: کلید شخصی + سه endpoint عمومی ----------
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
        Retrofit.Builder().baseUrl("https://api.mainnet-beta.solana.com/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(SolanaRpcApi::class.java)
    }
}

object SolanaRpc2 {
    val api: SolanaRpcApi by lazy {
        Retrofit.Builder().baseUrl("https://solana-rpc.publicnode.com/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(SolanaRpcApi::class.java)
    }
}

private fun heliusClient(apiKey: String): SolanaRpcApi = Retrofit.Builder()
    .baseUrl("https://mainnet.helius-rpc.com/?api-key=$apiKey")
    .addConverterFactory(GsonConverterFactory.create()).build().create(SolanaRpcApi::class.java)

suspend fun solanaRaw(
    body: Map<String, @JvmSuppressWildcards Any?>,
    preferAlt: Boolean = false,
    ctx: Context? = null
): SolanaRawResponse? {
    val personalKey = ctx?.let { RpcKeyStore.get(it) } ?: RpcKeyStore.cachedKey()

    val endpoints = mutableListOf<SolanaRpcApi>()
    if (!personalKey.isNullOrEmpty()) endpoints.add(heliusClient(personalKey))
    if (preferAlt) {
        endpoints.add(SolanaRpc2.api); endpoints.add(SolanaRpc.api)
    } else {
        endpoints.add(SolanaRpc.api); endpoints.add(SolanaRpc2.api)
    }

    var lastError: Exception? = null
    for (client in endpoints) {
        var waitMs = 2000L
        for (attempt in 0 until 4) {
            try {
                val r = client.rpcRaw(body)
                if (r.result != null) return r
                if (r.error != null) {
                    val msg = r.error.message ?: ""
                    if (msg.contains("429", true) || msg.contains("Too many requests", true) || msg.contains("rate", true)) {
                        if (attempt < 3) { delay(waitMs); waitMs *= 2; continue }
                        lastError = Exception("429: $msg")
                    } else {
                        lastError = Exception("RPC error: ${r.error.code} $msg")
                    }
                } else {
                    lastError = Exception("null result")
                }
                break
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("429") || msg.contains("Too many requests", true)) {
                    if (attempt < 3) { delay(waitMs); waitMs *= 2; continue }
                    lastError = Exception("429: $msg")
                } else {
                    lastError = e
                }
                break
            }
        }
    }
    return if (lastError != null) SolanaRawResponse(result = null, error = SolRpcError(code = 429, message = lastError.message ?: "no endpoint")) else null
}

suspend fun solanaTyped(body: Map<String, @JvmSuppressWildcards Any?>, ctx: Context? = null): SolanaRpcResponse? {
    val personalKey = ctx?.let { RpcKeyStore.get(it) } ?: RpcKeyStore.cachedKey()

    val endpoints = mutableListOf<SolanaRpcApi>()
    if (!personalKey.isNullOrEmpty()) endpoints.add(heliusClient(personalKey))
    endpoints.add(SolanaRpc.api)
    endpoints.add(SolanaRpc2.api)

    for (client in endpoints) {
        try {
            val r = client.rpc(body)
            if (r.result != null) return r
        } catch (_: Exception) { }
    }
    return null
}
