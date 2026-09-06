package com.pumpwatch.app.ui

import android.content.Context
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val TGreen = Color(0xFF00E676)
private val TRed = Color(0xFFFF5252)
private val TBlue = Color(0xFF40C4FF)
private val TGold = Color(0xFFFFC107)
private val TGray = Color(0xFF8B949E)
private val TCard = Color(0xFF1A2230)
private val TPurple = Color(0xFFCE93D8)

private val GSON = Gson()

data class PaperTrade(
    var symbol: String = "",
    var tier: String = "",
    var entry: Double = 0.0,
    var sizeUsd: Double = 0.0,
    var qty: Double = 0.0,
    var price: Double = 0.0,
    var stop: Double = 0.0,
    var stopPct: Double = 10.0,
    var target: Double = 0.0,
    var openTime: Long = 0L,
    var score: Int = 0,
    var status: String = "OPEN",
    var pnl: Double = 0.0,
    var closeTime: Long = 0L,
    var trailing: Boolean? = null
)

data class PaperState(
    var cash: Double = 1000.0,
    var trades: MutableList<PaperTrade> = mutableListOf()
)

private data class ConsensusPick(
    val symbol: String, val rank: Int?, val price: Double, val chain: String?,
    val trend: Int, val whaleRatio: Double, val whaleVol: Double, val ch24: Double,
    val total: Int, val isDex: Boolean, val atr: Double
)

private val TIERS = listOf(
    "1-10" to (1..10),
    "11-50" to (11..50),
    "51-100" to (51..100),
    "101-200" to (101..200),
    "201-1000" to (201..1000),
    "DEX" to null
)

private const val START_CAPITAL = 1000.0
private const val MAX_PER_TIER = 4

private fun tierOfRank(rank: Int?): String = when (rank) {
    null -> "DEX"
    in 1..10 -> "1-10"
    in 11..50 -> "11-50"
    in 51..100 -> "51-100"
    in 101..200 -> "101-200"
    else -> "201-1000"
}

private fun loadState(ctx: Context): PaperState = try {
    val p = ctx.getSharedPreferences("pumpwatch_prefs", 0)
    val json = p.getString("paper_state", "") ?: ""
    if (json.isEmpty()) PaperState() else GSON.fromJson(json, PaperState::class.java) ?: PaperState()
} catch (_: Exception) { PaperState() }

private fun saveState(ctx: Context, s: PaperState) {
    ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
        .putString("paper_state", GSON.toJson(s)).apply()
}

private fun loadAlloc(ctx: Context): MutableMap<String, Int> {
    val def = linkedMapOf("1-10" to 30, "11-50" to 25, "51-100" to 15, "101-200" to 10, "201-1000" to 10, "DEX" to 10)
    return try {
        val p = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        val json = p.getString("paper_alloc", "") ?: ""
        if (json.isEmpty()) def
        else {
            val m: MutableMap<String, Int>? = GSON.fromJson(json, object : TypeToken<MutableMap<String, Int>>() {}.type)
            m ?: def
        }
    } catch (_: Exception) { def }
}

private fun saveAlloc(ctx: Context, a: Map<String, Int>) {
    ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
        .putString("paper_alloc", GSON.toJson(a)).apply()
}

private fun usd(v: Double): String = String.format(Locale.US, "$%,.2f", v)

