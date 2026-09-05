package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val ABlue = Color(0xFF40C4FF)
private val AGold = Color(0xFFFFC107)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A2230)

private data class CoinAnalysis(
    val symbol: String,
    val name: String,
    val coingeckoId: String?,
    val rank: Int?,
    val score: Int,
    val recommendation: String,
    val arrow: String,
    val indicators: Map<String, String>,
    val whaleActivity: String,
    val reason: String,
    val trustScore: Int,
    val isDex: Boolean,
    val chainName: String?,
    val poolUrl: String?
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
            loading = true
            error = null
            searchResult = null
            searchStatus = "🔍 جستجو در ۱۰۰۰ ارز برتر CEX..."
            try {
                val coins = withContext(Dispatchers.IO) {
                    try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                }
                val asRank = q.toIntOrNull()
                val found = coins.firstOrNull {
                    it.symbol.equals(q, true) || it.name.equals(q, true) ||
                            (asRank != null && it.market_cap_rank == asRank)
                }
                if (found != null) {
                    searchStatus = "✅ پیدا شد در CEX — رتبه #${found.market_cap_rank ?: "-"}"
                    searchResult = analyzeCoin(found.id, found.symbol, found.name, found.market_cap_rank, false, null)
                } else {
                    searchStatus = "🔍 در CEX نبود؛ جستجو در DEX‌ها..."
                    val dex = searchDex(q)
                    if (dex != null) {
                        searchStatus = "✅ پیدا شد در DEX (${dex.chainName})"
                        searchResult = dex
                    } else {
                        searchStatus = "❌ پیدا نشد"
                        error = "ارز «$q» نه در ۱۰۰۰ ارز برتر CEX و نه در DEX‌ها پیدا نشد. نماد یا عدد رتبه رو درست بنویس."
                    }
                }
            } catch (t: Throwable) {
                error = "خطا: ${t.message}"
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("🤖 دستیار هوشمند", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AGreen)
        Text(
            "تحلیل هر ارزی از رتبه ۱ تا ۱۰۰ CEX + تمام DEX‌ها",
            fontSize = 12.sp, color = AGray, modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(colors = CardDefaults.cardColors(containerColor = ACard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("🔍 جستجوی ارز (نماد، اسم یا رتبه)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ABlue)
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("مثلاً: BTC ، Ansem ، USELESS یا 46", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { doSearch() },
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = AGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text("  تحلیل کن 🔍", fontSize = 12.sp)
                }
                if (searchStatus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        searchStatus, fontSize = 10.sp,
                        color = if (searchStatus.contains("✅")) AGreen else if (searchStatus.contains("❌")) ARed else AGray
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "", fontSize = 10.sp, color = ARed)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        searchResult?.let { a ->
            // ---------- کارت تحلیل ----------
            Card(colors = CardDefaults.cardColors(containerColor = ACard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.symbol}${if (a.isDex) " (DEX)" else ""}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = ABlue)
                            Text(
                                a.name + (if (a.rank != null) " • رتبه #${a.rank}" else if (a.chainName != null) " • ${a.chainName}" else ""),
                                fontSize = 11.sp, color = AGray
                            )
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
                    a.indicators.forEach { (key, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(key, fontSize = 10.sp, color = AGray)
                            Text(value, fontSize = 10.sp, color = AGray)
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

            // ---------- دکمه‌های نمودار ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.coingeckoId != null) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.coingecko.com/en/coins/${a.coingeckoId}/chart")))
                            } catch (_: Throwable) { }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ABlue),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("📈 نمودار CoinGecko", fontSize = 11.sp) }
                }
                if (a.poolUrl != null) {
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.poolUrl)))
                            } catch (_: Throwable) { }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ACard),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("🌊 استخر DEX", fontSize = 11.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- نمودار حرفه‌ای داخل اپ ----------
            ProChart(coinId = a.coingeckoId ?: "", symbol = a.symbol)

            Spacer(Modifier.height(12.dp))
            Text("⚠️ این توصیه مالی نیست — مسئولیت معامله با خودته.", fontSize = 10.sp, color = ARed)
        }
    }
}

// ================= توابع تحلیل =================

private suspend fun searchDex(symbol: String): CoinAnalysis? {
    return try {
        val pool = withContext(Dispatchers.IO) {
            GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
        } ?: return null
        val name = pool.attributes?.name ?: symbol
        val network = pool.relationships?.network?.data?.id ?: "solana"
        val addr = pool.id?.substringAfter('_') ?: ""
        val chainName = network.replaceFirstChar { it.uppercase() }
        val url = "https://www.geckoterminal.com/$network/pools/$addr"
        analyzeCoin(null, symbol, name, null, true, chainName, url)
    } catch (_: Throwable) {
        null
    }
}

