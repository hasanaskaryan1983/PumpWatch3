package com.pumpwatch.app.engine

import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    val fdv: Double,
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val target2: Double,
    val reasons: List<String>
)

// ---------- رادار میم‌کوین (GeckoTerminal — بدون بایننس) ----------

object MemeRadar {

    private val CHAINS = listOf("solana", "bsc", "base", "ethereum")

    var lastScanFailed = false

    private fun ageHours(createdAt: String?): Double {
        if (createdAt == null) return 9999.0
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val t = sdf.parse(createdAt) ?: return 9999.0
            (System.currentTimeMillis() - t.time) / 3_600_000.0
        } catch (_: Exception) {
            9999.0
        }
    }

    suspend fun scan(
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<MemeSignal> {
        lastScanFailed = false
        onProgress(5, "دریافت استخرهای داغ...")

        var anyOk = false
        val pools = mutableListOf<GeckoPool>()
        for (chain in CHAINS) {
            onProgress(10 + CHAINS.indexOf(chain) * 12, "اسکن زنجیره $chain...")
            try {
                val r = GeckoTerminal.api.trendingPools(chain).data
                if (r != null) {
                    anyOk = true
                    pools.addAll(r)
                }
            } catch (_: Exception) { }
        }

        if (!anyOk) {
            lastScanFailed = true
            return emptyList()
        }

        onProgress(70, "تحلیل معیارهای اعتماد...")
        val results = pools.mapNotNull { analyze(it) }
        onProgress(95, "رتبه‌بندی نهایی...")
        return results.sortedByDescending { it.score }.take(20)
    }

    private fun analyze(p: GeckoPool): MemeSignal? {
        val a = p.attributes ?: return null
        val price = a.priceUsd?.toDoubleOrNull() ?: return null
        if (price <= 0) return null

        val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
        val vol1 = a.volume?.h1 ?: 0.0
        val vol24 = a.volume?.h24 ?: 0.0
        val b1 = a.transactions?.h1?.buys ?: 0.0
        val s1 = a.transactions?.h1?.sells ?: 0.0
        val h1 = a.priceChange?.h1 ?: 0.0
        val h6 = a.priceChange?.h6 ?: 0.0
        val h24 = a.priceChange?.h24 ?: 0.0
        val age = ageHours(a.createdAt)
        val fdv = a.fdvUsd ?: 0.0

        // ---------- فیلترهای ایمنی ----------
        if (liq < 20_000) return null
        if (vol24 < 50_000) return null
        if (s1 <= 0) return null
        if (age < 1) return null

        val t = b1 + s1
        val buyRatio = if (t > 0) b1 / t else 0.5

        var score = 10
        val reasons = mutableListOf("استخر داغ امروز 🔥")

        if (buyRatio >= 0.65) { score += 20; reasons.add("فشار خرید سنگین 🐳") }
        else if (buyRatio >= 0.55) { score += 10; reasons.add("فشار خرید مثبت") }

        if (vol1 * 6 > vol24 * 1.5 && vol1 > 30_000) { score += 20; reasons.add("شتاب حجم در ساعت اخیر 💥") }
        else if (vol1 * 6 > vol24) score += 10

        if (liq in 100_000.0..5_000_000.0) { score += 15; reasons.add("نقدینگی سالم 💧") }
        else score += 5

        if (h1 in 2.0..20.0) { score += 15; reasons.add("شروع حرکت صعودی 🚀") }
        else if (h1 in 0.0..2.0) score += 5

        if (h24 in -20.0..80.0) { score += 10; reasons.add("هنوز پارابولیک نشده 📈") }
        if (h24 > 200) score -= 15

        if (age in 24.0..720.0) { score += 10; reasons.add("توکن جاافتاده (۱-۳۰ روز)") }
        else score += 5

        if (score < 30) return null

        val fullName = a.name ?: "?"
        val sym = fullName.split("/").firstOrNull()?.trim() ?: "?"

        return MemeSignal(
            symbol = sym,
            name = fullName,
            chain = p.relationships?.network?.data?.id ?: "?",
            dex = p.relationships?.dex?.data?.id ?: "?",
            price = price,
            score = score.coerceAtMost(100),
            liquidity = liq,
            volumeH1 = vol1,
            buyRatio = buyRatio,
            ageHours = age,
            changeH1 = h1,
            changeH6 = h6,
            changeH24 = h24,
            fdv = fdv,
            entry = price,
            stopLoss = price * 0.90,
            target1 = price * 1.25,
            target2 = price * 1.60,
            reasons = reasons
        )
    }
}
