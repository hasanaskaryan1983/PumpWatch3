package com.pumpwatch.app.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * GoPlus Security API - رایگان، بدون کلید API
 * مستندات: https://docs.gopluslabs.io/reference/api
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

interface GoPlusApi {
    /**
     * گرفتن اطلاعات امنیتی توکن
     * @param chain نام زنجیره (ethereum, bsc, solana, base, etc.)
     * @param addresses آدرس‌های contract (comma-separated)
     */
    @GET("api/v1/token_security/{chain}")
    suspend fun getTokenSecurity(
        @Path("chain") chain: String,
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
}
