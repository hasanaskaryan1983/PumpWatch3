package com.pumpwatch.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import kotlinx.coroutines.launch
import java.util.Locale

private val BGreen = Color(0xFF00E676)
private val BRed = Color(0xFFFF5252)
private val BGold = Color(0xFFFFC107)
private val BGray = Color(0xFF8B949E)

private data class BTrade(
    val entry: Double,
    val exit: Double,
    val pnl: Double
)

private data class BStats(
    val total: Int,
    val wins: Int,
    val winRate: Double,
    val totalPnl: Double,
    val maxDrawdown: Double
)

// ---------- موتور بک‌تست محلی ----------

private fun emaSeries(v: List<Double>, p: Int): List<Double> {
    if (v.size < p) return emptyList()
    val k = 2.0 / (p + 1)
    val out = ArrayList<Double>(v.size)
    var e = v.take(p).average()
    for (i in v.indices) {
        e = if (i < p) e else v[i] * k + e * (1 - k)
        out.add(e)
    }
    return out
}

private fun rsiSeries(v: List<Double>, p: Int = 14): List<Double> {
    if (v.size <= p) return emptyList()
    val out = ArrayList<Double>(v.size)
    for (i in 0 until p) out.add(50.0)
    var g = 0.0
    var l = 0.0
    for (i in 1..p) {
        val d = v[i] - v[i - 1]
        if (d > 0) g += d else l -= d
    }
    var ag = g / p
    var al = l / p
    out.add(if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al))
    for (i in p + 1 until v.size) {
        val d = v[i] - v[i - 1]
        ag = (ag * (p - 1) + maxOf(d, 0.0)) / p
        al = (al * (p - 1) + maxOf(-d, 0.0)) / p
        out.add(if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al))
    }
    return out
}

private fun runBacktest(closes: List<Double>, strategy: String): Pair<BStats, List<BTrade>> {
    val buys = mutableListOf<Int>()
    val sells = mutableListOf<Int>()

    if (strategy == "EMA") {
        val e20 = emaSeries(closes, 20)
        val e50 = emaSeries(closes, 50)
        val o20 = closes.size - e20.size
        val o50 = closes.size - e50.size
        for (i in 1 until closes.size) {
            val a = i - o20
            val b = i - o50
            if (a < 1 || b < 1) continue
            if (e20[a - 1] <= e50[b - 1] && e20[a] > e50[b]) buys.add(i)
            if (e20[a - 1] >= e50[b - 1] && e20[a] < e50[b]) sells.add(i)
        }
    } else {
        val rsi = rsiSeries(closes)
        val off = closes.size - rsi.size
        for (i in 1 until rsi.size) {
            if (rsi[i - 1] < 30 && rsi[i] >= 30) buys.add(i + off)
            if (rsi[i - 1] > 70 && rsi[i] <= 70) sells.add(i + off)
        }
    }

    // شبیه‌سازی معاملات لانگ
    val trades = mutableListOf<BTrade>()
    var openAt: Double? = null
    var bi = 0
    var si = 0
    while (bi < buys.size || si < sells.size) {
        if (openAt == null) {
            if (bi >= buys.size) break
            val b = buys[bi++]
            openAt = closes[b]
        } else {
            val nextSell = sells.firstOrNull { it > buys.getOrElse(bi - 1) { 0 } }
            if (nextSell == null) {
                trades.add(BTrade(openAt, closes.last(), (closes.last() - openAt) / openAt * 100))
                openAt = null
                break
            } else {
                trades.add(BTrade(openAt, closes[nextSell], (closes[nextSell] - openAt) / openAt * 100))
                openAt = null
                si = sells.indexOf(nextSell) + 1
            }
        }
    }
    if (openAt != null) {
        trades.add(BTrade(openAt, closes.last(), (closes.last() - openAt) / openAt * 100))
    }

    val wins = trades.count { it.pnl > 0 }
    val totalPnl = trades.sumOf { it.pnl }
    var cum = 0.0
    var peak = 0.0
    var maxDd = 0.0
    trades.forEach { t ->
        cum += t.pnl
        if (cum > peak) peak = cum
        val dd = peak - cum
        if (dd > maxDd) maxDd = dd
    }

    val stats = BStats(
        total = trades.size,
        wins = wins,
        winRate = if (trades.isNotEmpty()) wins * 100.0 / trades.size else 0.0,
        totalPnl = totalPnl,
        maxDrawdown = maxDd
    )
    return Pair(stats, trades)
}

// ---------- صفحه بک‌تست ----------

