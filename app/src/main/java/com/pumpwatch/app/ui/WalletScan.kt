package com.pumpwatch.app.ui

import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.Blockscout
import com.pumpwatch.app.data.DexScreenerClient
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.SuiClient
import com.pumpwatch.app.data.TonClient
import com.pumpwatch.app.data.bestPriceUsd
import com.pumpwatch.app.data.findCoinByContractOrId
import com.pumpwatch.app.data.solanaRaw
import com.pumpwatch.app.data.solanaTyped
import com.pumpwatch.app.data.suiAmount
import com.pumpwatch.app.data.tonAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

internal data class ChainCfg(val key: String, val label: String, val gt: String, val bs: String?, val kind: String)

internal val CHAINS = listOf(
    ChainCfg("auto", "Auto 🌐", "", null, "auto"),
    ChainCfg("solana", "Solana 🟣", "solana", null, "solana"),
    ChainCfg("eth", "Ethereum ⚪", "eth", "https://eth.blockscout.com/", "evm"),
    ChainCfg("base", "Base 🔵", "base", "https://base.blockscout.com/", "evm"),
    ChainCfg("bsc", "BNB 🟡", "bsc", null, "evm"),
    ChainCfg("arbitrum", "Arbitrum 🔷", "arbitrum", "https://arbitrum.blockscout.com/", "evm"),
    ChainCfg("optimism", "Optimism 🔴", "optimism", "https://optimism.blockscout.com/", "evm"),
    ChainCfg("polygon", "Polygon 🟣", "polygon_pos", "https://polygon.blockscout.com/", "evm"),
    ChainCfg("avalanche", "Avalanche 🔺", "avalanche", null, "evm"),
    ChainCfg("ton", "TON 🔵", "ton", null, "ton"),
    ChainCfg("sui", "SUI 💧", "sui", null, "sui"),
    ChainCfg("sei", "SEI 🌊", "sei", null, "evm"),
    ChainCfg("gnosis", "Gnosis 🦉", "gnosis", "https://gnosis.blockscout.com/", "evm"),
    ChainCfg("robinhood", "Robinhood 🪽", "robinhood", "https://robinhoodchain.blockscout.com/", "evm"),
    ChainCfg("arc", "Arc 🟣", "arc", null, "evm")
)

internal fun dexChainIdFor(key: String): String? = when (key) {
    "eth" -> "ethereum"
    "base" -> "base"
    "bsc" -> "bsc"
    "arbitrum" -> "arbitrum"
    "optimism" -> "optimism"
    "polygon" -> "polygon"
    "avalanche" -> "avax"
    "sei" -> "sei"
    "gnosis" -> "gnosis"
    "arc" -> "arc"
    else -> null
}

data class WalletHolding(
    val symbol: String,
    val name: String,
    val amount: Double,
    val price: Double?,
    val value: Double,
    val contract: String? = null,
    val host: String? = null,
    val dexChainId: String? = null,
    var firstBuyTs: Long? = null,
    var buyPrice: Double? = null
)

data class WalletTx(
    val dateText: String,
    val dateDay: String,
    val symbol: String,
    val amount: Double,
    val incoming: Boolean,
    var priceUsd: Double?
)

internal data class WalletScanResult(
    val holdings: List<WalletHolding>,
    val txs: List<WalletTx>,
    val total: Double,
    val info: String
)

internal const val WALLET_PARALLELISM = 5
internal const val WALLET_CHUNK_DELAY_MS = 200L

internal fun shortAddr(a: String): String =
    if (a.length > 12) "${a.take(6)}...${a.takeLast(4)}" else a

internal fun detectKind(a: String): String = when {
    a.startsWith("0x") && a.length == 42 -> "evm"
    a.startsWith("0x") && a.length >= 64 -> "sui"
    a.startsWith("EQ") || a.startsWith("UQ") || a.startsWith("kQ") || a.startsWith("0:") -> "ton"
    else -> "solana"
}

