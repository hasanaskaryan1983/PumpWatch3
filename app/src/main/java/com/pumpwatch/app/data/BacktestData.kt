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
 * BatchScanner Pro — سازگار با MonitorWorker
 * خروجی: List<SignalResult> | ورودی: (mode, params, limit)
 */
object BatchScanner {

    private const val TAG = "BatchScanner"
    private val STABLES = setOf("USDT", "USDC", "DAI", "FDUSD", "TUSD", "BUSD")

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
                    val isStable = STABLES.any { m.symbol.contains(it, true) }
                    !isStable && (m.volume ?: 0.0) > 500.0
                }
                .sortedByDescending { quickScore(it) }
                .take(limit)

            Log.d(TAG, "🎯 candidates: ${candidates.size}")

            val results = mutableListOf<SignalResult>()
            candidates.chunked(6).forEach { chunk ->
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

            results.sortedByDescending { it.score }
        } catch (e: Exception) {
            Log.e(TAG, "❌ scan failed: ${e.message}")
            emptyList()
        }
    }

    // ---------- بارگذاری ----------

    private suspend fun loadMarkets(mode: String): List<ScanMarket> {
        val out = mutableListOf<ScanMarket>()
        val pages = if (mode == "FUT") 1 else 4
        for (p in 1..pages) {
            out.addAll(ScanClient.api.markets(perPage = 250, page = p))
            if (p < pages) delay(400)
        }
        return out
    }

    private suspend fun loadFunding(): Map<String, Double> {
        return try {
            ScanClient.api.derivatives()
                .filter { it.base != null && it.fundingRate != null }
                .associate { it.base!!.uppercase() to it.fundingRate!! }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun quickScore(m: ScanMarket): Double {
        val vol = m.volume ?: 0.0
        val ch = m.change24h ?: 0.0
        return vol * 0.000001 + abs(ch)
    }

    // ---------- تحلیل و ساخت SignalResult ----------

    private suspend fun analyze(
        m: ScanMarket,
        mode: String,
        funding: Double?,
        params: SignalParams
    ): SignalResult? {
        val chart = ScanClient.api.chart(m.id, days = 30)
        val closes = chart.prices.map { it[1] }
        if (closes.size < 60) return null

        val price = closes.last()
        val rsi = rsiOf(closes)
        val e20 = emaLast(closes, 20)
        val e50 = emaLast(closes, 50)
        val mUp = macdUp(closes)
        val atr = atrOf(closes)
        val up = price > e20 && e20 > e50
        val dn = price < e20 && e20 < e50

        var pump = 0
        var dump = 0
        val reasons = mutableListOf<String>()
        if (up && mUp) { pump += 40; reasons.add("روند صعودی + MACD 🟢") }
        if (dn && !mUp) { dump += 40; reasons.add("روند نزولی + MACD 🔴") }
        if (rsi < 30) { pump += 25; reasons.add("RSI اشباع فروش — فرصت") }
        if (rsi > 75) { dump += 15; reasons.add("⚠️ RSI اشباع خرید") }
        if (rsi in 40.0..70.0 && up) pump += 10
        val ch = m.change24h ?: 0.0
        if (ch > 5) { pump += 10; reasons.add("حرکت ۲۴ ساعته قوی") }
        if (ch < -5) { dump += 10; reasons.add("ریزش ۲۴ ساعته") }
        if (funding != null && funding <= -0.0003) { pump += 15; reasons.add("فاندینگ منفی = اسکوییز 🚀") }

        val side = if (pump >= dump) "PUMP" else "DUMP"
        val score = maxOf(pump, dump).coerceAtMost(100)

        val cap = m.marketCap ?: 0.0
        val vol = m.volume ?: 0.0
        val risk = if (atr > 0) atr * params.atrMult else price * 0.03
        val (stop, t1, t2) = if (side == "PUMP")
            Triple(price - risk, price + risk * params.rr, price + risk * params.rr * 2)
        else
            Triple(price + risk, price - risk * params.rr, price - risk * params.rr * 2)

        return SignalResult(
            coinId = m.id,
            symbol = m.symbol.uppercase(),
            name = m.name,
            price = price,
            mode = mode,
            pumpScore = pump,
            dumpScore = dump,
            side = side,
            score = score,
            golden = score >= params.goldenScore,
            ultra = score >= 90,
            mtfAligned = (up && mUp) || (dn && !mUp),
            mtfTrend = if (up) "صعودی" else if (dn) "نزولی" else "خنثی",
            adx = abs(ch),
            rsi = rsi,
            volumeRatio = if (cap > 0) vol / cap else 0.0,
            funding = funding,
            entry = price,
            stopLoss = stop,
            target1 = t1,
            target2 = t2,
            reasons = reasons
        )
    }

    // ---------- اندیکاتورهای محلی ----------

    private fun emaLast(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
        return ema
    }

    private fun rsiOf(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 50.0
        var g = 0.0
        var l = 0.0
        for (i in 1..period) {
            val d = data[i] - data[i - 1]
            if (d > 0) g += d else l -= d
        }
        var ag = g / period
        var al = l / period
        for (i in period + 1 until data.size) {
            val d = data[i] - data[i - 1]
            ag = (ag * (period - 1) + maxOf(d, 0.0)) / period
            al = (al * (period - 1) + maxOf(-d, 0.0)) / period
        }
        if (al == 0.0) return 100.0
        return 100.0 - 100.0 / (1.0 + ag / al)
    }

    private fun macdUp(data: List<Double>): Boolean {
        if (data.size < 35) return false
        val prev = data.dropLast(1)
        return (emaLast(data, 12) - emaLast(data, 26)) > (emaLast(prev, 12) - emaLast(prev, 26))
    }

    private fun atrOf(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 0.0
        var s = 0.0
        for (i in 1..period) s += abs(data[i] - data[i - 1])
        return s / period
    }
}
