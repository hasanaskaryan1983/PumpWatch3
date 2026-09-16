package com.pumpwatch.app.engine

import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.platformContractOf

/**
 * 🚀 Sprint 12 (C4b): موتور «برترین‌های ۱ سال»
 *
 * سؤال: در ۱۲ ماه گذشته کدام کوین‌ها بیشترین رشد را داشتند و روی کدام شبکه بودند؟
 *
 * منطق:
 * ۱. فیلتر ایمنی: market_cap >= $10M (حذف میم‌های مرده/اسکم/کم‌نقدینگی)
 * ۲. فیلتر برنده: change1y != null && change1y > 0
 * ۳. رتبه‌بندی: نزولی بر اساس change1y
 * ۴. شبکه: از platformMap (اولین platform = شبکهٔ اصلی) یا «بومی»
 *
 * خروجی:
 * - topList: ۳۰ کوین برتر با همهٔ فیلدهای لازم برای UI
 *   (نماد، قیمت فعلی، ATH، ATL، درصد رشد ۱ ساله، شبکه، کانترکت، رتبه)
 * - networkStats: آمار تجمیعی هر شبکه (تعداد برنده‌ها، میانگین رشد، بهترین کوین)
 *   تا کاربر ببیند «کدام اکوسیستم برندهٔ سیکل بوده»
 *
 * Pure function — بدون I/O، قابل تست واحد.
 */
object TopPerformersEngine {

    private const val MIN_MARKET_CAP = 10_000_000.0
    private const val TOP_N = 30

    data class TopPerformer(
        val coin: CoinMarket,
        val network: String,          // نام شبکهٔ اصلی یا "بومی"
        val networkEmoji: String,     // ایموجی شبکه برای UI
        val contract: String?,        // کانترکت توکن یا null اگر بومی
        val change1y: Double,         // درصد رشد ۱ ساله
        val currentPrice: Double,     // قیمت فعلی
        val ath: Double?,             // All-Time High (تمام تاریخ)
        val atl: Double?,             // All-Time Low (تمام تاریخ)
        val athChangePct: Double?,    // فاصله از ATH (منفی = زیر سقف)
        val atlChangePct: Double?,    // فاصله از ATL (مثبت = بالای کف)
        val rank: Int?                // رتبهٔ CoinGecko
    )

    data class NetworkStat(
        val network: String,
        val networkEmoji: String,
        val winnerCount: Int,         // تعداد کوین‌های برندهٔ این شبکه در top30
        val avgChange1y: Double,      // میانگین رشد ۱ سالهٔ برنده‌های این شبکه
        val bestSymbol: String,       // بهترین کوین این شبکه
        val bestChange1y: Double      // رشد ۱ سالهٔ بهترین کوین
    )

    data class Report(
        val topList: List<TopPerformer>,
        val networkStats: List<NetworkStat>,
        val totalScanned: Int,        // تعداد کوین‌های اسکن‌شده
        val winnersCount: Int         // تعداد کوین‌های برنده (change1y > 0)
    )

    /**
     * تحلیل اصلی — pure function
     */
    fun analyze(
        coins: List<CoinMarket>,
        platformMap: Map<String, Map<String, String>>
    ): Report {
        val winners = coins.filter { c ->
            val cap = c.market_cap
            val ch = c.change1y
            cap >= MIN_MARKET_CAP && ch != null && ch > 0
        }

        val sorted = winners.sortedByDescending { it.change1y ?: 0.0 }
        val top = sorted.take(TOP_N)

        val performers = top.map { c ->
            val platforms = platformMap[c.id]
            val networkId = platforms?.keys?.firstOrNull()
            val network = networkId ?: "native"
            val (networkName, networkEmoji) = networkLabel(network)
            val contract = platformContractOf(platformMap, c.id)

            TopPerformer(
                coin = c,
                network = networkName,
                networkEmoji = networkEmoji,
                contract = contract,
                change1y = c.change1y ?: 0.0,
                currentPrice = c.current_price,
                ath = c.ath,
                atl = c.atl,
                athChangePct = c.ath_change_percentage,
                atlChangePct = c.atl_change_percentage,
                rank = c.market_cap_rank
            )
        }

        // آمار شبکه‌ای: گروه‌بندی برنده‌ها بر اساس شبکه
        val grouped = performers.groupBy { it.network }
        val stats = grouped.map { (network, list) ->
            val best = list.maxByOrNull { it.change1y } ?: list.first()
            NetworkStat(
                network = network,
                networkEmoji = list.firstOrNull()?.networkEmoji ?: "⛓️",
                winnerCount = list.size,
                avgChange1y = list.map { it.change1y }.average(),
                bestSymbol = best.coin.symbol.uppercase(),
                bestChange1y = best.change1y
            )
        }.sortedByDescending { it.winnerCount * 1000 + it.avgChange1y }

        return Report(
            topList = performers,
            networkStats = stats,
            totalScanned = coins.size,
            winnersCount = winners.size
        )
    }

    /**
     * نگاشت network id → (نام نمایشی، ایموجی)
     * برای شبکه‌های ناشناخته: نام خام + ⛓️
     */
    private fun networkLabel(networkId: String): Pair<String, String> = when (networkId) {
        "native" -> "بومی (لایه ۱)" to "⛓️"
        "ethereum" -> "Ethereum" to "⚪"
        "binance-smart-chain", "bsc" -> "BSC" to "🟡"
        "base" -> "Base" to "🔵"
        "arbitrum-one", "arbitrum" -> "Arbitrum" to "🔷"
        "optimistic-ethereum", "optimism" -> "Optimism" to "🔴"
        "polygon-pos", "polygon" -> "Polygon" to "🟣"
        "avalanche", "avax" -> "Avalanche" to "🔺"
        "solana" -> "Solana" to "🟣"
        "the-open-network", "ton" -> "TON" to "🔵"
        "sui" -> "SUI" to "💧"
        "sei" -> "SEI" to "🌊"
        "robinhood" -> "Robinhood" to "🪽"
        "fantom" -> "Fantom" to "👻"
        "gnosis" -> "Gnosis" to "🦉"
        "celo" -> "Celo" to "🟢"
        "cronos" -> "Cronos" to "🔷"
        "kava" -> "Kava" to "☕"
        "metis-andromeda", "metis" -> "Metis" to "🏛️"
        else -> networkId to "⛓️"
    }
}