@Composable
fun TradesScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(loadState(context)) }
    var alloc by remember { mutableStateOf(loadAlloc(context)) }
    var botOn by remember { mutableStateOf(prefs.getBoolean("paper_bot", true)) }
    var status by remember { mutableStateOf("⏳ منتظر اولین اسکن...") }
    var confirmReset by remember { mutableStateOf(false) }
    var consensus by remember { mutableStateOf<List<ConsensusPick>>(emptyList()) }

    var mSymbol by remember { mutableStateOf("") }
    var mPrice by remember { mutableStateOf<Double?>(null) }
    var mAmount by remember { mutableStateOf("100") }
    var mStop by remember { mutableStateOf("10") }
    var mTarget by remember { mutableStateOf("20") }
    var mTrailing by remember { mutableStateOf(true) }
    var mStatus by remember { mutableStateOf("") }
    var mLoading by remember { mutableStateOf(false) }

    fun save() { saveState(context, state) }

    fun openTrades() = state.trades.filter { it.status == "OPEN" }
    fun invested() = openTrades().sumOf { it.price * it.qty }
    fun equity() = state.cash + invested()

    fun closeTrade(t: PaperTrade, px: Double) {
        t.price = px
        t.status = "CLOSED"
        t.pnl = if (t.entry > 0) (px - t.entry) / t.entry * 100 else 0.0
        t.closeTime = System.currentTimeMillis()
        state.cash += t.qty * px
    }

    fun openTrade(symbol: String, tier: String, px: Double, score: Int, atrPct: Double, sizeUsd: Double, trailing: Boolean? = true, stopPctOverride: Double? = null, targetPctOverride: Double? = null) {
        if (px <= 0 || sizeUsd <= 0) return
        val sp = stopPctOverride ?: (atrPct * 2.5).coerceIn(7.0, 15.0)
        val tp = targetPctOverride ?: (sp * 2.0)
        state.trades.add(
            PaperTrade(
                symbol = symbol, tier = tier, entry = px, sizeUsd = sizeUsd, qty = sizeUsd / px,
                price = px, stop = px * (1 - sp / 100), stopPct = sp,
                target = px * (1 + tp / 100), openTime = System.currentTimeMillis(),
                score = score, trailing = trailing
            )
        )
        state.cash -= sizeUsd
    }

    fun updateTrail(t: PaperTrade, px: Double): Boolean {
        t.price = px
        if (t.trailing != false) {
            val nt = px * (1 - t.stopPct / 100)
            if (nt > t.stop) t.stop = nt
        }
        return when {
            px <= t.stop -> { closeTrade(t, px); true }
            px >= t.target -> { closeTrade(t, px); true }
            else -> false
        }
    }

    fun findPrice() {
        val q = mSymbol.trim()
        if (q.isEmpty()) return
        scope.launch {
            mLoading = true
            mStatus = "🔍 جستجوی $q..."
            mPrice = null
            try {
                val res = withContext(Dispatchers.IO) {
                    val coins = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                    coins.firstOrNull { it.symbol.equals(q, true) }?.let {
                        return@withContext Triple(it.current_price, "CEX #${it.market_cap_rank ?: "-"}", it.symbol.uppercase(Locale.US))
                    }
                    try {
                        val kl = BinanceClient.api.klines("${q.uppercase(Locale.US)}USDT", "1h", 5)
                        if (kl.isNotEmpty()) return@withContext Triple(kl.last()[4].asDouble, "Binance", q.uppercase(Locale.US))
                    } catch (_: Exception) { }
                    val pool = try { GeckoTerminal.api.searchPools(q).data?.firstOrNull { it.attributes != null } } catch (_: Exception) { null }
                    pool?.attributes?.priceUsd?.toDoubleOrNull()?.let { return@withContext Triple(it, "DEX", q.uppercase(Locale.US)) }
                    null
                }
                if (res != null) {
                    mPrice = res.first; mSymbol = res.third
                    mStatus = "✅ قیمت: ${usd(res.first)} (${res.second})"
                } else mStatus = "❌ ارز پیدا نشد"
            } catch (t: Throwable) { mStatus = "⚠️ خطا: ${t.message}" }
            mLoading = false
        }
    }

    fun cycle() {
        scope.launch {
            try {
                status = "🔄 اسکن بازار + اجماع تمام تب‌ها..."
                val eq = equity()

                val coins = withContext(Dispatchers.IO) {
                    try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                }
                val dexPools = withContext(Dispatchers.IO) {
                    listOf("solana", "bsc", "base", "optimism", "arbitrum", "polygon", "avalanche", "ton").flatMap { ch ->
                        try { GeckoTerminal.api.trendingPools(ch).data ?: emptyList() } catch (_: Exception) { emptyList() }
                    }
                }
                val dexInfo = mutableMapOf<String, Pair<Double, Double>>()
                val whaleMap = mutableMapOf<String, Triple<Double, Double, String>>()
                dexPools.forEach { p ->
                    val a = p.attributes ?: return@forEach
                    val sym = a.name?.split("/")?.firstOrNull()?.trim() ?: return@forEach
                    val px = a.priceUsd?.toDoubleOrNull() ?: return@forEach
                    val b = a.transactions?.h1?.buys ?: 0.0
                    val s = a.transactions?.h1?.sells ?: 0.0
                    val v = a.volume?.h1 ?: 0.0
                    val r = if (b + s > 0) b / (b + s) else 0.0
                    val chain = p.relationships?.network?.data?.id ?: ""
                    dexInfo[sym] = px to r
                    val prev = whaleMap[sym]
                    if (prev == null || v > prev.second) whaleMap[sym] = Triple(r, v, chain)
                }

                var closedNow = 0
                openTrades().forEach { t ->
                    val px = if (t.tier == "DEX") dexInfo[t.symbol]?.first
                    else if (t.tier == "دستی") coins.firstOrNull { it.symbol.equals(t.symbol, true) }?.current_price
                        ?: try { BinanceClient.api.klines("${t.symbol}USDT", "1h", 2).last()[4].asDouble } catch (_: Exception) { null }
                    else coins.firstOrNull { it.symbol.equals(t.symbol, true) }?.current_price
                    if (px != null && px > 0) if (updateTrail(t, px)) closedNow++
                }

                // ---------- ساخت لیست اجماع (ترکیب همه تب‌ها) ----------
                val picks = mutableListOf<ConsensusPick>()
                val seen = mutableSetOf<String>()

                val cexCands = coins.filter { c ->
                    val sym = c.symbol.uppercase(Locale.US)
                    (c.price_change_percentage_24h ?: 0.0) >= 5.0 ||
                            whaleMap.containsKey(sym) ||
                            (c.total_volume ?: 0.0) > 300_000_000.0
                }.take(25)

                for (c in cexCands) {
                    val sym = c.symbol.uppercase(Locale.US)
                    if (seen.contains(sym)) continue
                    seen.add(sym)
                    val (trend, atr) = evalCoin(sym)
                    val w = whaleMap[sym]
                    val ch24 = c.price_change_percentage_24h ?: 0.0
                    var total = trend
                    if (w != null) total += when { w.first >= 0.7 -> 20; w.first >= 0.6 -> 15; w.first >= 0.55 -> 8; else -> 0 }
                    total += when { ch24 >= 15 -> 15; ch24 >= 8 -> 10; ch24 >= 4 -> 5; else -> 0 }
                    picks.add(ConsensusPick(sym, c.market_cap_rank, c.current_price, w?.third,
                        trend, w?.first ?: 0.0, w?.second ?: 0.0, ch24, total.coerceIn(0, 100), false, atr))
                }

                for ((sym, w) in whaleMap) {
                    if (seen.contains(sym)) continue
                    if (coins.any { it.symbol.equals(sym, true) }) continue
                    seen.add(sym)
                    val px = dexInfo[sym]?.first ?: continue
                    var total = 50
                    total += when { w.first >= 0.7 -> 25; w.first >= 0.6 -> 18; else -> 8 }
                    picks.add(ConsensusPick(sym, null, px, w.third, 50, w.first, w.second, 0.0, total.coerceIn(0, 100), true, 12.0))
                }

                consensus = picks.sortedByDescending { it.total }.take(10)

                // ---------- معامله خودکار per tier ----------
                var openedNow = 0
                for ((tierName, range) in TIERS) {
                    val pct = alloc[tierName] ?: 0
                    if (pct <= 0) continue
                    val tierBudget = eq * pct / 100.0
                    val tierInvested = openTrades().filter { it.tier == tierName }.sumOf { it.price * it.qty }
                    val free = tierBudget - tierInvested
                    val heldCount = openTrades().count { it.tier == tierName }
                    if (free < 10 || heldCount >= MAX_PER_TIER) continue
                    val size = min(eq * pct / 100.0 / MAX_PER_TIER, min(free, state.cash))
                    if (size < 10) continue

                    if (tierName == "DEX") {
                        val cands = dexPools.mapNotNull { p ->
                            val a = p.attributes ?: return@mapNotNull null
                            val sym = a.name?.split("/")?.firstOrNull()?.trim() ?: return@mapNotNull null
                            val px = a.priceUsd?.toDoubleOrNull() ?: return@mapNotNull null
                            val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
                            val b = a.transactions?.h1?.buys ?: 0.0
                            val s = a.transactions?.h1?.sells ?: 0.0
                            val r = if (b + s > 0) b / (b + s) else 0.0
                            if (liq < 50_000 || r < 0.6 || s <= 0) return@mapNotNull null
                            if (openTrades().any { it.symbol == sym }) return@mapNotNull null
                            sym to px
                        }.sortedByDescending { dexInfo[it.first]?.second ?: 0.0 }.take(2)
                        for (cand in cands) {
                            if (openTrades().count { it.tier == "DEX" } >= MAX_PER_TIER || state.cash < 10) break
                            openTrade(cand.first, "DEX", cand.second, 75, 12.0, min(size, state.cash))
                            openedNow++
                        }
                    } else {
                        val cands = coins
                            .filter { (it.market_cap_rank ?: 0) in range!! }
                            .filter { c -> openTrades().none { it.symbol.equals(c.symbol, true) } }
                            .sortedWith(
                                compareByDescending<CoinMarket> { if ((it.price_change_percentage_24h ?: 0.0) >= 8.0) 1 else 0 }
                                    .thenByDescending { it.total_volume ?: 0.0 }
                            )
                            .take(12)
                        var tierOpened = 0
                        for (c in cands) {
                            if (tierOpened >= 2 || state.cash < 10) break
                            val (score, atr) = evalCoin(c.symbol)
                            val ch24 = c.price_change_percentage_24h ?: 0.0
                            if (score >= 70 || (ch24 >= 8.0 && score >= 45)) {
                                openTrade(c.symbol.uppercase(Locale.US), tierName, c.current_price, score, atr, min(size, state.cash))
                                openedNow++
                                tierOpened++
                            }
                        }
                    }
                }

                // ---------- معامله خودکار از اجماع (ANSEM‌ها اینجا شکار می‌شن) ----------
                var consensusOpened = 0
                for (pk in consensus) {
                    if (consensusOpened >= 2 || state.cash < 10) break
                    if (pk.total < 80 || pk.trend < 50) continue
                    if (openTrades().any { it.symbol == pk.symbol }) continue
                    val tierName = tierOfRank(pk.rank)
                    val pct = alloc[tierName] ?: 0
                    if (pct <= 0) continue
                    if (openTrades().count { it.tier == tierName } >= MAX_PER_TIER) continue
                    val size = min(eq * pct / 100.0 / MAX_PER_TIER, state.cash)
                    if (size < 10) continue
                    openTrade(pk.symbol, tierName, pk.price, pk.total, pk.atr, size)
                    openedNow++
                    consensusOpened++
                }

                save()
                val e = equity()
                status = "✅ اسکن کامل | دارایی: ${usd(e)} | باز: ${openTrades().size} | بسته: $closedNow | باز شد: $openedNow | اجماع ≥۸۰: ${consensus.count { it.total >= 80 }}"
            } catch (t: Throwable) {
                status = "⚠️ خطا: ${t.message}"
            }
        }
    }

    LaunchedEffect(Unit) { cycle() }
    LaunchedEffect(botOn) {
        if (botOn) {
            while (true) {
                delay(5 * 60 * 1000)
                cycle()
            }
        }
    }

    val closed = state.trades.filter { it.status == "CLOSED" }
    val wins = closed.count { it.pnl > 0 }
    val losses = closed.count { it.pnl <= 0 }
    val winRate = if (wins + losses > 0) wins * 100.0 / (wins + losses) else 0.0
    val totalPnl = equity() - START_CAPITAL
    val allocSum = alloc.values.sum()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📈 معاملات شبیه‌سازی", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("ربات:", fontSize = 12.sp, color = TGray)
            Switch(checked = botOn, onCheckedChange = {
                botOn = it
                prefs.edit().putBoolean("paper_bot", it).apply()
            })
        }

        Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("دارایی کل", fontSize = 10.sp, color = TGray)
                        Text(usd(equity()), fontSize = 20.sp, fontWeight = FontWeight.Black, color = TGreen)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("سود/زیان کل", fontSize = 10.sp, color = TGray)
                        Text(String.format(Locale.US, "%+.2f%%", totalPnl / START_CAPITAL * 100),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (totalPnl >= 0) TGreen else TRed)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("نقد: ${usd(state.cash)}", fontSize = 11.sp, color = TGray)
                    Text("درگیر: ${usd(invested())}", fontSize = 11.sp, color = TBlue)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("باز: ${openTrades().size}", fontSize = 11.sp, color = TGold)
                    Text("برد: $wins", fontSize = 11.sp, color = TGreen)
                    Text("باخت: $losses", fontSize = 11.sp, color = TRed)
                    Text("وین‌ریت: ${String.format(Locale.US, "%.0f%%", winRate)}", fontSize = 11.sp, color = if (winRate >= 50) TGreen else TRed)
                }
            }
        }

        Text(status, fontSize = 10.sp, color = TGray)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { cycle() }, colors = ButtonDefaults.buttonColors(containerColor = TBlue),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                Text("🔄 اسکن حالا", fontSize = 11.sp)
            }
            Button(
                onClick = {
                    if (!confirmReset) { confirmReset = true }
                    else {
                        state = PaperState()
                        consensus = emptyList()
                        save()
                        confirmReset = false
                        status = "♻️ ریست شد — ۱۰۰$ تازه"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (confirmReset) TRed else TCard),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
            ) {
                Text(if (confirmReset) "مطمئنی؟ بزن قطعی!" else "♻️ ریست کامل", fontSize = 11.sp)
            }
        }
        if (confirmReset) Text("⚠️ دکمه ریست رو دوباره بزن تا همه چی صفر بشه", fontSize = 9.sp, color = TRed)

        // ---------- اجماع تمام تب‌ها ----------
        Text("🧠 اجماع همه تب‌ها (نهنگ🐳 + روند📈 + مومنتوم⚡ + ترند🐸) — بررسی کن و انتخاب کن:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TPurple)
        if (consensus.isEmpty()) {
            Text("⏳ در حال محاسبه اجماع...", fontSize = 11.sp, color = TGray)
        }
        consensus.forEach { pk ->
            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pk.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Text(if (pk.isDex) " (${pk.chain ?: "DEX"})" else " #${pk.rank}", fontSize = 10.sp, color = TGray)
                        Spacer(Modifier.weight(1f))
                        Text("${pk.total}/100", fontSize = 16.sp, fontWeight = FontWeight.Black,
                            color = if (pk.total >= 80) TGreen else if (pk.total >= 65) TGold else TGray)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📈 روند: ${pk.trend}", fontSize = 10.sp, color = if (pk.trend >= 65) TGreen else TGray)
                        Text("🐳 نهنگ: ${String.format(Locale.US, "%.0f", pk.whaleRatio * 100)}٪", fontSize = 10.sp, color = if (pk.whaleRatio >= 0.6) TGreen else if (pk.whaleRatio > 0) TRed else TGray)
                        Text("⚡ ۲۴س: ${String.format(Locale.US, "%+.1f%%", pk.ch24)}", fontSize = 10.sp, color = if (pk.ch24 >= 0) TGreen else TRed)
                    }
                    if (pk.total >= 80) Text("🎯 سیگنال اجماع — تأیید چند منبع", fontSize = 9.sp, color = TGreen, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (state.cash >= 10 && openTrades().none { it.symbol == pk.symbol }) {
                                openTrade(pk.symbol, tierOfRank(pk.rank), pk.price, pk.total, pk.atr, min(50.0, state.cash))
                                save()
                                status = "✅ ${pk.symbol} با اجماع ${pk.total}/100 باز شد"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (pk.total >= 80) TGreen else TCard),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("🟢 معامله با این سیگنال (۵۰$)", fontSize = 10.sp) }
                }
            }
        }

        // ---------- معامله دستی ----------
        Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("➕ معامله دستی (خودت انتخاب کن)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TGold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = mSymbol, onValueChange = { mSymbol = it },
                        placeholder = { Text("نماد ارز... (BTC, ANSEM...)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { findPrice() }, enabled = !mLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = TBlue),
                        shape = RoundedCornerShape(8.dp)) {
                        Text("💲 قیمت", fontSize = 11.sp)
                    }
                }
                if (mStatus.isNotEmpty()) {
                    Text(mStatus, fontSize = 10.sp,
                        color = if (mStatus.contains("✅")) TGreen else if (mStatus.contains("❌") || mStatus.contains("⚠️")) TRed else TGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("مبلغ ($)", fontSize = 9.sp, color = TGray)
                        TextField(value = mAmount, onValueChange = { mAmount = it }, singleLine = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        Text("استاپ ٪", fontSize = 9.sp, color = TRed)
                        TextField(value = mStop, onValueChange = { mStop = it }, singleLine = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        Text("هدف ٪", fontSize = 9.sp, color = TGreen)
                        TextField(value = mTarget, onValueChange = { mTarget = it }, singleLine = true, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("استاپ شناور (تریلینگ):", fontSize = 11.sp, color = TGray)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = mTrailing, onCheckedChange = { mTrailing = it })
                    Text(if (mTrailing) "🔄 شناور" else "📌 ثابت", fontSize = 11.sp, color = if (mTrailing) TGreen else TGold)
                }
                Button(
                    onClick = {
                        val px = mPrice
                        val amt = mAmount.toDoubleOrNull() ?: 0.0
                        val stp = mStop.toDoubleOrNull() ?: 10.0
                        val tgt = mTarget.toDoubleOrNull() ?: 20.0
                        when {
                            px == null || px <= 0 -> mStatus = "❌ اول دکمه «قیمت» رو بزن"
                            amt <= 0 || amt > state.cash -> mStatus = "❌ مبلغ نامعتبر (نقد: ${usd(state.cash)})"
                            stp <= 0 || tgt <= 0 -> mStatus = "❌ استاپ/هدف نامعتبر"
                            else -> {
                                openTrade(mSymbol.uppercase(Locale.US), "دستی", px, 0, stp, amt,
                                    trailing = mTrailing, stopPctOverride = stp, targetPctOverride = tgt)
                                save()
                                mStatus = "✅ معامله ${mSymbol.uppercase(Locale.US)} باز شد (${usd(amt)})"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TGreen),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                ) { Text("🟢 باز کردن معامله", fontSize = 12.sp) }
            }
        }

        // ---------- تقسیم‌بندی ----------
        Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚙️ تقسیم‌بندی دارایی ربات (مجموع: $allocSum٪)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TBlue)
                TIERS.forEach { (name, _) ->
                    val v = alloc[name] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 11.sp, color = TGray, modifier = Modifier.width(70.dp))
                        Button(onClick = {
                            if (v > 0) { alloc[name] = v - 5; alloc = LinkedHashMap(alloc); saveAlloc(context, alloc) }
                        }, colors = ButtonDefaults.buttonColors(containerColor = TCard), shape = RoundedCornerShape(6.dp)) { Text("−", fontSize = 12.sp) }
                        Text(" $v٪ ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TGold)
                        Button(onClick = {
                            if (allocSum < 100) { alloc[name] = v + 5; alloc = LinkedHashMap(alloc); saveAlloc(context, alloc) }
                        }, colors = ButtonDefaults.buttonColors(containerColor = TCard), shape = RoundedCornerShape(6.dp)) { Text("+", fontSize = 12.sp) }
                        Spacer(Modifier.weight(1f))
                        Text("≤${MAX_PER_TIER} پوزیشن", fontSize = 9.sp, color = TGray)
                    }
                }
                if (allocSum != 100) Text("💡 مجموع رو روی ۱۰۰٪ تنظیم کن (الان $allocSum٪)", fontSize = 9.sp, color = TGold)
            }
        }

        // ---------- پوزیشن‌های باز ----------
        Text("📂 پوزیشن‌های باز (${openTrades().size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (openTrades().isEmpty()) {
            Text("هنوز پوزیشنی باز نشده 🤖", fontSize = 11.sp, color = TGray)
        }
        openTrades().forEach { t ->
            val pnl = if (t.entry > 0) (t.price - t.entry) / t.entry * 100 else 0.0
            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.symbol} • ${t.tier}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (t.trailing != false) " 🔄" else " 📌", fontSize = 12.sp)
                        Text(" (${t.score})", fontSize = 9.sp, color = TGray)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.US, "%+.2f%%", pnl), fontWeight = FontWeight.Black, fontSize = 14.sp,
                            color = if (pnl >= 0) TGreen else TRed)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ورود: ${usd(t.entry)}", fontSize = 9.sp, color = TGray)
                        Text("الان: ${usd(t.price)}", fontSize = 9.sp, color = TGray)
                        Text("استاپ: ${usd(t.stop)}", fontSize = 9.sp, color = TRed)
                        Text("هدف: ${usd(t.target)}", fontSize = 9.sp, color = TGreen)
                    }
                    Button(
                        onClick = { closeTrade(t, t.price); save(); status = "✋ ${t.symbol} دستی بسته شد" },
                        colors = ButtonDefaults.buttonColors(containerColor = TRed.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("✋ بستن دستی", fontSize = 10.sp) }
                }
            }
        }

        // ---------- تاریخچه ----------
        Text("📜 آخرین معامله‌های بسته:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (closed.isEmpty()) Text("هنوز معامله‌ای بسته نشده", fontSize = 11.sp, color = TGray)
        closed.sortedByDescending { it.closeTime }.take(15).forEach { t ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${if (t.pnl > 0) "✅" else "❌"} ${t.symbol} • ${t.tier}", fontSize = 11.sp)
                Text(String.format(Locale.US, "%+.2f%%", t.pnl), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (t.pnl > 0) TGreen else TRed)
            }
        }

        Text("⚠️ شبیه‌سازی کاغذی — پول واقعی در کار نیست.", fontSize = 9.sp, color = TGold)
    }
}

