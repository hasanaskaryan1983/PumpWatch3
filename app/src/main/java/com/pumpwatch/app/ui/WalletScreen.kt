package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.Blockscout
import com.pumpwatch.app.data.DexScreenerClient
import com.pumpwatch.app.data.GeckoOhlcv
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.GtTrade
import com.pumpwatch.app.data.RpcKeyStore
import com.pumpwatch.app.data.SecureStorage
import com.pumpwatch.app.data.SuiClient
import com.pumpwatch.app.data.TonClient
import com.pumpwatch.app.data.bestPriceUsd
import com.pumpwatch.app.data.solanaRaw
import com.pumpwatch.app.data.solanaTyped
import com.pumpwatch.app.data.suiAmount
import com.pumpwatch.app.data.tonAmount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.pow

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VPurple = Color(0xFFCE93D8)
private val VOrange = Color(0xFFFFA726)
private val VCard = Color(0xFF1A2230)

private const val WALLET_PARALLELISM = 5
private const val WALLET_CHUNK_DELAY_MS = 200L

private val forensicsSigsCache = mutableMapOf<String, List<Pair<String, Long>>>()
private val forensicsDepthCache = mutableMapOf<String, Long>()
private val forensicsParsedCache = mutableMapOf<String, List<Triple<String, Double, Long>>>()

private data class ChainCfg(val key: String, val label: String, val gt: String, val bs: String?, val kind: String)

private val CHAINS = listOf(
    ChainCfg("auto", "Auto 🌐", "", null, "auto"),
    ChainCfg("solana", "Solana 🟣", "solana", null, "solana"),
    ChainCfg("eth", "Ethereum ⚪", "eth", "https://eth.blockscout.com/", "evm"),
    ChainCfg("base", "Base 🔵", "base", "https://base.blockscout.com/", "evm"),
    ChainCfg("bsc", "BNB 🟡", "bsc", null, "evm"),
    ChainCfg("arbitrum", "Arbitrum 🔷", "arbitrum", "https://arbitrum.blockscout.com/", "evm"),
    ChainCfg("optimism", "Optimism 🔴", "optimism", "https://optimism.blockscout.com/", "evm"),
    ChainCfg("polygon", "Polygon ", "polygon_pos", "https://polygon.blockscout.com/", "evm"),
    ChainCfg("avalanche", "Avalanche 🔺", "avalanche", null, "evm"),
    ChainCfg("ton", "TON 🔵", "ton", null, "ton"),
    ChainCfg("sui", "SUI 💧", "sui", null, "sui"),
    ChainCfg("sei", "SEI 🌊", "sei", null, "evm"),
    ChainCfg("gnosis", "Gnosis 🦉", "gnosis", "https://gnosis.blockscout.com/", "evm"),
    ChainCfg("robinhood", "Robinhood 🪽", "robinhood", "https://robinhoodchain.blockscout.com/", "evm")
)

private fun dexChainIdFor(key: String): String? = when (key) {
    "eth" -> "ethereum"
    "base" -> "base"
    "bsc" -> "bsc"
    "arbitrum" -> "arbitrum"
    "optimism" -> "optimism"
    "polygon" -> "polygon"
    "avalanche" -> "avax"
    "sei" -> "sei"
    "gnosis" -> "gnosis"
    else -> null
}

// ✅ حذف private از اینجا
data class WalletHolding(
    val symbol: String, val name: String, val amount: Double, val price: Double?, val value: Double,
    val contract: String? = null, val host: String? = null,
    val dexChainId: String? = null,
    var firstBuyTs: Long? = null, var buyPrice: Double? = null
)

// ✅ حذف private از اینجا
data class WalletTx(val dateText: String, val dateDay: String, val symbol: String, val amount: Double, val incoming: Boolean, var priceUsd: Double?)

