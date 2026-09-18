package com.pumpwatch.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.sourceLabel
import com.pumpwatch.app.engine.ScoringEngine
import com.pumpwatch.app.engine.WhaleFlowEngine
import com.pumpwatch.app.engine.WhaleFlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private val TGreen = Color(0xFF00E676)
private val TRed = Color(0xFFFF5252)
private val TBlue = Color(0xFF40C4FF)
private val TGold = Color(0xFFFFC107)
private val TGray = Color(0xFF8B949E)
private val TCard = Color(0xFF1A2230)
private val TPurple = Color(0xFFCE93D8)
private val TCardB = Color(0xFF141B25)

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

private data class TierStats(
    val count: Int,
    val wins: Int,
    val pnlPct: Double,
    val pnlUsd: Double,
    val winRate: Double
)

private data class MonthlyPnl(
    val key: String,
    val label: String,
    val pnlPct: Double,
    val count: Int
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

// ---------- 🚀 Sprint 13 (F6d): محاسبات ژورنال ----------

private fun rMultiple(t: PaperTrade): Double {
    if (t.entry <= 0.0 || t.stopPct <= 0.0) return 0.0
    val risk = t.entry * t.stopPct / 100.0
    val move = t.price - t.entry
    return move / risk
}

private fun buildEquityCurve(closed: List<PaperTrade>): List<Double> {
    val sorted = closed.sortedBy { it.closeTime }
    val curve = mutableListOf(100.0)
    var equity = 100.0
    sorted.forEach { t ->
        equity *= (1 + t.pnl / 100.0)
        curve.add(equity)
    }
    return curve
}

private fun computeMaxDrawdown(curve: List<Double>): Double {
    if (curve.isEmpty()) return 0.0
    var peak = curve[0]
    var maxDD = 0.0
    for (e in curve) {
        if (e > peak) peak = e
        val dd = if (peak > 0) (peak - e) / peak * 100.0 else 0.0
        if (dd > maxDD) maxDD = dd
    }
    return maxDD
}

private fun computeMonthly(closed: List<PaperTrade>): List<MonthlyPnl> {
    val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
    val labelFmt = SimpleDateFormat("MMM yy", Locale.US)
    val grouped = closed
        .filter { it.closeTime > 0 }
        .groupBy { fmt.format(Date(it.closeTime)) }
    return grouped.entries
        .sortedBy { it.key }
        .map { (key, list) ->
            MonthlyPnl(
                key = key,
                label = try { labelFmt.format(Date(list.first().closeTime)) } catch (_: Exception) { key },
                pnlPct = list.sumOf { it.pnl * it.sizeUsd / 100.0 },
                count = list.size
            )
        }
}

private fun computeTierStats(closed: List<PaperTrade>): Map<String, TierStats> {
    return closed.groupBy { it.tier }.mapValues { (_, list) ->
        val wins = list.count { it.pnl > 0 }
        val pnlPct = list.sumOf { it.pnl }
        val pnlUsd = list.sumOf { it.pnl * it.sizeUsd / 100.0 }
        val winRate = if (list.isEmpty()) 0.0 else wins * 100.0 / list.size
        TierStats(list.size, wins, pnlPct, pnlUsd, winRate)
    }
}

@Composable
fun TradesScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(loadState(context)) }
    var alloc by remember { mutableStateOf(loadAlloc(context)) }
    var botOn by remember { mutableStateOf(prefs.getBoolean("paper_bot", false)) }
    var status by remember { mutableStateOf("⏳ منتظر اولین اسکن...") }
    var confirmReset by remember { mutableStateOf(false) }
    var consensus by remember { mutableStateOf<List<ConsensusPick>>(emptyList()) }
    var realWhale by remember { mutableStateOf<Map<String, WhaleFlowResult>>(emptyMap()) }

    // 🚀 Sprint 13 (F6d): sub-tab switcher بین Paper و Journal
    var journalTab by remember { mutableStateOf(false) }

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
        if (t.target > 0 && px >= t.target) {
            t.target = -1.0
            val lock = t.entry * (1 + t.stopPct / 100)
            if (lock > t.stop) t.stop = lock
        }
        if (t.trailing != false) {
            val dist = if (t.target < 0) t.stopPct * 0.7 else t.stopPct
            val nt = px * (1 - dist / 100)
            if (nt > t.stop) t.stop = nt
        }
        return if (px <= t.stop) { closeTrade(t, px); true } else false
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

                val dexPools = coroutineScope {
                    listOf("solana", "bsc", "base", "optimism", "arbitrum", "polygon", "avalanche", "ton").map { ch ->
                        async(Dispatchers.IO) {
                            try { GeckoTerminal.api.trendingPools(ch).data ?: emptyList<GeckoPool>() }
                            catch (_: Exception) { emptyList<GeckoPool>() }
                        }
                    }.awaitAll().flatten()
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
                    val (trend, atr) = ScoringEngine.score(sym, live = true)
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

                val rwList = coroutineScope {
                    consensus.take(6).map { pk ->
                        async(Dispatchers.IO) {
                            try {
                                val r = WhaleFlowEngine.analyze(pk.symbol + "USDT", 100_000.0, 1000)
                                if (r != null) pk.symbol to r else null
                            } catch (_: Exception) { null }
                        }
                    }.awaitAll().filterNotNull()
                }
                realWhale = rwList.toMap()

                var openedNow = 0
                if (botOn) for ((tierName, range) in TIERS) {
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
                            val (score, atr) = ScoringEngine.score(c.symbol, live = true)
                            val ch24 = c.price_change_percentage_24h ?: 0.0
                            if (score >= 70 || (ch24 >= 8.0 && score >= 45)) {
                                openTrade(c.symbol.uppercase(Locale.US), tierName, c.current_price, score, atr, min(size, state.cash))
                                openedNow++
                                tierOpened++
                            }
                        }
                    }
                }

                var consensusOpened = 0
                if (botOn) for (pk in consensus) {
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
            Text("📈 معاملات و ژورنال", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("ربات:", fontSize = 12.sp, color = TGray)
            Switch(checked = botOn, onCheckedChange = {
                botOn = it
                prefs.edit().putBoolean("paper_bot", it).apply()
            })
        }

        // 🚀 Sprint 13 (F6d): sub-tab switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { journalTab = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!journalTab) TBlue else TCard
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("📈 معاملات", fontSize = 12.sp, color = Color.White) }
            Button(
                onClick = { journalTab = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (journalTab) TPurple else TCard
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) { Text("📓 ژورنال", fontSize = 12.sp, color = Color.White) }
        }

        if (journalTab) {
            JournalContent(state.trades)
        } else {
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
                            status = "♻️ ریست شد — ۱۰۰۰$ تازه"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (confirmReset) TRed else TCard),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                ) { Text(if (confirmReset) "مطمئنی؟ بزن قطعی!" else "♻️ ریست کامل", fontSize = 11.sp) }
            }
            if (confirmReset) Text("⚠️ دکمه ریست رو دوباره بزن تا همه چی صفر بشه", fontSize = 9.sp, color = TRed)

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
                            Text("🐳 فشار DEX: ${String.format(Locale.US, "%.0f", pk.whaleRatio * 100)}٪", fontSize = 10.sp, color = if (pk.whaleRatio >= 0.6) TGreen else if (pk.whaleRatio > 0) TRed else TGray)
                            Text("⚡ ۴س: ${String.format(Locale.US, "%+.1f%%", pk.ch24)}", fontSize = 10.sp, color = if (pk.ch24 >= 0) TGreen else TRed)
                        }
                        realWhale[pk.symbol]?.let { rw ->
                            Text(
                                // 🚀 Sprint 14 (Commit 5): منبع واقعی جریان نهنگ‌ها — نه برچسب سخت‌کدشده
                                "🐳 جریان نهنگ‌ها (${rw.sourceLabel()}): ${String.format(Locale.US, "%.0f", rw.buyRatio * 100)}٪ خرید • ${rw.whaleTrades} معاملهٔ بالای ۱۰۰K",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (rw.buyRatio >= 0.6) TGreen else if (rw.buyRatio <= 0.4) TRed else TGray
                            )
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

            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("➕ معامله دستی (خودت انتخاب کن)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TGold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = mSymbol, onValueChange = { mSymbol = it },
                            placeholder = { Text("نماد ارز... (BTC, ZCAT...)", fontSize = 11.sp) },
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
                            if (t.target < 0) Text(" 🏃", fontSize = 12.sp)
                            Text(" (${t.score})", fontSize = 9.sp, color = TGray)
                            Spacer(Modifier.weight(1f))
                            Text(String.format(Locale.US, "%+.2f%%", pnl), fontWeight = FontWeight.Black, fontSize = 14.sp,
                                color = if (pnl >= 0) TGreen else TRed)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ورود: ${usd(t.entry)}", fontSize = 9.sp, color = TGray)
                            Text("الان: ${usd(t.price)}", fontSize = 9.sp, color = TGray)
                            Text("استاپ: ${usd(t.stop)}", fontSize = 9.sp, color = TRed)
                            Text(if (t.target > 0) "هدف: ${usd(t.target)}" else "هدف: 🏃 آزاد", fontSize = 9.sp, color = TGreen)
                        }
                        if (t.target < 0) Text("🏃 حالت دونده: سود قفل شده، تا برگشت روند ادامه می‌ده", fontSize = 9.sp, color = TGold)
                        Button(
                            onClick = { closeTrade(t, t.price); save(); status = "✋ ${t.symbol} دستی بسته شد" },
                            colors = ButtonDefaults.buttonColors(containerColor = TRed.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                        ) { Text("✋ بستن دستی", fontSize = 10.sp) }
                    }
                }
            }

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
}

