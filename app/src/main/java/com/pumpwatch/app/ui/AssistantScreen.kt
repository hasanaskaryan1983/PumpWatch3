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
import com.pumpwatch.app.engine.MacdCalc
import com.pumpwatch.app.ui.components.GlassCard // 🚀 اضافه شد
import com.pumpwatch.app.ui.design.TabPalette // 🚀 اضافه شد
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
private val AOrange = Color(0xFFFFA726)
private val ACyan = Color(0xFF26C6DA)

private data class DexCandle(val o: Double, val h: Double, val l: Double, val c: Double)

private data class CoinAnalysis(
    val symbol: String, val name: String, val coingeckoId: String?, val rank: Int?,
    val score: Int, val recommendation: String, val arrow: String,
    val indicators: Map<String, String>, val whaleActivity: String, val reason: String,
    val dataScore: Int, val isDex: Boolean, val chainName: String?, val poolUrl: String?,
    val candles: List<DexCandle>,
    val invalidationScenario: String,
    val validityHours: Int,
    val formulaBreakdown: Map<String, String>
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
            searchStatus = "🔍 جستجو در ۱۰۰۰ ارز برتر CEX..."
            try {
                var coins = withContext(Dispatchers.IO) { try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() } }
                if (coins.size < 100) coins = withContext(Dispatchers.IO) { try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() } }
                val asRank = q.toIntOrNull()
                val found = coins.firstOrNull { it.symbol.equals(q, true) || it.name.equals(q, true) || (asRank != null && it.market_cap_rank == asRank) }
                if (found != null) {
                    searchStatus = "✅ پیدا شد در CEX — رتبه #${found.market_cap_rank ?: "-"}"
                    searchResult = analyzeCex(found.id, found.symbol, found.name, found.market_cap_rank)
                } else {
                    searchStatus = " در CEX نبود؛ جستجو در DEX‌ها..."
                    val dex = analyzeDex(q)
                    if (dex != null) {
                        searchStatus = "✅ پیدا شد در DEX (${dex.chainName}) — تحلیل کامل با کندل GeckoTerminal"
                        searchResult = dex
                    } else {
                        searchStatus = "❌ پیدا نشد"
                        error = "ارز «$q» پیدا نشد. نماد، اسم یا عدد رتبه رو درست بنویس."
                    }
                }
            } catch (t: Throwable) { error = "خطا: ${t.message}" }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("📊 تحلیل‌گر قواعدمحور", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
        Text("تحلیل تکنیکال CEX/DEX با RSI/MACD/EMA + جریان نهنگ‌ها (بدون AI)", fontSize = 12.sp, color = AGray, modifier = Modifier.padding(vertical = 8.dp))

        GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("ℹ️ این ابزار چگونه کار می‌کند؟", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                Text("این تحلیل‌گر از مدل زبانی یا AI استفاده نمی‌کند. امتیاز ۰-۱۰۰ فقط از جمع وزن‌دار چند اندیکاتور تکنیکال (RSI، MACD، EMA) و نسبت خرید/فروش نهنگ‌ها محاسبه می‌شود. این امتیاز احتمال موفقیت کالیبره‌شده نیست و هیچ سابقهٔ backtest عمومی ندارد. مسئولیت تصمیم نهایی با شماست.", fontSize = 10.sp, color = AGray, lineHeight = 14.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("🔍 جستجوی ارز (نماد، اسم یا رتبه)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                Spacer(Modifier.height(8.dp))
                TextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("مثلاً: BTC ، ANSEM ، USELESS یا 46", fontSize = 11.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { doSearch() }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = TabPalette.Assistant), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" تحلیل کن 🔍", fontSize = 12.sp, color = Color.Black)
                }
                if (searchStatus.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(searchStatus, fontSize = 10.sp, color = if (searchStatus.contains("✅")) AGreen else if (searchStatus.contains("❌")) ARed else AGray) }
                if (error != null) { Spacer(Modifier.height(6.dp)); Text(error ?: "", fontSize = 10.sp, color = ARed) }
            }
        }

        Spacer(Modifier.height(16.dp))

        searchResult?.let { a ->
            GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.symbol}${if (a.isDex) " (DEX)" else ""}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFE6EDF3))
                            Text(a.name + (if (a.rank != null) " • رتبه #${a.rank}" else if (a.chainName != null) " • ${a.chainName}" else ""), fontSize = 11.sp, color = AGray)
                        }
                        Text("${a.arrow} ${a.recommendation}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = when { a.recommendation.contains("خرید") -> AGreen; a.recommendation.contains("فروش") -> ARed; else -> AGray })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("📊 امتیاز فنی:", fontSize = 10.sp, color = AGray); Text("${a.score}/100", fontSize = 14.sp, color = AGold, fontWeight = FontWeight.Bold) }
                        Column(horizontalAlignment = Alignment.End) { Text("🛡️ کیفیت داده:", fontSize = 10.sp, color = AGray); Text("${a.dataScore}/100", fontSize = 14.sp, color = ABlue, fontWeight = FontWeight.Bold) }
                    }
                    Text("امتیاز فنی = جمع وزن‌دار اندیکاتورها • کیفیت داده = پوشش و تازگی منابع (نه احتمال سود)", fontSize = 9.sp, color = AGray, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("🎯 سناریوی ابطال:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AOrange)
                            Text(a.invalidationScenario, fontSize = 10.sp, color = AGray, lineHeight = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("⏱️ بازه اعتبار:", fontSize = 10.sp, color = AGray)
                        Text(if (a.validityHours >= 24) "${a.validityHours / 24} روز" else "${a.validityHours} ساعت", fontSize = 11.sp, color = AOrange, fontWeight = FontWeight.Bold)
                    }
                    Text("پس از این زمان، اندیکاتورها تغییر می‌کنند و باید دوباره تحلیل کنید.", fontSize = 9.sp, color = AGray)
                    Spacer(Modifier.height(8.dp))
                    Text("📈 اندیکاتورها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                    a.indicators.forEach { (k, v) -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(k, fontSize = 10.sp, color = AGray); Text(v, fontSize = 10.sp, color = AGray) } }
                    Spacer(Modifier.height(6.dp))
                    Text("🐳 خرید نهنگ‌ها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                    Text(a.whaleActivity, fontSize = 10.sp, color = AGray)
                    Spacer(Modifier.height(6.dp))
                    Text("💡 چرا این امتیاز؟", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                    Text(a.reason, fontSize = 10.sp, color = AGray)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (a.formulaBreakdown.isNotEmpty()) {
                GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("📐 فرمول امتیازدهی (وزن‌های شفاف)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                        Text("این امتیاز چگونه محاسبه شده؟", fontSize = 9.sp, color = AGray, modifier = Modifier.padding(bottom = 6.dp))
                        a.formulaBreakdown.forEach { (indicator, weight) -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(indicator, fontSize = 10.sp, color = AGray); Text(weight, fontSize = 10.sp, color = AGold, fontWeight = FontWeight.Bold) } }
                        Text("مجموع امتیازها در بازه ۰-۱۰۰ محدود شده است.", fontSize = 9.sp, color = AGray, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (a.candles.isNotEmpty()) {
                GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
                    Column {
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
                    Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coingecko.com/en/coins/${a.coingeckoId}/chart"))) } catch (_: Throwable) { } }, colors = ButtonDefaults.buttonColors(containerColor = ABlue), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("📊 نمودار CoinGecko", fontSize = 11.sp) }
                }
                if (a.poolUrl != null) {
                    Button(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.poolUrl))) } catch (_: Throwable) { } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2230)), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("🌊 استخر DEX", fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            
            GlassCard(accent = TabPalette.Assistant, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("⚠️ محدودیت‌های این تحلیل‌گر", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ARed)
                    Text("• این ابزار AI نیست — فقط چند فرمول تکنیکال ساده است\n• امتیاز ۰-۱۰۰ احتمال موفقیت کالیبره‌شده نیست\n• هیچ backtest عمومی روی این فرمول‌ها انجام نشده\n• مسئولیت تصمیم و معامله کاملاً با شماست", fontSize = 9.sp, color = AGray, lineHeight = 13.sp)
                }
            }
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
        val cw = w / vis.size; val bw = cw * 0.55f
        fun y(v: Double) = (h - ((v - minV) / range * h * 0.9 + h * 0.05)).toFloat()

        vis.forEachIndexed { i, c ->
            val x = i * cw + cw / 2; val col = if (c.c >= c.o) AGreen else ARed
            drawLine(col, Offset(x, y(c.h)), Offset(x, y(c.l)), strokeWidth = 2f)
            val yo = y(c.o); val yc = y(c.c)
            drawRect(col, topLeft = Offset(x - bw / 2, min(yo, yc)), size = Size(bw, max(3f, abs(yo - yc))))
        }
        val closes = vis.map { it.c }
        val e20 = emaSeries(closes, 20); val e50 = emaSeries(closes, 50)
        fun drawLine2(series: List<Double>, color: Color) {
            if (series.isEmpty()) return
            val off = closes.size - series.size; var prev: Offset? = null
            for (i in off until closes.size) {
                val cur = Offset((i * cw + cw / 2), y(series[i - off]))
                if (prev != null) drawLine(color, prev!!, cur, strokeWidth = 2f)
                prev = cur
            }
        }
        drawLine2(e20, ARed); drawLine2(e50, ACyan)
    }
}

private fun emaSeries(v: List<Double>, p: Int): List<Double> {
    if (v.size < p) return emptyList()
    val k = 2.0 / (p + 1); val out = ArrayList<Double>(v.size); var e = v.take(p).average()
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
        ag = (ag * (p - 1) + max(d, 0.0)) / p; al = (al * (p - 1) + max(-d, 0.0)) / p
    }
    return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
}

private suspend fun analyzeCex(coingeckoId: String, symbol: String, name: String, rank: Int?): CoinAnalysis = withContext(Dispatchers.IO) {
    try {
        var closes = emptyList<Double>()
        try { val kl = BinanceClient.api.klines("${symbol.uppercase(Locale.US)}USDT", "1h", 100); closes = kl.map { it[4].asDouble } } catch (_: Throwable) { }

        var score = 50; val ind = mutableMapOf<String, String>(); val formula = mutableMapOf<String, String>()
        formula["پایه"] = "۵۰ امتیاز"
        if (closes.size >= 35) {
            val rsi = rsiOf(closes); val mUp = MacdCalc.macdUp(closes)
            val e20 = emaSeries(closes, 20).lastOrNull() ?: closes.last(); val e50 = emaSeries(closes, 50).lastOrNull() ?: closes.last(); val px = closes.last()
            when { rsi < 30 -> { score += 15; ind["RSI"] = "${rsi.toInt()} اشباع فروش ✅"; formula["RSI < ۳۰"] = "+۱۵" }; rsi > 70 -> { score -= 15; ind["RSI"] = "${rsi.toInt()} اشباع خرید ❌"; formula["RSI > ۷۰"] = "−۱۵" }; else -> ind["RSI"] = "${rsi.toInt()} نرمال ⚪" }
            if (mUp) { score += 15; ind["MACD"] = "صعودی ✅"; formula["MACD صعودی"] = "+۱۵" } else { score -= 10; ind["MACD"] = "نزولی ❌"; formula["MACD نزولی"] = "−۱۰" }
            when { px > e20 && e20 > e50 -> { score += 20; ind["EMA"] = "صعودی ✅"; formula["EMA صعودی"] = "+۲۰" }; px < e20 && e20 < e50 -> { score -= 20; ind["EMA"] = "نزولی ❌"; formula["EMA نزولی"] = "−۲۰" }; else -> ind["EMA"] = "خنثی " }
        } else { ind["تکنیکال"] = "کندل کافی نیست"; formula["تکنیکال"] = "۰ (داده ناکافی)" }

        var whale = "بدون داده"; var poolUrl: String? = null
        try {
            val pool = GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
            if (pool != null) {
                val a = pool.attributes!!; val b = a.transactions?.h1?.buys ?: 0.0; val s = a.transactions?.h1?.sells ?: 0.0; val t = b + s
                val net = pool.relationships?.network?.data?.id ?: "solana"; poolUrl = "https://www.geckoterminal.com/$net/pools/${pool.id?.substringAfter('_') ?: ""}"
                if (t > 0) {
                    val r = b / t * 100; whale = "فشار خرید ۱س: ${r.toInt()}٪" + (if (r > 60) " 🟢" else if (r < 40) " 🔴" else " ")
                    when { r > 60 -> { score += 10; formula["فشار خرید > ۶۰٪"] = "+۱۰" }; r < 40 -> { score -= 10; formula["فشار خرید < ۴۰٪"] = "−۱۰" } }
                }
            }
        } catch (_: Throwable) { }

        val rec = when { score >= 80 -> "خرید قوی"; score >= 65 -> "خرید"; score >= 45 -> "صبر"; score >= 30 -> "فروش"; else -> "فروش قوی" }
        val arrow = when { score >= 65 -> "⬆️"; score <= 35 -> "⬇️"; else -> "➡️" }
        val dataScore = when { rank != null && rank <= 10 -> 95; rank != null && rank <= 50 -> 85; rank != null && rank <= 100 -> 75; rank != null && rank <= 500 -> 60; rank != null -> 50; else -> 30 }
        val invalidation = when { score >= 65 -> "اگر RSI > ۷۰ یا MACD نزولی شود، این توصیه باطل است."; score <= 35 -> "اگر RSI < ۳۰ یا MACD صعودی شود، این توصیه باطل است."; else -> "اگر RSI یا MACD سیگنال قوی بدهند، این توصیه باطل است." }
        val validityHours = when { closes.size >= 24 -> 24; closes.size >= 12 -> 12; else -> 4 }
        
        CoinAnalysis(symbol.uppercase(Locale.US), name, coingeckoId, rank, score.coerceIn(0, 100), rec, arrow, ind, whale, "رتبه #$rank • امتیاز $score/100", dataScore, false, null, poolUrl, emptyList(), invalidation, validityHours, formula)
    } catch (t: Throwable) {
        CoinAnalysis(symbol, name, coingeckoId, rank, 50, "صبر", "➡️", mapOf("خطا" to "داده نیست"), "بدون داده", "تحلیل در دسترس نیست", 50, false, null, null, emptyList(), "داده کافی برای تعیین سناریوی ابطال موجود نیست.", 4, emptyMap())
    }
}

private suspend fun analyzeDex(symbol: String): CoinAnalysis? = withContext(Dispatchers.IO) {
    try {
        val pool = GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null } ?: return@withContext null
        val a = pool.attributes!!; val name = a.name ?: symbol; val net = pool.relationships?.network?.data?.id ?: "solana"
        val addr = pool.id?.substringAfter('_') ?: ""; val chainName = net.replaceFirstChar { it.uppercase() }; val poolUrl = "https://www.geckoterminal.com/$net/pools/$addr"
        val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0; val b1 = a.transactions?.h1?.buys ?: 0.0; val s1 = a.transactions?.h1?.sells ?: 0.0
        val v1 = a.volume?.h1 ?: 0.0; val v24 = a.volume?.h24 ?: 0.0; val fdv = a.fdvUsd ?: 0.0; val ch24 = a.priceChange?.h24 ?: 0.0
        val ratio = if (b1 + s1 > 0) b1 / (b1 + s1) else 0.5

        var candles = emptyList<DexCandle>()
        try {
            val oh = GeckoOhlcv.api.poolOhlcvHour(net, addr)
            candles = oh.data?.attributes?.ohlcv_list?.mapNotNull { row -> if (row.size >= 5) DexCandle(row[1], row[2], row[3], row[4]) else null } ?: emptyList()
        } catch (_: Throwable) { }

        val closes = candles.map { it.c }; var score = 50; val ind = mutableMapOf<String, String>(); val formula = mutableMapOf<String, String>()
        formula["پایه"] = "۵۰ امتیاز"
        if (closes.size >= 35) {
            val rsi = rsiOf(closes); val mUp = MacdCalc.macdUp(closes)
            val e20 = emaSeries(closes, 20).lastOrNull() ?: closes.last(); val e50 = emaSeries(closes, 50).lastOrNull() ?: closes.last(); val px = closes.last()
            when { rsi < 30 -> { score += 10; ind["RSI"] = "${rsi.toInt()} اشباع فروش ✅"; formula["RSI < ۳۰"] = "+۱۰" }; rsi > 70 -> { score -= 10; ind["RSI"] = "${rsi.toInt()} اشباع خرید ❌"; formula["RSI > ۷۰"] = "−۱۰" }; else -> ind["RSI"] = "${rsi.toInt()} نرمال ⚪" }
            if (mUp) { score += 10; ind["MACD"] = "صعودی ✅"; formula["MACD صعودی"] = "+۱۰" } else { score -= 8; ind["MACD"] = "نزولی ❌"; formula["MACD نزولی"] = "−۸" }
            when { px > e20 && e20 > e50 -> { score += 15; ind["EMA"] = "صعودی ✅"; formula["EMA صعودی"] = "+۱۵" }; px < e20 && e20 < e50 -> { score -= 15; ind["EMA"] = "نزولی ❌"; formula["EMA نزولی"] = "−۱۵" }; else -> ind["EMA"] = "خنثی " }
        } else { ind["تکنیکال"] = "کندل ساعتی در دسترس نیست"; formula["تکنیکال"] = "۰ (داده ناکافی)" }

        score += when { ch24 > 5 -> 10; ch24 > 0 -> 5; ch24 < -5 -> -10; else -> -3 }
        ind["روند ۲۴س"] = String.format(Locale.US, "%+.1f%%", ch24) + if (ch24 > 0) " 🟢" else " 🔴"
        when { ch24 > 5 -> formula["روند ۲۴س > ۵٪"] = "+۱۰"; ch24 > 0 -> formula["روند ۲۴س > ۰٪"] = "+۵"; ch24 < -5 -> formula["روند ۲۴س < −۵٪"] = "−۱۰"; else -> formula["روند ۲۴س خنثی"] = "−۳" }
        
        score += when { ratio >= 0.6 -> 10; ratio >= 0.5 -> 4; ratio <= 0.4 -> -10; else -> 0 }
        ind["فشار خرید نهنگی"] = "${(ratio * 100).toInt()}٪" + if (ratio >= 0.6) " " else if (ratio <= 0.4) " 🔴" else " ⚪"
        when { ratio >= 0.6 -> formula["فشار خرید ≥ ۶۰٪"] = "+۱۰"; ratio >= 0.5 -> formula["فشار خرید ≥ ۵۰٪"] = "+۴"; ratio <= 0.4 -> formula["فشار خرید ≤ ۴۰٪"] = "−۱۰" }

        val whale = buildString {
            append("فشار خرید ۱س: ${(ratio * 100).toInt()}٪"); append(if (ratio > 0.6) " 🟢 نهنگ‌ها می‌خرن" else if (ratio < 0.4) " 🔴 نهنگ‌ها می‌فروشن" else " ⚪ متعادل")
            append("\nحجم ۱س: ${String.format(Locale.US, "$%.0f", v1)} | حجم ۲۴س: ${String.format(Locale.US, "$%.0f", v24)}")
        }
        val checks = listOf(liq >= 100_000, v1 >= 50_000, b1 > 0 && s1 > 0, ratio >= 0.55, a.createdAt != null, fdv in 100_000.0..20_000_000.0, liq >= 50_000)
        val passed = checks.count { it }; val dataScore = passed * 100 / 7
        val rec = when { score >= 80 -> "خرید قوی"; score >= 65 -> "خرید"; score >= 45 -> "صبر"; score >= 30 -> "فروش"; else -> "فروش قوی" }
        val arrow = when { score >= 65 -> "⬆️"; score <= 35 -> "⬇️"; else -> "➡️" }
        val reason = "شبکه $chainName • امتیاز $score/100 • هم‌گرایی داده $passed/7 • " + when { score >= 65 -> "نهنگ‌ها + مومنتوم مثبت"; score <= 35 -> "فشار فروش/روند نزولی"; else -> "منتظر شکست بمون" }
        val invalidation = when { score >= 65 -> "اگر RSI > ۷۰ یا فشار خرید < ۴۰٪ شود، این توصیه باطل است."; score <= 35 -> "اگر RSI < 30 یا فشار خرید > ۶۰٪ شود، این توصیه باطل است."; else -> "اگر RSI یا فشار خرید سیگنال قوی بدهند، این توصیه باطل است." }
        val validityHours = when { candles.size >= 24 -> 24; candles.size >= 12 -> 12; else -> 4 }

        CoinAnalysis(symbol.uppercase(Locale.US), name, null, null, score.coerceIn(0, 100), rec, arrow, ind, whale, reason, dataScore, true, chainName, poolUrl, candles, invalidation, validityHours, formula)
    } catch (_: Throwable) { null }
}
