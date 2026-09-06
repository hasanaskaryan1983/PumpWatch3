package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.GeckoOhlcv
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val ABlue = Color(0xFF40C4FF)
private val AGold = Color(0xFFFFC107)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A2230)
private val AOrange = Color(0xFFFFA726)
private val ACyan = Color(0xFF26C6DA)

private data class DexCandle(val o: Double, val h: Double, val l: Double, val c: Double)

private data class CoinAnalysis(
    val symbol: String, val name: String, val coingeckoId: String?, val rank: Int?,
    val score: Int, val recommendation: String, val arrow: String,
    val indicators: Map<String, String>, val whaleActivity: String, val reason: String,
    val trustScore: Int, val isDex: Boolean, val chainName: String?, val poolUrl: String?,
    val candles: List<DexCandle>
)

@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<CoinAnalysis?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchStatus by remember { mutableStateOf("") }

    fun doSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty()) return
        scope.launch {
            loading = true; error = null; searchResult = null
            searchStatus = "🔍 جستجو در ۱۰۰ ارز برتر CEX..."
            try {
                var coins = withContext(Dispatchers.IO) {
                    try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                }
                if (coins.size < 100) {
                    coins = withContext(Dispatchers.IO) {
                        try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                    }
                }
                val asRank = q.toIntOrNull()
                val found = coins.firstOrNull {
                    it.symbol.equals(q, true) || it.name.equals(q, true) ||
                            (asRank != null && it.market_cap_rank == asRank)
                }
                if (found != null) {
                    searchStatus = "✅ پیدا شد در CEX — رتبه #${found.market_cap_rank ?: "-"}"
                    searchResult = analyzeCex(found.id, found.symbol, found.name, found.market_cap_rank)
                } else {
                    searchStatus = "🔍 در CEX نبود؛ جستجو در DEX‌ها..."
                    val dex = analyzeDex(q)
                    if (dex != null) {
                        searchStatus = "✅ پیدا شد در DEX (${dex.chainName}) — تحلیل کامل با کندل GeckoTerminal"
                        searchResult = dex
                    } else {
                        searchStatus = "❌ پیدا نشد"
                        error = "ارز «$q» پیدا نشد. نماد، اسم یا عدد رتبه رو درست بنویس."
                    }
                }
            } catch (t: Throwable) {
                error = "خطا: ${t.message}"
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("🤖 دستیار هوشمند", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AGreen)
        Text("تحلیل کامل CEX (رتبه ۱-۱۰) + DEX با نمودار و اندیکاتور", fontSize = 12.sp, color = AGray,
            modifier = Modifier.padding(vertical = 8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = ACard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🔍 جستجوی ارز (نماد، اسم یا رتبه)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ABlue)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("مثلاً: BTC ، ANSEM ، USELESS یا 46", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { doSearch() }, enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = AGreen),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" تحلیل کن 🔍", fontSize = 12.sp)
                }
                if (searchStatus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(searchStatus, fontSize = 10.sp,
                        color = if (searchStatus.contains("✅")) AGreen else if (searchStatus.contains("❌")) ARed else AGray)
                }
                if (error != null) { Spacer(Modifier.height(6.dp)); Text(error ?: "", fontSize = 10.sp, color = ARed) }
            }
        }

        Spacer(Modifier.height(16.dp))

        searchResult?.let { a ->
            Card(colors = CardDefaults.cardColors(containerColor = ACard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.symbol}${if (a.isDex) " (DEX)" else ""}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ABlue)
                            Text(a.name + (if (a.rank != null) " • رتبه #${a.rank}" else if (a.chainName != null) " • ${a.chainName}" else ""),
                                fontSize = 11.sp, color = AGray)
                        }
                        Text("${a.arrow} ${a.recommendation}", fontSize = 14.sp, fontWeight = FontWeight.Black,
                            color = when {
                                a.recommendation.contains("خرید") -> AGreen
                                a.recommendation.contains("فروش") -> ARed
                                else -> AGray
                            })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📊 امتیاز: ${a.score}/100", fontSize = 12.sp, color = AGold, fontWeight = FontWeight.Bold)
                        Text("🛡️ اعتبار: ${a.trustScore}/100", fontSize = 12.sp, color = ABlue, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("📈 اندیکاتورها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AGreen)
                    a.indicators.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, fontSize = 10.sp, color = AGray)
                            Text(v, fontSize = 10.sp, color = AGray)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("🐳 خرید نهنگ‌ها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ABlue)
                    Text(a.whaleActivity, fontSize = 10.sp, color = AGray)
                    Spacer(Modifier.height(6.dp))
                    Text("💡 چرا این امتیاز؟", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AGold)
                    Text(a.reason, fontSize = 10.sp, color = AGray)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (a.candles.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = ACard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📊 نمودار ساعتی + میانگین‌های متحرک (قرمز=EMA20، آبی=EMA50)", fontSize = 10.sp, color = AGray)
                        Spacer(Modifier.height(6.dp))
                        DexChart(a.candles)
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (a.coingeckoId != null) {
                ProChart(coinId = a.coingeckoId, symbol = a.symbol)
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.coingeckoId != null) {
                    Button(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coingecko.com/en/coins/${a.coingeckoId}/chart"))) } catch (_: Throwable) { }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ABlue),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text("📈 نمودار CoinGecko", fontSize = 11.sp)
                    }
                }
                if (a.poolUrl != null) {
                    Button(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.poolUrl))) } catch (_: Throwable) { }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ACard),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text("🌊 استخر DEX", fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("⚠️ این توصیه مالی نیست — مسئولیت معامله با خودته.", fontSize = 10.sp, color = ARed)
        }
    }
}

