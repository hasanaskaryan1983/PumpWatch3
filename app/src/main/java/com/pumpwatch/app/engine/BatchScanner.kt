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
 * BatchScanner Pro — سازگار با ساختار فعلی پروژه
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
                    !isStable && (m.volume ?: 0.0) > 500_000.0
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

    private suspend fun analyze(
        m: ScanMarket,
        mode: String,
        funding: Double?,
        params: SignalParams
    ): SignalResult? {
        val chart = ScanClient.api.chart(m.id, days = 30)
        val candles = buildCandles(chart.prices)
        if (candles.size < 100) return null

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

    private fun buildCandles(prices: List<List<Double>>): List<Candle> {
        if (prices.size < 2) return emptyList()
        val out = mutableListOf<Candle>()
        val hourMs = 3_600_000L
        val sorted = prices.sortedBy { it[0].toLong() }

        var bucketStart = (sorted.first()[0].toLong() / hourMs) * hourMs
        val bucketPrices = mutableListOf<Double>()

        for (p in sorted) {
            val ts = p[0].toLong()
            val price = p[1]

            if (ts - bucketStart >= hourMs && bucketPrices.isNotEmpty()) {
                out.add(
                    Candle(
                        time = bucketStart,
                        open = bucketPrices.first(),
                        high = bucketPrices.max(),
                        low = bucketPrices.min(),
                        close = bucketPrices.last(),
                        volume = 0.0
                    )
                )
                bucketStart += hourMs
                bucketPrices.clear()
            }
            bucketPrices.add(price)
        }

        if (bucketPrices.isNotEmpty()) {
            out.add(
                Candle(
                    time = bucketStart,
                    open = bucketPrices.first(),
                    high = bucketPrices.max(),
                    low = bucketPrices.min(),
                    close = bucketPrices.last(),
                    volume = 0.0
                )
            )
        }

        return out
    }
}
