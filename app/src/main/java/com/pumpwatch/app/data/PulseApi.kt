package com.pumpwatch.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale
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
// ============================================

/**
 * مدل واحد داخلی — همهٔ منابع خروجی خود را به این فرمت تبدیل می‌کنند.
 * buyerIsMaker = true → فروش تهاجمی (SELL)
 * buyerIsMaker = false → خرید تهاجمی (BUY)
 */
data class AggTradeNormalized(
    val price: Double,
    val qty: Double,
    val time: Long,        // milliseconds UTC
    val buyerIsMaker: Boolean
) {
    val notional: Double get() = price * qty
}

/**
 * abstraction یکسان برای همهٔ منابع (CEX و DEX).
 */
interface WhaleProvider {
    val name: String
    suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized>
}

// -------- خطاهای طبقه‌بندی‌شده (CONSTITUTION بند ۷) --------

/**
 * خطای موقتی: قابل تلاش مجدد (شبکه، تایم‌اوت، 5xx، 429).
 * لایهٔ بالاتر می‌تواند Exponential Backoff اعمال کند.
 */
class TransientError(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * خطای قطعی: غیرقابل تلاش مجدد (نماد اشتباه، 4xx، ساختار نامعتبر).
 * لایهٔ بالاتر باید provider بعدی را امتحان کند، نه همین provider را.
 */
class TerminalError(message: String, cause: Throwable? = null) : Exception(message, cause)

// -------- Shared helpers (top-level) --------

/**
 * تبدیل مقدار خام (Number یا String یا هر نوع Gson) به Double.
 * فیلدهای attributes در GeckoTerminal از نوع خام (Any?) هستند،
 * پس toDoubleOrNull مستقیم رویشان کار نمی‌کند.
 */
private fun numd(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

/**
 * 🚀 Commit 68 (بند ۷ CONSTITUTION): نرمال‌سازی نماد مبتنی بر Suffix Parsing.
 *
 * باگ قبلی: `replace("USDT","").replace("USD","")` → BTC--USDT می‌سازد
 *   (چون USDT اول جایگزین می‌شود، بعد USD از داخل USDT باقی‌مانده دوباره replace می‌شود)
 *
 * راه‌حل: پسوند را از **انتهای رشته** جدا کن، نه با replace عمومی.
 *   این کار ساختار داخلی نماد (مثل STETH-ETH) را دست‌نخورده نگه می‌دارد.
 *
 * مثال‌ها:
 *   BTCUSDT      → Binance: BTCUSDT، OKX: BTC-USDT
 *   BTC-USDT     → Binance: BTCUSDT، OKX: BTC-USDT
 *   BTC_USDT     → Binance: BTCUSDT، Gate: BTC_USDT
 *   1000PEPEUSDT → Binance: 1000PEPEUSDT (نه 1000PEPE)
 *   PEPEUSDT     → Binance: PEPEUSDT
 */
fun normalizeSymbol(symbol: String, exchange: String): String {
    val cleaned = symbol.uppercase(Locale.US).trim()
    if (cleaned.isEmpty()) return cleaned

    // لیست پسوندهای رایج به ترتیب طول (طولانی‌تر اولویت دارد)
    // این مهم است: USDT قبل از USD چک شود تا USDT به US+DT تجزیه نشود
    val quotes = listOf("USDT", "USDC", "USD", "EUR", "GBP", "BUSD", "FDUSD",
        "TUSD", "DAI", "ETH", "BTC", "BNB", "SOL")

    var base = cleaned
    var quote = "USDT" // پیش‌فرض

    for (q in quotes) {
        if (cleaned.endsWith(q)) {
            val potentialBase = cleaned.substring(0, cleaned.length - q.length)
            // حذف جداکننده‌های احتمالی انتهای base (مثل - یا _)
            val trimmedBase = potentialBase.trimEnd('-', '_')
            if (trimmedBase.isNotEmpty()) {
                base = trimmedBase
                quote = q
                break
            }
        }
    }

    return when (exchange) {
        "BINANCE" -> "$base$quote"        // BTCUSDT
        "BYBIT" -> "$base$quote"          // BTCUSDT
        "OKX" -> "$base-$quote"           // BTC-USDT
        "GATE" -> "${base}_$quote"        // BTC_USDT
        else -> cleaned
    }
}

/**
 * 🚀 Commit 68: تابع کمکی برای تشخیص و لاگ‌گیری خطاها.
 * خطاهای موقتی (Transient) → WARNING (قابل retry)
 * خطاهای قطعی (Terminal) → ERROR (باید provider عوض شود)
 */
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
            if (isTransient) {
                android.util.Log.w("WhaleProvider", "[$providerName] Likely Transient ($cls): ${e.message}")
            } else {
                android.util.Log.e("WhaleProvider", "[$providerName] Error ($cls): ${e.message}", e)
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
    val side: String?   // "Buy" or "Sell"
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
    val ts: String?,   // milliseconds as string
    val side: String?  // "buy" or "sell"
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
            val resp = api.trades(inst, limit.coerceAtMost(100)) // OKX limit max 100
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
    val create_time_ms: Long?,
    val side: String?   // "buy" or "sell"
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
                val ts = t.create_time_ms ?: return@mapNotNull null
                AggTradeNormalized(p, q, ts, t.side?.equals("sell", true) == true)
            }
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

// -------- 🟢 کف بدون‌مجوز: GeckoTerminal DEX (با کش بهینه) --------

/**
 * 🚀 Commit 68: کش ساده برای نتایج جستجوی استخرها.
 * کاهش درخواست‌های متوالی HTTP → کمتر با 429 برخورد می‌کنیم.
 * آدرس استخرهای اصلی برای یک نماد به ندرت تغییر می‌کند.
 */
private val poolCache = ConcurrentHashMap<String, Pair<String, String>>()

object GeckoDexProvider : WhaleProvider {
    override val name: String = "GECKO_DEX"

    override suspend fun fetchNormalized(symbol: String, limit: Int): List<AggTradeNormalized> {
        return try {
            // ۱. بررسی کش
            var networkAndPool = poolCache[symbol]

            // ۲. اگر در کش نبود، جستجو کن
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

            // ۳. دریافت معاملات
            val trades = GeckoPrice.api.poolTrades(net, poolAddr).data ?: emptyList()

            trades.mapNotNull { t ->
                val a = t.attributes ?: return@mapNotNull null
                val vol = numd(a.volume_in_usd) ?: return@mapNotNull null
                val px = numd(a.price_in_usd) ?: numd(a.price) ?: return@mapNotNull null

                // ✅ محافظت در برابر تقسیم بر صفر
                if (px <= 0) return@mapNotNull null

                // ✅ محاسبه حجم توکن: qty = volume_usd / price_usd
                val qty = vol / px

                val tsSec = numd(a.block_timestamp) ?: return@mapNotNull null
                val isSell = a.type?.toString()?.equals("sell", true) == true

                AggTradeNormalized(px, qty, tsSec.toLong() * 1000L, isSell)
            }.take(limit)
        } catch (e: Exception) {
            handleProviderError(e, name)
            emptyList()
        }
    }
}

// -------- زنجیرهٔ fallback --------

/**
 * ترتیب: CEXها اول (دقیق‌ترین برای نهنگ)، و در انتها کف بدون‌مجوز آن‌چین
 * که تضمین می‌کند قابلیت هرگز کاملاً خالی نشود.
 */
object WhaleProviders {
    val all: List<WhaleProvider> = listOf(
        BinanceProvider,
        BybitProvider,
        OkxProvider,
        GateProvider,
        GeckoDexProvider   // 🟢 کف تضمینی — بدون کلید، بدون مسدودسازی جغرافیایی
    )
}
