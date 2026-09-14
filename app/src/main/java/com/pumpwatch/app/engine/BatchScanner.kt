package com.pumpwatch.app.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import com.pumpwatch.app.data.KlineCache
import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.data.ScanMarket
import kotlin.math.abs

/**
 * BatchScanner Pro — نسخهٔ یکپارچه (قدم: حذف تضاد موتورهای سیگنال)
 * تغییرات:
 * - تحلیل فقط با UnifiedSignalEngine (منبع واحد حقیقت)
 * - منبع دادهٔ اصلی: Binance klines 1h (همان منبع QuickScanner)
 * - مسیر fallback: نمودار CoinGecko + buildCandlesChecked (برای جفت‌های غیرBinance)
 * - خروجی همچنان List<SignalResult> است تا همهٔ فراخوان‌ها (MonitorWorker, PicksStore) سالم بمانند
 *
 * P0-6: استانداردسازی Candle.time = زمان بسته شدن کندل (نه باز شدن)
 * 🚀 P1-1: کندل‌های Binance از KlineCache (TTL=60s) خوانده می‌شوند
 */
object BatchScanner {

    private const val TAG = "BatchScanner"
    private val STABLES = setOf("USDT", "USDC", "DAI", "FDUSD", "TUSD", "BUSD", "TETHER", "USDCOIN")

    suspend fun scan(
        mode: String,
        params: SignalParams = SignalParams(),
        limit: Int = 100
    ): List<SignalResult> {
        return try {
            Log.d(TAG, "🚀 scan start: $mode")
            val markets = loadMarkets(mode)
            val fundingMap = if (mode == "FUT") loadFunding() else emptyMap()

            val candidates = markets
                .filter { m ->
                    val sym = m.symbol.uppercase().replace("-", "")
                    val isStable = STABLES.contains(sym) ||
                            sym.matches(Regex("^(USDT|USDC|DAI|FDUSD|TUSD|BUSD)(USD|EUR|GBP)?$"))

                    !isStable && (m.volume ?: 0.0) > 500_000.0
                }
                .sortedByDescending { quickScore(it) }
                .take(limit)

            Log.d(TAG, "🎯 candidates: ${candidates.size}")

            val results = mutableListOf<SignalResult>()
            candidates.chunked(5).forEach { chunk ->
                val part = coroutineScope {
                    chunk.map { m ->
                        async(Dispatchers.IO) {
                            try {
                                analyze(m, mode, fundingMap[m.symbol.uppercase()], params)
                            } catch (e: Exception) {
                                Log.w(TAG, "skip ${m.symbol}: ${e.message}")
                                null
                            }
                        }
                    }.awaitAll()
                }
                results.addAll(part.filterNotNull())
                delay(300)
            }

            Log.d(TAG, "✅ scan done: ${results.size}")
            results.sortedByDescending { it.score }
        } catch (e: Exception) {
            Log.e(TAG, "❌ scan failed: ${e.message}")
            emptyList()
        }
    }

    // ---------- بارگذاری بازارها ----------

    private suspend fun loadMarkets(mode: String): List<ScanMarket> {
        val out = mutableListOf<ScanMarket>()
        val pages = if (mode == "FUT") 1 else 4
        for (p in 1..pages) {
            try {
                out.addAll(ScanClient.api.markets(perPage = 250, page = p))
            } catch (e: Exception) {
                Log.w(TAG, "page $p failed: ${e.message}")
            }
            if (p < pages) delay(500)
        }
        return out
    }

