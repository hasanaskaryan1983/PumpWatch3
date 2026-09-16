package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.math.pow

/**
 * TonAPI v2 — عمومی، بدون کلید (rate limit ~۱ درخواست/ثانیه).
 * مستندات: https://tonapi.io/api-v2
 *
 * P0-1: همهٔ فیلدها nullable اند؛ مصرف‌کننده باید حالت‌های
 * Ready / Empty / Failed را جداگانه مدیریت کند.
 *
 * Sprint 9 (I2a): baseUrl قابل تزریق است (فقط برای تست با MockWebServer).
 * در تولید همان tonapi.io پیش‌فرض است و هیچ رفتار دیگری عوض نشده.
 */

data class TonAddr(val address: String?, val name: String?, val is_scam: Boolean?)

data class TonJettonMeta(
    val symbol: String?,
    val name: String?,
    val decimals: Int?,
    val address: String?
)

data class TonAccount(val balance: Long?, val status: String?)

data class TonJettonBalance(val balance: String?, val jetton: TonJettonMeta?)
data class TonJettons(val jettons: List<TonJettonBalance>?)

data class TonTransfer(val amount: String?, val sender: TonAddr?, val receiver: TonAddr?)

data class TonJettonTransfer(
    val amount: String?,
    val sender: TonAddr?,
    val receiver: TonAddr?,
    val jetton: TonJettonMeta?
)

// کلیدهای JSON در TonAPI دقیقاً با حرف بزرگ شروع می‌شوند: "TonTransfer" / "JettonTransfer"
data class TonAction(
    val type: String?,
    val TonTransfer: TonTransfer?,
    val JettonTransfer: TonJettonTransfer?
)

data class TonEvent(val event_id: String?, val timestamp: Long?, val actions: List<TonAction>?)
data class TonEvents(val events: List<TonEvent>?, val next_from: Long?)

interface TonApi {
    @GET("v2/accounts/{addr}")
    suspend fun account(@Path("addr") addr: String): TonAccount

    @GET("v2/accounts/{addr}/jettons")
    suspend fun jettons(@Path("addr") addr: String): TonJettons

    @GET("v2/accounts/{addr}/events")
    suspend fun events(
        @Path("addr") addr: String,
        @Query("limit") limit: Int,
        @Query("before_lt") beforeLt: Long? = null
    ): TonEvents
}

object TonClient {
    const val DEFAULT_BASE_URL = "https://tonapi.io/"

    /** فقط برای تست (MockWebServer). تغییرش نمونهٔ Retrofit را باطل می‌کند. */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) {
            field = value
            cached = null
        }

    @Volatile
    private var cached: TonApi? = null

    val api: TonApi
        get() = cached ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TonApi::class.java)
            .also { cached = it }
}

// 🚀 Sprint 8 (T1): تابع pure برای تبدیل رشتهٔ raw به مقدار اعشاری
// (مثلاً "1500000000" با ۹ اعشار = 1.5 TON) — تست‌پذیر و بدون وابستگی به UI
internal fun tonAmount(raw: String?, decimals: Int): Double? {
    val v = raw?.toDoubleOrNull() ?: return null
    return v / 10.0.pow(decimals.coerceIn(0, 18).toDouble())
}