private suspend fun analyzeCoin(
    coingeckoId: String?, symbol: String, name: String, rank: Int?,
    isDex: Boolean, chainName: String?, poolUrl: String? = null
): CoinAnalysis = withContext(Dispatchers.IO) {
    try {
        var closes = emptyList<Double>()
        var volumes = emptyList<Double>()

        try {
            val klines = BinanceClient.api.klines("${symbol.uppercase(Locale.US)}USDT", "1h", 100)
            closes = klines.map { it[4].asDouble }
            volumes = klines.map { it[5].asDouble }
        } catch (_: Throwable) { }

        var score = 50
        val indicators = mutableMapOf<String, String>()

        if (closes.size >= 35) {
            val rsi = calculateRSI(closes)
            val macdUp = calculateMACD(closes)
            val ema20 = calculateEMA(closes, 20)
            val ema50 = calculateEMA(closes, 50)
            val price = closes.last()
            val volAvg = volumes.dropLast(1).takeLast(20).average()
            val volRatio = if (volAvg > 0) volumes.last() / volAvg else 1.0

            when {
                rsi < 30 -> { score += 15; indicators["RSI"] = "${rsi.toInt()} اشباع فروش ✅" }
                rsi > 70 -> { score -= 15; indicators["RSI"] = "${rsi.toInt()} اشباع خرید ❌" }
                else -> indicators["RSI"] = "${rsi.toInt()} نرمال ⚪"
            }
            if (macdUp) { score += 15; indicators["MACD"] = "صعودی ✅" } else { score -= 10; indicators["MACD"] = "نزولی ❌" }
            when {
                price > ema20 && ema20 > ema50 -> { score += 20; indicators["EMA"] = "روند صعودی ✅" }
                price < ema20 && ema20 < ema50 -> { score -= 20; indicators["EMA"] = "روند نزولی ❌" }
                else -> indicators["EMA"] = "خنثی ⚪"
            }
            when {
                volRatio > 2.0 -> { score += 10; indicators["حجم"] = "${String.format(Locale.US, "%.1f", volRatio)}x بالا 🔥" }
                volRatio < 0.5 -> { score -= 5; indicators["حجم"] = "${String.format(Locale.US, "%.1f", volRatio)}x پایین ⚠️" }
                else -> indicators["حجم"] = "${String.format(Locale.US, "%.1f", volRatio)}x نرمال"
            }
        } else {
            indicators["تکنیکال"] = "کندل CEX موجود نیست (ارز DEX)"
        }

        var whale = "داده‌ای موجود نیست"
        var url = poolUrl
        try {
            val pool = GeckoTerminal.api.searchPools(symbol).data?.firstOrNull { it.attributes != null }
            if (pool != null) {
                val a = pool.attributes!!
                val buys = a.transactions?.h1?.buys ?: 0.0
                val sells = a.transactions?.h1?.sells ?: 0.0
                val total = buys + sells
                val volH1 = a.volume?.h1 ?: 0.0
                val volH24 = a.volume?.h24 ?: 0.0
                if (url == null) {
                    val network = pool.relationships?.network?.data?.id ?: "solana"
                    url = "https://www.geckoterminal.com/$network/pools/${pool.id?.substringAfter('_') ?: ""}"
                }
                whale = if (total > 0) {
                    val r = buys / total * 100
                    buildString {
                        append("فشار خرید ۱س: ${r.toInt()}٪")
                        append(if (r > 60) " 🟢 نهنگ‌ها می‌خرن" else if (r < 40) " 🔴 نهنگ‌ها می‌فروشن" else " ⚪ متعادل")
                        append("\nحجم ۱س: ${String.format(Locale.US, "$%.0f", volH1)} | حجم ۲۴س: ${String.format(Locale.US, "$%.0f", volH24)}")
                    }
                } else "بدون معامله در ۱ ساعت اخیر"
            }
        } catch (_: Throwable) { }

        val recommendation = when {
            score >= 80 -> "خرید قوی"
            score >= 65 -> "خرید"
            score >= 45 -> "صبر"
            score >= 30 -> "فروش"
            else -> "فروش قوی"
        }
        val arrow = when {
            score >= 65 -> "⬆️"
            score <= 35 -> "⬇️"
            else -> "➡️"
        }

        val reason = buildString {
            if (rank != null) append("رتبه بازار #$rank • ")
            if (isDex && chainName != null) append("شبکه $chainName • ")
            append("امتیاز $score/100 • ")
            when {
                score >= 65 -> append("هم‌راستایی روند + مومنتوم + حجم")
                score <= 35 -> append("روند نزولی + ضعف مومنتوم")
                else -> append("بازار خنثی — منتظر شکست بمون")
            }
        }

        val trust = when {
            rank != null && rank <= 10 -> 95
            rank != null && rank <= 50 -> 85
            rank != null && rank <= 100 -> 75
            rank != null && rank <= 500 -> 60
            rank != null -> 50
            else -> 30
        }

        CoinAnalysis(symbol.uppercase(Locale.US), name, coingeckoId, rank, score.coerceIn(0, 100),
            recommendation, arrow, indicators, whale, reason, trust, isDex, chainName, url)
    } catch (t: Throwable) {
        CoinAnalysis(symbol.uppercase(Locale.US), name, coingeckoId, rank, 50, "صبر", "➡️",
            mapOf("خطا" to "داده کافی نیست"), "بدون داده", "تحلیل در دسترس نیست", 50, isDex, chainName, poolUrl)
    }
}

private fun calculateRSI(closes: List<Double>, period: Int = 14): Double {
    if (closes.size <= period) return 50.0
    var g = 0.0; var l = 0.0
    for (i in 1..period) { val d = closes[i] - closes[i - 1]; if (d > 0) g += d else l -= d }
    var ag = g / period; var al = l / period
    for (i in period + 1 until closes.size) {
        val d = closes[i] - closes[i - 1]
        ag = (ag * (period - 1) + max(d, 0.0)) / period
        al = (al * (period - 1) + max(-d, 0.0)) / period
    }
    return if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al)
}

private fun calculateEMA(closes: List<Double>, period: Int): Double {
    if (closes.size < period) return closes.lastOrNull() ?: 0.0
    val k = 2.0 / (period + 1)
    var e = closes.take(period).average()
    for (i in period until closes.size) e = closes[i] * k + e * (1 - k)
    return e
}

private fun calculateMACD(closes: List<Double>): Boolean {
    if (closes.size < 35) return false
    return (calculateEMA(closes, 12) - calculateEMA(closes, 26)) >
            (calculateEMA(closes.dropLast(1), 12) - calculateEMA(closes.dropLast(1), 26))
}