// ====================================================================
// =====================  📓 ژورنال پیشرفته  =========================
// ====================================================================

@Composable
private fun JournalContent(allTrades: List<PaperTrade>) {
    val closed = allTrades.filter { it.status == "CLOSED" }
    val wins = closed.filter { it.pnl > 0 }
    val losses = closed.filter { it.pnl < 0 }

    val winRate = if (closed.isEmpty()) 0.0 else wins.size * 100.0 / closed.size
    val avgPnl = if (closed.isEmpty()) 0.0 else closed.map { it.pnl }.average()
    val avgWin = if (wins.isEmpty()) 0.0 else wins.map { it.pnl }.average()
    val avgLoss = if (losses.isEmpty()) 0.0 else abs(losses.map { it.pnl }.average())
    val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)
    val totalPnlUsd = closed.sumOf { it.pnl * it.sizeUsd / 100.0 }

    val grossProfit = wins.sumOf { it.pnl * it.sizeUsd / 100.0 }
    val grossLoss = abs(losses.sumOf { it.pnl * it.sizeUsd / 100.0 })
    val profitFactor = when {
        grossLoss > 0 -> grossProfit / grossLoss
        grossProfit > 0 -> Double.POSITIVE_INFINITY
        else -> 0.0
    }

    val equityCurve = buildEquityCurve(closed)
    val maxDD = computeMaxDrawdown(equityCurve)

    val rMultiples = closed.map { rMultiple(it) }
    val avgR = if (rMultiples.isEmpty()) 0.0 else rMultiples.average()
    val bestR = rMultiples.maxOrNull() ?: 0.0
    val worstR = rMultiples.minOrNull() ?: 0.0

    val monthly = computeMonthly(closed)
    val tierStats = computeTierStats(closed)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "📓 ژورنال عملکرد (${closed.size} معاملهٔ بسته‌شده)",
            fontWeight = FontWeight.Black, fontSize = 15.sp, color = TPurple
        )

        if (closed.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Text("هنوز معاملهٔ بسته‌شده‌ای نیست", fontSize = 13.sp, color = TGray)
                    Text("بعد از بسته‌شدن اولین معامله، ژورنال اینجا نمایش داده می‌شود", fontSize = 11.sp, color = TGray)
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📊 خلاصهٔ عملکرد", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TBlue)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("کل معاملات", "${closed.size}", TGray)
                        Stat("✅ برد", "${wins.size}", TGreen)
                        Stat("❌ باخت", "${losses.size}", TRed)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("وین‌ریت", String.format(Locale.US, "%.1f%%", winRate),
                            if (winRate >= 50) TGreen else TRed)
                        Stat("Profit Factor",
                            if (profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", profitFactor),
                            if (profitFactor >= 1.5) TGreen else if (profitFactor >= 1.0) TGold else TRed)
                        Stat("Max DD", String.format(Locale.US, "%.1f%%", maxDD),
                            if (maxDD < 15) TGreen else if (maxDD < 30) TGold else TRed)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("میانگین PnL", String.format(Locale.US, "%+.2f%%", avgPnl),
                            if (avgPnl >= 0) TGreen else TRed)
                        Stat("امید ریاضی", String.format(Locale.US, "%+.2f%%", expectancy),
                            if (expectancy > 0) TGreen else TRed)
                        Stat("PnL کل", String.format(Locale.US, "%+.2f$", totalPnlUsd),
                            if (totalPnlUsd >= 0) TGreen else TRed)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🎯 کیفیت معاملات (R-multiple و میانگین‌ها)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TBlue)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("میانگین R", String.format(Locale.US, "%+.2f", avgR),
                            if (avgR >= 0.5) TGreen else if (avgR >= 0) TGold else TRed)
                        Stat("بهترین R", String.format(Locale.US, "%+.2f", bestR), TGreen)
                        Stat("بدترین R", String.format(Locale.US, "%+.2f", worstR), TRed)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("میانگین سود", String.format(Locale.US, "%+.2f%%", avgWin), TGreen)
                        Stat("میانگین ضرر", String.format(Locale.US, "%+.2f%%", -avgLoss), TRed)
                    }

                    val rr = if (avgLoss > 0) avgWin / avgLoss else 0.0
                    Text(
                        "💡 نسبت Reward:Risk = ${String.format(Locale.US, "%.2f:1", rr)}" +
                            (if (rr >= 2.0) " — عالی" else if (rr >= 1.0) " — قابل‌قبول" else " — نیاز به بهبود"),
                        fontSize = 10.sp, color = if (rr >= 2.0) TGreen else if (rr >= 1.0) TGold else TRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📈 منحنی سرمایه", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TBlue)
                    Text(
                        "شروع: ۱۰۰$ • الان: ${String.format(Locale.US, "$%.2f", equityCurve.lastOrNull() ?: 100.0)}" +
                            " • رشد: ${String.format(Locale.US, "%+.1f%%", (equityCurve.lastOrNull() ?: 100.0) - 100)}",
                        fontSize = 10.sp, color = TGray
                    )
                    EquityChart(equityCurve)
                }
            }

            if (monthly.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🗓️ Heatmap ماهانه (PnL $)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TBlue)
                        val maxAbs = monthly.maxOfOrNull { abs(it.pnlPct) }?.coerceAtLeast(1.0) ?: 1.0
                        monthly.chunked(4).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { m ->
                                    val intensity = (abs(m.pnlPct) / maxAbs).coerceIn(0.0, 1.0).toFloat()
                                    val base = if (m.pnlPct >= 0) TGreen else TRed
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.3f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(base.copy(alpha = 0.15f + intensity * 0.5f))
                                            .padding(4.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(m.label, fontSize = 8.sp, color = TGray)
                                            Text(
                                                String.format(Locale.US, "%+.0f$", m.pnlPct),
                                                fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White
                                            )
                                            Text("${m.count}", fontSize = 8.sp, color = TGray)
                                        }
                                    }
                                }
                                repeat(4 - row.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            if (tierStats.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🏷️ عملکرد بر اساس دستهٔ ارز", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TBlue)
                        Text("کدام دسته برای تو سودآورتر بوده؟", fontSize = 9.sp, color = TGray)
                        tierStats.entries
                            .sortedByDescending { it.value.pnlUsd }
                            .forEach { (tier, s) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tier, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("${s.count} معامله", fontSize = 10.sp, color = TGray)
                                    Text(
                                        String.format(Locale.US, "%.0f%% برد", s.winRate),
                                        fontSize = 10.sp, color = if (s.winRate >= 50) TGreen else TRed
                                    )
                                    Text(
                                        String.format(Locale.US, "%+.1f$", s.pnlUsd),
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (s.pnlUsd >= 0) TGreen else TRed
                                    )
                                }
                            }
                    }
                }
            }

            Text("📋 ۳۰ معاملهٔ اخیر (جزئیات کامل)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TPurple)
            closed.sortedByDescending { it.closeTime }.take(30).forEach { t ->
                Card(colors = CardDefaults.cardColors(containerColor = TCardB), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (t.pnl > 0) "✅" else "❌"} ${t.symbol}",
                                fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("• ${t.tier}", fontSize = 10.sp, color = TGray)
                            Spacer(Modifier.weight(1f))
                            Text(
                                String.format(Locale.US, "%+.2f%%", t.pnl),
                                fontWeight = FontWeight.Black, fontSize = 13.sp,
                                color = if (t.pnl > 0) TGreen else TRed
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ورود: ${usd(t.entry)}", fontSize = 9.sp, color = TGray)
                            Text("خروج: ${usd(t.price)}", fontSize = 9.sp, color = TGray)
                            Text("امتیاز: ${t.score}", fontSize = 9.sp, color = TGold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سایز: ${usd(t.sizeUsd)}", fontSize = 9.sp, color = TGray)
                            Text(
                                "PnL $: ${String.format(Locale.US, "%+.2f", t.pnl * t.sizeUsd / 100)}",
                                fontSize = 9.sp, color = if (t.pnl >= 0) TGreen else TRed
                            )
                            Text(
                                "R: ${String.format(Locale.US, "%+.2f", rMultiple(t))}",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (rMultiple(t) >= 1) TGreen else if (rMultiple(t) >= 0) TGold else TRed
                            )
                        }
                        if (t.trailing == false) Text("📌 استاپ ثابت", fontSize = 8.sp, color = TGold)
                        else if (t.target < 0) Text("🏃 حالت دونده", fontSize = 8.sp, color = TGold)
                        else Text("🔄 تریلینگ فعال", fontSize = 8.sp, color = TBlue)
                    }
                }
            }

            Text(
                "💡 تفسیر ژورنال: وین‌ریت ≥ ۵۰٪ + Profit Factor ≥ ۱.۵ + R میانگین ≥ ۰.۵ = استراتژی سودآور. " +
                "Max DD < ۲۰٪ = ریسک کنترل‌شده. اگر هر کدام پایین‌تر است، تنظیمات ربات را بازبینی کن.",
                fontSize = 9.sp, color = TGold, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Text(label, fontSize = 9.sp, color = TGray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun EquityChart(curve: List<Double>) {
    if (curve.size < 2) {
        Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            Text("داده کافی نیست", fontSize = 10.sp, color = TGray)
        }
        return
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val minV = curve.min().coerceAtMost(100.0)
        val maxV = curve.max().coerceAtLeast(100.0)
        val range = if (maxV > minV) maxV - minV else 1.0
        val w = size.width
        val h = size.height
        val pad = 20f

        fun y(v: Double) = pad + ((maxV - v) / range * (h - 2 * pad)).toFloat()

        drawLine(
            TGray.copy(alpha = 0.3f),
            Offset(0f, y(100.0)),
            Offset(w, y(100.0)),
            strokeWidth = 1f
        )

        for (i in 1 until curve.size) {
            val x1 = (i - 1).toFloat() / (curve.size - 1) * w
            val x2 = i.toFloat() / (curve.size - 1) * w
            val col = if (curve[i] >= curve[i - 1]) TGreen else TRed
            drawLine(col, Offset(x1, y(curve[i - 1])), Offset(x2, y(curve[i])), strokeWidth = 3f)
        }

        val paint = android.graphics.Paint().apply {
            textSize = 22f
            color = android.graphics.Color.GRAY
        }
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "$%.0f", maxV), 4f, y(maxV) + 14f, paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "$%.0f", minV), 4f, y(minV) - 4f, paint
        )
    }
}
