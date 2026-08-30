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
 * BatchScanner Pro (نسخه سازگار):
 * اسکن موازی + تحلیل تکنیکال واقعی + فاندینگ فیوچرز
 */
object BatchScanner {

    private const val TAG = "BatchScanner"
    private val STABLES = setOf("USDT", "USDC", "DAI", "FDUSD", "TUSD", "BUSD")

    data class BatchResult(
        val coinId: String,
        val symbol: String,
        val name: String,
        val side: String,
        val score: Int,
        val entry: Double,
        val stopLoss: Double,
        val target1: Double,
        val rsi: Double,
        val reasons: List<String>
    )

    // ---------- اسکن اصلی ----------

    suspend fun scan(mode: String, limit: Int = 30): List<BatchResult> {
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

            val results = mutableListOf<BatchResult>()
            candidates.chunked(6).forEach { chunk ->
                val part = coroutineScope {
                    chunk.map { m ->
                        async(Dispatchers.IO) {
                            try {
                                analyze(m, fundingMap[m.symbol.uppercase()])
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

    // ---------- تحلیل تکنیکال یک ارز ----------

    private suspend fun analyze(m: ScanMarket, funding: Double?): BatchResult? {
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

        var score = 0
        val reasons = mutableListOf<String>()
        if (up && mUp) { score += 40; reasons.add("روند صعودی + MACD 🟢") }
        if (dn && !mUp) { score += 40; reasons.add("روند نزولی + MACD 🔴") }
        if (rsi < 30) { score += 25; reasons.add("RSI اشباع فروش — فرصت") }
        if (rsi in 40.0..70.0) { score += 15; reasons.add("RSI سالم") }
        if (rsi > 75) reasons.add("⚠️ RSI اشباع خرید")
        if (funding != null && funding <= -0.0003) { score += 15; reasons.add("فاندینگ منفی = پتانسیل اسکوییز 🚀") }
        val ch = m.change24h ?: 0.0
        if (abs(ch) > 5) { score += 10; reasons.add("حرکت ۲۴ ساعته قوی") }

        val side = if (up) "PUMP" else if (dn) "DUMP" else if (ch >= 0) "PUMP" else "DUMP"
        val risk = if (atr > 0) atr * 1.5 else price * 0.03
        val (stop, target) = if (side == "PUMP")
            Pair(price - risk, price + risk * 2)
        else
            Pair(price + risk, price - risk * 2)

        return BatchResult(
            coinId = m.id,
            symbol = m.symbol.uppercase(),
            name = m.name,
            side = side,
            score = score.coerceAtMost(100),
            entry = price,
            stopLoss = stop,
            target1 = target,
            rsi = rsi,
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
