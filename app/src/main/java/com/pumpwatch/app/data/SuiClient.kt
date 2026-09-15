package com.pumpwatch.app.data

import com.google.gson.JsonObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * SUI Mainnet RPC — عمومی، بدون کلید.
 * مستندات: https://docs.sui.io/sui-jsonrpc
 *
 * P0-1: پاسخ‌ها raw JsonObject اند؛ مصرف‌کننده باید حالت‌های
 * Ready / Empty / Failed را جداگانه مدیریت کند.
 *
 * Sprint 8 (S1): فقط لایهٔ داده — هنوز به هیچ UI ای وصل نیست.
 */

interface SuiApi {
    @POST("/")
    suspend fun rpc(@Body body: Map<String, @JvmSuppressWildcards Any?>): JsonObject
}

object SuiClient {
    const val MAINNET = "https://fullnode.mainnet.sui.io/"

    val api: SuiApi by lazy {
        Retrofit.Builder()
            .baseUrl(MAINNET)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SuiApi::class.java)
    }

    /** موجودی همهٔ کوین‌ها (SUI خام + توکن‌ها) برای یک آدرس */
    suspend fun balances(addr: String): JsonObject? = try {
        api.rpc(mapOf(
            "jsonrpc" to "2.0", "id" to 1,
            "method" to "getAllBalances",
            "params" to listOf(addr)
        ))
    } catch (_: Exception) { null }

    /** لیست تراکنش‌های آدرس + تغییرات موجودی هر تراکنش */
    suspend fun txBlocks(addr: String, limit: Int): JsonObject? = try {
        api.rpc(mapOf(
            "jsonrpc" to "2.0", "id" to 1,
            "method" to "queryTransactionBlocks",
            "params" to listOf(
                mapOf(
                    "filter" to mapOf("Address" to addr),
                    "options" to mapOf(
                        "showBalanceChanges" to true,
                        "showTimestamp" to true
                    )
                ),
                limit
            )
        ))
    } catch (_: Exception) { null }

    /** متادیتای کوین (symbol/decimals) برای یک coinType */
    suspend fun coinMetadata(coinType: String): JsonObject? = try {
        api.rpc(mapOf(
            "jsonrpc" to "2.0", "id" to 1,
            "method" to "getCoinMetadata",
            "params" to listOf(coinType)
        ))
    } catch (_: Exception) { null }
}

// 🚀 Sprint 8 (S1): تابع pure — SUI همیشه ۹ اعشار دارد
// (مثلاً "1000000000" = 1.0 SUI) — تست‌پذیر و بدون وابستگی به UI
internal fun suiAmount(raw: String?): Double? {
    val v = raw?.toDoubleOrNull() ?: return null
    return v / 1_000_000_000.0
}
