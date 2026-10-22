package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

// ============================================
// 🟢 کد موجود (دست‌نخورده) — Binance legacy
// ============================================

data class AggTrade(
    @SerializedName("p") val price: String?,
    @SerializedName("q") val qty: String?,
    @SerializedName("T") val time: Long?,
    @SerializedName("m") val buyerIsMaker: Boolean?
)

interface WhaleBinanceApi {
    @GET("api/v3/aggTrades")
    suspend fun aggTrades(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int
    ): List<AggTrade>
}

object WhaleClient {
    val api: WhaleBinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WhaleBinanceApi::class.java)
    }
}

// ============================================
// 🆕 Commit 68: Suffix Parsing + مدیریت خطای هوشمند + کش DEX
// 🚀 Commit 82: مدل ترید GeckoTerminal اصلاح شد (W1)
// 🚀 Commit 83-fix: parsing ISO-8601 در GeckoDexProvider
// ============================================

data class AggTradeNormalized(
    val price: Double,
    val qty: Double,
    val time: Long,        // milliseconds UTC
    val buyerIsMaker: Boolean
) {
    val notional: Double get() = price * qty
}

interface WhaleProvider {
    val name: String
    suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized>
}

class TransientError(message: String, cause: Throwable? = null) : Exception(message, cause)
class TerminalError(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun numd(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

/**
 * 🚀 Commit 83: پارسر ISO-8601 thread-safe برای block_timestamp.
 *
 * ThreadLocal به‌جای SimpleDateFormat سراسری (SimpleDateFormat thread-safe نیست).
 * در providerها چند coroutine ممکن است همزمان parse کنند.
 */
internal val isoFmt: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

/**
 * 🚀 Commit 82/83: پارسر ISO-8601 برای block_timestamp.
 *
 * Public است تا از package‌های دیگر (مخصوصاً تست در `com.pumpwatch.app.app.data`
 * که ساختار دایرکتوری‌اش یک `app` اضافی دارد) قابل دسترسی باشد.
 *
 * @param s رشتهٔ ISO-8601 (مثلاً "2026-09-27T21:10:03Z")
 * @return epoch milliseconds، یا null اگر رشته نامعتبر یا null باشد
 */
fun parseIso8601(s: String?): Long? {
    if (s == null) return null
    val fmt = isoFmt.get() ?: return null
    return runCatching { fmt.parse(s)?.time }.getOrNull()
}

fun normalizeSymbol(symbol: String, exchange: String): String {
    val cleaned = symbol.uppercase(Locale.US).trim()
    if (cleaned.isEmpty()) return cleaned

    val quotes = listOf("USDT", "USDC", "USD", "EUR", "GBP", "BUSD", "FDUSD",
        "TUSD", "DAI", "ETH", "BTC", "BNB", "SOL")

    var base = cleaned
    var quote = "USDT"

    for (q in quotes) {
        if (cleaned.endsWith(q)) {
            val potentialBase = cleaned.substring(0, cleaned.length - q.length)
            val trimmedBase = potentialBase.trimEnd('-', '_')
            if (trimmedBase.isNotEmpty()) {
                base = trimmedBase
                quote = q
                break
            }
        }
    }

    return when (exchange) {
        "BINANCE" -> "$base$quote"
        "BYBIT" -> "$base$quote"
        "OKX" -> "$base-$quote"
        "GATE" -> "${base}_$quote"
        else -> cleaned
    }
}

private fun handleProviderError(e: Exception, providerName: String) {
    when (e) {
        is TerminalError -> {
            android.util.Log.e("WhaleProvider", "[$providerName] Terminal: ${e.message}")
        }
        is TransientError -> {
            android.util.Log.w("WhaleProvider", "[$providerName] Transient: ${e.message}")
        }
        else -> {
            val msg = e.message ?: ""
            val cls = e::class.java.simpleName
            val isTransient = msg.contains("429") ||
                msg.contains("timeout", true) ||
                msg.contains("Timeout", true) ||
                msg.contains("503") ||
                msg.contains("502") ||
                msg.contains("504") ||
                msg.contains("500") ||
                cls.contains("Timeout") ||
                cls.contains("Connect")
            try {
                if (isTransient) {
                    android.util.Log.w("WhaleProvider", "[$providerName] Likely Transient ($cls): ${e.message}")
                } else {
                    android.util.Log.e("WhaleProvider", "[$providerName] Error ($cls): ${e.message}", e)
                }
            } catch (_: Throwable) {
                println("WhaleProvider: [$providerName] $cls: ${e.message}")
            }
        }
    }
}

// -------- Binance --------

private interface BinanceRawApi {
    @GET("api/v3/aggTrades")
    suspend fun aggTrades(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int
    ): List<AggTrade>
}

object BinanceProvider : WhaleProvider {
    override val name: String = "BINANCE"
    private val api: BinanceRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BinanceRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val sym = normalizeSymbol(symbol, "BINANCE")
            api.aggTrades(sym, limit).mapNotNull { t ->
                val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.qty?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.time ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.buyerIsMaker ?: false)
            }
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

// -------- Bybit --------

private data class BybitResponse<T>(val retCode: Int?, val result: BybitResult<T>?)
private data class BybitResult<T>(val list: List<T>?)
private data class BybitTrade(
    val price: String?,
    val size: String?,
    val time: Long?,
    val side: String?
)

private interface BybitRawApi {
    @GET("v5/market/recent-trade")
    suspend fun recentTrades(
        @Query("category") category: String,
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int
    ): BybitResponse<BybitTrade>
}

object BybitProvider : WhaleProvider {
    override val name: String = "BYBIT"
    private val api: BybitRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bybit.com/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BybitRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val sym = normalizeSymbol(symbol, "BYBIT")
            val resp = api.recentTrades("spot", sym, limit)
            if (resp.retCode != 0) {
                throw TerminalError("Bybit API Error: retCode=${resp.retCode}")
            }
            val list = resp.result?.list
            list?.mapNotNull { t ->
                val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.size?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.time ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("Sell", true) == true)
            } ?: emptyList()
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

// -------- OKX --------

private data class OkxResponse(val code: String?, val data: List<OkxTrade>?)
private data class OkxTrade(
    val instId: String?,
    val px: String?,
    val sz: String?,
    val ts: String?,
    val side: String?
)

private interface OkxRawApi {
    @GET("api/v5/market/trades")
    suspend fun trades(
        @Query("instId") instId: String,
        @Query("limit") limit: Int
    ): OkxResponse
}

object OkxProvider : WhaleProvider {
    override val name: String = "OKX"
    private val api: OkxRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.okx.com/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OkxRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val inst = normalizeSymbol(symbol, "OKX")
            val resp = api.trades(inst, limit.coerceAtMost(100))
            if (resp.code != "0") {
                throw TerminalError("OKX API Error: code=${resp.code}")
            }
            resp.data?.mapNotNull { t ->
                val p = t.px?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.sz?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.ts?.toLongOrNull() ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("sell", true) == true)
            } ?: emptyList()
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

// -------- Gate --------

private data class GateTrade(
    val price: String?,
    val amount: String?,
    val create_time_ms: String?,
    val side: String?
)

private interface GateRawApi {
    @GET("api/v4/spot/trades")
    suspend fun trades(
        @Query("currency_pair") pair: String,
        @Query("limit") limit: Int
    ): List<GateTrade>
}

object GateProvider : WhaleProvider {
    override val name: String = "GATE"
    private val api: GateRawApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.gateio.ws/")
            .client(ThrottledHttp.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GateRawApi::class.java)
    }

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            val pair = normalizeSymbol(symbol, "GATE")
            api.trades(pair, limit).mapNotNull { t ->
                val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                val q = t.amount?.toDoubleOrNull() ?: return@mapNotNull null
                val ts = t.create_time_ms?.toDoubleOrNull()?.toLong() ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("sell", true) == true)
            }
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

// -------- 🟢 کف بدون‌مجوز: GeckoTerminal DEX (با کش بهینه) --------

private val poolCache = ConcurrentHashMap<String, Pair<String, String>>()

/**
 * 🚀 Commit 82 + 83-fix: GeckoDexProvider با مدل دقیق مطابق مستندات رسمی.
 *
 * اصلاحات نسبت به نسخهٔ اولیه:
 *   1. `a.kind` برای جهت معامله (به‌جای `a.type` که "trade" ثابت بود)
 *   2. `a.block_timestamp` با `parseIso8601` (ISO-8601 → epoch ms)
 *   3. `price_to_in_usd` یا `price_from_in_usd` برای قیمت (به‌جای `price_in_usd`)
 *   4. `volume_in_usd` رشته است → `toDoubleOrNull` مستقیم
 *
 * نتیجه: GECKO_DEX دیگر همیشه خالی برنمی‌گردد.
 */
object GeckoDexProvider : WhaleProvider {
    override val name: String = "GECKO_DEX"

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            var networkAndPool = poolCache[symbol]

            if (networkAndPool == null) {
                val pools = GeckoTerminal.api.searchPools(symbol).data?.filter { it.attributes != null } ?: emptyList()
                val bestPool = pools.maxByOrNull { it.attributes?.volume?.h24 ?: 0.0 }

                if (bestPool == null) {
                    throw TerminalError("No pool found for $symbol on GeckoTerminal")
                }

                val net = bestPool.relationships?.network?.data?.id
                    ?: throw TerminalError("Network ID missing")
                val poolAddr = bestPool.id?.substringAfter('_')
                    ?: throw TerminalError("Pool Address missing")

                networkAndPool = Pair(net, poolAddr)
                poolCache[symbol] = networkAndPool
            }

            val (net, poolAddr) = networkAndPool

            val trades = GeckoPrice.api.poolTrades(net, poolAddr).data ?: emptyList()

            trades.mapNotNull { t ->
                val a = t.attributes ?: return@mapNotNull null

                // 🚀 Commit 82: volume_in_usd رشته است
                val vol = a.volume_in_usd?.toDoubleOrNull() ?: return@mapNotNull null

                // 🚀 Commit 83-fix: block_timestamp ISO-8601 است
                val tsMs = parseIso8601(a.block_timestamp) ?: return@mapNotNull null

                // 🚀 Commit 82: kind = "buy" | "sell" (نه type که "trade" ثابت است)
                val isSell = a.kind.equals("sell", ignoreCase = true)

                // 🚀 Commit 82: قیمت از price_to_in_usd یا price_from_in_usd
                val px = a.price_to_in_usd?.toDoubleOrNull()
                    ?: a.price_from_in_usd?.toDoubleOrNull()
                    ?: return@mapNotNull null

                if (px <= 0) return@mapNotNull null

                // محاسبه حجم توکن از حجم دلاری
                val qty = vol / px

                AggTradeNormalized(px, qty, tsMs, isSell)
            }.take(limit)
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

object WhaleProviders {
    val all: List<WhaleProvider> = listOf(
        BinanceProvider,
        BybitProvider,
        OkxProvider,
        GateProvider,
        GeckoDexProvider
    )
}