@Composable
private fun DexChart(candles: List<DexCandle>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val vis = candles.takeLast(80)
        if (vis.size < 5) return@Canvas
        val w = size.width; val h = size.height
        val minV = vis.minOf { it.l }; val maxV = vis.maxOf { it.h }
        val range = if (maxV > minV) maxV - minV else 1.0
        val cw = w / vis.size
        val bw = cw * 0.55f
        fun y(v: Double) = (h - ((v - minV) / range * h * 0.9 + h * 0.05)).toFloat()

        vis.forEachIndexed { i, c ->
            val x = i * cw + cw / 2
            val col = if (c.c >= c.o) AGreen else ARed
            drawLine(col, Offset(x, y(c.h)), Offset(x, y(c.l)), strokeWidth = 2f)
            val yo = y(c.o); val yc = y(c.c)
            drawRect(col, topLeft = Offset(x - bw / 2, min(yo, yc)), size = Size(bw, max(3f, abs(yo - yc))))
        }
        val closes = vis.map { it.c }
        val e20 = emaSeries(closes, 20)
        val e50 = emaSeries(closes, 50)
        fun drawLine2(series: List<Double>, color: Color) {
            if (series.isEmpty()) return
            val off = closes.size - series.size
            var prev: Offset? = null
            for (i in off until closes.size) {
                val cur = Offset((i * cw + cw / 2), y(series[i - off]))
                if (prev != null) drawLine(color, prev!!, cur, strokeWidth = 2f)
                prev = cur
            }
        }
        drawLine2(e20, ARed)
        drawLine2(e50, ACyan)
    }
}

private fun emaSeries(v: List<Double>, p: Int): List<Double> {
    if (v.size < p) return emptyList()
    val k = 2.0 / (p + 1)
    val out = ArrayList<Double>(v.size)
    var e = v.take(p).average()
    for (i in v.indices) { e = if (i < p) e else v[i] * k + e * (1 - k); out.add(e) }
    return out
}

private fun rsiOf(v: List<Double>, p: Int = 14): Double {
    if (v.size <= p) return 50.0
    var g = 0.0; var l = 0.0
    for (i in 1..p) { val d = v[i] - v[i - 1]; if (d > 0) g += d else l -= d }
    var ag = g / p; var al = l / p
    for (i in p + 1 until v.size) {
        val d = v[i] - v[i - 1]
        ag = (ag * (p - 1) + max(d, 0.0)) / p
        al = (al * (p - 1) + max(-d, 0.0)) / p
    }
    return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
}

private fun macdUp(v: List<Double>): Boolean {
    if (v.size < 35) return false
    fun emaL(d: List<Double>, p: Int): Double {
        val k = 2.0 / (p + 1); var e = d.take(p).average()
        for (i in p until d.size) e = d[i] * k + e * (1 - k)
        return e
    }
    val p = v.dropLast(1)
    return (emaL(v, 12) - emaL(v, 26)) > (emaL(p, 12) - emaL(p, 26))
}

