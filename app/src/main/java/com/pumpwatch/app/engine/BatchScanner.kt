package com.pumpwatch.app.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.data.ScanMarket
import kotlin.math.abs

/**
 * BatchScanner Pro — نسخه بازبینی دوم
 * تغییرات قدم ۳:
 * - buildCandlesChecked: تابع pure و قابل تست برای ساخت کندل ساعتی
 * - تشخیص واحد timestamp (ثانیه/میلی‌ثانیه)
 * - مرتب‌سازی صریح داده
 * - شمارش و لاگ gapهای زمانی
 * - مدیریت reset حجم (بدون حجم منفی)
 * - حذف کندل آخر ناتمام از خروجی سیگنال
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

    // ---------- تحلیل کامل یک ارز با SignalEngine ----------

    private suspend fun analyze(
        m: ScanMarket,
        mode: String,
        funding: Double?,
        params: SignalParams
    ): SignalResult? {
        val chart = ScanClient.api.chart(m.id, days = 30)
        val build = buildCandlesChecked(chart.prices, chart.volumes)

        if (build.gapCount > 0) {
            Log.w(TAG, "${m.symbol}: ${build.gapCount} gap ساعتی در داده — کندل‌ها پیوسته نیستند")
        }

        val candles = build.candles
        if (candles.size < 100) {
            Log.w(TAG, "${m.symbol}: ${candles.size} candles < 100")
            return null
        }

        return SignalEngine.analyze(
            coinId = m.id,
            symbol = m.symbol,
            name = m.name,
            candles1h = candles,
            mode = mode,
            funding = funding,
            params = params
        )
    }

    // ---------- ساخت کندل ساعتی: تابع pure و قابل تست ----------

    internal data class CandleBuild(
        val candles: List<Candle>,
        val gapCount: Int,
        val droppedTrailingIncomplete: Boolean
    )

    /**
     * ورودی: prices = لیست [timestamp, price] و volumes = لیست [timestamp, volume]
     * (فرمت خام پاسخ market_chart کوین‌گکو)
     *
     * قواعد:
     * ۱) اگر بیشینهٔ timestamp کمتر از 1e11 باشد، واحد «ثانیه» فرض می‌شود و به ms تبدیل می‌گردد.
     * ۲) داده قبل از bucketing مرتب می‌شود.
     * ۳) یک bucket فقط وقتی بسته می‌شود که نقطهٔ ساعت بعد رسیده باشد؛
     *    ساعت‌های گم‌شده بین دو bucket به‌عنوان gap شمرده می‌شوند.
     * ۴) اگر حجم تجمعی ریست شود (v < lastVol)، مقدار جدید به‌عنوان اختلاف مبنا گرفته می‌شود
     *    تا حجم منفی تولید نشود.
     * ۵) آخرین bucket (ناتمام) هرگز وارد خروجی نمی‌شود.
     */
    internal fun buildCandlesChecked(
        prices: List<List<Double>>,
        volumes: List<List<Double>>?
    ): CandleBuild {
        if (prices.size < 2) return CandleBuild(emptyList(), 0, false)
        val hourMs = 3_600_000L

        val pts = prices.filter { it.size >= 2 }.map { it[0] to it[1] }
        if (pts.size < 2) return CandleBuild(emptyList(), 0, false)

        // ۱) تشخیص واحد timestamp
        val unitMs = if (pts.maxOf { it.first } < 100_000_000_000.0) 1000.0 else 1.0

        // ۲) مرتب‌سازی صریح
        val sorted = pts.sortedBy { it.first }

        // ۳) نگاشت حجم بر اساس timestamp
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
                // بستن bucket قبلی (چون نقطهٔ ساعت بعد رسیده، کامل بوده است)
                out.add(
                    Candle(
                        time = bucketStart,
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

            // ۴) حجم با مدیریت reset
            val v = volByTs?.get(tsMs)
            if (v != null) {
                val dv = if (v >= lastVol) v - lastVol else v
                if (dv > 0 && dv < 1_000_000_000.0) vol += dv
                lastVol = v
            }
        }

        // ۵) آخرین bucket ناتمام است (نقطهٔ بعدی برای بستنش نرسیده) → حذف
        return CandleBuild(out, gapCount, droppedTrailingIncomplete = true)
    }
}
