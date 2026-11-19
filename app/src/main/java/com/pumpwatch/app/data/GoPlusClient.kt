package com.pumpwatch.app.data

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * GoPlus Security API — رایگان، بدون کلید API.
 * مستندات: https://docs.gopluslabs.io/reference/api
 *
 * 🚀 Commit 85 (M1 + M2):
 *   - M1: `lp_holders` به `List<LpHolder>` تغییر یافت (API آرایه برمی‌گرداند، نه آبجکت).
 *   - M1: `end_time` با پارسر تحمل‌پذیر (`parseLockEndTime`) که هم epoch و هم ISO را می‌خواند.
 *   - M2: `SolanaTokenSecurity` به‌عنوان مدل جدا با فیلدهای واقعی endpoint سولانا.
 *   - M2: `SecurityData` sealed class با دو زیرکلاس Evm و Solana برای type-safe dispatch.
 *
 * 🚀 Commit 89 (A2): تاب‌آوری مستقل:
 *   - OkHttpClient اختصاصی با timeout (connect 8s / read 12s / call 15s)
 *   - throttle با GlobalHostLimiter.acquire("api.gopluslabs.io") قبل از هر تلاش
 *   - retry هوشمند: حداکثر ۳ تلاش، فقط برای خطاهای retryable (429/5xx/network)
 *   - خطاهای terminal (400/404/451) فوراً رد می‌شوند
 */

// ============================================
// 🟢 EVM Security Model (ethereum, bsc, base, ...)
// ============================================

data class GoPlusTokenSecurity(
    val is_honeypot: String?,
    val is_open_source: String?,
    val is_proxy: String?,
    val is_mintable: String?,
    val can_take_back_ownership: String?,
    val owner_change_balance: String?,
    val hidden_owner: String?,
    val selfdestruct: String?,
    val buy_tax: String?,
    val sell_tax: String?,
    val holder_count: String?,
    val lp_holder_count: String?,
    val lp_total_supply: String?,
    val lp_holders: List<LpHolder>?,
    val holders: List<TokenHolder>?,
    val total_supply: String?,
    val contract_creator: String?
)

data class LpHolder(
    val address: String?,
    val tag: String?,
    val percent: Double?,
    val is_locked: String?,
    val locked_detail: List<LockedDetail>?
)

data class LockedDetail(
    val amount: String?,
    val end_time: String?,
    val opt_time: String?
)

data class TokenHolder(
    val address: String?,
    val tag: String?,
    val percent: Double?,
    val is_contract: Int?
)

// ============================================
// 🆕 Solana Security Model
// ============================================

data class SolanaTokenSecurity(
    val total_supply: String?,
    val holder_count: String?,
    val creator_address: String?,
    val creator_percent: Double?,
    val mintable: Any?,
    val freezable: Any?,
    val closable: Any?,
    val balance_mutable_authority: Any?,
    val transfer_fee: Any?,
    val non_transferable: Any?,
    val trusted_token: Any?,
    val top_holders: List<SolanaHolder>?,
    val is_true_token: Any?,
    val is_airdrop: Any?
)

data class SolanaHolder(
    val address: String?,
    val tag: String?,
    val percent: Double?,
    val is_contract: Int?
)

// ============================================
//  Sealed class برای type-safe dispatch
// ============================================

sealed class SecurityData {
    data class Evm(val data: GoPlusTokenSecurity) : SecurityData()
    data class Solana(val data: SolanaTokenSecurity) : SecurityData()
}

data class GoPlusResponse(
    val code: Int?,
    val message: String?,
    val result: Map<String, Any?>?
)

sealed class SecurityResult {
    data class Ready(val security: SecurityData) : SecurityResult()
    data class Empty(val reason: String = "Token not found in GoPlus database") : SecurityResult()
    data class Failed(val reason: String, val retryable: Boolean = false) : SecurityResult()
}

// ============================================
// 🆕 پارسرهای کمکی
// ============================================

internal fun parseLockEndTime(s: String?): Long? {
    if (s.isNullOrBlank()) return null

    s.toLongOrNull()?.let { epoch ->
        return when {
            epoch < 10_000_000_000L -> epoch * 1000L
            else -> epoch
        }
    }

    val formats = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd"
    )
    for (pattern in formats) {
        try {
            val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return fmt.parse(s)?.time
        } catch (_: Exception) { }
    }

    return null
}

internal fun anyToBool(v: Any?): Boolean? = when (v) {
    null -> null
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> when (v.trim()) {
        "1", "true", "yes", "y" -> true
        "0", "false", "no", "n", "" -> false
        else -> null
    }
    is Map<*, *> -> anyToBool(v["status"])  // Solana authority flags: {"authority":[...],"status":"1"}
    else -> null
}

