package com.pumpwatch.app.data

/**
 * 🚀 Commit 84 (فاز ۳ — W4 + L1 + W5): lookup کوین با contract یا id یا ticker.
 *
 * باگ قبلی: همه‌جا `coins.firstOrNull { symbol == sym }` استفاده می‌شد.
 *   تیکرها یکتا نیستند (چند توکن PEPE روی Base/Ethereum/...).
 *   توکن اسکم با تیکر PEPE روی Base → رتبه و مارکت‌کپ PEPE واقعی (ERC-20) را می‌گرفت.
 *   چک «لیست‌شده در CoinGecko» اشتباه سبز می‌شد.
 *
 * حالا:
 *   1. contract + chain (دقیق‌ترین) — reverse lookup از platform map
 *   2. CoinGecko id (اگر موجود باشد)
 *   3. ticker (fallback، با ambiguous = true)
 *
 * @param coins لیست کوین‌ها (از ApiClient.getTop1000Coins)
 * @param contract آدرس کانترکت توکن (اختیاری)
 * @param chain نام زنجیره (اختیاری)
 * @param ticker نماد ارز (fallback)
 * @param platformMap از ApiClient.getPlatformMap() — null اگر در دسترس نیست
 * @return Pair(coin, ambiguous) — ambiguous = true اگر از ticker fallback شد
 */
suspend fun findCoinByContractOrId(
    coins: List<CoinMarket>,
    contract: String?,
    chain: String?,
    ticker: String?,
    platformMap: Map<String, Map<String, String>>? = null
): Pair<CoinMarket?, Boolean> {
    if (coins.isEmpty()) return null to false

    // ۱. Contract match (دقیق‌ترین)
    if (!contract.isNullOrEmpty() && !chain.isNullOrEmpty() && platformMap != null) {
        val chainLower = chain.lowercase()
        for ((coinId, platforms) in platformMap) {
            // چک همهٔ aliases احتمالی chain (eth/ethereum، bsc/binance-smart-chain، ...)
            val contractOnChain = platforms[chainLower]
                ?: platforms[chain]
                ?: platformContractOfAlias(platforms, chainLower)
            if (contractOnChain != null && contractOnChain.equals(contract, ignoreCase = true)) {
                val match = coins.firstOrNull { it.id == coinId }
                if (match != null) return match to false
            }
        }
    }

    // ۲. Fallback به ticker (ambiguous — ممکن است توکن اشتباه را برگرداند)
    if (!ticker.isNullOrEmpty()) {
        val match = coins.firstOrNull { it.symbol.equals(ticker, ignoreCase = true) }
        if (match != null) return match to true
    }

    return null to false
}

/**
 * تلاش برای match با aliases رایج chain name.
 *
 * مثال‌ها:
 *   - chain = "eth" → می‌گردد دنبال "ethereum"
 *   - chain = "bsc" → می‌گردد دنبال "binance-smart-chain"
 */
private fun platformContractOfAlias(
    platforms: Map<String, String>,
    chainHint: String
): String? {
    val aliases = when (chainHint) {
        "ethereum", "eth" -> listOf("ethereum", "eth")
        "bsc" -> listOf("binance-smart-chain", "bsc")
        "base" -> listOf("base")
        "arbitrum" -> listOf("arbitrum-one", "arbitrum")
        "optimism" -> listOf("optimistic-ethereum", "optimism")
        "polygon", "polygon_pos" -> listOf("polygon-pos", "polygon")
        "avalanche", "avax" -> listOf("avalanche", "avax")
        "solana" -> listOf("solana")
        "ton" -> listOf("the-open-network", "ton")
        "sui" -> listOf("sui")
        "sei" -> listOf("sei")
        "gnosis" -> listOf("gnosis")
        "cronos" -> listOf("cronos")
        "fantom" -> listOf("fantom")
        "celo" -> listOf("celo")
        "aurora" -> listOf("aurora")
        "harmony" -> listOf("harmony-shard-0")
        "moonbeam" -> listOf("moonbeam")
        "moonriver" -> listOf("moonriver")
        else -> listOf(chainHint)
    }
    for (a in aliases) {
        val v = platforms[a]
        if (!v.isNullOrBlank()) return v
    }
    return null
}