private data class SusWallet(
    val addr: String, val boughtUsd: Double, val avgEntry: Double,
    val firstBuyText: String, val txCount: Int, val soldUsd: Double,
    val statusText: String, val multiplier: Double,
    val bottomTag: Boolean = false,
    val maxSingleUsd: Double = 0.0
)

private fun num(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private fun shortAddr(a: String): String = if (a.length > 12) "${a.take(6)}...${a.takeLast(4)}" else a

private fun detectKind(a: String): String = when {
    a.startsWith("0x") && a.length == 42 -> "evm"
    a.startsWith("0x") && a.length >= 64 -> "sui"
    a.startsWith("EQ") || a.startsWith("UQ") || a.startsWith("kQ") || a.startsWith("0:") -> "ton"
    else -> "solana"
}

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sdfBuy = SimpleDateFormat("yyyy/MM/dd", Locale.US)

    var subTab by remember { mutableStateOf(0) }
    var infoText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        FavStore.load(context)
        scope.launch { scanStarred(context) }
    }

    var chain by remember { mutableStateOf(CHAINS[0]) }

    var address by remember { mutableStateOf("") }
    var holdings by remember { mutableStateOf<List<WalletHolding>>(emptyList()) }
    var txs by remember { mutableStateOf<List<WalletTx>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0.0) }
    var info by remember { mutableStateOf("") }

    fun saveStar(addr: String, symbol: String, score: Int, note: String, boughtUsd: Double, maxSingleUsd: Double, soldUsd: Double, txCount: Int) {
        FavStore.load(context)
        val multiplier = score / 10.0
        FavStore.addFav(
            ctx = context,
            addr = addr,
            note = note,
            starred = true,
            symbol = symbol,
            role = note,
            huntedAtMs = System.currentTimeMillis(),
            multiplier = multiplier,
            boughtUsd = boughtUsd,
            maxSingleUsd = maxSingleUsd,
            soldUsd = soldUsd,
            txCount = txCount
        )
        info = "❤️ نهنگ «$symbol • $note» با ضریب ${String.format("%.1f", multiplier)}x و خرید ${String.format(Locale.US, "$%,.0f", boughtUsd)} به پرونده اضافه شد"
    }

    fun check() {
        val addr = address.trim().replace(Regex("[^A-Za-z0-9]"), "")
        if (addr.isEmpty()) { error = "❌ آدرس کیف پول رو وارد کن"; return }
        val cfg = chain
        scope.launch {
            loading = true; error = null; holdings = emptyList(); txs = emptyList()
            info = "🔍 در حال اسکن کیف پول..."
            try {
                withContext(Dispatchers.IO) {
                    val kind = if (cfg.kind == "auto") detectKind(addr) else cfg.kind
                    when (kind) {
                        "sui" -> {
                            val bal = SuiClient.balances(addr)
                            val arr = bal?.getAsJsonArray("result")
                            if (arr == null) {
                                info = "⚠️ اتصال به SUI RPC ناموفق بود — دوباره تلاش کن"
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
                                val suiPx = coinsS.firstOrNull { it.symbol.equals("SUI", true) }?.current_price
                                val finalList = held.map { h ->
                                    if (h.symbol == "SUI" && suiPx != null && suiPx > 0) h.copy(price = suiPx, value = h.amount * suiPx) else h
                                }.toMutableList()

                                try {
                                    val tb2 = SuiClient.txBlocks(addr, 50)
                                    val dataArr2 = tb2?.getAsJsonObject("result")?.getAsJsonArray("data")
                                    if (dataArr2 != null) {
                                        val symByCt = mutableMapOf("0x2::sui::SUI" to "SUI")
                                        for (h in held) { h.contract?.let { symByCt[it] = h.symbol } }
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
                                                if (idx >= 0) {
                                                    val old = finalList[idx]
                                                    finalList[idx] = old.copy(price = px, value = old.amount * px)
                                                }
                                            }
                                            if (unpriced.size > 2) delay(1000L)
                                        }
                                    }
                                } catch (_: Exception) { }

                                holdings = finalList.sortedByDescending { it.value }
                                total = finalList.sumOf { it.value }
                                txs = emptyList()
                                info = if (finalList.isEmpty()) "😴 این کیف SUI خالیه" else "✅ SUI: ${finalList.size} کوین پیدا شد"
                            }
                        }
                        "ton" -> {
                            val acc = try { TonClient.api.account(addr) } catch (_: Exception) { null }
                            val jets = try { TonClient.api.jettons(addr) } catch (_: Exception) { null }
                            if (acc == null && jets == null) {
                                info = "⚠️ اتصال به TonAPI ناموفق بود — دوباره تلاش کن"
                            } else {
                                val coinsT = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                                val tonPx = coinsT.firstOrNull { it.symbol.equals("TON", true) }?.current_price
                                val list = mutableListOf<WalletHolding>()

                                val tonBal = tonAmount(acc?.balance?.toString(), 9) ?: 0.0
                                if (tonBal > 0.0) {
                                    list.add(WalletHolding("TON", "Toncoin", tonBal, tonPx, tonBal * (tonPx ?: 0.0)))
                                }

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
                                                    if (px != null) (h.symbol to px) else null
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
                                            if (a.TonTransfer != null) { recv = a.TonTransfer.receiver?.address; sym = "TON" }
                                            else if (a.JettonTransfer != null) { recv = a.JettonTransfer.receiver?.address; sym = a.JettonTransfer.jetton?.symbol }
                                            else continue
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
                                                if (idx >= 0) {
                                                    val old = finalList[idx]
                                                    finalList[idx] = old.copy(price = px, value = old.amount * px)
                                                }
                                            }
                                            if (stillUnpriced.size > 2) delay(1000L)
                                        }
                                    }
                                } catch (_: Exception) { }

                                holdings = finalList.sortedByDescending { it.value }
                                total = finalList.sumOf { it.value }
                                txs = emptyList()
                                info = "✅ TON: ${finalList.size} توکن پیدا شد"
                            }
                        }
                        "solana" -> {
                            val body = mapOf(
                                "jsonrpc" to "2.0", "id" to 1,
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
                                                            "jsonrpc" to "2.0", "id" to 1,
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
                                            if (idx >= 0) {
                                                val old = list[idx]
                                                list[idx] = old.copy(price = px, value = old.amount * px)
                                            }
                                        }
                                        if (unpriced.size > 2) delay(1000L)
                                    }
                                }
                            } catch (_: Exception) { }

                            holdings = list.sortedByDescending { it.value }
                            total = list.sumOf { it.value }
                            txs = emptyList()
                            info = "✅ Solana: ${list.size} توکن پیدا شد"
                        }
                        else -> {
                            val hosts = if (cfg.kind == "auto") CHAINS.filter { it.kind == "evm" && it.bs != null }
                            else listOf(cfg).filter { it.bs != null }
                            if (hosts.isEmpty()) { info = "⚠️ منبع دادهٔ این شبکه فعلاً قطع است (Blockscout غیرفعال — ممیزی 2026-09-16)"; return@withContext }

                            val coins = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }

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
                                                                if (px == null || px <= 0) px = coins.firstOrNull { it.symbol.equals(t.symbol ?: "", true) }?.current_price
                                                                WalletHolding("${t.symbol ?: "?"}·${h.key}", t.name ?: "", amt, px, amt * (px ?: 0.0), contract = contract, host = h.bs, dexChainId = dexChainIdFor(h.key))
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
                                            if (idx >= 0) {
                                                val old = allHold[idx]
                                                allHold[idx] = old.copy(price = px, value = old.amount * px)
                                            }
                                        }
                                        if (unpriced.size > 2) delay(1000L)
                                    }
                                }
                            } catch (_: Exception) { }

                            holdings = allHold.sortedByDescending { it.value }
                            total = allHold.sumOf { it.value }

                            val th = txHost
                            if (th != null) {
                                val all = try { Blockscout.api(th.bs!!).tokenTx("account", "tokentx", addr, "desc").result } catch (_: Exception) { null }
                                val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                val sdfShow = SimpleDateFormat("MM/dd", Locale.US)
                                val rawTxs = all?.take(100)?.mapNotNull { t ->
                                    val ts = (t.timeStamp?.toLongOrNull() ?: return@mapNotNull null) * 1000
                                    val dec = t.tokenDecimal?.toDoubleOrNull() ?: 18.0
                                    val amt = (t.value?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                                    WalletTx(sdfShow.format(Date(ts)), sdfDay.format(Date(ts)), t.tokenSymbol ?: "?", amt, (t.to ?: "").equals(addr, true), null)
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
                                txs = rawTxs
                            }
                            info = if (allHold.isEmpty()) {
                                if (addr.startsWith("0x") && addr.length == 42)
                                    "😴 موجودی توکنی روی ۷ شبکهٔ EVM فعال پیدا نشد • اگر آدرس BSC/Avax/Sei است: منبع این شبکه‌ها قطع شده (🚫)"
                                else "😴 موجودی پیدا نشد (آدرس یا شبکه رو چک کن)"
                            }
                            else "✅ ${allHold.size} توکن روی ${hosts.size} شبکه بررسی شد"
                        }
                    }
                }
            } catch (t: Throwable) {
                error = "⚠️ خطا: ${t.message}"
            }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("👛 کارآگاه کیف پول", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VGreen)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = subTab == 0, onClick = { subTab = 0 }, label = { Text("⚙️ موتورها", fontSize = 11.sp) })
                FilterChip(selected = subTab == 1, onClick = { subTab = 1 }, label = { Text("❤️ مورد پسند", fontSize = 11.sp) })
                FilterChip(selected = subTab == 2, onClick = { subTab = 2 }, label = { Text(if (FavStore.unread() > 0) "⚡️ هشدار 🔴" else "⚡️ هشدار", fontSize = 11.sp) })
                FilterChip(selected = subTab == 3, onClick = { subTab = 3 }, label = { Text("♻️ سطل", fontSize = 11.sp) })
                FilterChip(selected = subTab == 4, onClick = { subTab = 4 }, label = { Text("🔒 حریم", fontSize = 11.sp) })
            }
            if (infoText.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(infoText, fontSize = 10.sp, color = VGreen, modifier = Modifier.weight(1f))
                        Button(onClick = { infoText = "" }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("✖", fontSize = 10.sp) }
                    }
                }
            }
        }

        when (subTab) {
            1 -> Box(modifier = Modifier.weight(1f)) { FavoritesPage() }
            2 -> Box(modifier = Modifier.weight(1f)) { AlertsPage() }
            3 -> Box(modifier = Modifier.weight(1f)) { TrashPage() }
            4 -> Box(modifier = Modifier.weight(1f)) { PrivacyCenterScreen() }
            else -> Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CHAINS.forEach { c ->
                        FilterChip(selected = chain.key == c.key, onClick = { chain = c }, label = { Text(c.label, fontSize = 10.sp) })
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔍 موتور ۱: بررسی کیف پول مشکوک (Auto = تشخیص خودکار شبکه)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VBlue, modifier = Modifier.weight(1f))
                            Button(onClick = { infoText = "موتور ۱: آدرس کیف بده → موجودی فعلی همه توکن‌ها + کانترکت با کپی + تاریخ/قیمت اولین مشاهده. سوال: الان داخلش چیه؟" }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("ℹ️", fontSize = 10.sp) }
                        }
                        TextField(value = address, onValueChange = { address = it },
                            placeholder = { Text("آدرس کیف پول...", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                        Button(onClick = { check() }, enabled = !loading,
                            colors = ButtonDefaults.buttonColors(containerColor = VBlue),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (loading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                            Text("🔍 بررسی کیف پول", fontSize = 12.sp)
                        }
                        if (info.isNotEmpty()) Text(info, fontSize = 10.sp, color = VGreen)
                    }
                }

                if (error != null) Text(error ?: "", fontSize = 10.sp, color = VRed)

                if (holdings.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("💰 ارزش کل", fontSize = 10.sp, color = VGray)
                            Text(String.format(Locale.US, "$%,.2f", total), fontSize = 20.sp, fontWeight = FontWeight.Black, color = VGreen)
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    ) {
                        items(holdings) { h ->
                            Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(h.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (h.price != null && h.price > 0) {
                                                Text("مقدار: ${String.format(Locale.US, "%.4f", h.amount)} • قیمت: ${String.format(Locale.US, "$%.6f", h.price)}", fontSize = 9.sp, color = VGray)
                                            } else {
                                                Text("مقدار: ${String.format(Locale.US, "%.4f", h.amount)} • قیمت: ❓ نامشخص", fontSize = 9.sp, color = VGold, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Text(String.format(Locale.US, "$%,.2f", h.value), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VGreen)
                                    }
                                    if (h.firstBuyTs != null) {
                                        Text(
                                            "🕐 اولین مشاهده در داده موجود: ${sdfBuy.format(Date(h.firstBuyTs!!))} • قیمت تقریبی آن روز: ${if (h.buyPrice != null && h.buyPrice!! > 0) String.format(Locale.US, "$%.6f", h.buyPrice!!) else "توی CoinGecko لیست نشده"}",
                                            fontSize = 9.sp, color = VGold, fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (h.contract != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("کانترکت: ${h.contract}", fontSize = 8.sp, color = VGray, modifier = Modifier.weight(1f))
                                            Button(onClick = {
                                                try {
                                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("contract", h.contract))
                                                    info = "📋 کانترکت کپی شد — توی CoinGecko پیست کن تا اشتباهی نخری"
                                                } catch (_: Exception) { }
                                            }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) {
                                                Text("📋 کپی", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (txs.isNotEmpty()) {
                    Text("📜 تاریخچه معاملات:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                    ) {
                        items(txs) { t ->
                            Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (t.incoming) "🟢" else "🔴", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${if (t.incoming) "خرید/ورود" else "فروش/خروج"} ${t.symbol}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("تاریخ: ${t.dateText} • مقدار: ${String.format(Locale.US, "%.4f", t.amount)}", fontSize = 9.sp, color = VGray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (t.priceUsd != null && t.priceUsd!! > 0) {
                                            Text("قیمت اون روز: ${String.format(Locale.US, "$%.6f", t.priceUsd)}", fontSize = 9.sp, color = VGold)
                                            Text("ارزش: ${String.format(Locale.US, "$%.2f", t.amount * t.priceUsd!!)}", fontSize = 10.sp, color = if (t.incoming) VGreen else VRed)
                                        } else {
                                            Text("قیمت اون روز: ❓ نامشخص", fontSize = 9.sp, color = VGold, fontWeight = FontWeight.Bold)
                                            Text("ارزش: ❓", fontSize = 10.sp, color = VGray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                WalletHistorySection()

                ChainForensicsSection(
                    onCopy = { a ->
                        try {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("addr", a))
                            info = "📋 آدرس کپی شد"
                        } catch (_: Exception) { }
                    },
                    onInspect = { a -> address = a; check() },
                    onStar = { a, s, sc, n, bought, maxSingle, sold, txCount -> saveStar(a, s, sc, n, bought, maxSingle, sold, txCount) }
                )

                Text("⚠️ داده‌های عمومی آن‌چین — توصیه مالی نیست.", fontSize = 9.sp, color = VGold)
            }
        }
    }
}

// ... (بقیه کد WalletScreen.kt شامل ChainForensicsSection بدون تغییر باقی می‌ماند، فقط برای کوتاهی اینجا خلاصه شده اما در فایل کامل شما باید کل کد قبلی را نگه دارید و فقط آن دو خط private را حذف کنید)