@Composable
fun BacktestScreen() {
    val scope = rememberCoroutineScope()
    var coinId by remember { mutableStateOf("bitcoin") }
    var symbol by remember { mutableStateOf("BTC") }
    var strategy by remember { mutableStateOf("EMA") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stats by remember { mutableStateOf<BStats?>(null) }
    var trades by remember { mutableStateOf<List<BTrade>>(emptyList()) }

    val coins = listOf(
        "bitcoin" to "BTC",
        "ethereum" to "ETH",
        "solana" to "SOL",
        "binancecoin" to "BNB",
        "ripple" to "XRP",
        "dogecoin" to "DOGE",
        "tron" to "TRX",
        "cardano" to "ADA"
    )

    fun run() {
        scope.launch {
            running = true
            error = null
            try {
                val chart = ApiClient.getCoinChart(coinId, days = 30)
                val closes = chart.prices.map { it[1] }
                if (closes.size < 100) {
                    error = "داده کافی برای بک‌تست نیست"
                } else {
                    val r = runBacktest(closes, strategy)
                    stats = r.first
                    trades = r.second
                }
            } catch (e: Exception) {
                error = e.message
            }
            running = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "🧪 بک‌تست استراتژی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "عملکرد استراتژی روی ۳۰ روز گذشته — قبل از ریسک واقعی!",
            fontSize = 11.sp,
            color = BGray
        )

        // ---------- انتخاب ارز ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            coins.take(4).forEach { (id, sym) ->
                FilterChip(
                    selected = coinId == id,
                    onClick = { coinId = id; symbol = sym; stats = null },
                    label = { Text(sym, fontSize = 11.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            coins.drop(4).forEach { (id, sym) ->
                FilterChip(
                    selected = coinId == id,
                    onClick = { coinId = id; symbol = sym; stats = null },
                    label = { Text(sym, fontSize = 11.sp) }
                )
            }
        }

        // ---------- انتخاب استراتژی ----------
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = strategy == "EMA",
                onClick = { strategy = "EMA"; stats = null },
                label = { Text("📊 کراس EMA", fontSize = 11.sp) }
            )
            FilterChip(
                selected = strategy == "RSI",
                onClick = { strategy = "RSI"; stats = null },
                label = { Text("📈 RSI 30/70", fontSize = 11.sp) }
            )
        }

        Button(
            onClick = { run() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BGreen),
            enabled = !running
        ) {
            Text(if (running) "در حال اجرا..." else "▶️ اجرای بک‌تست", fontWeight = FontWeight.Bold)
        }

        when {
            running -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = BGreen) }

            error != null -> Text(
                error ?: "",
                color = BRed,
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center
            )

            stats != null -> {
                val s = stats!!

                // ---------- کارت آمار ----------
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "نتایج $symbol — ۳۰ روز گذشته",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox("معاملات", "${s.total}", BGray)
                            StatBox("بردها", "${s.wins}", BGreen)
                            StatBox("وین‌ریت", String.format(Locale.US, "%.0f%%", s.winRate),
                                if (s.winRate >= 50) BGreen else BRed)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatBox("سود کل", String.format(Locale.US, "%+.1f%%", s.totalPnl),
                                if (s.totalPnl >= 0) BGreen else BRed)
                            StatBox("بیشترین افت", String.format(Locale.US, "%.1f%%", s.maxDrawdown), BRed)
                        }
                        Text(
                            when {
                                s.totalPnl > 0 && s.winRate >= 50 -> "✅ این استراتژی روی این ارز جواب داده — قابل بررسی برای ورود"
                                s.total == 0 -> "⚪ سیگنالی تولید نشده — استراتژی دیگه رو تست کن"
                                else -> "⚠️ این استراتژی روی این ارز ضررده — سراغش نرو!"
                            },
                            fontSize = 12.sp,
                            color = if (s.totalPnl > 0) BGreen else BGold
                        )
                    }
                }

                // ---------- لیست معاملات ----------
                Text("📜 معاملات اخیر:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                trades.takeLast(10).reversed().forEachIndexed { i, t ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${i + 1}.", fontSize = 11.sp, color = BGray)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    String.format(Locale.US, "ورود: $%.4f", t.entry),
                                    fontSize = 11.sp
                                )
                                Text(
                                    String.format(Locale.US, "خروج: $%.4f", t.exit),
                                    fontSize = 11.sp,
                                    color = BGray
                                )
                            }
                            Text(
                                String.format(Locale.US, "%+.1f%%", t.pnl),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (t.pnl >= 0) BGreen else BRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color)
        Text(label, fontSize = 10.sp, color = BGray)
    }
}
