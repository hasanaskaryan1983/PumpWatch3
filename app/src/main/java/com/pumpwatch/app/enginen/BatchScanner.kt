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

    // ---------- اسکن کامل ----------

    suspend fun scan(
        mode: String,
        params: SignalParams,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<SignalResult> {

        onProgress(5, "دریافت لیست بازار...")
        val markets = loadMarkets(mode)

        // مرحله ۱: فیلتر نقدشوندگی + حذف استیبل‌کوین
        val filtered = markets.filter { m ->
            m.symbol.lowercase() !in STABLES &&
                    (m.volume ?: 0.0) > 5_000_000 &&
                    m.rank != null
        }

        // مرحله ۲: انتخاب کاندیداها (حجم بالا + حرکت زیاد)
        val candidates = pickCandidates(filtered, mode)

        val funding = if (mode == "FUT") loadFunding() else emptyMap()

        // مرحله ۳: تحلیل عمیق هر کاندیدا
        val results = mutableListOf<SignalResult>()
        var done = 0
        for (m in candidates) {
            done++
            onProgress(
                10 + done * 85 / candidates.size,
                "تحلیل ${m.symbol.uppercase(Locale.US)}... ($done/${candidates.size})"
            )
            try {
                val chart = ScanClient.api.chart(m.id, days = 30, interval = "hourly")
                val candles = toCandles(chart.prices, chart.volumes ?: emptyList())
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
                // خطای API یا محدودیت نرخ → رد کن
            }
            delay(1200) // احترام به محدودیت API
        }

        val limit = if (mode == "SPOT") 50 else 20
        return results.sortedByDescending { it.score }.take(limit)
    }

    // ---------- دریافت لیست بازار ----------

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

    // ---------- انتخاب کاندیداها ----------

    private fun pickCandidates(list: List<ScanMarket>, mode: String): List<ScanMarket> {
        val volCount = if (mode == "SPOT") 40 else 50
        val moveCount = if (mode == "SPOT") 40 else 40
        val cap = if (mode == "SPOT") 60 else 40

        val byVol = list.sortedByDescending { it.volume ?: 0.0 }.take(volCount)
        val byMove = list.sortedByDescending { abs(it.change24h ?: 0.0) }.take(moveCount)

        val set = linkedMapOf<String, ScanMarket>()
        (byVol + byMove).forEach { set[it.id] = it }
        return set.values.take(cap)
    }

    // ---------- فاندینگ فیوچرز ----------

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

    // ---------- تبدیل داده ساعتی به کندل ----------

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