// ============================================
// 🚀 Commit 89 (A2): توابع pure تاب‌آوری
// ============================================

const val GOPLUS_HOST = "api.gopluslabs.io"
const val GOPLUS_MAX_RETRIES = 3
const val GOPLUS_BASE_BACKOFF_MS = 2_000L
const val GOPLUS_MAX_BACKOFF_MS = 15_000L

fun goPlusBackoffMs(attempt: Int): Long {
    val shift = (attempt - 1).coerceIn(0, 20)
    return (GOPLUS_BASE_BACKOFF_MS shl shift).coerceAtMost(GOPLUS_MAX_BACKOFF_MS)
}

fun isRetryableError(e: Exception): Boolean = when (e) {
    is HttpException -> e.code() == 429 || e.code() in 500..599
    is IOException -> true
    else -> false
}

// ============================================
//  API Interfaces
// ============================================

interface GoPlusApi {
    @GET("api/v1/token_security/{chainId}")
    suspend fun getTokenSecurity(
        @Path("chainId") chainId: String,
        @Query("contract_addresses") addresses: String
    ): GoPlusResponse

    @GET("api/v1/solana/token_security")
    suspend fun getSolanaTokenSecurity(
        @Query("contract_addresses") addresses: String
    ): GoPlusResponse
}

object GoPlusClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val api: GoPlusApi by lazy {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://api.gopluslabs.io/")
            .client(httpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
        retrofit.create(GoPlusApi::class.java)
    }

    /**
     * 🚀 Commit 92: اضافه شدن Arc Network با Chain ID 5042
     */
    private fun chainIdFor(chainName: String): String? = when (chainName.lowercase()) {
        "ethereum", "eth" -> "1"
        "bsc", "binance" -> "56"
        "base" -> "8453"
        "polygon", "matic" -> "137"
        "avalanche", "avax" -> "43114"
        "arbitrum" -> "42161"
        "optimism" -> "10"
        "arc" -> "5042"  // 🚀 Commit 92: Arc Network
        else -> null
    }

    private fun isSolana(chainName: String): Boolean =
        chainName.lowercase() == "solana" || chainName.lowercase() == "sol"

    private suspend fun <T> resilientCall(block: suspend () -> T): T {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < GOPLUS_MAX_RETRIES) {
            GlobalHostLimiter.acquire(GOPLUS_HOST)
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt >= GOPLUS_MAX_RETRIES || !isRetryableError(e)) break
                delay(goPlusBackoffMs(attempt))
            }
        }
        throw lastError ?: IllegalStateException("GoPlus call failed without error")
    }

    suspend fun getTokenSecurityResult(chain: String, address: String): SecurityResult {
        if (address.isBlank()) {
            return SecurityResult.Failed("Empty contract address", retryable = false)
        }

        return try {
            val response: GoPlusResponse = if (isSolana(chain)) {
                resilientCall { api.getSolanaTokenSecurity(address) }
            } else {
                val chainId = chainIdFor(chain)
                    ?: return SecurityResult.Failed(
                        "Unsupported chain: $chain",
                        retryable = false
                    )
                resilientCall { api.getTokenSecurity(chainId, address) }
            }

            if (response.code != 1) {
                return SecurityResult.Failed(
                    "GoPlus API error: ${response.message ?: "code=${response.code}"}",
                    retryable = true
                )
            }

            val result = response.result
            if (result.isNullOrEmpty()) {
                return SecurityResult.Empty("GoPlus returned empty result for $address on $chain")
            }

            val raw = result[address.lowercase()] ?: result[address]
            if (raw == null) {
                return SecurityResult.Empty("Token $address not found in response map")
            }

            val gson = com.google.gson.Gson()
            val json = gson.toJson(raw)
            val securityData: SecurityData = if (isSolana(chain)) {
                val sol = gson.fromJson(json, SolanaTokenSecurity::class.java)
                    ?: return SecurityResult.Failed("Failed to parse Solana security data", retryable = true)
                SecurityData.Solana(sol)
            } else {
                val evm = gson.fromJson(json, GoPlusTokenSecurity::class.java)
                    ?: return SecurityResult.Failed("Failed to parse EVM security data", retryable = true)
                SecurityData.Evm(evm)
            }

            SecurityResult.Ready(securityData)
        } catch (e: Exception) {
            SecurityResult.Failed(
                "Network error after $GOPLUS_MAX_RETRIES attempts: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}",
                retryable = true
            )
        }
    }
}
