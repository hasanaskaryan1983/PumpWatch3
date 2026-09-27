package com.pumpwatch.app.data

import android.util.Log
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
 * Sprint 9 (I2a): baseUrl قابل تزریق است (فقط برای تست با MockWebServer).
 * در تولید همان fullnode.mainnet.sui.io پیش‌فرض است.
 *
 * 🚀 Commit 74 (فاز ۲ — بند ۵ CONSTITUTION): log explicit + طبقه‌بندی خطا.
 * قبلاً هر سه متد silent catch داشتند → caller نمی‌فهمید شبکه قطع است
 * یا داده‌ای وجود ندارد. حالا: log warning/error + طبقه‌بندی transient/terminal.
 */

interface SuiApi {
    @POST("/")
    suspend fun rpc(@Body body: Map<String, @JvmSuppressWildcards Any?>): JsonObject
}

object SuiClient {
    private const val TAG = "SuiClient"
    const val MAINNET = "https://fullnode.mainnet.sui.io/"

    /** فقط برای تست (MockWebServer). تغییرش نمونهٔ Retrofit را باطل می‌کند. */
    @Volatile
    var baseUrl: String = MAINNET
        set(value) {
            field = value
            cached = null
        }

    @Volatile
    private var cached: SuiApi? = null

    val api: SuiApi
        get() = cached ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SuiApi::class.java)
            .also { cached = it }

    /**
     * 🚀 Commit 74: طبقه‌بندی خطا (مثل WhaleApi.kt).
     * Transient (شبکه/timeout/5xx/429) → Log.w
     * Terminal (باگ/parse/ورودی نامعتبر) → Log.e
     *
     * 🚀 Commit 74-fix: try-catch روی Log calls — در JVM unit test
     * (بدون Robolectric) android.util.Log در دسترس نیست، پس به
     * println fallback می‌کنیم.
     */
    private fun handleError(method: String, e: Exception) {
        val msg = e.message ?: ""
        val cls = e::class.java.simpleName
        val isTransient = msg.contains("429") ||
            msg.contains("timeout", true) ||
            msg.contains("503") ||
            msg.contains("502") ||
            msg.contains("504") ||
            msg.contains("500") ||
            msg.contains("network", true) ||
            cls.contains("Timeout", true) ||
            cls.contains("Connect", true) ||
            cls.contains("Socket", true) ||
            cls.contains("UnknownHost", true)

        val logMsg = "[$method] $cls: $msg"
        try {
            if (isTransient) {
                Log.w(TAG, logMsg)
            } else {
                Log.e(TAG, logMsg, e)
            }
        } catch (_: Throwable) {
            // Log در JVM unit test در دسترس نیست — fallback به println
            println("$TAG: $logMsg")
        }
    }

    /** موجودی همهٔ کوین‌ها (SUI خام + توکن‌ها) برای یک آدرس */
    suspend fun balances(addr: String): JsonObject? = try {
        api.rpc(mapOf(
            "jsonrpc" to "2.0", "id" to 1,
            "method" to "getAllBalances",
            "params" to listOf(addr)
        ))
    } catch (e: Exception) {
        handleError("balances($addr)", e)
        null
    }

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
    } catch (e: Exception) {
        handleError("txBlocks($addr)", e)
        null
    }

    /** متادیتای کوین (symbol/decimals) برای یک coinType */
    suspend fun coinMetadata(coinType: String): JsonObject? = try {
        api.rpc(mapOf(
            "jsonrpc" to "2.0", "id" to 1,
            "method" to "getCoinMetadata",
            "params" to listOf(coinType)
        ))
    } catch (e: Exception) {
        handleError("coinMetadata($coinType)", e)
        null
    }
}

// 🚀 Sprint 8 (S1): تابع pure — SUI همیشه ۹ اعشار دارد
// (مثلاً "1000000000" = 1.0 SUI) — تست‌پذیر و بدون وابستگی به UI
internal fun suiAmount(raw: String?): Double? {
    val v = raw?.toDoubleOrNull() ?: return null
    return v / 1_000_000_000.0
}