    private suspend fun loadFunding(): Map<String, Double> {
        return try {
            ScanClient.api.derivatives()
                .filter { !it.base.isNullOrBlank() && it.fundingRate != null }
                .associate { it.base!!.uppercase() to it.fundingRate!! }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun quickScore(m: ScanMarket): Double {
        val vol = m.volume ?: 0.0
        val ch = abs(m.change24h ?: 0.0)
        return vol * 0.0000001 + ch * 10
    }

    // ---------- تحلیل کامل یک ارز: فقط UnifiedSignalEngine ----------

    private suspend fun analyze(
        m: ScanMarket,
        mode: String,
        funding: Double?,
        params: SignalParams
    ): SignalResult? {
        // ۱) منبع اصلی و یکپارچه: Binance klines 1h — از cache مرکزی (P1-1)
        val binanceCandles: List<Candle>? = try {
            val klines = KlineCache.klines("${m.symbol.uppercase()}USDT", "1h", 300)
            if (klines.size >= 60) {
                klines.map { k ->
                    Candle(
                        // P0-6: k[6] = close time رسمی Binance (نه k[0] که open time است)
                        time = k[6].asLong,
                        open = k[1].asDouble,
                        high = k[2].asDouble,
                        low = k[3].asDouble,
                        close = k[4].asDouble,
                        volume = k[5].asDouble
                    )
                }
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "${m.symbol}: Binance klines unavailable (${e.message}) — fallback to CoinGecko")
            null
        }

        // ۲) مسیر fallback: نمودار CoinGecko + buildCandlesChecked (تابع pure و تست‌شده)
        val candles: List<Candle> = binanceCandles ?: run {
            val chart = ScanClient.api.chart(m.id, days = 30)
            val build = buildCandlesChecked(chart.prices, chart.volumes)
            if (build.gapCount > 0) {
                Log.w(TAG, "${m.symbol}: ${build.gapCount} gap ساعتی در دادهٔ CoinGecko")
            }
            build.candles
        }

        if (candles.size < 60) {
            Log.w(TAG, "${m.symbol}: ${candles.size} candles < 60")
            return null
        }

        // ۳) موتور واحد حقیقت
        val unified = UnifiedSignalEngine.analyze(
            coinId = m.id,
            symbol = m.symbol,
            name = m.name,
            candles1h = candles,
            mode = mode,
            funding = funding,
            params = toUnifiedParams(params)
        )

        return unified?.toSignalResult()
    }

    // ---------- نگاشت انواع برای سازگاری با فراخوان‌های موجود ----------

    private fun toUnifiedParams(p: SignalParams): UnifiedSignalParams = UnifiedSignalParams(
        rsiPeriod = p.rsiPeriod,
        adxMin = p.adxMin,
        volumeMin = p.volumeMin,
        breakoutLookback = p.breakoutLookback,
        minScore = p.minScore,
        goldenScore = p.goldenScore,
        atrMult = p.atrMult,
        rr = p.rr
    )

    private fun UnifiedSignalResult.toSignalResult(): SignalResult = SignalResult(
        coinId = coinId,
        symbol = symbol,
        name = name,
        price = price,
        mode = mode,
        side = side,
        score = score,
        golden = golden,
        mtfAligned = mtfAligned,
        mtfTrend = mtfTrend,
        adx = adx,
        rsi = rsi,
        volumeRatio = volumeRatio,
        funding = funding,
        entry = entry,
        stopLoss = stopLoss,
        target1 = target1,
        target2 = target2,
        reasons = reasons
    )

    // ---------- ساخت کندل ساعتی: تابع pure و قابل تست ----------

    internal data class CandleBuild(
        val candles: List<Candle>,
        val gapCount: Int,
        val droppedTrailingIncomplete: Boolean
    )

    internal fun buildCandlesChecked(
        prices: List<List<Double>>,
        volumes: List<List<Double>>?
    ): CandleBuild {
        if (prices.size < 2) return CandleBuild(emptyList(), 0, false)
        val hourMs = 3_600_000L

        val pts = prices.filter { it.size >= 2 }.map { it[0] to it[1] }
        if (pts.size < 2) return CandleBuild(emptyList(), 0, false)

        val unitMs = if (pts.maxOf { it.first } < 100_000_000_000.0) 1000.0 else 1.0

        val sorted = pts.sortedBy { it.first }

        val volByTs = volumes
            ?.filter { it.size >= 2 }
            ?.associate { (it[0] * unitMs).toLong() to it[1] }

        val firstTs = (sorted.first().first * unitMs).toLong()
        var bucketStart = (firstTs / hourMs) * hourMs
        var open = sorted.first().second
        var high = open
        var low = open
        var lastClose = open
        var vol = 0.0
        var lastVol = volByTs?.get(firstTs) ?: 0.0

        val out = mutableListOf<Candle>()
        var gapCount = 0

        for (i in 1 until sorted.size) {
            val tsMs = (sorted[i].first * unitMs).toLong()
            val p = sorted[i].second
            val bStart = (tsMs / hourMs) * hourMs

            if (bStart != bucketStart) {
                // P0-6: time = پایان bucket (زمان بسته شدن کندل)، نه شروع آن
                out.add(
                    Candle(
                        time = bucketStart + hourMs,
                        open = open,
                        high = high,
                        low = low,
                        close = lastClose,
                        volume = vol
                    )
                )
                val missing = (bStart - bucketStart) / hourMs - 1
                if (missing > 0) gapCount += missing.toInt()

                bucketStart = bStart
                open = p
                high = p
                low = p
                vol = 0.0
            } else {
                if (p > high) high = p
                if (p < low) low = p
            }
            lastClose = p

            val v = volByTs?.get(tsMs)
            if (v != null) {
                val dv = if (v >= lastVol) v - lastVol else v
                if (dv > 0 && dv < 1_000_000_000.0) vol += dv
                lastVol = v
            }
        }

        return CandleBuild(out, gapCount, droppedTrailingIncomplete = true)
    }
}
