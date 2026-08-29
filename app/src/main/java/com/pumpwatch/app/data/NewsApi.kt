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

object NewsClient {
    val api: NewsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://min-api.cryptocompare.com/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApi::class.java)
    }
}