// ================= تحلیل CEX =================
private suspend fun analyzeCex(coingeckoId: String, symbol: String, name: String, rank: Int?): CoinAnalysis =
    withContext(Dispatchers.IO) {
        try {
            var closes = emptyList<Double>(); var volumes = emptyList<Double>()
            try {
                val kl = BinanceClient.api.klines("${symbol.uppercase(Locale.US)}USDT", "1h", 100)
                closes = kl.map { it[4].asDouble }; volumes = kl.map { it[5].asDouble }
            } catch (_: Throwable) { }

            var score = 50
            val ind = mutableMapOf<String, String>()
            if (closes.size >= 35) {
                val rsi = rsiOf(closes); val mUp = macdUp(closes)
                val e20 = emaSeries(closes, 20).lastOrNull() ?: closes.last()
                val e50 = emaSeries(closes, 50).lastOrNull() ?: closes.last()
                val px = closes.last()
                when { rsi < 30 -> { score += 15; ind["RSI"] = "${rsi.toInt()} اشباع فروش ✅" }
                       rsi > 70 -> { score -= 15; ind["RSI"] = "${rsi.toInt()} اشباع خرید ❌" }
                       else -> ind["RSI"] = "${rsi.toInt()} نرمال ⚪" }
                if (mUp) { score += 15; ind["MACD"] = "صعودی ✅" } else { score -= 10; ind["MACD"] = "نزولی ❌" }
                when { px > e20 && e20 > e50 -> { score += 20; ind["EMA"] = "صعودی ✅" }
                       px < e20 && e20 < e50 -> { score -= 20; ind["EMA"] = "نزولی ❌" }
                       else -> ind["EMA"] = "خنثی ⚪" }
            } else ind["تکنیکال"] = "کندل کافی نیست"

            var whale = "بدون داده"; var poolUrl: String? = null
            try {
                val pool = GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
                if (pool != null) {
                    val a = pool.attributes!!
                    val b = a.transactions?.h1?.buys ?: 0.0; val s = a.transactions?.h1?.sells ?: 0.0
                    val t = b + s
                    val net = pool.relationships?.network?.data?.id ?: "solana"
                    poolUrl = "https://www.geckoterminal.com/$net/pools/${pool.id?.substringAfter('_') ?: ""}"
                    if (t > 0) {
                        val r = b / t * 100
                        whale = "فشار خرید ۱س: ${r.toInt()}٪" + (if (r > 60) " 🟢" else if (r < 40) " 🔴" else " ⚪")
                        score += when { r > 60 -> 10; r < 40 -> -10; else -> 0 }
                    }
                }
            } catch (_: Throwable) { }

            val rec = when { score >= 80 -> "خرید قوی"; score >= 65 -> "خرید"; score >= 45 -> "صبر"; score >= 30 -> "فروش"; else -> "فروش قوی" }
            val arrow = when { score >= 65 -> "⬆️"; score <= 35 -> "⬇️"; else -> "➡️" }
            val trust = when {
                rank != null && rank <= 10 -> 95; rank != null && rank <= 50 -> 85
                rank != null && rank <= 100 -> 75; rank != null && rank <= 500 -> 60
                rank != null -> 50; else -> 30
            }
            CoinAnalysis(symbol.uppercase(Locale.US), name, coingeckoId, rank, score.coerceIn(0, 100), rec, arrow,
                ind, whale, "رتبه #$rank • امتیاز $score/100", trust, false, null, poolUrl, emptyList())
        } catch (t: Throwable) {
            CoinAnalysis(symbol, name, coingeckoId, rank, 50, "صبر", "➡️", mapOf("خطا" to "داده نیست"),
                "بدون داده", "تحلیل در دسترس نیست", 50, false, null, null, emptyList())
        }
    }

