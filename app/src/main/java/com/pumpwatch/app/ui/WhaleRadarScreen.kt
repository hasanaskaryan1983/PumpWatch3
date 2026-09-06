package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.Blockscout
import com.pumpwatch.app.data.GeckoOhlcv
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.SolanaRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

private val WGSON = Gson()
private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VPurple = Color(0xFFCE93D8)
private val VOrange = Color(0xFFFFA726)
private val VCard = Color(0xFF1A2230)

private data class ChainCfg(val key: String, val label: String, val gt: String, val bs: String?)

private val CHAINS = listOf(
    ChainCfg("solana", "Solana 🟣", "solana", null),
    ChainCfg("eth", "Ethereum ⚪", "eth", "https://eth.blockscout.com/"),
    ChainCfg("base", "Base 🔵", "base", "https://base.blockscout.com/"),
    ChainCfg("arbitrum", "Arbitrum 🔷", "arbitrum", "https://arbitrum.blockscout.com/"),
    ChainCfg("optimism", "Optimism 🔴", "optimism", "https://optimism.blockscout.com/"),
    ChainCfg("polygon", "Polygon 🟣", "polygon_pos", "https://polygon.blockscout.com/"),
    ChainCfg("gnosis", "Gnosis 🦉", "gnosis", "https://gnosis.blockscout.com/")
)

private data class WalletHolding(val symbol: String, val name: String, val amount: Double, val price: Double, val value: Double)
private data class WalletTx(val dateText: String, val dateDay: String, val symbol: String, val amount: Double, val incoming: Boolean, var priceUsd: Double?)
private data class SusWallet(
    val addr: String, val boughtUsd: Double, val avgEntry: Double,
    val firstBuyText: String, val txCount: Int, val soldUsd: Double,
    val statusText: String, val multiplier: Double
)
private data class HunterReport(
    val poolName: String, val bottomPrice: Double, val peakPrice: Double,
    val currentPrice: Double, val pumpStartText: String, val risePct: Double,
    val wallets: List<SusWallet>
)
private data class InsiderSus(
    val addr: String, val eventsCount: Int, val totalPreBuyUsd: Double,
    val avgLeadMin: Long, val avgEntry: Double, val multiplier: Double,
    val score: Int, val lastSeenText: String
)
private data class InsiderReport(val poolName: String, val eventsCount: Int, val suspects: List<InsiderSus>)
private data class SavedTrader(val addr: String, val symbol: String, val score: Int, val note: String)

