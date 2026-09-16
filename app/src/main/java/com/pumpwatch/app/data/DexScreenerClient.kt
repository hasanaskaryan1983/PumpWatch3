package com.pumpwatch.app.data

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * DexScreener API — رایگان، بدون کلید.
 * مستندات: https://docs.dexscreener.com/api/reference
 *
 * نقش: آخرین لایهٔ قیمت‌گذاری با «آدرس کانترکت» (نه نماد).
 *
 * Sprint 10 (hotfix): تایم‌اوت صریح — اگر این endpoint روی شبکهٔ کاربر
 * کند یا مسدود باشد، نباید کل اسکن کیف را گروگان بگیرد؛
 * لایه‌های قبلی قیمت معتبر می‌مانند و ردیف‌های باقی‌مانده
 * صادقانه «❓ نامشخص» می‌مانند (P0-3).
 */

data class DexToken(
    val address: String?,
    val name: String?,
    val symbol: String?
)

data class DexLiquidity(val usd: Double?)

data class DexPair(
    val chainId: String?,
    val baseToken: DexToken?,
    val quoteToken: DexToken?,
    val priceUsd: String?,
    val liquidity: DexLiquidity?
)

data class DexResponse(
    val schemaVersion: String?,
    val pairs: List<DexPair>?
)

interface DexScreenerApi {
    @GET("latest/dex/tokens/{addr}")
    suspend fun tokens(@Path("addr") addr: String): DexResponse
}

object DexScreenerClient {
    const val DEFAULT_BASE_URL = "https://api.dexscreener.com/"

    /** فقط برای تست (MockWebServer). تغییرش نمونهٔ Retrofit را باطل می‌کند. */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) {
            field = value
            cached = null
        }

    @Volatile
    private var cached: DexScreenerApi? = null

    val api: DexScreenerApi
        get() = cached ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .callTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DexScreenerApi::class.java)
            .also { cached = it }
}

/**
 * Pure: از میان همهٔ استخرهای برگشتی، معتبرترین قیمت را انتخاب می‌کند:
 * - فقط زنجیرهٔ درخواست‌شده (chainId)
 * - فقط استخرهایی که آدرس ما «توکن پایه» آن‌هاست (نه quote)
 * - فقط استخرهای با نقدینگی مثبت
 * - پرنقدینگی‌ترین = منبع قیمت
 *
 * هیچ قیمتی ساخته نمی‌شود: اگر شرط‌ها برقرار نبود → null (P0-3/P0-1).
 */
internal fun bestPriceUsd(pairs: List<DexPair>?, chainId: String, address: String): Double? {
    val candidates = pairs?.filter { p ->
        p.chainId.equals(chainId, true) &&
        p.baseToken?.address.equals(address, true) &&
        (p.liquidity?.usd ?: 0.0) > 0.0
    } ?: return null
    val best = candidates.maxByOrNull { it.liquidity?.usd ?: 0.0 } ?: return null
    return best.priceUsd?.toDoubleOrNull()?.takeIf { it > 0.0 }
}