internal suspend fun runWalletScan(addr: String, cfg: ChainCfg): WalletScanResult = withContext(Dispatchers.IO) {
    val kind = if (cfg.kind == "auto") detectKind(addr) else cfg.kind
    var holdingsOut = emptyList<WalletHolding>()
    var txsOut = emptyList<WalletTx>()
    var totalOut = 0.0
    var infoMsg = ""

    when (kind) {
        "sui" -> {
            val bal = SuiClient.balances(addr)
            val arr = bal?.getAsJsonArray("result")
            if (arr == null) {
                infoMsg = "⚠️ اتصال به SUI RPC ناموفق بود — دوباره تلاش کن"
            } else {
                val coinsS = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                val pairs = mutableListOf<Pair<String, String>>()
                for (el in arr) {
                    val o = el.asJsonObject
                    val ct = o.get("coinType")?.asString ?: continue
                    val rb = o.get("balance")?.asString ?: continue
                    if ((rb.toLongOrNull() ?: 0L) > 0) pairs.add(ct to rb)
                }

                val held = mutableListOf<WalletHolding>()
                coroutineScope {
                    pairs.chunked(2).forEach { chunk ->
                        val part = chunk.map { (ct, rb) ->
                            async(Dispatchers.IO) {
                                try {
                                    if (ct == "0x2::sui::SUI") {
                                        val amt = suiAmount(rb) ?: return@async null
                                        WalletHolding("SUI", "Sui", amt, null, 0.0)
                                    } else {
                                        val md = SuiClient.coinMetadata(ct)?.getAsJsonObject("result")
                                        val sym = md?.get("symbol")?.asString ?: ct.take(8)
                                        var d = (md?.get("decimals")?.asInt ?: 9).coerceIn(0, 18)
                                        var amt = rb.toDoubleOrNull() ?: return@async null
                                        while (d > 0) { amt /= 10.0; d-- }
                                        WalletHolding(sym, "", amt, null, 0.0, contract = ct, dexChainId = "sui")
                                    }
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                        for (h in part) if (h.amount > 0.0) held.add(h)
                        if (pairs.size > 2) delay(500L)
                    }
                }

                val suiPx = coinsS.firstOrNull { it.id == "sui" }?.current_price
                    ?: coinsS.firstOrNull { it.symbol.equals("SUI", true) }?.current_price
                val finalList = held.map { h ->
                    if (h.symbol == "SUI" && suiPx != null && suiPx > 0) h.copy(price = suiPx, value = h.amount * suiPx) else h
                }.toMutableList()

                try {
                    val tb2 = SuiClient.txBlocks(addr, 50)
                    val dataArr2 = tb2?.getAsJsonObject("result")?.getAsJsonArray("data")
                    if (dataArr2 != null) {
                        val symByCt = mutableMapOf("0x2::sui::SUI" to "SUI")
                        for (h in held) h.contract?.let { symByCt[it] = h.symbol }
                        val firstTsBySym = mutableMapOf<String, Long>()
                        for (el in dataArr2) {
                            val obj = el.asJsonObject
                            val ts = obj.get("timestampMs")?.asString?.toLongOrNull() ?: continue
                            val bc = obj.getAsJsonArray("balanceChanges") ?: continue
                            for (b in bc) {
                                val o = b.asJsonObject
                                val ownerEl = o.get("owner")
                                val owner = if (ownerEl != null && ownerEl.isJsonObject) ownerEl.asJsonObject.get("AddressOwner")?.asString else null
                                if (owner != addr) continue
                                if ((o.get("amount")?.asString?.toLongOrNull() ?: 0L) <= 0L) continue
                                val ct = o.get("coinType")?.asString ?: continue
                                val sym = symByCt[ct] ?: continue
                                val cur = firstTsBySym[sym]
                                if (cur == null || ts < cur) firstTsBySym[sym] = ts
                            }
                        }
                        for (h in finalList) h.firstBuyTs = firstTsBySym[h.symbol]
                    }
                } catch (_: Exception) { }

                try {
                    val sdfD2 = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    for (h in finalList) {
                        val fts = h.firstBuyTs ?: continue
                        val coin = coinsS.firstOrNull { it.symbol.equals(h.symbol, true) } ?: continue
                        try {
                            val chart = ApiClient.getCoinChart(coin.id, days = 365)
                            val byDay = chart.prices.associate { p -> sdfD2.format(Date(p[0].toLong())) to p[1] }
                            h.buyPrice = byDay[sdfD2.format(Date(fts))]
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }

                try {
                    val unpriced = finalList.filter { it.price == null && !it.contract.isNullOrEmpty() }
                    coroutineScope {
                        unpriced.chunked(2).forEach { chunk ->
                            val part = chunk.map { h ->
                                async(Dispatchers.IO) {
                                    try {
                                        val c = h.contract!!
                                        val resp = DexScreenerClient.api.tokens(c)
                                        val px = bestPriceUsd(resp.pairs, "sui", c)
                                        if (px != null) h to px else null
                                    } catch (_: Exception) { null }
                                }
                            }.awaitAll().filterNotNull()
                            for ((h, px) in part) {
                                val idx = finalList.indexOf(h)
                                if (idx >= 0) finalList[idx] = finalList[idx].copy(price = px, value = h.amount * px)
                            }
                            if (unpriced.size > 2) delay(1000L)
                        }
                    }
                } catch (_: Exception) { }

                holdingsOut = finalList.sortedByDescending { it.value }
                totalOut = finalList.sumOf { it.value }
                txsOut = emptyList()
                infoMsg = if (finalList.isEmpty()) "😴 این کیف SUI خالیه" else "✅ SUI: ${finalList.size} کوین پیدا شد"
            }
        }

        "ton" -> {
            val acc = try { TonClient.api.account(addr) } catch (_: Exception) { null }
            val jets = try { TonClient.api.jettons(addr) } catch (_: Exception) { null }
            if (acc == null && jets == null) {
                infoMsg = "⚠️ اتصال به TonAPI ناموفق بود — دوباره تلاش کن"
            } else {
                val coinsT = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                val tonPx = coinsT.firstOrNull { it.id == "the-open-network" }?.current_price
                    ?: coinsT.firstOrNull { it.symbol.equals("TON", true) }?.current_price
                val list = mutableListOf<WalletHolding>()

                val tonBal = tonAmount(acc?.balance?.toString(), 9) ?: 0.0
                if (tonBal > 0.0) list.add(WalletHolding("TON", "Toncoin", tonBal, tonPx, tonBal * (tonPx ?: 0.0)))

                for (jb in (jets?.jettons ?: emptyList())) {
                    val meta = jb.jetton ?: continue
                    val mint = meta.address ?: continue
                    val dec = meta.decimals ?: 9
                    val amt = tonAmount(jb.balance, dec) ?: continue
                    if (amt <= 0.0) continue
                    list.add(WalletHolding(meta.symbol ?: mint.take(6), meta.name ?: "", amt, null, 0.0, contract = mint, dexChainId = "ton"))
                }

                val priceMap = mutableMapOf<String, Double>()
                val unpriced = list.filter { it.price == null && it.contract != null }
                coroutineScope {
                    unpriced.chunked(2).forEach { chunk ->
                        val part = chunk.map { h ->
                            async(Dispatchers.IO) {
                                val mint = h.contract ?: return@async null
                                try {
                                    val pools = GeckoTerminal.api.searchPools(mint).data
                                    val sol = pools?.firstOrNull {
                                        it.relationships?.network?.data?.id == "ton" &&
                                            it.relationships?.base_token?.data?.id?.contains(mint, true) == true
                                    }
                                    val px = sol?.attributes?.priceUsd?.toDoubleOrNull()?.takeIf { it > 0 }
                                    if (px != null) h.symbol to px else null
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                        for ((sym, px) in part) priceMap[sym] = px
                        if (unpriced.size > 2) delay(1000L)
                    }
                }

                val finalList = list.map { h ->
                    val px = h.price ?: priceMap[h.symbol]
                    if (px != null && px > 0) h.copy(price = px, value = h.amount * px) else h
                }.toMutableList()

                try {
                    val ev = TonClient.api.events(addr, limit = 100)
                    val firstTsBySym = mutableMapOf<String, Long>()
                    for (e in (ev.events ?: emptyList())) {
                        val ts = (e.timestamp ?: 0L) * 1000
                        if (ts <= 0L) continue
                        val actions = e.actions ?: continue
                        for (a in actions) {
                            val recv: String?
                            val sym: String?
                            if (a.TonTransfer != null) {
                                recv = a.TonTransfer.receiver?.address
                                sym = "TON"
                            } else if (a.JettonTransfer != null) {
                                recv = a.JettonTransfer.receiver?.address
                                sym = a.JettonTransfer.jetton?.symbol
                            } else continue
                            if (recv != addr || sym == null) continue
                            val cur = firstTsBySym[sym]
                            if (cur == null || ts < cur) firstTsBySym[sym] = ts
                        }
                    }
                    for (h in finalList) h.firstBuyTs = firstTsBySym[h.symbol]
                } catch (_: Exception) { }

                try {
                    val sdfD = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    for (h in finalList) {
                        val fts = h.firstBuyTs ?: continue
                        val coin = coinsT.firstOrNull { it.symbol.equals(h.symbol, true) } ?: continue
                        try {
                            val chart = ApiClient.getCoinChart(coin.id, days = 365)
                            val byDay = chart.prices.associate { p -> sdfD.format(Date(p[0].toLong())) to p[1] }
                            h.buyPrice = byDay[sdfD.format(Date(fts))]
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }

                try {
                    val stillUnpriced = finalList.filter { it.price == null && !it.contract.isNullOrEmpty() }
                    coroutineScope {
                        stillUnpriced.chunked(2).forEach { chunk ->
                            val part = chunk.map { h ->
                                async(Dispatchers.IO) {
                                    try {
                                        val c = h.contract!!
                                        val resp = DexScreenerClient.api.tokens(c)
                                        val px = bestPriceUsd(resp.pairs, "ton", c)
                                        if (px != null) h to px else null
                                    } catch (_: Exception) { null }
                                }
                            }.awaitAll().filterNotNull()
                            for ((h, px) in part) {
                                val idx = finalList.indexOf(h)
                                if (idx >= 0) finalList[idx] = finalList[idx].copy(price = px, value = h.amount * px)
                            }
                            if (stillUnpriced.size > 2) delay(1000L)
                        }
                    }
                } catch (_: Exception) { }

                holdingsOut = finalList.sortedByDescending { it.value }
                totalOut = finalList.sumOf { it.value }
                txsOut = emptyList()
                infoMsg = "✅ TON: ${finalList.size} توکن پیدا شد"
            }
        }

        "solana" -> {
            val body = mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "getTokenAccountsByOwner",
                "params" to listOf(
                    addr,
                    mapOf("programId" to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
                    mapOf("encoding" to "jsonParsed")
                )
            )
            val res = solanaTyped(body)
            val raw = res?.result?.value?.mapNotNull { a ->
                val inf = a.account?.data?.parsed?.info ?: return@mapNotNull null
                val mint = inf.mint ?: return@mapNotNull null
                val amt = inf.tokenAmount?.uiAmountString?.toDoubleOrNull() ?: 0.0
                if (amt <= 0.0) null else Triple(mint, amt, a.pubkey ?: "")
            } ?: emptyList()

            val list = coroutineScope {
                raw.take(50).chunked(WALLET_PARALLELISM).flatMap { chunk ->
                    val part = chunk.map { (mint, amt, acc) ->
                        async(Dispatchers.IO) {
                            try {
                                val t = GeckoPrice.api.tokenInfo("solana", mint).data?.attributes
                                val px = t?.price_usd?.toDoubleOrNull()
                                val h = WalletHolding(t?.symbol ?: mint.take(6), t?.name ?: "", amt, px, amt * (px ?: 0.0), contract = mint, dexChainId = "solana")
                                try {
                                    if (acc.isNotEmpty()) {
                                        val sg = solanaRaw(mapOf(
                                            "jsonrpc" to "2.0",
                                            "id" to 1,
                                            "method" to "getSignaturesForAddress",
                                            "params" to listOf(acc, mapOf("limit" to 1000))
                                        ))
                                        val oldest = sg?.result?.asJsonArray?.lastOrNull()?.asJsonObject
                                        val fts = (oldest?.get("blockTime")?.asLong ?: 0L) * 1000
                                        if (fts > 0) h.firstBuyTs = fts
                                    }
                                } catch (_: Exception) { }
                                h
                            } catch (_: Exception) { null }
                        }
                    }.awaitAll().filterNotNull()
                    delay(WALLET_CHUNK_DELAY_MS)
                    part
                }.toMutableList()
            }

            try {
                val coinsH = ApiClient.getTop1000Coins()
                val sdfD = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                for (h in list) {
                    val coin = coinsH.firstOrNull { it.symbol.equals(h.symbol, true) } ?: continue
                    try {
                        val chart = ApiClient.getCoinChart(coin.id, days = 365)
                        val byDay = chart.prices.associate { p -> sdfD.format(Date(p[0].toLong())) to p[1] }
                        h.buyPrice = h.firstBuyTs?.let { byDay[sdfD.format(Date(it))] }
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }

            try {
                val unpriced = list.filter { it.price == null && !it.contract.isNullOrEmpty() }
                coroutineScope {
                    unpriced.chunked(2).forEach { chunk ->
                        val part = chunk.map { h ->
                            async(Dispatchers.IO) {
                                try {
                                    val c = h.contract!!
                                    val resp = DexScreenerClient.api.tokens(c)
                                    val px = bestPriceUsd(resp.pairs, "solana", c)
                                    if (px != null) h to px else null
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                        for ((h, px) in part) {
                            val idx = list.indexOf(h)
                            if (idx >= 0) list[idx] = list[idx].copy(price = px, value = h.amount * px)
                        }
                        if (unpriced.size > 2) delay(1000L)
                    }
                }
            } catch (_: Exception) { }

            holdingsOut = list.sortedByDescending { it.value }
            totalOut = list.sumOf { it.value }
            txsOut = emptyList()
            infoMsg = "✅ Solana: ${list.size} توکن پیدا شد"
        }

        else -> {
            val hosts = if (cfg.kind == "auto") CHAINS.filter { it.kind == "evm" && it.bs != null }
            else listOf(cfg).filter { it.bs != null }
            if (hosts.isEmpty()) {
                return@withContext WalletScanResult(emptyList(), emptyList(), 0.0, "⚠️ منبع دادهٔ این شبکه فعلاً قطع است")
            }

            val coins = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
            val platformMap = try { ApiClient.getPlatformMap() } catch (_: Exception) { null }

            data class HostResult(val holdings: List<WalletHolding>, val cfg: ChainCfg?)
            val hostResults = coroutineScope {
                hosts.map { h ->
                    async(Dispatchers.IO) {
                        try {
                            val bs = Blockscout.api(h.bs!!)
                            val tokens = bs.tokenList("account", "tokenlist", addr).result
                                ?: return@async HostResult(emptyList(), null)

                            val list = tokens.filter { (it.balance?.toDoubleOrNull() ?: 0.0) > 0 }
                                .take(50)
                                .chunked(WALLET_PARALLELISM)
                                .flatMap { chunk ->
                                    val part = chunk.map { t ->
                                        async(Dispatchers.IO) {
                                            try {
                                                val dec = t.decimals?.toDoubleOrNull() ?: 18.0
                                                val amt = (t.balance?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                                                val contract = t.contractAddress ?: return@async null
                                                var px = try {
                                                    GeckoPrice.api.tokenInfo(h.gt, contract).data?.attributes?.price_usd?.toDoubleOrNull()
                                                } catch (_: Exception) { null }
                                                if (px == null || px <= 0) {
                                                    val (coinMatch, _) = findCoinByContractOrId(
                                                        coins = coins,
                                                        contract = contract,
                                                        chain = h.key,
                                                        ticker = t.symbol,
                                                        platformMap = platformMap
                                                    )
                                                    px = coinMatch?.current_price
                                                }
                                                WalletHolding(
                                                    symbol = "${t.symbol ?: "?"}·${h.key}",
                                                    name = t.name ?: "",
                                                    amount = amt,
                                                    price = px,
                                                    value = amt * (px ?: 0.0),
                                                    contract = contract,
                                                    host = h.bs,
                                                    dexChainId = dexChainIdFor(h.key)
                                                )
                                            } catch (_: Exception) { null }
                                        }
                                    }.awaitAll().filterNotNull()
                                    delay(WALLET_CHUNK_DELAY_MS)
                                    part
                                }.toMutableList()

                            if (list.isNotEmpty()) {
                                list.chunked(WALLET_PARALLELISM).forEach { chunk ->
                                    chunk.map { hd ->
                                        async(Dispatchers.IO) {
                                            try {
                                                val c = hd.contract ?: return@async
                                                val asc = Blockscout.api(hd.host!!).tokenTx("account", "tokentx", addr, "asc").result
                                                val first = asc?.firstOrNull { (it.contractAddress ?: "").equals(c, true) }
                                                val fts = (first?.timeStamp?.toLongOrNull() ?: 0L) * 1000
                                                if (fts > 0) hd.firstBuyTs = fts
                                            } catch (_: Exception) { }
                                        }
                                    }.awaitAll()
                                    delay(WALLET_CHUNK_DELAY_MS)
                                }
                            }

                            HostResult(list, if (list.isNotEmpty()) h else null)
                        } catch (_: Exception) {
                            HostResult(emptyList(), null)
                        }
                    }
                }.awaitAll()
            }

            val allHold = mutableListOf<WalletHolding>()
            var txHost: ChainCfg? = null
            for (r in hostResults) {
                if (r.holdings.isNotEmpty()) {
                    allHold.addAll(r.holdings)
                    if (txHost == null) txHost = r.cfg
                }
            }

            try {
                val sdfD = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                for (hd in allHold) {
                    val sym = hd.symbol.substringBefore('·')
                    val coin = coins.firstOrNull { it.symbol.equals(sym, true) } ?: continue
                    try {
                        val chart = ApiClient.getCoinChart(coin.id, days = 365)
                        val byDay = chart.prices.associate { p -> sdfD.format(Date(p[0].toLong())) to p[1] }
                        hd.buyPrice = hd.firstBuyTs?.let { byDay[sdfD.format(Date(it))] }
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }

            try {
                val unpriced = allHold.filter { it.price == null && !it.contract.isNullOrEmpty() && !it.dexChainId.isNullOrEmpty() }
                coroutineScope {
                    unpriced.chunked(2).forEach { chunk ->
                        val part = chunk.map { h ->
                            async(Dispatchers.IO) {
                                try {
                                    val c = h.contract!!
                                    val resp = DexScreenerClient.api.tokens(c)
                                    val px = bestPriceUsd(resp.pairs, h.dexChainId!!, c)
                                    if (px != null) h to px else null
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                        for ((h, px) in part) {
                            val idx = allHold.indexOf(h)
                            if (idx >= 0) allHold[idx] = allHold[idx].copy(price = px, value = h.amount * px)
                        }
                        if (unpriced.size > 2) delay(1000L)
                    }
                }
            } catch (_: Exception) { }

            holdingsOut = allHold.sortedByDescending { it.value }
            totalOut = allHold.sumOf { it.value }

            val th = txHost
            if (th != null) {
                val all = try { Blockscout.api(th.bs!!).tokenTx("account", "tokentx", addr, "desc").result } catch (_: Exception) { null }
                val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val sdfShow = SimpleDateFormat("MM/dd", Locale.US)
                val rawTxs = all?.take(100)?.mapNotNull { t ->
                    val ts = (t.timeStamp?.toLongOrNull() ?: return@mapNotNull null) * 1000
                    val dec = t.tokenDecimal?.toDoubleOrNull() ?: 18.0
                    val amt = (t.value?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                    WalletTx(
                        dateText = sdfShow.format(Date(ts)),
                        dateDay = sdfDay.format(Date(ts)),
                        symbol = t.tokenSymbol ?: "?",
                        amount = amt,
                        incoming = (t.to ?: "").equals(addr, true),
                        priceUsd = null
                    )
                } ?: emptyList()

                try {
                    for (sym in rawTxs.map { it.symbol }.distinct()) {
                        val coin = coins.firstOrNull { it.symbol.equals(sym, true) } ?: continue
                        try {
                            val chart = ApiClient.getCoinChart(coin.id, days = 365)
                            val byDay = chart.prices.associate { p -> sdfDay.format(Date(p[0].toLong())) to p[1] }
                            rawTxs.forEach { b -> if (b.symbol.equals(sym, true)) b.priceUsd = byDay[b.dateDay] }
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
                txsOut = rawTxs
            }

            infoMsg = if (allHold.isEmpty()) {
                if (addr.startsWith("0x") && addr.length == 42)
                    "😴 موجودی توکنی روی شبکه‌های EVM فعال پیدا نشد"
                else "😴 موجودی پیدا نشد (آدرس یا شبکه رو چک کن)"
            } else "✅ ${allHold.size} توکن روی ${hosts.size} شبکه بررسی شد"
        }
    }

    WalletScanResult(holdingsOut, txsOut, totalOut, infoMsg)
}
