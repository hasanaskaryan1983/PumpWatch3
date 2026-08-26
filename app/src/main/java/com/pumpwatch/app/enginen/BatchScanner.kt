package com.pumpwatch.app.engine

import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.data.ScanMarket
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object BatchScanner {

    private val STABLES = setOf(
        "usdt", "usdc", "dai", "busd", "tusd", "fdusd",
        "usde", "usds", "pyusd", "usdd", "gusd"
    )

    suspend fun scan(
        mode: String,
        params: SignalParams,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<SignalResult> {

        onProgress(
            3,
            if (mode == "SPOT") "دریافت لیست ۱۰۰۰ ارز اسپات..."
            else "دریافت لیست ۱۰۰ ارز فیوچرز..."
        )
        val markets = loadMarkets(mode)

        val minVolume = if (mode == "SPOT") 5_000_000 else 1_000_000
        val filtered = markets.filter { m ->
            m.symbol.lowercase() !in STABLES &&
                    (m.volume ?: 0.0) > minVolume &&
                    m.rank != null
        }

        onProgress(8, "اسکن سریع ${filtered.size} ارز...")
        val ranked = filtered
            .map { it to quickScore(it) }
            .sortedByDescending { it.second }

        val deepCount = if (mode == "SPOT") {
            minOf(200, ranked.size)
        } else {
            minOf(100, ranked.size)
        }
        val candidates = ranked.take(deepCount).map { it.first }

        val funding = if (mode == "FUT") loadFunding() else emptyMap()

        val results = mutableListOf<SignalResult>()
        var done = 0
        for (m in candidates) {
            done++
            onProgress(
                10 + done * 85 / candidates.size,
                "تحلیل عمیق ${m.symbol.uppercase(Locale.US)}... ($done/${candidates.size})"
            )
            try {
                // فقط days=90، بدون interval
                val chart = ScanClient.api.chart(m.id, days = 90)
                val candles = toCandles(chart.prices, chart.totalVolumes ?: emptyList())
                val sig = SignalEngine.analyze(
                    coinId = m.id,
                    symbol = m.symbol.uppercase(Locale.US),
                    name = m.name,
                    candles1h = candles,
                    mode = mode,
                    funding = funding[m.symbol.uppercase(Locale.US)],
                    params = params
                )
                if (sig != null && sig.side != "NONE") results.add(sig)
            } catch (_: Exception) {
            }
            delay(300)
        }

        val limit = if (mode == "SPOT") 50 else 20
        return results.sortedByDescending { it.score }.take(limit)
    }

    private fun quickScore(m: ScanMarket): Double {
        val change = abs(m.change24h ?: 0.0)
        val change7 = abs(m.change7d ?: 0.0)
        val cap = m.marketCap ?: 0.0
        val turnover = if (cap > 0) (m.volume ?: 0.0) / cap else 0.0
        val high = m.high24h ?: 0.0
        val low = m.low24h ?: 0.0
        val rangePos = if (high > low) (m.price - low) / (high - low) else 0.5
        return change * 2.0 + change7 + turnover * 50.0 + rangePos * 10.0
    }

    private suspend fun loadMarkets(mode: String): List<ScanMarket> {
        return if (mode == "SPOT") {
            val all = mutableListOf<ScanMarket>()
            for (page in 1..4) {
                try {
                    all += ScanClient.api.markets(perPage = 250, page = page)
                } catch (_: Exception) { }
                delay(600)
            }
            all
        } else {
            ScanClient.api.markets(perPage = 100, page = 1)
        }
    }

    private suspend fun loadFunding(): Map<String, Double> {
        return try {
            ScanClient.api.derivatives()
                .filter { it.base != null && it.fundingRate != null }
                .groupBy { it.base!!.uppercase(Locale.US) }
                .mapValues { e -> e.value.mapNotNull { it.fundingRate }.average() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun toCandles(
        prices: List<List<Double>>,
        volumes: List<List<Double>>
    ): List<Candle> {
        if (prices.size < 2) return emptyList()
        val vols = volumes.associate { it[0].toLong() to it[1] }
        val out = ArrayList<Candle>(prices.size)
        var prevClose = prices[0][1]
        for (p in prices) {
            val t = p[0].toLong()
            val c = p[1]
            out.add(
                Candle(
                    time = t,
                    open = prevClose,
                    high = max(prevClose, c),
                    low = min(prevClose, c),
                    close = c,
                    volume = vols[t] ?: 0.0
                )
            )
            prevClose = c
        }
        return out
    }
}
