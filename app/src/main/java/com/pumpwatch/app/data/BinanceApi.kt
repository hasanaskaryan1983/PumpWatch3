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

// ================= Bybit =================
data class BybitKlineResponse(val retCode: Int, val result: BybitKlineResult?)
data class BybitKlineResult(val list: List<List<String>>?)

interface BybitApi {
    @GET("v5/market/kline")
    suspend fun kline(
        @Query("category") category: String,
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): BybitKlineResponse
}

// ================= OKX =================
data class OkxKlineResponse(val code: String, val data: List<List<String>>?)

interface OkxApi {
    @GET("api/v5/market/candles")
    suspend fun candles(
        @Query("instId") instId: String,
        @Query("bar") bar: String,
        @Query("limit") limit: Int
    ): OkxKlineResponse
}

// ================= Gate =================
interface GateApi {
    @GET("api/v4/spot/candlesticks")
    suspend fun candlesticks(
        @Query("currency_pair") pair: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<String>>
}

object MultiExchange {

    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private fun <T> create(baseUrl: String, cls: Class<T>): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(cls)

    val bybit: BybitApi by lazy { create("https://api.bybit.com/", BybitApi::class.java) }
    val okx: OkxApi by lazy { create("https://www.okx.com/", OkxApi::class.java) }
    val gate: GateApi by lazy { create("https://api.gateio.ws/", GateApi::class.java) }

    suspend fun fetchKlines(symbolUpper: String, interval: String, limit: Int): List<BinanceCandle> {
        // 1) Bybit
        try {
            val r = bybit.kline("spot", "${symbolUpper}USDT", bybitInterval(interval), limit)
            val list = r.result?.list
            if (!list.isNullOrEmpty()) {
                val out = list.reversed().mapNotNull { a ->
                    candle(a, 0, 1, 2, 3, 4, 5, true)
                }
                if (out.isNotEmpty()) return out
            }
        } catch (_: Exception) { }

        // 2) OKX
        try {
            val r = okx.candles("${symbolUpper}-USDT", okxBar(interval), limit)
            val list = r.data
            if (!list.isNullOrEmpty()) {
                val out = list.reversed().mapNotNull { a ->
                    candle(a, 0, 1, 2, 3, 4, 5, true)
                }
                if (out.isNotEmpty()) return out
            }
        } catch (_: Exception) { }

        // 3) Gate
        try {
            val list = gate.candlesticks("${symbolUpper}_USDT", interval, limit)
            if (list.isNotEmpty()) {
                val out = list.mapNotNull { a ->
                    candle(a, 0, 2, 3, 4, 5, 6, false)
                }
                if (out.isNotEmpty()) return out
            }
        } catch (_: Exception) { }

        return emptyList()
    }

    private fun candle(
        a: List<String>,
        t: Int,
        o: Int,
        h: Int,
        l: Int,
        c: Int,
        v: Int,
        timeMs: Boolean
    ): BinanceCandle? {
        val close = a.getOrNull(c)?.toDoubleOrNull() ?: return null
        val time = a.getOrNull(t)?.toDoubleOrNull() ?: return null
        return BinanceCandle(
            time = if (timeMs) time.toLong() else (time * 1000).toLong(),
            open = a.getOrNull(o)?.toDoubleOrNull() ?: 0.0,
            high = a.getOrNull(h)?.toDoubleOrNull() ?: 0.0,
            low = a.getOrNull(l)?.toDoubleOrNull() ?: 0.0,
            close = close,
            volume = a.getOrNull(v)?.toDoubleOrNull() ?: 0.0
        )
    }

    private fun bybitInterval(i: String): String = when (i) {
        "1m" -> "1"
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

    private fun okxBar(i: String): String = when (i) {
        "1m" -> "1m"
        "5m" -> "5m"
        "15m" -> "15m"
        "30m" -> "30m"
        "1h" -> "1H"
        "2h" -> "2H"
        "4h" -> "4H"
        "6h" -> "6H"
        "12h" -> "12H"
        "1d" -> "1D"
        "1w" -> "1W"
        else -> "1H"
    }
}

// ---------- همان BinanceClient قبلی، با موتور ۳ صرافی ----------
object BinanceClient {

    val api: KlineCompat = KlineCompat

    object KlineCompat {
        suspend fun klines(symbol: String, interval: String, limit: Int): List<JsonArray> {
            val sym = symbol.uppercase().removeSuffix("USDT")
            return MultiExchange.fetchKlines(sym, interval, limit).map { c ->
                JsonArray().apply {
                    add(c.time)
                    add(c.open)
                    add(c.high)
                    add(c.low)
                    add(c.close)
                    add(c.volume)
                }
            }
        }
    }

    suspend fun candles(symbol: String, tf: String): List<BinanceCandle> {
        val sym = symbol.uppercase().removeSuffix("USDT")
        return MultiExchange.fetchKlines(sym, tf, 200)
    }
}
