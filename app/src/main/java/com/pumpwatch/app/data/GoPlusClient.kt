package com.pumpwatch.app.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GoPlus Security API - رایگان، بدون کلید API
 * مستندات: https://docs.gopluslabs.io/reference/api
 *
 * P0-1: chain mapping عددی + endpoint Solana جدا + مدل سه‌حالتهٔ نتیجه.
 *
 * Endpoint EVM: /api/v1/token_security/{chainId}?contract_addresses=...
 * Endpoint Solana: /api/v1/solana/token_security?contract_addresses=...
 */

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
    val lp_holders: Map<String, LpHolder>?,
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

data class GoPlusResponse(
    val code: Int?,
    val message: String?,
    val result: Map<String, GoPlusTokenSecurity>?
)

/**
 * P0-1: مدل سه‌حالته برای نتیجهٔ امنیتی.
 *
 * - Ready: دادهٔ کامل معتبر برای این contract دریافت شد.
 * - Empty: API پاسخ داد ولی نتیجه برای این contract خالی بود (توکن شناخته‌شده نیست).
 * - Failed: درخواست API شکست خورد (network/timeout/rate limit/invalid chain).
 *
 * هیچ‌کدام به score خنثی یا "safe" تبدیل نمی‌شوند — UI باید برای Empty و Failed
 * رفتار جداگانه داشته باشد و توکن را به‌عنوان UNKNOWN علامت بزند.
 */
sealed class SecurityResult {
    data class Ready(val security: GoPlusTokenSecurity) : SecurityResult()
    data class Empty(val reason: String = "Token not found in GoPlus database") : SecurityResult()
    data class Failed(val reason: String, val retryable: Boolean = false) : SecurityResult()
}

interface GoPlusApi {
    /**
     * گرفتن اطلاعات امنیتی توکن برای زنجیره‌های EVM.
     * @param chainId شناسهٔ عددی زنجیره (1=ethereum, 56=bsc, 8453=base, 137=polygon, 43114=avalanche)
     * @param addresses آدرس‌های contract (comma-separated)
     */
    @GET("api/v1/token_security/{chainId}")
    suspend fun getTokenSecurity(
        @Path("chainId") chainId: String,
        @Query("contract_addresses") addresses: String
    ): GoPlusResponse

    /**
     * گرفتن اطلاعات امنیتی توکن برای Solana (endpoint جدا).
     * @param addresses آدرس‌های contract (comma-separated)
     */
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

    /**
     * P0-1: mapping نام زنجیره به شناسهٔ عددی GoPlus.
     * زنجیره‌های پشتیبانی‌نشده → null.
     */
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
     * P0-1: API اصلی برای مصرف‌کننده‌ها.
     * @param chain نام زنجیره (ethereum, bsc, base, solana, ...)
     * @param address آدرس contract
     * @return مدل سه‌حالته: Ready / Empty / Failed
     *
     * هرگز score خنثی یا "safe" برنمی‌گرداند — UI باید برای Empty و Failed برچسب UNKNOWN نمایش دهد.
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
                        "Unsupported chain: $chain (supported: ethereum, bsc, base, polygon, avalanche, arbitrum, optimism, solana)",
                        retryable = false
                    )
                api.getTokenSecurity(chainId, address)
            }

            // GoPlus code=1 یعنی موفقیت
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

            // GoPlus کلید نتیجه را به lowercase برمی‌گرداند
            val security = result[address.lowercase()] ?: result[address]
            if (security == null) {
                return SecurityResult.Empty("Token $address not found in response map")
            }

            SecurityResult.Ready(security)
        } catch (e: Exception) {
            SecurityResult.Failed(
                "Network error: ${e.javaClass.simpleName}: ${e.message ?: "unknown"}",
                retryable = true
            )
        }
    }
}
