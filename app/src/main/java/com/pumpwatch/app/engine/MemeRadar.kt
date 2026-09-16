package com.pumpwatch.app.engine

import android.util.Log
import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.GoPlusClient
import com.pumpwatch.app.data.SecurityResult
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
    val reasons: List<String>,
    // P0-1: nullable — null یعنی "UNKNOWN" (داده امنیتی در دسترس نیست)
    val rugScore: Int? = null,
    val rugWarnings: List<String> = emptyList(),
    // P0-1: وضعیت داده امنیتی
    val securityStatus: String = "UNKNOWN",  // "READY" | "EMPTY" | "FAILED" | "UNKNOWN"
    // 🚀 Sprint 10 (V2b): آدرس کانترکت توکن (برای کپی/پیست در CoinGecko/GoPlus)
    val contract: String? = null
)

// ---------- رادار میم‌کوین (GeckoTerminal + Rug Safety Check با GoPlus) ----------

object MemeRadar {

    private const val TAG = "MemeRadar"
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

        onProgress(70, "تحلیل معیارهای اعتماد + Rug Safety Check...")
        val results = pools.mapNotNull { analyze(it) }
        onProgress(95, "رتبه‌بندی نهایی...")
        return results.sortedByDescending { it.score }.take(20)
    }

    private suspend fun analyze(p: GeckoPool): MemeSignal? {
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

        // ---------- فیلترهای ایمنی پایه ----------
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
        val chain = p.relationships?.network?.data?.id ?: "?"

        // 🚀 Sprint 10 (V2b): استخراج آدرس کانترکت توکن (base_token)
        // pool.relationships.base_token.data.id = "{chain}_{address}"
        val contractAddress = p.relationships?.base_token?.data?.id?.substringAfter('_', "")

        // ---------- چک Rug Safety با GoPlus API (مدل سه‌حالته) ----------
        val (rugScore, rugWarnings, securityStatus) = checkRugSafety(p, chain)

        // P0-1: فقط وقتی rugScore واقعاً پایین است فیلتر کن
        // null (UNKNOWN) یا score بالا → توکن را نگه دار
        if (rugScore != null && rugScore < 40) {
            Log.w(TAG, "🚨 $sym rug score too low: $rugScore — $rugWarnings")
            return null
        }

        return MemeSignal(
            symbol = sym,
            name = fullName,
            chain = chain,
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
            reasons = reasons,
            rugScore = rugScore,
            rugWarnings = rugWarnings,
            securityStatus = securityStatus,
            contract = contractAddress
        )
    }

    /**
     * P0-1: چک Rug Safety با GoPlus API (مدل سه‌حالته)
     * @return Triple(rugScore: Int?, warnings: List<String>, status: String)
     *
     * - Ready: rugScore = عدد واقعی (0-100)
     * - Empty: rugScore = null، status = "EMPTY" (توکن در GoPlus نیست)
     * - Failed: rugScore = null، status = "FAILED" (API شکست خورد)
     *
     * هیچ‌کدام به score خنثی تبدیل نمی‌شوند — UI باید برای UNKNOWN برچسب نمایش دهد.
     */
    private suspend fun checkRugSafety(
        pool: GeckoPool,
        chain: String
    ): Triple<Int?, List<String>, String> {
        val warnings = mutableListOf<String>()

        val poolId = pool.id ?: return Triple(null, listOf("⚠️ آدرس contract در دسترس نیست"), "FAILED")
        val contractAddress = poolId.substringAfter('_', "")
        if (contractAddress.isEmpty()) return Triple(null, listOf("⚠️ آدرس contract یافت نشد"), "FAILED")

        // P0-1: استفاده از مدل سه‌حالته
        return when (val result = GoPlusClient.getTokenSecurityResult(chain, contractAddress)) {
            is SecurityResult.Failed -> {
                Log.w(TAG, "GoPlus API failed for $contractAddress: ${result.reason}")
                Triple(null, listOf("⚠️ خطا در دریافت داده امنیتی"), "FAILED")
            }
            is SecurityResult.Empty -> {
                Log.w(TAG, "🚫 GoPlus: داده امنیتی برای $contractAddress در $chain یافت نشد")
                Triple(null, listOf("⚠️ داده امنیتی در دسترس نیست"), "EMPTY")
            }
            is SecurityResult.Ready -> {
                val security = result.security
                var score = 100

                // چک Honeypot
                if (security.is_honeypot == "1") {
                    score -= 80
                    warnings.add("🚨 Honeypot: نمی‌توانید بفروشید!")
                }

                // چک Mintable
                if (security.is_mintable == "1") {
                    score -= 20
                    warnings.add("⚠️ Mintable: تیم می‌تواند توکن جدید بسازد")
                }

                // چک Owner Change Balance
                if (security.owner_change_balance == "1") {
                    score -= 30
                    warnings.add("🚨 Owner می‌تواند balance را تغییر دهد")
                }

                // چک Hidden Owner
                if (security.hidden_owner == "1") {
                    score -= 15
                    warnings.add("⚠️ Owner مخفی")
                }

                // چک Self Destruct
                if (security.selfdestruct == "1") {
                    score -= 50
                    warnings.add("🚨 Contract می‌تواند خود را حذف کند")
                }

                // چک Proxy Contract
                if (security.is_proxy == "1") {
                    score -= 10
                    warnings.add("⚠️ Proxy Contract (ممکن است منطق تغییر کند)")
                }

                // چک Buy/Sell Tax
                val buyTax = security.buy_tax?.toDoubleOrNull() ?: 0.0
                val sellTax = security.sell_tax?.toDoubleOrNull() ?: 0.0
                if (buyTax > 0.10 || sellTax > 0.10) {
                    score -= 20
                    warnings.add("⚠️ Tax بالا: Buy ${(buyTax * 100).toInt()}% / Sell ${(sellTax * 100).toInt()}%")
                }

                // چک Top 10 Holders
                val topHolders = security.holders?.take(10)
                val topHoldersPercent = topHolders?.sumOf { it.percent ?: 0.0 } ?: 0.0
                if (topHoldersPercent > 0.50) {
                    score -= 25
                    warnings.add("🚨 Top 10 Holders: ${(topHoldersPercent * 100).toInt()}% (تمرکز بالا)")
                } else if (topHoldersPercent > 0.30) {
                    score -= 10
                    warnings.add("⚠️ Top 10 Holders: ${(topHoldersPercent * 100).toInt()}%")
                }

                // چک Liquidity Lock
                val lpHolders = security.lp_holders
                val lpLocked = lpHolders?.values?.any { it.is_locked == "1" } ?: false
                if (!lpLocked) {
                    score -= 30
                    warnings.add("🚨 Liquidity قفل نیست (خطر Rug Pull)")
                } else {
                    val lockedDetails = lpHolders?.values?.flatMap { it.locked_detail ?: emptyList() }
                    val maxEndTime = lockedDetails?.maxOfOrNull { it.end_time?.toLongOrNull() ?: 0L }
                    if (maxEndTime != null && maxEndTime > 0) {
                        val lockDays = (maxEndTime - System.currentTimeMillis() / 1000) / 86400
                        if (lockDays < 30) {
                            score -= 15
                            warnings.add("⚠️ Liquidity فقط $lockDays روز قفل است")
                        }
                    }
                }

                // چک Open Source
                if (security.is_open_source != "1") {
                    score -= 20
                    warnings.add("⚠️ Contract Open Source نیست")
                }

                Triple(score.coerceIn(0, 100), warnings, "READY")
            }
        }
    }
}
