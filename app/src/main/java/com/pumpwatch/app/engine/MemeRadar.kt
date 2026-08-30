package com.pumpwatch.app.engine

import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.RadarBinance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class MemeSignal(
    val symbol: String,
    val name: String,
    val chain: String,
    val dex: String,
    val price: Double,
    val score: Int,
    val liquidity: Double,
    val volumeH1: Double,
    val buyRatio: Double,
    val ageHours: Double,
    val changeH1: Double,
    val changeH6: Double,
    val changeH24: Double,
    val boosts: Double,
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val reasons: List<String>
)

// ---------- رادار میم‌کوین: GeckoTerminal + پشتیبان بایننس ----------

object MemeRadar {

    private val CHAINS = listOf("solana", "bsc", "base", "ethereum")

    var lastScanFailed = false

    suspend fun scan(
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<MemeSignal> {
        lastScanFailed = false
        onProgress(10, "دریافت استخرهای داغ GeckoTerminal...")

        val perChain = coroutineScope {
            CHAINS.map { chain ->
                async(Dispatchers.IO) {
                    try {
                        Pair(true, GeckoTerminal.api.trendingPools(chain).data ?: emptyList())
                    } catch (_: Exception) {
                        Pair(false, emptyList<GeckoPool>())
                    }
                }
            }.awaitAll()
        }

        val connected = perChain.any { it.first }
        val pools = perChain.flatMap { it.second }

        // ---------- حالت پشتیبان: بایننس ----------
        if (!connected) {
            onProgress(50, "GeckoTerminal در دسترس نیست — حالت پشتیبان بایننس...")
            val fb = try {
                binanceFallback()
            } catch (_: Exception) {
                emptyList()
            }
            if (fb.isEmpty()) {
                lastScanFailed = true
                return emptyList()
            }
            return fb
        }

        onProgress(60, "تحلیل معیارهای تریدرهای برتر...")
        val results = pools.mapNotNull { analyzePool(it) }
        onProgress(95, "رتبه‌بندی نهایی...")
        return results.sortedByDescending { it.score }.take(20)
    }

    // ---------- تحلیل یک استخر داغ ----------

    private fun analyzePool(p: GeckoPool): MemeSignal? {
        val a = p.attributes ?: return null
        val price = a.priceUsd?.toDoubleOrNull() ?: return null
        if (price <= 0) return null

        val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
        val vol1 = a.volume?.h1 ?: 0.0
        val vol24 = a.volume?.h24 ?: 0.0
        val buys = a.transactions?.h1?.buys ?: 0.0
        val sells = a.transactions?.h1?.sells ?: 0.0
        val h1 = a.priceChange?.h1 ?: 0.0
        val h6 = a.priceChange?.h6 ?: 0.0
        val h24 = a.priceChange?.h24 ?: 0.0
        val ageH = ageHours(a.createdAt)

        // فیلترهای ایمنی
        if (liq < 20_000) return null
        if (vol24 < 50_000) return null
        if (sells <= 0) return null
        if (ageH < 1) return null

        var score = 10
        val reasons = mutableListOf("استخر داغ امروز 🔥")

        val buyRatio = buys / (buys + sells)
        if (buyRatio >= 0.65) {
            score += 20
            reasons.add("فشار خرید سنگین ${"%.0f".format(buyRatio * 100)}٪ 🐳")
        } else if (buyRatio >= 0.55) {
            score += 10
            reasons.add("فشار خرید مثبت")
        }

        if (vol1 * 6 > vol24 * 1.5 && vol1 > 30_000) {
            score += 20
            reasons.add("شتاب حجم در ساعت اخیر 💥")
        } else if (vol1 * 6 > vol24) {
            score += 10
        }

        if (liq in 100_000.0..5_000_000.0) {
            score += 15
            reasons.add("نقدینگی سالم 💧")
        } else {
            score += 5
        }

        if (h1 in 2.0..20.0) {
            score += 15
            reasons.add("شروع حرکت صعودی 🚀")
        } else if (h1 in 0.0..2.0) {
            score += 5
        }

        if (h24 in -20.0..80.0) {
            score += 10
            reasons.add("هنوز پارابولیک نشده 📈")
        }
        if (h24 > 200) score -= 15

        if (ageH in 24.0..720.0) {
            score += 10
            reasons.add("توکن جاافتاده (۱-۳۰ روز) ")
        } else if (ageH >= 1) {
            score += 5
            reasons.add("توکن تازه ولی فعال 🌱")
        }

        if (score < 30) return null

        val parts = (a.name ?: "?").split("/")
        return MemeSignal(
            symbol = parts.firstOrNull()?.trim() ?: "?",
            name = a.name ?: "?",
            chain = p.relationships?.network?.data?.id ?: "?",
            dex = p.relationships?.dex?.data?.id ?: "?",
            price = price,
            score = score.coerceAtMost(100),
            liquidity = liq,
            volumeH1 = vol1,
            buyRatio = buyRatio,
            ageHours = ageH,
            changeH1 = h1,
            changeH6 = h6,
            changeH24 = h24,
            boosts = 0.0,
            entry = price,
            stopLoss = price * 0.90,
            target1 = price * 1.25,
            target2 = price * 1.60,
            reasons = reasons
        )
    }

    // ---------- پشتیبان بایننس: حرکات سریع ----------

    private suspend fun binanceFallback(): List<MemeSignal> {
        val all = RadarBinance.api.tickers()
        return all.mapNotNull { t ->
            if (t.symbol?.endsWith("USDT") != true) return@mapNotNull null
            val ch = t.changePercent?.toDoubleOrNull() ?: return@mapNotNull null
            val qv = t.quoteVolume?.toDoubleOrNull() ?: 0.0
            val price = t.lastPrice?.toDoubleOrNull() ?: return@mapNotNull null
            if (qv < 20_000_000) return@mapNotNull null
            if (abs(ch) < 5.0 || abs(ch) > 60.0) return@mapNotNull null

            val up = ch > 0
            val score = (40 + (abs(ch).toInt() / 2).coerceAtMost(40) + if (qv > 100_000_000) 15 else 10)
                .coerceAtMost(100)

            MemeSignal(
                symbol = t.symbol.removeSuffix("USDT"),
                name = "حرکت سریع بایننس ⚡",
                chain = "binance",
                dex = "spot",
                price = price,
                score = score,
                liquidity = qv,
                volumeH1 = qv / 24,
                buyRatio = if (up) 0.6 else 0.4,
                ageHours = 0.0,
                changeH1 = ch / 6,
                changeH6 = ch / 2,
                changeH24 = ch,
                boosts = 0.0,
                entry = price,
                stopLoss = if (up) price * 0.92 else price * 1.08,
                target1 = if (up) price * 1.2 else price * 0.85,
                target2 = if (up) price * 1.5 else price * 0.7,
                reasons = listOf(
                    "شتاب ۲۴ ساعته ${"%.1f".format(ch)}٪ 🚀",
                    "حجم سنگین ${"%.0f".format(qv / 1_000_000)}M 💥"
                )
            )
        }.sortedByDescending { it.score }.take(15)
    }

    // ---------- سن استخر ----------

    private fun ageHours(createdAt: String?): Double {
        if (createdAt == null) return 999.0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            (System.currentTimeMillis() - (sdf.parse(createdAt)?.time ?: return 999.0)) / 3_600_000.0
        } catch (_: Exception) {
            999.0
        }
    }
}
