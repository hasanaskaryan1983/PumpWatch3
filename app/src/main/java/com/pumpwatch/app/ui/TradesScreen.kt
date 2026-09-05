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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
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
    var closeTime: Long = 0L
)

data class PaperState(
    var cash: Double = 1000.0,
    var trades: MutableList<PaperTrade> = mutableListOf()
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
private const val MAX_PER_TIER = 3

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(loadState(context)) }
    var alloc by remember { mutableStateOf(loadAlloc(context)) }
    var botOn by remember { mutableStateOf(prefs.getBoolean("paper_bot", true)) }
    var status by remember { mutableStateOf("⏳ منتظر اولین اسکن...") }
    var confirmReset by remember { mutableStateOf(false) }

    fun save() { saveState(context, state) }

    fun openTrades() = state.trades.filter { it.status == "OPEN" }
    fun invested() = openTrades().sumOf { it.price * it.qty }
    fun equity() = state.cash + invested()

    fun closeTrade(t: PaperTrade, px: Double, manual: Boolean) {
        t.price = px
        t.status = "CLOSED"
        t.pnl = if (t.entry > 0) (px - t.entry) / t.entry * 100 else 0.0
        t.closeTime = System.currentTimeMillis()
        state.cash += t.qty * px
    }

    fun openTrade(symbol: String, tier: String, px: Double, score: Int, atrPct: Double, sizeUsd: Double) {
        if (px <= 0 || sizeUsd <= 0) return
        val sp = (atrPct * 2.5).coerceIn(7.0, 15.0)
        state.trades.add(
            PaperTrade(
                symbol = symbol, tier = tier, entry = px, sizeUsd = sizeUsd, qty = sizeUsd / px,
                price = px, stop = px * (1 - sp / 100), stopPct = sp,
                target = px * (1 + 2 * sp / 100), openTime = System.currentTimeMillis(), score = score
            )
        )
        state.cash -= sizeUsd
    }

    fun updateTrail(t: PaperTrade, px: Double): Boolean {
        t.price = px
        val nt = px * (1 - t.stopPct / 100)
        if (nt > t.stop) t.stop = nt
        return when {
            px <= t.stop -> { closeTrade(t, px, false); true }
            px >= t.target -> { closeTrade(t, px, false); true }
            else -> false
        }
    }

    fun cycle() {
        scope.launch {
            try {
                status = "🔄 اسکن بازار و مدیریت پوزیشن‌ها..."
                val eq = equity()

                // ---------- بروزرسانی قیمت + تریلینگ ----------
                val coins = withContext(Dispatchers.IO) {
                    try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                }
                val dexPools = withContext(Dispatchers.IO) {
                    listOf("solana", "bsc", "base").flatMap { ch ->
                        try { GeckoTerminal.api.trendingPools(ch).data ?: emptyList() } catch (_: Exception) { emptyList() }
                    }
                }
                val dexInfo = mutableMapOf<String, Pair<Double, Double>>()
                dexPools.forEach { p ->
                    val a = p.attributes ?: return@forEach
                    val sym = a.name?.split("/")?.firstOrNull()?.trim() ?: return@forEach
                    val px = a.priceUsd?.toDoubleOrNull() ?: return@forEach
                    val b = a.transactions?.h1?.buys ?: 0.0
                    val s = a.transactions?.h1?.sells ?: 0.0
                    dexInfo[sym] = px to (if (b + s > 0) b / (b + s) else 0.5)
                }

                var closedNow = 0
                openTrades().forEach { t ->
                    val px = if (t.tier == "DEX") dexInfo[t.symbol]?.first else
                        coins.firstOrNull { it.symbol.equals(t.symbol, true) }?.current_price
                    if (px != null && px > 0) if (updateTrail(t, px)) closedNow++
                }

                // ---------- باز کردن معامله جدید per tier ----------
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
                        val cand = dexPools.mapNotNull { p ->
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
                        }.sortedByDescending { dexInfo[it.first]?.second ?: 0.0 }.firstOrNull()
                        if (cand != null) {
                            openTrade(cand.first, "DEX", cand.second, 75, 12.0, size)
                            openedNow++
                        }
                    } else {
                        val cands = coins
                            .filter { (it.market_cap_rank ?: 0) in range!! }
                            .filter { c -> openTrades().none { it.symbol.equals(c.symbol, true) } }
                            .sortedByDescending { it.total_volume ?: 0.0 }
                            .take(4)
                        for (c in cands) {
                            val (score, atr) = evalCoin(c.symbol)
                            if (score >= 70) {
                                openTrade(c.symbol.uppercase(Locale.US), tierName, c.current_price, score, atr, size)
                                openedNow++
                                break
                            }
                        }
                    }
                }

                save()
                val e = equity()
                status = "✅ اسکن کامل | دارایی: ${usd(e)} | باز: ${openTrades().size} | بسته شد: $closedNow | باز شد: $openedNow"
            } catch (t: Throwable) {
                status = "⚠️ خطا: ${t.message}"
            }
        }
    }

    LaunchedEffect(botOn) {
        if (botOn) {
            while (true) {
                cycle()
                delay(5 * 60 * 1000)
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

        // ---------- کارت دارایی ----------
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

        // ---------- تنظیم تقسیم‌بندی ----------
        Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚙️ تقسیم‌بندی دارایی (مجموع: $allocSum٪)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TBlue)
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
            Text("هنوز پوزیشنی باز نشده — ربات دنبال سیگنال ≥۷۰ می‌گرده 🤖", fontSize = 11.sp, color = TGray)
        }
        openTrades().forEach { t ->
            val pnl = if (t.entry > 0) (t.price - t.entry) / t.entry * 100 else 0.0
            Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${t.symbol} • ${t.tier}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(" (${t.score})", fontSize = 10.sp, color = TGray)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.US, "%+.2f%%", pnl), fontWeight = FontWeight.Black, fontSize = 14.sp,
                            color = if (pnl >= 0) TGreen else TRed)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ورود: ${usd(t.entry)}", fontSize = 9.sp, color = TGray)
                        Text("الان: ${usd(t.price)}", fontSize = 9.sp, color = TGray)
                        Text("استاپ شناور: ${usd(t.stop)}", fontSize = 9.sp, color = TRed)
                        Text("هدف: ${usd(t.target)}", fontSize = 9.sp, color = TGreen)
                    }
                    Text("حجم: ${usd(t.qty * t.price)}", fontSize = 9.sp, color = TBlue)
                    Button(
                        onClick = { closeTrade(t, t.price, true); save(); status = "✋ ${t.symbol} دستی بسته شد" },
                        colors = ButtonDefaults.buttonColors(containerColor = TRed.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("✋ بستن دستی", fontSize = 10.sp) }
                }
            }
        }

        // ---------- تاریخچه ----------
        Text("📜 آخرین معامله‌های بسته:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (closed.isEmpty()) {
            Text("هنوز معامله‌ای بسته نشده", fontSize = 11.sp, color = TGray)
        }
        closed.sortedByDescending { it.closeTime }.take(15).forEach { t ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${if (t.pnl > 0) "✅" else "❌"} ${t.symbol} • ${t.tier}", fontSize = 11.sp)
                Text(String.format(Locale.US, "%+.2f%%", t.pnl), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (t.pnl > 0) TGreen else TRed)
            }
        }

        Text("⚠️ شبیه‌سازی کاغذی — پول واقعی در کار نیست. برای آموزش و تست استراتژی.", fontSize = 9.sp, color = TGold)
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
