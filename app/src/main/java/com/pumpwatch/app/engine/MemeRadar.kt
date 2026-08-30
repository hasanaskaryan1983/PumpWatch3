package com.pumpwatch.app.engine

import com.pumpwatch.app.data.DexClient
import com.pumpwatch.app.data.DexPair

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

// ---------- رادار میم‌کوین (معیارهای تریدرهای برتر) ----------

object MemeRadar {

    private val CHAINS = listOf("solana", "bsc", "base", "ethereum")

    // آیا اسکن به‌خاطر قطعی اتصال خالی بود؟
    var lastScanFailed = false

    suspend fun scan(
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<MemeSignal> {
        lastScanFailed = false

        // ---------- ۱) توکن‌های ترند/بوست‌شده ----------
        onProgress(5, "دریافت توکن‌های ترند DexScreener...")
        val boosts = try {
            DexClient.api.topBoosts()
        } catch (_: Exception) {
            emptyList()
        }

        if (boosts.isEmpty()) {
            lastScanFailed = true
            return emptyList()
        }

        val valid = boosts.filter {
            it.tokenAddress != null && it.chainId in CHAINS
        }

        val boostMap = valid
            .groupBy { (it.chainId ?: "") + ":" + (it.tokenAddress ?: "") }
            .mapValues { e -> e.value.sumOf { it.totalBoosts ?: 0.0 } }

        val byChain = valid
            .distinctBy { it.chainId + it.tokenAddress }
            .groupBy { it.chainId ?: "" }

        // ---------- ۲) دریافت جفت‌ارزها ----------
        val pairs = mutableListOf<DexPair>()
        var done = 0
        val totalChunks = byChain.values.sumOf { (it.size + 19) / 20 }
        for ((chain, tokens) in byChain) {
            tokens.chunked(20).forEach { chunk ->
                done++
                onProgress(
                    10 + done * 40 / maxOf(1, totalChunks),
                    "دریافت جفت‌ارزهای $chain..."
                )
                try {
                    val addrs = chunk.mapNotNull { it.tokenAddress }.joinToString(",")
                    pairs += DexClient.api.tokens(addrs)
                } catch (_: Exception) { }
            }
        }

        if (pairs.isEmpty()) {
            lastScanFailed = true
            return emptyList()
        }

        // ---------- ۳) بهترین جفت‌ارز هر توکن ----------
        onProgress(55, "تحلیل معیارهای تریدرهای برتر...")
        val bestByToken = pairs
            .filter { it.baseToken?.address != null }
            .groupBy { (it.chainId ?: "") + ":" + it.baseToken!!.address }
            .mapValues { e -> e.value.maxByOrNull { it.liquidity?.usd ?: 0.0 } }
            .values.filterNotNull()

        // ---------- ۴) امتیازدهی ----------
        val results = mutableListOf<MemeSignal>()
        for (p in bestByToken) {
            val key = (p.chainId ?: "") + ":" + (p.baseToken?.address ?: "")
            analyze(p, boostMap[key] ?: 0.0)?.let { results.add(it) }
        }

        onProgress(95, "رتبه‌بندی نهایی...")
        return results.sortedByDescending { it.score }.take(20)
    }

    // ---------- تحلیل یک جفت‌ارز (فیلترهای متعادل) ----------

    private fun analyze(p: DexPair, boosts: Double): MemeSignal? {
        val price = p.priceUsd?.toDoubleOrNull() ?: return null
        if (price <= 0) return null

        val liq = p.liquidity?.usd ?: 0.0
        val vol1 = p.volume?.h1 ?: 0.0
        val vol24 = p.volume?.h24 ?: 0.0
        val buys = p.txns?.h1?.buys ?: 0.0
        val sells = p.txns?.h1?.sells ?: 0.0
        val h1 = p.priceChange?.h1 ?: 0.0
        val h6 = p.priceChange?.h6 ?: 0.0
        val h24 = p.priceChange?.h24 ?: 0.0
        val ageH = (System.currentTimeMillis() - (p.pairCreatedAt ?: 0L)) / 3_600_000.0

        // ---------- فیلترهای ایمنی (متعادل‌تر) ----------
        if (liq < 20_000) return null
        if (vol24 < 50_000) return null
        if (sells <= 0) return null        // مشکوک به هانی‌پات
        if (ageH < 1) return null          // زیر ۱ ساعت = ریسک راگ

        var score = 0
        val reasons = mutableListOf<String>()

        // ۱) فشار خرید — ردپای نهنگ‌ها
        val buyRatio = buys / (buys + sells)
        if (buyRatio >= 0.65) {
            score += 20
            reasons.add("فشار خرید سنگین ${"%.0f".format(buyRatio * 100)}٪ 🐳")
        } else if (buyRatio >= 0.55) {
            score += 10
            reasons.add("فشار خرید مثبت")
        }

        // ۲) شتاب حجم — پول هوشمند
        if (vol1 * 6 > vol24 * 1.5 && vol1 > 30_000) {
            score += 20
            reasons.add("شتاب حجم در ساعت اخیر 💥")
        } else if (vol1 * 6 > vol24) {
            score += 10
        }

        // ۳) نقدینگی امن
        if (liq in 100_000.0..5_000_000.0) {
            score += 15
            reasons.add("نقدینگی سالم 💧")
        } else {
            score += 5
        }

        // ۴) مومنتوم اولیه (قبل از پامپ کامل)
        if (h1 in 2.0..20.0) {
            score += 15
            reasons.add("شروع حرکت صعودی 🚀")
        } else if (h1 in 0.0..2.0) {
            score += 5
        }

        // ۵) هنوز پارابولیک نشده = جای رشد داره
        if (h24 in -20.0..80.0) {
            score += 10
            reasons.add("هنوز پارابولیک نشده 📈")
        }
        if (h24 > 200) score -= 15

        // ۶) سن توکن (از ۱ ساعت به بالا قبوله)
        if (ageH in 24.0..720.0) {
            score += 10
            reasons.add("توکن جاافتاده (۱-۳۰ روز) ")
        } else if (ageH >= 1) {
            score += 5
            reasons.add("توکن تازه ولی فعال 🌱")
        }

        // ۷) توجه جامعه
        if (boosts >= 10) {
            score += 10
            reasons.add("ترند در DexScreener 🔥")
        } else if (boosts >= 3) {
            score += 5
        }

        if (score < 30) return null

        return MemeSignal(
            symbol = p.baseToken?.symbol ?: "?",
            name = p.baseToken?.name ?: "?",
            chain = p.chainId ?: "?",
            dex = p.dexId ?: "?",
            price = price,
            score = score.coerceAtMost(100),
            liquidity = liq,
            volumeH1 = vol1,
            buyRatio = buyRatio,
            ageHours = ageH,
            changeH1 = h1,
            changeH6 = h6,
            changeH24 = h24,
            boosts = boosts,
            entry = price,
            stopLoss = price * 0.90,
            target1 = price * 1.25,
            target2 = price * 1.60,
            reasons = reasons
        )
    }
}
