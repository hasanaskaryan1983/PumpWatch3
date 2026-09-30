package com.pumpwatch.app.engine

import android.util.Log
import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.GoPlusGateway
import com.pumpwatch.app.data.GoPlusTokenSecurity
import com.pumpwatch.app.data.SecurityData
import com.pumpwatch.app.data.SecurityResult
import com.pumpwatch.app.data.SolanaTokenSecurity
import com.pumpwatch.app.data.anyToBool
import com.pumpwatch.app.data.parseLockEndTime
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
    val reasons: List<String>,
    val rugScore: Int? = null,
    val rugWarnings: List<String> = emptyList(),
    val securityStatus: String = "UNKNOWN",
    val contract: String? = null,
    val poolAddress: String? = null
)

// 🚀 Commit 97: آمار قیف اسکن برای نمایش شفافیت در UI
data class SniperStats(
    val totalScanned: Int,
    val rejectedLiquidity: Int,
    val rejectedVolume: Int,
    val rejectedAge: Int,
    val rejectedNoTx: Int,
    val rejectedSecurity: Int,
    val rejectedScore: Int,
    val accepted: Int
) {
    fun toSummary(): String {
        val pass1 = totalScanned - rejectedLiquidity
        val pass2 = pass1 - rejectedVolume
        val pass3 = pass2 - rejectedAge - rejectedNoTx
        val pass4 = pass3 - rejectedSecurity - rejectedScore
        return "بررسی $totalScanned استخر: $pass1 با نقدینگی • $pass2 با حجم • $pass3 تازه و فعال • $pass4 امن/قوی • ✅ $accepted قبول"
    }
}

object MemeRadar {

    private const val TAG = "MemeRadar"

    // 🚀 Commit 92: اضافه شدن Arc Network
    private val CHAINS = listOf(
        "solana", "bsc", "base", "ethereum",
        "ton", "robinhood", "avalanche", "sei",
        "arc"
    )

    var lastScanFailed = false

    // 🚀 Commit 97: آخرین آمار قیف (قابل خواندن از UI)
    var lastSniperStats: SniperStats? = null
        private set

    private data class ScanThresholds(
        val minLiq: Double,
        val minVol24: Double,
        val minAgeH: Double,
        val maxAgeH: Double,
        val minRugScore: Int,
        val allowUnknownSecurity: Boolean
    )

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

