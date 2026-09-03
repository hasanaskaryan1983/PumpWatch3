package com.pumpwatch.app.data

import com.google.gson.JsonArray
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class BinanceCandle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

// ---------- پاسخ Bybit ----------
data class BybitKlineResponse(
    val retCode: Int,
    val result: BybitKlineResult?
)

data class BybitKlineResult(
    val list: List<List<String>>?
)

interface BybitApi {
    @GET("v5/market/kline")
    suspend fun kline(
        @Query("category") category: String,
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): BybitKlineResponse
}

object BybitClient {
    val api: BybitApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bybit.com/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BybitApi::class.java)
    }
}

// ---------- همان BinanceClient قبلی، ولی با موتور Bybit ----------
object BinanceClient {

    private fun toBybitInterval(interval: String): String = when (interval) {
        "1m" -> "1"
        "3m" -> "3"
        "5m" -> "5"
        "15m" -> "15"
        "30m" -> "30"
        "1h" -> "60"
        "2h" -> "120"
        "4h" -> "240"
        "6h" -> "360"
        "12h" -> "720"
        "1d" -> "D"
        "1w" -> "W"
        else -> "60"
    }

    // همین‌جوری نگه داشتیم تا هیچ فایلی خراب نشه: BinanceClient.api.klines(...)
    val api: KlineCompat = KlineCompat

    object KlineCompat {
        suspend fun klines(symbol: String, interval: String, limit: Int): List<JsonArray> {
            val resp = BybitClient.api.kline("spot", symbol, toBybitInterval(interval), limit)
            val list = resp.result?.list ?: return emptyList()
            // Bybit از جدید به قدیم می‌ده → برعکس می‌کنیم
            return list.reversed().map { a ->
                JsonArray().apply {
                    add(a.getOrNull(0)?.toLongOrNull() ?: 0L)
                    add(a.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
                    add(a.getOrNull(2)?.toDoubleOrNull() ?: 0.0)
                    add(a.getOrNull(3)?.toDoubleOrNull() ?: 0.0)
                    add(a.getOrNull(4)?.toDoubleOrNull() ?: 0.0)
                    add(a.getOrNull(5)?.toDoubleOrNull() ?: 0.0)
                }
            }
        }
    }

    suspend fun candles(symbol: String, tf: String): List<BinanceCandle> {
        val pair = symbol.uppercase() + "USDT"
        val resp = BybitClient.api.kline("spot", pair, toBybitInterval(tf), 200)
        val list = resp.result?.list ?: return emptyList()
        return list.reversed().map { a ->
            BinanceCandle(
                time = a.getOrNull(0)?.toLongOrNull() ?: 0L,
                open = a.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
                high = a.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
                low = a.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
                close = a.getOrNull(4)?.toDoubleOrNull() ?: 0.0,
                volume = a.getOrNull(5)?.toDoubleOrNull() ?: 0.0
            )
        }
    }
}