private suspend fun evalCoin(symbol: String): Pair<Int, Double> = withContext(Dispatchers.IO) {
    try {
        val kl = BinanceClient.api.klines("${symbol.uppercase(Locale.US)}USDT", "1d", 300)
        if (kl.size < 210) return@withContext 0 to 12.0
        val closes = kl.map { it[4].asDouble }
        val vols = kl.map { it[5].asDouble }
        val weekly = closes.chunked(7).map { it.last() }

        val price = closes.last()
        val e50 = emaL(closes, 50)
        val e200 = emaL(closes, 200)
        var s = when {
            price > e50 && e50 > e200 -> 50
            price > e50 -> 25
            price < e50 && e50 < e200 -> -50
            else -> -25
        }
        s += if (macdU(closes)) 20 else -20
        val r = rsi(closes)
        s += when {
            r in 45.0..65.0 -> 15
            r < 35 -> 20
            r > 75 -> -25
            else -> 5
        }
        if (vols.size > 40) {
            val rec = vols.takeLast(20).average()
            val prior = vols.dropLast(20).takeLast(20).average()
            if (prior > 0 && rec > prior * 1.2) s += 10
        }
        if (weekly.size >= 25) {
            val w = weekly.last(); val e10 = emaL(weekly, 10); val e20 = emaL(weekly, 20)
            s += when { w > e10 && e10 > e20 -> 15; w > e10 -> 8; w < e10 && e10 < e20 -> -20; else -> -8 }
        }
        if (closes.size >= 30) {
            var obv = 0.0
            val ser = mutableListOf<Double>()
            for (i in 1 until closes.size) {
                obv += when { closes[i] > closes[i - 1] -> vols[i]; closes[i] < closes[i - 1] -> -vols[i]; else -> 0.0 }
                ser.add(obv)
            }
            if (ser.size >= 21) {
                val now = ser.last(); val past = ser[ser.size - 21]
                s += when { now > past * 1.05 -> 10; now > past -> 5; now < past * 0.95 -> -10; else -> -5 }
            }
        }
        s = s.coerceIn(-100, 100)

        var atr = 0.0
        for (i in closes.size - 14 until closes.size) atr += abs(closes[i] - closes[i - 1])
        atr /= 14
        val atrPct = if (price > 0) atr / price * 100 else 12.0
        s to atrPct
    } catch (_: Exception) { 0 to 12.0 }
}

private fun emaL(d: List<Double>, p: Int): Double {
    if (d.size < p) return d.lastOrNull() ?: 0.0
    val k = 2.0 / (p + 1)
    var e = d.take(p).average()
    for (i in p until d.size) e = d[i] * k + e * (1 - k)
    return e
}

private fun rsi(d: List<Double>, p: Int = 14): Double {
    if (d.size <= p) return 50.0
    var g = 0.0; var l = 0.0
    for (i in 1..p) { val x = d[i] - d[i - 1]; if (x > 0) g += x else l -= x }
    var ag = g / p; var al = l / p
    for (i in p + 1 until d.size) {
        val x = d[i] - d[i - 1]
        ag = (ag * (p - 1) + max(x, 0.0)) / p
        al = (al * (p - 1) + max(-x, 0.0)) / p
    }
    return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
}

private fun macdU(d: List<Double>): Boolean {
    if (d.size < 35) return false
    val p = d.dropLast(1)
    return (emaL(d, 12) - emaL(d, 26)) > (emaL(p, 12) - emaL(p, 26))
}
