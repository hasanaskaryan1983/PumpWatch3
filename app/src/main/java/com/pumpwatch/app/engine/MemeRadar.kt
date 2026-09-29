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

object MemeRadar {

    private const val TAG = "MemeRadar"

    // 🚀 Commit 92: اضافه شدن Arc Network
    private val CHAINS = listOf(
        "solana", "bsc", "base", "ethereum",
        "ton", "robinhood", "avalanche", "sei",
        "arc"
    )

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
        onProgress(5, "دریافت استخرهای داغ و تازه...")

        var anyOk = false
        val pools = mutableListOf<GeckoPool>()
        for (chain in CHAINS) {
            onProgress(10 + CHAINS.indexOf(chain) * 8, "اسکن زنجیره $chain...")
            try {
                val r = GeckoTerminal.api.trendingPools(chain).data
                if (r != null) {
                    anyOk = true
                    pools.addAll(r)
                }
            } catch (_: Exception) { }
            try {
                val n = GeckoTerminal.api.newPools(chain).data
                if (n != null) {
                    anyOk = true
                    pools.addAll(n)
                }
            } catch (_: Exception) { }
        }

        if (!anyOk) {
            lastScanFailed = true
            return emptyList()
        }

        onProgress(75, "تحلیل معیارهای اعتماد + Rug Safety Check...")
        val seen = mutableSetOf<String>()
        val unique = pools.filter { p -> seen.add(p.id ?: "") }
        val results = unique.mapNotNull { analyze(it) }
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

        if (h24 in -20.0..80.0) { score += 10; reasons.add("هنوز پارابولیک نشده ") }
        if (h24 > 200) score -= 15

        if (age in 24.0..720.0) { score += 10; reasons.add("توکن جاافتاده (۱-۳۰ روز)") }
        else score += 5

        if (score < 30) return null

        val fullName = a.name ?: "?"
        val sym = fullName.split("/").firstOrNull()?.trim() ?: "?"
        val chain = p.relationships?.network?.data?.id ?: "?"

        val contractAddress = p.relationships?.base_token?.data?.id?.substringAfter('_', "")
        val poolAddress = p.id?.substringAfter('_', "")

        val (rugScore, rugWarnings, securityStatus) = checkRugSafety(chain, contractAddress)

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
            score = if (rugScore == null) {
                reasons.add("️ امنیت نامشخص — حداکثر امتیاز ۵۰")
                score.coerceAtMost(50)
            } else {
                score
            }.coerceAtMost(100),
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
        if (anyToBool(security.freezable) == true) { score -= 20; warnings.add(" Freezable") }
        if (anyToBool(security.closable) == true) { score -= 15; warnings.add("⚠️ Closable") }
        if (anyToBool(security.balance_mutable_authority) == true) { score -= 40; warnings.add(" Balance Mutable") }
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
