package com.pumpwatch.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * DexScreener API — رایگان، بدون کلید.
 * مستندات: https://docs.dexscreener.com/api/reference
 *
 * نقش در اپ: آخرین لایهٔ قیمت‌گذاری برای توکن‌هایی که نه در CoinGecko
 * لیست‌اند و نه GeckoTerminal استخرشان را برمی‌گرداند. جست‌وجو با
 * «آدرس کانترکت» انجام می‌شود، نه نماد → بدون ابهام نمادهای مشابه.
 *
 * P0-1: پاسخ‌ها nullable؛ انتخاب استخر معتبر بر عهدهٔ bestPriceUsd است.
 * Sprint 10 (C2): فقط لایهٔ داده — Wiring در C3/C4.
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