private fun num(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private fun shortAddr(a: String): String = if (a.length > 12) "${a.take(6)}...${a.takeLast(4)}" else a

private fun loadTraders(ctx: Context): MutableList<SavedTrader> = try {
    val json = ctx.getSharedPreferences("pumpwatch_prefs", 0).getString("top_traders", "") ?: ""
    if (json.isEmpty()) mutableListOf()
    else WGSON.fromJson(json, object : TypeToken<MutableList<SavedTrader>>() {}.type) ?: mutableListOf()
} catch (_: Exception) { mutableListOf() }

private fun saveTraders(ctx: Context, list: List<SavedTrader>) {
    ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
        .putString("top_traders", WGSON.toJson(list)).apply()
}

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var chain by remember { mutableStateOf(CHAINS[0]) }

    // موتور ۱
    var address by remember { mutableStateOf("") }
    var holdings by remember { mutableStateOf<List<WalletHolding>>(emptyList()) }
    var txs by remember { mutableStateOf<List<WalletTx>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0.0) }
    var info by remember { mutableStateOf("") }

    // موتور ۲
    var hunterSymbol by remember { mutableStateOf("") }
    var hunterLoading by remember { mutableStateOf(false) }
    var hunterError by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<HunterReport?>(null) }

    // موتور ۳
    var insiderSymbol by remember { mutableStateOf("") }
    var insiderLoading by remember { mutableStateOf(false) }
    var insiderError by remember { mutableStateOf<String?>(null) }
    var iReport by remember { mutableStateOf<InsiderReport?>(null) }

    // لیست ستاره‌ها
    var topTraders by remember { mutableStateOf(loadTraders(context)) }
    var manualAddr by remember { mutableStateOf("") }

    fun saveStar(addr: String, symbol: String, score: Int, note: String) {
        if (topTraders.any { it.addr == addr }) { info = "⭐ قبلاً ذخیره شده"; return }
        topTraders.add(SavedTrader(addr, symbol, score, note))
        topTraders = ArrayList(topTraders)
        saveTraders(context, topTraders)
        info = "⭐ به لیست بهترین تریدرها اضافه شد"
    }

    fun check() {
        val addr = address.trim()
        if (addr.isEmpty()) { error = "❌ آدرس کیف پول رو وارد کن"; return }
        val cfg = chain
        scope.launch {
            loading = true; error = null; holdings = emptyList(); txs = emptyList()
            info = "🔍 در حال اسکن کیف پول..."
            try {
                withContext(Dispatchers.IO) {
                    if (cfg.bs == null) {
                        val body = mapOf(
                            "jsonrpc" to "2.0", "id" to 1,
                            "method" to "getTokenAccountsByOwner",
                            "params" to listOf(
                                addr,
                                mapOf("programId" to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"),
                                mapOf("encoding" to "jsonParsed")
                            )
                        )
                        val res = SolanaRpc.api.rpc(body)
                        val raw = res.result?.value?.mapNotNull { a ->
                            val inf = a.account?.data?.parsed?.info ?: return@mapNotNull null
                            val mint = inf.mint ?: return@mapNotNull null
                            val amt = inf.tokenAmount?.uiAmountString?.toDoubleOrNull() ?: 0.0
                            if (amt <= 0.0) null else mint to amt
                        } ?: emptyList()

                        val list = mutableListOf<WalletHolding>()
                        for ((mint, amt) in raw.take(15)) {
                            try {
                                val t = GeckoPrice.api.tokenInfo(cfg.gt, mint).data?.attributes
                                val px = t?.price_usd?.toDoubleOrNull() ?: 0.0
                                list.add(WalletHolding(t?.symbol ?: mint.take(6), t?.name ?: "", amt, px, amt * px))
                            } catch (_: Exception) { }
                        }
                        holdings = list.sortedByDescending { it.value }
                        total = list.sumOf { it.value }
                        txs = emptyList()
                        info = "✅ Solana: ${list.size} توکن پیدا شد"
                    } else {
                        val bs = Blockscout.api(cfg.bs)
                        val tokens = try { bs.tokenList("account", "tokenlist", addr).result } catch (_: Exception) { null }
                        val list = mutableListOf<WalletHolding>()
                        tokens?.filter { (it.balance?.toDoubleOrNull() ?: 0.0) > 0 }?.take(15)?.forEach { t ->
                            try {
                                val dec = t.decimals?.toDoubleOrNull() ?: 18.0
                                val amt = (t.balance?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                                val contract = t.contractAddress ?: return@forEach
                                val px = try {
                                    GeckoPrice.api.tokenInfo(cfg.gt, contract).data?.attributes?.price_usd?.toDoubleOrNull() ?: 0.0
                                } catch (_: Exception) { 0.0 }
                                list.add(WalletHolding(t.symbol ?: "?", t.name ?: "", amt, px, amt * px))
                            } catch (_: Exception) { }
                        }
                        holdings = list.sortedByDescending { it.value }
                        total = list.sumOf { it.value }

                        val all = try { bs.tokenTx("account", "tokentx", addr, "desc").result } catch (_: Exception) { null }
                        val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val sdfShow = SimpleDateFormat("MM/dd", Locale.US)
                        val rawTxs = all?.take(20)?.mapNotNull { t ->
                            val ts = (t.timeStamp?.toLongOrNull() ?: return@mapNotNull null) * 1000
                            val dec = t.tokenDecimal?.toDoubleOrNull() ?: 18.0
                            val amt = (t.value?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                            WalletTx(sdfShow.format(Date(ts)), sdfDay.format(Date(ts)), t.tokenSymbol ?: "?", amt, (t.to ?: "").equals(addr, true), null)
                        } ?: emptyList()

                        try {
                            val coins = ApiClient.getTop1000Coins()
                            for (sym in rawTxs.map { it.symbol }.distinct().take(3)) {
                                val coin = coins.firstOrNull { it.symbol.equals(sym, true) } ?: continue
                                try {
                                    val chart = ApiClient.getCoinChart(coin.id, days = 365)
                                    val byDay = chart.prices.associate { p -> sdfDay.format(Date(p[0].toLong())) to p[1] }
                                    rawTxs.forEach { b -> if (b.symbol.equals(sym, true)) b.priceUsd = byDay[b.dateDay] }
                                } catch (_: Exception) { }
                            }
                        } catch (_: Exception) { }
                        txs = rawTxs
                        info = "✅ ${cfg.label}: ${list.size} توکن + ${rawTxs.size} تراکنش"
                    }
                }
            } catch (t: Throwable) {
                error = "⚠️ خطا: ${t.message}"
            }
            loading = false
        }
    }

    fun hunt() {
        val sym = hunterSymbol.trim()
        if (sym.isEmpty()) { hunterError = "❌ نماد ارز رو وارد کن"; return }
        val cfg = chain
        scope.launch {
            hunterLoading = true; hunterError = null; report = null
            try {
                val rep = withContext(Dispatchers.IO) {
                    val pools = GeckoTerminal.api.searchPools(sym).data?.filter { it.attributes != null } ?: emptyList()
                    val sameChain = pools.filter { it.relationships?.network?.data?.id == cfg.gt }
                    val pool = (if (sameChain.isNotEmpty()) sameChain else pools)
                        .maxByOrNull { it.attributes?.volume?.h24 ?: 0.0 } ?: throw Exception("استخری پیدا نشد")
                    val net = pool.relationships?.network?.data?.id ?: cfg.gt
                    val poolAddr = pool.id?.substringAfter('_') ?: ""

                    val rows = try { GeckoOhlcv.api.poolOhlcvHour(net, poolAddr).data?.attributes?.ohlcv_list ?: emptyList() } catch (_: Exception) { emptyList<List<Double>>() }
                    var bottomPrice = 0.0; var peakPrice = 0.0; var pumpTs = 0L
                    if (rows.size >= 10) {
                        val cut = rows.size * 2 / 3
                        val bottomRow = rows.subList(0, cut).minByOrNull { it[4] }
                        bottomPrice = bottomRow?.get(4) ?: 0.0
                        pumpTs = (bottomRow?.get(0)?.toLong() ?: 0L) * 1000
                        peakPrice = rows.maxOf { it[2] }
                    }
                    val currentPrice = pool.attributes?.priceUsd?.toDoubleOrNull() ?: 0.0
                    val risePct = if (bottomPrice > 0) (peakPrice - bottomPrice) / bottomPrice * 100 else 0.0

                    val trades = try { GeckoPrice.api.poolTrades(net, poolAddr).data ?: emptyList() } catch (_: Exception) { emptyList() }
                    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)

                    val buyMap = mutableMapOf<String, MutableList<Triple<Double, Double, Long>>>()
                    val sellMap = mutableMapOf<String, Double>()
                    for (t in trades) {
                        val a = t.attributes ?: continue
                        val vol = num(a.volume_in_usd) ?: continue
                        val px = num(a.price_in_usd) ?: num(a.price) ?: continue
                        val ts = (num(a.block_timestamp) ?: 0.0).toLong() * 1000
                        val wallet = a.tx_from_address ?: continue
                        if ((a.type ?: "").equals("buy", true)) {
                            if (bottomPrice <= 0 || px <= bottomPrice * 1.3) buyMap.getOrPut(wallet) { mutableListOf() }.add(Triple(vol, px, ts))
                        } else sellMap[wallet] = (sellMap[wallet] ?: 0.0) + vol
                    }

                    val suspects = buyMap.map { (wallet, list) ->
                        val bought = list.sumOf { it.first }
                        val avg = list.map { it.second }.average()
                        val first = list.minOf { it.third }
                        val sold = sellMap[wallet] ?: 0.0
                        SusWallet(wallet, bought, avg, if (first > 0) sdf.format(Date(first)) else "—", list.size, sold,
                            when { sold >= bought * 0.5 -> "✅ سود رو گرفته"; sold > 0 -> "⚠️ بخشی رو فروخته"; else -> "💎 هنوز هودل می‌کنه" },
                            if (avg > 0 && currentPrice > 0) currentPrice / avg else 0.0)
                    }.sortedByDescending { it.boughtUsd }.take(10)

                    HunterReport(pool.attributes?.name ?: sym, bottomPrice, peakPrice, currentPrice,
                        if (pumpTs > 0) sdf.format(Date(pumpTs)) else "—", risePct, suspects)
                }
                report = rep
                if (rep.wallets.isEmpty()) hunterError = "😴 کیف مشکوکی پیدا نشد"
            } catch (t: Throwable) { hunterError = "⚠️ خطا: ${t.message}" }
            hunterLoading = false
        }
    }

    fun huntInsider() {
        val sym = insiderSymbol.trim()
        if (sym.isEmpty()) { insiderError = "❌ نماد ارز رو وارد کن"; return }
        val cfg = chain
        scope.launch {
            insiderLoading = true; insiderError = null; iReport = null
            try {
                val rep = withContext(Dispatchers.IO) {
                    val pools = GeckoTerminal.api.searchPools(sym).data?.filter { it.attributes != null } ?: emptyList()
                    val sameChain = pools.filter { it.relationships?.network?.data?.id == cfg.gt }
                    val pool = (if (sameChain.isNotEmpty()) sameChain else pools)
                        .maxByOrNull { it.attributes?.volume?.h24 ?: 0.0 } ?: throw Exception("استخری پیدا نشد")
                    val net = pool.relationships?.network?.data?.id ?: cfg.gt
                    val poolAddr = pool.id?.substringAfter('_') ?: ""

                    val rows = try { GeckoOhlcv.api.poolOhlcvHour(net, poolAddr).data?.attributes?.ohlcv_list ?: emptyList() } catch (_: Exception) { emptyList<List<Double>>() }

                    val events = mutableListOf<Pair<Long, Double>>()
                    for (i in 1 until rows.size) {
                        val prev = rows[i - 1][4]
                        val cur = rows[i][4]
                        if (prev > 0) {
                            val rise = (cur - prev) / prev * 100
                            if (rise >= 8.0) events.add((rows[i][0].toLong()) * 1000 to rise)
                        }
                    }

                    val trades = try { GeckoPrice.api.poolTrades(net, poolAddr).data ?: emptyList() } catch (_: Exception) { emptyList() }
                    val currentPrice = pool.attributes?.priceUsd?.toDoubleOrNull() ?: 0.0
                    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)

                    class Acc { var events = mutableSetOf<Long>(); var vol = 0.0; var leads = mutableListOf<Long>(); var entries = mutableListOf<Double>(); var last = 0L }
                    val per = mutableMapOf<String, Acc>()

                    for ((evTs, _) in events) {
                        for (t in trades) {
                            val a = t.attributes ?: continue
                            if (!(a.type ?: "").equals("buy", true)) continue
                            val ts = (num(a.block_timestamp) ?: 0.0).toLong() * 1000
                            if (ts < evTs - 3 * 3600 * 1000 || ts >= evTs) continue
                            val wallet = a.tx_from_address ?: continue
                            val vol = num(a.volume_in_usd) ?: continue
                            val px = num(a.price_in_usd) ?: num(a.price) ?: continue
                            val acc = per.getOrPut(wallet) { Acc() }
                            acc.events.add(evTs); acc.vol += vol; acc.leads.add(evTs - ts); acc.entries.add(px); acc.last = maxOf(acc.last, ts)
                        }
                    }

                    val suspects = per.map { (wallet, acc) ->
                        val avgLead = acc.leads.average().toLong() / 60000
                        val avgEntry = acc.entries.average()
                        val leadBonus = if (avgLead in 30..180) 20 else if (avgLead < 30) 10 else 5
                        val score = (acc.events.size * 30 + minOf(30, (acc.vol / 1000).toInt()) + leadBonus).coerceIn(0, 100)
                        InsiderSus(wallet, acc.events.size, acc.vol, avgLead, avgEntry,
                            if (avgEntry > 0 && currentPrice > 0) currentPrice / avgEntry else 0.0, score,
                            if (acc.last > 0) sdf.format(Date(acc.last)) else "—")
                    }.sortedByDescending { it.score }.take(10)

                    InsiderReport(pool.attributes?.name ?: sym, events.size, suspects)
                }
                iReport = rep
                if (rep.suspects.isEmpty()) insiderError = "😴 هیچ کیفی قبل از جهش‌ها خرید سنگین نکرده"
            } catch (t: Throwable) { insiderError = "⚠️ خطا: ${t.message}" }
            insiderLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("👛 کارآگاه کیف پول", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = VGreen)

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CHAINS.forEach { c ->
                FilterChip(selected = chain.key == c.key, onClick = { chain = c }, label = { Text(c.label, fontSize = 10.sp) })
            }
        }

        // ================= موتور ۱ =================
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔍 موتور : بررسی کیف پول مشکوک", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VBlue)
                TextField(value = address, onValueChange = { address = it },
                    placeholder = { Text("آدرس کیف پول...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Button(onClick = { check() }, enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = VBlue),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" بررسی کیف پول", fontSize = 12.sp)
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
            holdings.forEach { h ->
                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(h.symbol, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("مقدار: ${String.format(Locale.US, "%.4f", h.amount)} • قیمت: ${String.format(Locale.US, "$%.6f", h.price)}", fontSize = 9.sp, color = VGray)
                        }
                        Text(String.format(Locale.US, "$%,.2f", h.value), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VGreen)
                    }
                }
            }
        }

        if (txs.isNotEmpty()) {
            Text("📜 تاریخچه معاملات:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            txs.forEach { t ->
                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (t.incoming) "🟢" else "🔴", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${if (t.incoming) "خرید/ورود" else "فروش/خروج"} ${t.symbol}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("تاریخ: ${t.dateText} • مقدار: ${String.format(Locale.US, "%.4f", t.amount)}", fontSize = 9.sp, color = VGray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (t.priceUsd != null) Text("قیمت اون روز: ${String.format(Locale.US, "$%.6f", t.priceUsd)}", fontSize = 9.sp, color = VGold)
                            Text("ارزش: ${String.format(Locale.US, "$%.2f", t.amount * (t.priceUsd ?: 0.0))}", fontSize = 10.sp, color = if (t.incoming) VGreen else VRed)
                        }
                    }
                }
            }
        }

        // ================= موتور ۲ =================
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🕵️ موتور ۲: کی کف خرید قبل از پامپ؟", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VPurple)
                TextField(value = hunterSymbol, onValueChange = { hunterSymbol = it },
                    placeholder = { Text("نماد ارز پامپ‌شده...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Button(onClick = { hunt() }, enabled = !hunterLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = VPurple),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hunterLoading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" 🕵️ پیدا کن کیف‌های کف‌خر", fontSize = 12.sp)
                }
                if (hunterError != null) Text(hunterError ?: "", fontSize = 10.sp, color = VGold)
            }
        }

        report?.let { r ->
            Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🎯 ${r.poolName}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = VBlue)
                    Text("کف: ${String.format(Locale.US, "$%.8f", r.bottomPrice)} • اوج: ${String.format(Locale.US, "$%.8f", r.peakPrice)} • الان: ${String.format(Locale.US, "$%.8f", r.currentPrice)}", fontSize = 10.sp, color = VGray)
                    Text("🚀 رشد از کف: ${String.format(Locale.US, "%.0f%%", r.risePct)} • شروع: ${r.pumpStartText}", fontSize = 10.sp, color = VGreen, fontWeight = FontWeight.Bold)
                }
            }
            r.wallets.forEach { w ->
                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🐋 ${shortAddr(w.addr)}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = VGold)
                            Spacer(Modifier.weight(1f))
                            Text("${String.format(Locale.US, "%.1f", w.multiplier)}x", fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (w.multiplier >= 2) VGreen else VGray)
                        }
                        Text("💵 خرید کف: ${String.format(Locale.US, "$%,.0f", w.boughtUsd)} • ورود: ${String.format(Locale.US, "$%.8f", w.avgEntry)}", fontSize = 10.sp, color = VGray)
                        Text("🕐 اولین خرید: ${w.firstBuyText} • ${w.txCount} تراکنش", fontSize = 9.sp, color = VGray)
                        Text(w.statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = if (w.statusText.contains("هودل")) VGreen else if (w.statusText.contains("✅")) VGold else VRed)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                try {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("addr", w.addr))
                                    info = "📋 آدرس کپی شد"
                                } catch (_: Exception) { }
                            }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("📋 کپی", fontSize = 9.sp) }
                            Button(onClick = { address = w.addr; check() },
                                colors = ButtonDefaults.buttonColors(containerColor = VBlue), shape = RoundedCornerShape(6.dp)) { Text("🔍 بررسی کامل", fontSize = 9.sp) }
                            Button(onClick = { saveStar(w.addr, r.poolName, (w.multiplier * 10).toInt(), "کف‌خر") },
                                colors = ButtonDefaults.buttonColors(containerColor = VGold), shape = RoundedCornerShape(6.dp)) { Text("⭐", fontSize = 9.sp) }
                        }
                    }
                }
            }
        }

        // ================= موتور ۳ =================
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📰 موتور ۳: شکارچی اینسایدرهای خبری (الگوی ترامپ)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VOrange)
                Text("جهش‌های ≥۸٪ = لحظه خبر • کیف‌هایی که ۳۰دقیقه تا ۳ساعت قبلش خریدن = مشکوک", fontSize = 9.sp, color = VGray)
                TextField(value = insiderSymbol, onValueChange = { insiderSymbol = it },
                    placeholder = { Text("نماد ارز خبرساز... (TRUMP, MAGA...)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Button(onClick = { huntInsider() }, enabled = !insiderLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = VOrange),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (insiderLoading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" 📰 پیدا کن اینسایدرها رو", fontSize = 12.sp)
                }
                if (insiderError != null) Text(insiderError ?: "", fontSize = 10.sp, color = VGold)
            }
        }

        iReport?.let { r ->
            Text("⚡ ${r.eventsCount} جهش خبری پیدا شد — مظنون‌ها:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VOrange)
            r.suspects.forEach { w ->
                Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕵️ ${shortAddr(w.addr)}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = VOrange)
                            Spacer(Modifier.weight(1f))
                            Text("شک: ${w.score}/100", fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (w.score >= 70) VRed else VGold)
                        }
                        Text("📰 قبل از ${w.eventsCount} جهش خبری خرید کرده!", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = VRed)
                        Text("💵 مجموع خرید قبل خبر: ${String.format(Locale.US, "$%,.0f", w.totalPreBuyUsd)} • میانگین فاصله: ${w.avgLeadMin} دقیقه قبل از جهش", fontSize = 9.sp, color = VGray)
                        Text("ورود: ${String.format(Locale.US, "$%.8f", w.avgEntry)} • سود فعلی: ${String.format(Locale.US, "%.1f", w.multiplier)}x • آخرین: ${w.lastSeenText}", fontSize = 9.sp, color = VGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = {
                                try {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("addr", w.addr))
                                    info = "📋 آدرس کپی شد"
                                } catch (_: Exception) { }
                            }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("📋 کپی", fontSize = 9.sp) }
                            Button(onClick = { address = w.addr; check() },
                                colors = ButtonDefaults.buttonColors(containerColor = VBlue), shape = RoundedCornerShape(6.dp)) { Text("🔍 بررسی کامل", fontSize = 9.sp) }
                            Button(onClick = { saveStar(w.addr, r.poolName, w.score, "اینسایدر خبری") },
                                colors = ButtonDefaults.buttonColors(containerColor = VGold), shape = RoundedCornerShape(6.dp)) { Text("⭐", fontSize = 9.sp) }
                        }
                    }
                }
            }
        }

        // ================= ⭐ بهترین تریدرها =================
        Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⭐ لیست بهترین تریدرها (${topTraders.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VGold)
                if (topTraders.isEmpty()) Text("هنوز کیفی ستاره نزده‌ای — از نتایج موتور ۲ و ۳ ⭐ بزن یا دستی اضافه کن", fontSize = 10.sp, color = VGray)
                topTraders.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("⭐ ${shortAddr(t.addr)}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VGold)
                            Text("${t.symbol} • ${t.note} • امتیاز ${t.score}", fontSize = 9.sp, color = VGray)
                        }
                        Button(onClick = { address = t.addr; check() },
                            colors = ButtonDefaults.buttonColors(containerColor = VBlue), shape = RoundedCornerShape(6.dp)) { Text("🔍", fontSize = 9.sp) }
                        Button(onClick = {
                            topTraders.remove(t); topTraders = ArrayList(topTraders); saveTraders(context, topTraders)
                        }, colors = ButtonDefaults.buttonColors(containerColor = VRed), shape = RoundedCornerShape(6.dp)) { Text("🗑", fontSize = 9.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(value = manualAddr, onValueChange = { manualAddr = it },
                        placeholder = { Text("افزودن دستی آدرس تریدر...", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        val a = manualAddr.trim()
                        if (a.isNotEmpty()) { saveStar(a, "دستی", 0, "تریدر معروف"); manualAddr = "" }
                    }, colors = ButtonDefaults.buttonColors(containerColor = VGold), shape = RoundedCornerShape(8.dp)) { Text("➕", fontSize = 10.sp) }
                }
            }
        }

        Text("⚠️ داده‌های عمومی آن‌چین — توصیه مالی نیست.", fontSize = 9.sp, color = VGold)
    }
}