// ================= تحلیل DEX با کندل GeckoTerminal =================
private suspend fun analyzeDex(symbol: String): CoinAnalysis? = withContext(Dispatchers.IO) {
    try {
        val pool = GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null } ?: return@withContext null
        val a = pool.attributes!!
        val name = a.name ?: symbol
        val net = pool.relationships?.network?.data?.id ?: "solana"
        val addr = pool.id?.substringAfter('_') ?: ""
        val chainName = net.replaceFirstChar { it.uppercase() }
        val poolUrl = "https://www.geckoterminal.com/$net/pools/$addr"

        val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
        val b1 = a.transactions?.h1?.buys ?: 0.0
        val s1 = a.transactions?.h1?.sells ?: 0.0
        val v1 = a.volume?.h1 ?: 0.0
        val v24 = a.volume?.h24 ?: 0.0
        val fdv = a.fdvUsd ?: 0.0
        val ch24 = a.priceChange?.h24 ?: 0.0
        val ratio = if (b1 + s1 > 0) b1 / (b1 + s1) else 0.5

        // ---------- کندل ساعتی از GeckoTerminal (فایل GeckoOhlcv) ----------
        var candles = emptyList<DexCandle>()
        try {
            val oh = GeckoOhlcv.api.poolOhlcvHour(net, addr)
            candles = oh.data?.attributes?.ohlcv_list?.mapNotNull { row ->
                if (row.size >= 5) DexCandle(row[1], row[2], row[3], row[4]) else null
            } ?: emptyList()
        } catch (_: Throwable) { }

        val closes = candles.map { it.c }
        var score = 50
        val ind = mutableMapOf<String, String>()

        if (closes.size >= 35) {
            val rsi = rsiOf(closes); val mUp = macdUp(closes)
            val e20 = emaSeries(closes, 20).lastOrNull() ?: closes.last()
            val e50 = emaSeries(closes, 50).lastOrNull() ?: closes.last()
            val px = closes.last()
            when { rsi < 30 -> { score += 10; ind["RSI"] = "${rsi.toInt()} اشباع فروش ✅" }
                   rsi > 70 -> { score -= 10; ind["RSI"] = "${rsi.toInt()} اشباع خرید ❌" }
                   else -> ind["RSI"] = "${rsi.toInt()} نرمال ⚪" }
            if (mUp) { score += 10; ind["MACD"] = "صعودی ✅" } else { score -= 8; ind["MACD"] = "نزولی ❌" }
            when { px > e20 && e20 > e50 -> { score += 15; ind["EMA"] = "صعودی ✅" }
                   px < e20 && e20 < e50 -> { score -= 15; ind["EMA"] = "نزولی ❌" }
                   else -> ind["EMA"] = "خنثی ⚪" }
        } else {
            ind["تکنیکال"] = "کندل ساعتی در دسترس نیست"
        }

        score += when { ch24 > 5 -> 10; ch24 > 0 -> 5; ch24 < -5 -> -10; else -> -3 }
        ind["روند ۲۴س"] = String.format(Locale.US, "%+.1f%%", ch24) + if (ch24 > 0) " 🟢" else " 🔴"
        score += when { ratio >= 0.6 -> 10; ratio >= 0.5 -> 4; ratio <= 0.4 -> -10; else -> 0 }
        ind["فشار خرید نهنگی"] = "${(ratio * 100).toInt()}٪" + if (ratio >= 0.6) " 🟢" else if (ratio <= 0.4) " 🔴" else " ⚪"

        val whale = buildString {
            append("فشار خرید ۱س: ${(ratio * 100).toInt()}٪")
            append(if (ratio > 0.6) " 🟢 نهنگ‌ها می‌خرن" else if (ratio < 0.4) " 🔴 نهنگ‌ها می‌فروشن" else " ⚪ متعادل")
            append("\nحجم ۱س: ${String.format(Locale.US, "$%.0f", v1)} | حجم ۲۴س: ${String.format(Locale.US, "$%.0f", v24)}")
        }

        val checks = listOf(
            liq >= 100_000, v1 >= 50_000, b1 > 0 && s1 > 0, ratio >= 0.55,
            a.createdAt != null, fdv in 100_000.0..20_000_000.0, liq >= 50_000
        )
        val passed = checks.count { it }
        val trust = passed * 100 / 7

        val rec = when { score >= 80 -> "خرید قوی"; score >= 65 -> "خرید"; score >= 45 -> "صبر"; score >= 30 -> "فروش"; else -> "فروش قوی" }
        val arrow = when { score >= 65 -> "⬆️"; score <= 35 -> "⬇️"; else -> "➡️" }
        val reason = "شبکه $chainName • امتیاز $score/100 • اعتماد $passed/7 • " +
                when { score >= 65 -> "نهنگ‌ها + مومنتوم مثبت"; score <= 35 -> "فشار فروش/روند نزولی"; else -> "منتظر شکست بمون" }

        CoinAnalysis(symbol.uppercase(Locale.US), name, null, null, score.coerceIn(0, 100), rec, arrow,
            ind, whale, reason, trust, true, chainName, poolUrl, candles)
    } catch (_: Throwable) {
        null
    }
}
