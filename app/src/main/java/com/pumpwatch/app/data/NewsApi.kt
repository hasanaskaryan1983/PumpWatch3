package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ---------- اخبار کریپتو (CryptoCompare — بدون کلید) ----------

data class NewsItem(
    val id: String?,
    val title: String?,
    val url: String?,
    @SerializedName("published_on") val publishedOn: Long?,
    val imageurl: String?,
    val source: String?,
    val categories: String?
)

data class NewsResponse(
    @SerializedName("Data") val data: List<NewsItem>?
)

interface NewsApi {

    @GET("data/v2/news/")
    suspend fun news(
        @Query("categories") categories: String?,
        @Query("lang") lang: String = "EN"
    ): NewsResponse
}

/**
 * 🚀 Commit 75 (فاز ۲ — تست‌پذیری): baseUrl قابل تزریق.
 *
 * قبلاً: baseUrl هاردکد داخل `by lazy` → بعد از ساخت قابل تغییر نبود
 *   → تست MockWebServer غیرممکن.
 * حالا: همان الگوی اثبات‌شدهٔ SuiClient/TonClient (Sprint 9 I2a/I2b):
 *   - `@Volatile var baseUrl` با setter که cached را invalidate می‌کند
 *   - getter با cache (ساخت Retrofit فقط یک‌بار تا تغییر baseUrl)
 * در تولید همان min-api.cryptocompare.com پیش‌فرض است.
 */
object NewsClient {
    const val DEFAULT_BASE_URL = "https://min-api.cryptocompare.com/"

    /** فقط برای تست (MockWebServer). تغییرش نمونهٔ Retrofit را باطل می‌کند. */
    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) {
            field = value
            cached = null
        }

    @Volatile
    private var cached: NewsApi? = null

    val api: NewsApi
        get() = cached ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApi::class.java)
            .also { cached = it }
}