    // 🚀 Commit 97: پارامتر sniperMode با default false (backward compatible)
    suspend fun scan(
        sniperMode: Boolean = false,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<MemeSignal> {
        lastScanFailed = false
        lastSniperStats = null

        // 🚀 Commit 97: آستانه‌های متفاوت برای حالت اسنایپر
        val thresholds = if (sniperMode) {
            ScanThresholds(
                minLiq = 5_000.0,            // آسان‌تر (قبلاً ۲۰K)
                minVol24 = 10_000.0,         // آسان‌تر (قبلاً ۵۰K)
                minAgeH = 0.5,               // از ۳۰ دقیقه
                maxAgeH = 168.0,             // تا ۷ روز
                minRugScore = 35,            // کمی آسان‌تر
                allowUnknownSecurity = true  // UNKNOWN قبول با سقف امتیاز ۵۰
            )
        } else {
            ScanThresholds(
                minLiq = 20_000.0,
                minVol24 = 50_000.0,
                minAgeH = 1.0,
                maxAgeH = 9999.0,
                minRugScore = 40,
                allowUnknownSecurity = false
            )
        }

        onProgress(
            5,
            if (sniperMode) "🎯 شکار توکن‌های تازه (آستانه آسان)..."
            else "دریافت استخرهای داغ و تازه..."
        )

        var anyOk = false
        val pools = mutableListOf<GeckoPool>()
        for (chain in CHAINS) {
            onProgress(10 + CHAINS.indexOf(chain) * 8, "اسکن زنجیره $chain...")

            // 🚀 Commit 97: در حالت اسنایپر، newPools اول و با weight بیشتر
            if (sniperMode) {
                try {
                    val n = GeckoTerminal.api.newPools(chain).data
                    if (n != null) {
                        anyOk = true
                        pools.addAll(n)
                        pools.addAll(n) // دوبار اضافه می‌کنیم تا اولویت داشته باشد
                    }
                } catch (_: Exception) { }
            }

            try {
                val r = GeckoTerminal.api.trendingPools(chain).data
                if (r != null) {
                    anyOk = true
                    pools.addAll(r)
                }
            } catch (_: Exception) { }

            if (!sniperMode) {
                try {
                    val n = GeckoTerminal.api.newPools(chain).data
                    if (n != null) {
                        anyOk = true
                        pools.addAll(n)
                    }
                } catch (_: Exception) { }
            }
        }

        if (!anyOk) {
            lastScanFailed = true
            return emptyList()
        }

        onProgress(
            75,
            if (sniperMode) "🔍 ارزیابی امنیت استخرهای تازه..."
            else "تحلیل معیارهای اعتماد + Rug Safety Check..."
        )

        val seen = mutableSetOf<String>()
        val unique = pools.filter { p -> seen.add(p.id ?: "") }

        // 🚀 Commit 97: شمارش دقیق قیف
        var rejectedLiq = 0
        var rejectedVol = 0
        var rejectedAge = 0
        var rejectedNoTx = 0
        var rejectedSec = 0
        var rejectedScore = 0

        val results = mutableListOf<MemeSignal>()
        for (p in unique) {
            when (val res = analyzeWithReason(p, thresholds)) {
                is AnalyzeResult.Success -> results.add(res.signal)
                is AnalyzeResult.Rejected -> when (res.reason) {
                    "liquidity" -> rejectedLiq++
                    "volume" -> rejectedVol++
                    "age" -> rejectedAge++
                    "noTx" -> rejectedNoTx++
                    "security" -> rejectedSec++
                    "score" -> rejectedScore++
                }
            }
        }

        lastSniperStats = SniperStats(
            totalScanned = unique.size,
            rejectedLiquidity = rejectedLiq,
            rejectedVolume = rejectedVol,
            rejectedAge = rejectedAge,
            rejectedNoTx = rejectedNoTx,
            rejectedSecurity = rejectedSec,
            rejectedScore = rejectedScore,
            accepted = results.size
        )

        Log.i(TAG, "📊 SniperStats: ${lastSniperStats?.toSummary()}")

        onProgress(95, "رتبه‌بندی نهایی...")
        return results.sortedByDescending { it.score }.take(20)
    }

    // 🚀 Commit 97: نتیجهٔ تحلیل با دلیل رد
    private sealed class AnalyzeResult {
        data class Success(val signal: MemeSignal) : AnalyzeResult()
        data class Rejected(val reason: String) : AnalyzeResult()
    }

    private suspend fun analyzeWithReason(p: GeckoPool, t: ScanThresholds): AnalyzeResult {
        val a = p.attributes ?: return AnalyzeResult.Rejected("score")
        val price = a.priceUsd?.toDoubleOrNull() ?: return AnalyzeResult.Rejected("score")
        if (price <= 0) return AnalyzeResult.Rejected("score")

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

        if (liq < t.minLiq) return AnalyzeResult.Rejected("liquidity")
        if (vol24 < t.minVol24) return AnalyzeResult.Rejected("volume")
        if (b1 + s1 <= 0) return AnalyzeResult.Rejected("noTx")
        if (age < t.minAgeH || age > t.maxAgeH) return AnalyzeResult.Rejected("age")

        val total = b1 + s1
        val buyRatio = if (total > 0) b1 / total else 0.5

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

        if (h24 in -20.0..80.0) { score += 10; reasons.add("هنوز پارابولیک نشده") }
        if (h24 > 200) score -= 15

        if (age in 24.0..720.0) { score += 10; reasons.add("توکن جاافتاده (۱-۳۰ روز)") }
        else if (age < 24.0) { score += 8; reasons.add("توکن تازه 🆕") }
        else score += 5

        if (score < 30) return AnalyzeResult.Rejected("score")

        val fullName = a.name ?: "?"
        val sym = fullName.split("/").firstOrNull()?.trim() ?: "?"
        val chain = p.relationships?.network?.data?.id ?: "?"

        val contractAddress = p.relationships?.base_token?.data?.id?.substringAfter('_', "")
        val poolAddress = p.id?.substringAfter('_', "")

        val (rugScore, rugWarnings, securityStatus) = checkRugSafety(chain, contractAddress)

        // 🚀 Commit 97: منطق Rug Safety دوگانه
        if (rugScore != null && rugScore < t.minRugScore) {
            Log.w(TAG, "🚨 $sym rug score too low: $rugScore — $rugWarnings")
            return AnalyzeResult.Rejected("security")
        }

        // اگر UNKNOWN باشد و allowUnknownSecurity = false → رد
        if (rugScore == null && !t.allowUnknownSecurity) {
            Log.w(TAG, "🚫 $sym: security UNKNOWN in normal mode — rejected")
            return AnalyzeResult.Rejected("security")
        }

        // در حالت اسنایپر، UNKNOWN قبول است ولی با سقف امتیاز ۵۰
        val finalScore = if (rugScore == null) {
            reasons.add("⚠️ امنیت نامشخص — حداکثر امتیاز ۵۰")
            score.coerceAtMost(50)
        } else {
            score
        }.coerceAtMost(100)

        return AnalyzeResult.Success(
            MemeSignal(
                symbol = sym,
                name = fullName,
                chain = chain,
                dex = p.relationships?.dex?.data?.id ?: "?",
                price = price,
                score = finalScore,
                liquidity = liq,
                volumeH1 = vol1,
                buyRatio = buyRatio,
                ageHours = age,
                changeH1 = h1,
                changeH6 = h6,
                changeH24 = h24,
                fdv = fdv,
                reasons = reasons,
                rugScore = rugScore,
                rugWarnings = rugWarnings,
                securityStatus = securityStatus,
                contract = contractAddress,
                poolAddress = poolAddress
            )
        )
    }

    private suspend fun checkRugSafety(
        chain: String,
        contractAddress: String?
    ): Triple<Int?, List<String>, String> {
        if (contractAddress.isNullOrEmpty()) {
            return Triple(null, listOf("⚠️ آدرس contract توکن در دسترس نیست"), "FAILED")
        }

        return when (val result = GoPlusGateway.getSecurity(chain, contractAddress)) {
            is SecurityResult.Failed -> {
                Log.w(TAG, "GoPlus failed for $contractAddress: ${result.reason}")
                Triple(null, listOf("⚠️ خطا در دریافت داده امنیتی"), "FAILED")
            }
            is SecurityResult.Empty -> {
                Log.w(TAG, "🚫 GoPlus: داده امنیتی برای $contractAddress در $chain یافت نشد")
                Triple(null, listOf("⚠️ داده امنیتی در دسترس نیست"), "EMPTY")
            }
            is SecurityResult.Ready -> {
                when (val data = result.security) {
                    is SecurityData.Evm -> scoreEvmSecurity(data.data)
                    is SecurityData.Solana -> scoreSolanaSecurity(data.data)
                }
            }
        }
    }

    private fun scoreEvmSecurity(security: GoPlusTokenSecurity): Triple<Int?, List<String>, String> {
        val warnings = mutableListOf<String>()
        var score = 100

        if (security.is_honeypot == "1") { score -= 80; warnings.add("🚨 Honeypot: نمی‌توانید بفروشید!") }
        if (security.is_mintable == "1") { score -= 20; warnings.add("⚠️ Mintable: تیم می‌تواند توکن جدید بسازد") }
        if (security.owner_change_balance == "1") { score -= 30; warnings.add("🚨 Owner می‌تواند balance را تغییر دهد") }
        if (security.hidden_owner == "1") { score -= 15; warnings.add("⚠️ Owner مخفی") }
        if (security.selfdestruct == "1") { score -= 50; warnings.add("🚨 Contract می‌تواند خود را حذف کند") }
        if (security.is_proxy == "1") { score -= 10; warnings.add("⚠️ Proxy Contract") }

        val buyTax = security.buy_tax?.toDoubleOrNull() ?: 0.0
        val sellTax = security.sell_tax?.toDoubleOrNull() ?: 0.0
        if (buyTax > 0.10 || sellTax > 0.10) {
            score -= 20
            warnings.add("⚠️ Tax بالا: Buy ${(buyTax * 100).toInt()}% / Sell ${(sellTax * 100).toInt()}%")
        }

        val topHolders = security.holders?.take(10)
        val topHoldersPercent = topHolders?.sumOf { it.percent ?: 0.0 } ?: 0.0
        if (topHoldersPercent > 0.50) {
            score -= 25
            warnings.add("🚨 Top 10 Holders: ${(topHoldersPercent * 100).toInt()}%")
        } else if (topHoldersPercent > 0.30) {
            score -= 10
            warnings.add("⚠️ Top 10 Holders: ${(topHoldersPercent * 100).toInt()}%")
        }

        val lpHolders = security.lp_holders
        val lpLocked = lpHolders?.any { it.is_locked == "1" } ?: false
        if (!lpLocked) {
            score -= 30
            warnings.add("🚨 Liquidity قفل نیست (خطر Rug Pull)")
        } else {
            val lockedDetails = lpHolders?.flatMap { it.locked_detail ?: emptyList() }
            val maxEndTime = lockedDetails?.maxOfOrNull { parseLockEndTime(it.end_time) ?: 0L }
            if (maxEndTime != null && maxEndTime > 0) {
                val lockDays = (maxEndTime - System.currentTimeMillis()) / (86400L * 1000L)
                if (lockDays < 30) {
                    score -= 15
                    warnings.add("⚠️ Liquidity فقط $lockDays روز قفل است")
                }
            }
        }

        if (security.is_open_source != "1") {
            score -= 20
            warnings.add("⚠️ Contract Open Source نیست")
        }

        return Triple(score.coerceIn(0, 100), warnings, "READY")
    }

    private fun scoreSolanaSecurity(security: SolanaTokenSecurity): Triple<Int?, List<String>, String> {
        val warnings = mutableListOf<String>()
        var score = 100

        if (anyToBool(security.mintable) == true) { score -= 25; warnings.add("🚨 Mintable") }
        if (anyToBool(security.freezable) == true) { score -= 20; warnings.add("🥶 Freezable") }
        if (anyToBool(security.closable) == true) { score -= 15; warnings.add("⚠️ Closable") }
        if (anyToBool(security.balance_mutable_authority) == true) { score -= 40; warnings.add("⚠️ Balance Mutable") }
        if (anyToBool(security.non_transferable) == true) { score -= 50; warnings.add("🚨 Non-transferable") }

        val transferFee = security.transfer_fee
        val feePercent = extractTransferFeePercent(transferFee)
        if (feePercent != null && feePercent > 10.0) {
            score -= 20
            warnings.add("⚠️ Transfer Fee بالا: ${feePercent.toInt()}%")
        }

        val creatorPercent = security.creator_percent ?: 0.0
        if (creatorPercent > 0.30) {
            score -= 25
            warnings.add("🚨 Creator ${(creatorPercent * 100).toInt()}% دارد")
        } else if (creatorPercent > 0.15) {
            score -= 10
            warnings.add("⚠️ Creator ${(creatorPercent * 100).toInt()}% دارد")
        }

        val topHolders = security.top_holders?.take(10)
        val topHoldersPercent = topHolders?.sumOf { it.percent ?: 0.0 } ?: 0.0
        if (topHoldersPercent > 0.50) {
            score -= 20
            warnings.add("🚨 Top 10 Holders: ${(topHoldersPercent * 100).toInt()}%")
        }

        if (anyToBool(security.trusted_token) == true || anyToBool(security.is_true_token) == true) {
            score += 10
        }

        return Triple(score.coerceIn(0, 100), warnings, "READY")
    }

    private fun extractTransferFeePercent(fee: Any?): Double? {
        if (fee == null) return null
        return when (fee) {
            is Number -> fee.toDouble()
            is String -> fee.trim().trimEnd('%').toDoubleOrNull()
            is Map<*, *> -> {
                val buy = (fee["buy_tax"] as? String)?.trimEnd('%')?.toDoubleOrNull() ?: 0.0
                val sell = (fee["sell_tax"] as? String)?.trimEnd('%')?.toDoubleOrNull() ?: 0.0
                maxOf(buy, sell).takeIf { it > 0 }
            }
            is List<*> -> fee.mapNotNull { extractTransferFeePercent(it) }.maxOrNull()
            else -> null
        }
    }
}
