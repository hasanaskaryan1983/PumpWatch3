package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 🚀 Commit 93: Blockscout API برای Token Approvals
 *
 * این API دسترسی‌های توکن (Token Approvals) را از Blockscout می‌خواند.
 * هر شبکه EVM که Blockscout دارد می‌تواند این API را پشتیبانی کند.
 */

data class BlockscoutTokenApproval(
    val contract_address: String?,
    val token_symbol: String?,
    val spender_address: String?,
    val value: String?,
    val expiration: String?
)

data class BlockscoutApprovalsResponse(
    val result: List<BlockscoutTokenApproval>?,
    val message: String?,
    val status: String?
)

interface BlockscoutApprovalsApi {
    @GET("api")
    suspend fun getTokenApprovals(
        @Query("module") module: String = "account",
        @Query("action") action: String = "tokenapprovals",
        @Query("address") address: String
    ): BlockscoutApprovalsResponse
}

object BlockscoutApprovalsClient {
    private val apiCache = mutableMapOf<String, BlockscoutApprovalsApi>()

    fun api(baseUrl: String): BlockscoutApprovalsApi {
        return apiCache.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BlockscoutApprovalsApi::class.java)
        }
    }
}
