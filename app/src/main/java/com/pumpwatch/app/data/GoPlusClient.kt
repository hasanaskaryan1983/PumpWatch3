package com.pumpwatch.app.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * GoPlus Security API — رایگان، بدون کلید API.
 * مستندات: https://docs.gopluslabs.io/reference/api
 *
 * 🚀 Commit 85 (M1 + M2):
 *   - M1: `lp_holders` به `List<LpHolder>` تغییر یافت (API آرایه برمی‌گرداند، نه آبجکت).
 *   - M1: `end_time` با پارسر تحمل‌پذیر (`parseLockEndTime`) که هم epoch و هم ISO را می‌خواند.
 *   - M2: `SolanaTokenSecurity` به‌عنوان مدل جدا با فیلدهای واقعی endpoint سولانا.
 *   - M2: `SecurityData` sealed class با دو زیرکلاس Evm و Solana برای type-safe dispatch.
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
    // 🚀 Commit 85 (M1): List به‌جای Map — API آرایه برمی‌گرداند
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
    val end_time: String?,   // 🚀 Commit 85: ISO یا epoch — parseLockEndTime هر دو را هندل می‌کند
    val opt_time: String?
)

data class TokenHolder(
    val address: String?,
    val tag: String?,
    val percent: Double?,
    val is_contract: Int?
)

// ============================================
// 🆕 Solana Security Model (endpoint جدا /api/v1/solana/token_security)
// ============================================

/**
 * 🚀 Commit 85 (M2): مدل جدا برای Solana endpoint.
 *
 * فیلدهای Solana با EVM متفاوت‌اند:
 *   - mintable / freezable / closable: اختیارات mint/freeze/close اکانت
 *   - balance_mutable_authority: آیا balance را می‌توان تغییر داد
 *   - transfer_fee: ساختار کارمزد انتقال (ممکن است object یا string باشد)
 *   - non_transferable: آیا توکن غیرقابل انتقال است
 *   - trusted_token: آیا توکن verified است
 *
 * فیلدهای EVM مثل is_honeypot / lp_holders / is_open_source وجود ندارند.
 */
data class SolanaTokenSecurity(
    val total_supply: String?,
    val holder_count: String?,
    val creator_address: String?,
    val creator_percent: Double?,
    val mintable: Any?,              // ممکن است String "0"/"1" یا Boolean باشد
    val freezable: Any?,
    val closable: Any?,
    val balance_mutable_authority: Any?,
    val transfer_fee: Any?,          // object یا string
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
// 🆕 Sealed class برای type-safe dispatch بین EVM و Solana
// ============================================

/**
 * 🚀 Commit 85 (M2): نوع دادهٔ امنیتی با sealed class.
 * MemeRadar با when(security) می‌تواند به‌طور type-safe به هر زیرنوع dispatch کند.
 */
sealed class SecurityData {
    data class Evm(val data: GoPlusTokenSecurity) : SecurityData()
    data class Solana(val data: SolanaTokenSecurity) : SecurityData()
}

data class GoPlusResponse(
    val code: Int?,
    val message: String?,
    val result: Map<String, Any?>?  // 🚀 Commit 85: Any? تا Gson بتواند هم EVM و هم Solana را deserialize کند
)

/**
 * مدل سه‌حالته برای نتیجهٔ امنیتی.
 *
 * - Ready: دادهٔ کامل معتبر برای این contract دریافت شد.
 * - Empty: API پاسخ داد ولی نتیجه برای این contract خالی بود.
 * - Failed: درخواست API شکست خورد.
 */
sealed class SecurityResult {
    data class Ready(val security: SecurityData) : SecurityResult()
    data class Empty(val reason: String = "Token not found in GoPlus database") : SecurityResult()
    data class Failed(val reason: String, val retryable: Boolean = false) : SecurityResult()
}

// ============================================
// 🆕 پارسرهای کمکی تحمل‌پذیر
// ============================================

/**
 * 🚀 Commit 85 (M1): پارسر `end_time` که هم epoch و هم ISO را می‌خواند.
 *
 * GoPlus EVM `end_time` را به‌صورت رشته‌ای مثل "2026-12-31 23:59:59" برمی‌گرداند،
 * نه epoch number. این تابع هر دو فرمت را تحمل می‌کند.
 *
 * @return epoch milliseconds، یا null اگر قابل پارس نباشد
 */
internal fun parseLockEndTime(s: String?): Long? {
    if (s.isNullOrBlank()) return null

    // ۱. ابتدا به‌عنوان epoch (ثانیه یا میلی‌ثانیه) تلاش کن
    s.toLongOrNull()?.let { epoch ->
        return when {
            epoch < 10_000_000_000L -> epoch * 1000L   // ثانیه → میلی‌ثانیه
            else -> epoch                               // میلی‌ثانیه
        }
    }

    // ۲. به‌عنوان ISO datetime تلاش کن (چند فرمت رایج)
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
        } catch (_: Exception) { /* امتحان بعدی */ }
    }

    return null
}

/**
 * 🚀 Commit 85 (M2): تشخیص "1"/"0"/true/false به‌صورت تحمل‌پذیر.
 *
 * برخی فیلدهای Solana ممکن است String "0"/"1" باشند، برخی Boolean واقعی.
 */
internal fun anyToBool(v: Any?): Boolean? = when (v) {
    null -> null
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> when (v.trim()) {
        "1", "true", "yes", "y" -> true
        "0", "false", "no", "n", "" -> false
        else -> null
    }
    else -> null
}

// ============================================
// 🟢 API Interfaces
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
    val api: GoPlusApi by lazy {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("https://api.gopluslabs.io/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
        retrofit.create(GoPlusApi::class.java)
    }

    private fun chainIdFor(chainName: String): String? = when (chainName.lowercase()) {
        "ethereum", "eth" -> "1"
        "bsc", "binance" -> "56"
        "base" -> "8453"
        "polygon", "matic" -> "137"
        "avalanche", "avax" -> "43114"
        "arbitrum" -> "42161"
        "optimism" -> "10"
        else -> null
    }

    private fun isSolana(chainName: String): Boolean =
        chainName.lowercase() == "solana" || chainName.lowercase() == "sol"

    /**
     * API اصلی برای مصرف‌کننده‌ها.
     *
     * 🚀 Commit 85 (M2): بسته به chain، `SecurityData.Evm` یا `SecurityData.Solana` برمی‌گرداند.
     */
    suspend fun getTokenSecurityResult(chain: String, address: String): SecurityResult {
        if (address.isBlank()) {
            return SecurityResult.Failed("Empty contract address", retryable = false)
        }

        return try {
            val response: GoPlusResponse = if (isSolana(chain)) {
                api.getSolanaTokenSecurity(address)
            } else {
                val chainId = chainIdFor(chain)
                    ?: return SecurityResult.Failed(
                        "Unsupported chain: $chain",
                        retryable = false
                    )
                api.getTokenSecurity(chainId, address)
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

            // کلید نتیجه معمولاً lowercase است
            val raw = result[address.lowercase()] ?: result[address]
            if (raw == null) {
                return SecurityResult.Empty("Token $address not found in response map")
            }

            // 🚀 Commit 85 (M2): deserialize به نوع مناسب بر اساس chain
            // چون result به‌صورت Map<String, Any?> آمده، Gson آن را به‌عنوان LinkedTreeMap نگه داشته.
            // باید با Gson دوباره به مدل صحیح serialize→deserialize کنیم.
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
                "Network error: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}",
                retryable = true
            )
        }
    }
}
