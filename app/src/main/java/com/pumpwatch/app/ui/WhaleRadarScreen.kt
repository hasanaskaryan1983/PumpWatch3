package com.pumpwatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val WGreen = Color(0xFF00E676)
private val WRed = Color(0xFFFF5252)
private val WBlue = Color(0xFF40C4FF)
private val WGold = Color(0xFFFFC107)
private val WGray = Color(0xFF8B949E)

private val CHAINS = listOf("solana", "bsc", "base", "ethereum")

private data class ChartCandle(val o: Double, val c: Double, val marker: Int)

private data class FlowRow(val label: String, val buy: Double, val sell: Double)

private data class AnalysisData(
    val candles: List<ChartCandle>,
    val zone: Double?,
    val flows: List<FlowRow>,
    val changePct: Double,
    val poolName: String?
)

private data class WhalePick(
    val symbol: String,
    val name: String,
    val chain: String,
    val price: Double,
    val volH1: Double,
    val buysH1: Double,
    val sellsH1: Double,
    val buysH6: Double,
    val sellsH6: Double,
    val buysH24: Double,
    val sellsH24: Double,
    val liquidity: Double,
    val changeH1: Double,
    val changeH6: Double,
    val changeH24: Double,
    val ageHours: Double,
    val fdv: Double,
    val credScore: Int
)

// ---------- توابع کمکی ----------

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun chainEmoji(chain: String): String = when (chain) {
    "solana" -> "🟣"
    "bsc" -> "🟡"
    "base" -> "🔵"
    "ethereum" -> "⚪"
    else -> "⛓️"
}

private fun ageText(h: Double): String = when {
    h >= 9999 -> "—"
    h < 1 -> "زیر ۱ ساعت"
    h < 48 -> "${h.toInt()} ساعت"
    else -> "${(h / 24).toInt()} روز"
}

private fun ageHours(createdAt: String?): Double {
    if (createdAt == null) return 9999.0
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val t = sdf.parse(createdAt) ?: return 9999.0
        (System.currentTimeMillis() - t.time) / 3_600_000.0
    } catch (_: Exception) {
        9999.0
    }
}

private fun ratio(b: Double, s: Double): Double {
    val t = b + s
    return if (t > 0) b / t else 0.5
}

// ---------- ساخت WhalePick + امتیاز اعتبار ----------

private fun poolStats(p: GeckoPool): WhalePick? {
    val a = p.attributes ?: return null
    val price = a.priceUsd?.toDoubleOrNull() ?: return null
    if (price <= 0) return null
    val name = a.name ?: "?"
    val symbol = name.split("/").firstOrNull()?.trim() ?: "?"

    val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
    val b1 = a.transactions?.h1?.buys ?: 0.0
    val s1 = a.transactions?.h1?.sells ?: 0.0
    val b6 = a.transactions?.h6?.buys ?: 0.0
    val s6 = a.transactions?.h6?.sells ?: 0.0
    val b24 = a.transactions?.h24?.buys ?: 0.0
    val s24 = a.transactions?.h24?.sells ?: 0.0
    val fdv = a.fdvUsd ?: 0.0
    val age = ageHours(a.createdAt)

    var score = 0
    if (liq >= 100_000) score += 25 else if (liq >= 50_000) score += 10
    if (b1 > 0 && s1 > 0) score += 15
    val r1 = ratio(b1, s1)
    if (r1 >= 0.6) score += 25 else if (r1 >= 0.5) score += 10
    if (age >= 24) score += 15 else if (age >= 6) score += 10 else score += 5
    if (fdv in 100_000.0..10_000_000.0) score += 20

    return WhalePick(
        symbol = symbol,
        name = name,
        chain = p.relationships?.network?.data?.id ?: "?",
        price = price,
        volH1 = a.volume?.h1 ?: 0.0,
        buysH1 = b1, sellsH1 = s1,
        buysH6 = b6, sellsH6 = s6,
        buysH24 = b24, sellsH24 = s24,
        liquidity = liq,
        changeH1 = a.priceChange?.h1 ?: 0.0,
        changeH6 = a.priceChange?.h6 ?: 0.0,
        changeH24 = a.priceChange?.h24 ?: 0.0,
        ageHours = age,
        fdv = fdv,
        credScore = score.coerceAtMost(100)
    )
}

// ---------- نمودار ردپای نهنگ‌ها ----------

@Composable
private fun WhaleFlowChart(candles: List<ChartCandle>, zone: Double?) {
    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
        if (candles.size < 2) return@Canvas
        val vals = candles.flatMap { listOf(it.o, it.c) }
        val min = vals.min()
        val max = vals.max()
        val range = if (max > min) max - min else 1.0
        val w = size.width
        val h = size.height

        fun x(i: Int) = i.toFloat() / (candles.size - 1) * w
        fun y(v: Double) = (h - ((v - min) / range * h * 0.9 + h * 0.05)).toFloat()

        for (i in 1 until candles.size) {
            val c = candles[i]
            val col = if (c.c >= c.o) WGreen else WRed
            drawLine(col, Offset(x(i - 1), y(c.o)), Offset(x(i), y(c.c)), strokeWidth = 3f)
        }

        candles.forEachIndexed { i, c ->
            if (c.marker == 1) drawCircle(WGreen, radius = 8f, center = Offset(x(i), y(c.c)))
            else if (c.marker == -1) drawCircle(WRed, radius = 6f, center = Offset(x(i), y(c.c)))
        }

        if (zone != null && zone in min..max) {
            drawLine(
                WGold,
                Offset(0f, y(zone)),
                Offset(w, y(zone)),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
        }
    }
}

// ---------- صفحه اصلی ----------

@Composable
fun WhaleRadarScreen() {
    val scope = rememberCoroutineScope()

    var searchInput by remember { mutableStateOf("") }
    var analysisSymbol by remember { mutableStateOf("BTC") }
    var window by remember { mutableStateOf("1d") }
    var analysis by remember { mutableStateOf<AnalysisData?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    var leaders by remember { mutableStateOf<List<WhalePick>>(emptyList()) }
    var fresh by remember { mutableStateOf<List<WhalePick>>(emptyList()) }
    var threshold by remember { mutableStateOf(100_000.0) }
    var loadingList by remember { mutableStateOf(true) }
    var lastUpdate by remember { mutableStateOf("") }

    // ---------- تحلیل ارز دلخواه ----------
    fun analyze(symbol: String, tf: String) {
        scope.launch {
            analyzing = true
            analysisError = null
            try {
                val coins = ApiClient.getTop1000Coins()
                val coin = coins.firstOrNull {
                    it.symbol.equals(symbol, true)
                } ?: throw Exception("coin not found")

                val days: Int
                val chunk: Int
                val take: Int
                val th: Double
                when (tf) {
                    "1h" -> { days = 1; chunk = 1; take = 70; th = 0.0015 }
                    "4h" -> { days = 1; chunk = 3; take = 70; th = 0.003 }
                    "12h" -> { days = 1; chunk = 6; take = 70; th = 0.005 }
                    "1d" -> { days = 2; chunk = 2; take = 24; th = 0.008 }
                    "3d" -> { days = 6; chunk = 8; take = 18; th = 0.015 }
                    else -> { days = 89; chunk = 24; take = 7; th = 0.03 }
                }

                val chart = ApiClient.getCoinChart(coin.id, days = days)
                val prices = chart.prices.map { it[1] }
                val chunked = prices.chunked(chunk).filter { it.size == chunk }
                val candles = mutableListOf<ChartCandle>()
                val buyZones = mutableListOf<Double>()
                for (c in chunked) {
                    val o = c.first()
                    val cl = c.last()
                    val body = if (o > 0) (cl - o) / o else 0.0
                    val marker = if (body > th) 1 else if (body < -th) -1 else 0
                    if (marker == 1) buyZones.add(cl)
                    candles.add(ChartCandle(o, cl, marker))
                }
                val shown = candles.takeLast(take)
                val zone = if (buyZones.isNotEmpty()) buyZones.average() else null
                val first = prices.first()
                val last = prices.last()
                val chg = if (first > 0) (last - first) / first * 100 else 0.0

                var poolName: String? = null
                val flows = mutableListOf<FlowRow>()
                try {
                    val pool = GeckoTerminal.api.searchPools(coin.symbol)
                        .data?.firstOrNull { it.attributes != null }
                    if (pool != null) {
                        poolName = pool.attributes?.name
                        val a = pool.attributes!!
                        fun split(vol: Double?, b: Double?, s: Double?): Pair<Double, Double> {
                            val v = vol ?: 0.0
                            val bb = b ?: 0.0
                            val ss = s ?: 0.0
                            val t = bb + ss
                            if (t <= 0) return Pair(v / 2, v / 2)
                            return Pair(v * bb / t, v * ss / t)
                        }
                        val f1 = split(a.volume?.h1, a.transactions?.h1?.buys, a.transactions?.h1?.sells)
                        val f6 = split(a.volume?.h6, a.transactions?.h6?.buys, a.transactions?.h6?.sells)
                        val f24 = split(a.volume?.h24, a.transactions?.h24?.buys, a.transactions?.h24?.sells)
                        flows.add(FlowRow("۱ ساعته", f1.first, f1.second))
                        flows.add(FlowRow("۶ ساعته", f6.first, f6.second))
                        flows.add(FlowRow("۲۴ ساعته", f24.first, f24.second))
                    }
                } catch (_: Exception) { }

                analysisSymbol = coin.symbol.uppercase(Locale.US)
                analysis = AnalysisData(shown, zone, flows, chg, poolName)
            } catch (e: Exception) {
                analysis = null
                analysisError = "ارز در لیست CoinGecko پیدا نشد 🤔 (نماد معتبر بنویس، مثلاً SOL)"
            }
            analyzing = false
        }
    }

    // ---------- مهمترین نهنگ‌ها + تازه‌واردها ----------
    fun fetchLists() {
        scope.launch {
            loadingList = true
            try {
                val (trend, news) = coroutineScope {
                    val t = async(Dispatchers.IO) {
                        CHAINS.map { chain ->
                            try {
                                GeckoTerminal.api.trendingPools(chain).data ?: emptyList()
                            } catch (_: Exception) {
                                emptyList<GeckoPool>()
                            }
                        }.flatten()
                    }
                    val n = async(Dispatchers.IO) {
                        CHAINS.map { chain ->
                            try {
                                GeckoTerminal.api.newPools(chain).data ?: emptyList()
                            } catch (_: Exception) {
                                emptyList<GeckoPool>()
                            }
                        }.flatten()
                    }
                    Pair(t.await(), n.await())
                }

                leaders = trend
                    .mapNotNull { poolStats(it) }
                    .filter { it.volH1 >= threshold && it.buysH1 > it.sellsH1 && it.sellsH1 > 0 }
                    .sortedByDescending { it.volH1 }
                    .take(12)

                fresh = news
                    .mapNotNull { poolStats(it) }
                    .filter { p ->
                        p.liquidity >= 50_000 &&
                                p.fdv in 100_000.0..20_000_000.0 &&
                                p.ageHours >= 1 &&
                                p.sellsH1 > 0 &&
                                p.buysH1 > p.sellsH1 &&
                                p.credScore >= 50
                    }
                    .sortedByDescending { it.credScore * 1_000_000 + it.volH1 }
                    .take(10)

                lastUpdate = "بروزرسانی: " +
                        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            } catch (_: Exception) { }
            loadingList = false
        }
    }

    LaunchedEffect(Unit) {
        analyze("BTC", "1d")
        fetchLists()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            fetchLists()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🐳 رادار نهنگ‌ها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { fetchLists() }, enabled = !loadingList) {
                Text(if (loadingList) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ================= ۱) تحلیل ارز دلخواه =================
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🔍 تحلیل نهنگی ارز دلخواه", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("نماد ارز... مثلاً SOL", fontSize = 12.sp, color = WGray) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val s = searchInput.trim()
                                    if (s.isNotEmpty()) analyze(s, window)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("تحلیل", fontSize = 12.sp) }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("BTC", "ETH", "SOL", "XRP", "DOGE", "PEPE", "SHIB", "TON").forEach { s ->
                                FilterChip(
                                    selected = analysisSymbol == s,
                                    onClick = { analyze(s, window) },
                                    label = { Text(s, fontSize = 10.sp) }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "1h" to "۱ ساعته", "4h" to "۴ ساعته", "12h" to "۱۲ ساعته",
                                "1d" to "روزانه", "3d" to "۳ روزه", "1w" to "هفتگی"
                            ).forEach { (k, label) ->
                                FilterChip(
                                    selected = window == k,
                                    onClick = {
                                        window = k
                                        analyze(analysisSymbol, k)
                                    },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }

                        when {
                            analyzing -> Text("⏳ در حال تحلیل...", color = WGray, fontSize = 12.sp)
                            analysisError != null -> Text(analysisError ?: "", color = WRed, fontSize = 12.sp)
                            analysis != null -> {
                                val an = analysis!!

                                Text(
                                    "📈 نمودار $analysisSymbol — 🟢 شروع خرید نهنگ‌ها / 🔴 فروش سنگین / خط‌چین زرد = منطقه ورود نهنگ‌ها",
                                    fontSize = 10.sp, color = WGray
                                )
                                WhaleFlowChart(an.candles, an.zone)
                                if (an.zone != null) {
                                    Text(
                                        "🐳 نهنگ‌ها حوالی ${String.format(Locale.US, "$%,.6f", an.zone)} شروع به خرید کردن",
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WGold
                                    )
                                }
                                Text(
                                    "تغییر بازه: ${String.format(Locale.US, "%+.2f%%", an.changePct)}",
                                    fontSize = 11.sp,
                                    color = if (an.changePct >= 0) WGreen else WRed
                                )

                                if (an.flows.isNotEmpty()) {
                                    Text(
                                        "💰 جریان پول در استخر ${an.poolName ?: ""}:",
                                        fontSize = 11.sp, color = WGray
                                    )
                                    an.flows.forEach { f ->
                                        val tot = f.buy + f.sell
                                        val r = if (tot > 0) f.buy / tot else 0.5
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(f.label, fontSize = 11.sp, color = WGray, modifier = Modifier.width(64.dp))
                                            Text("🟢 ${compact(f.buy)}", fontSize = 11.sp, color = WGreen, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text("🔴 ${compact(f.sell)}", fontSize = 11.sp, color = WRed, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "${String.format(Locale.US, "%.0f", r * 100)}٪",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (r >= 0.5) WGreen else WRed
                                            )
                                        }
                                    }
                                    val f24 = an.flows.last()
                                    Text(
                                        when {
                                            f24.buy > f24.sell * 1.5 -> "💡 نهنگ‌ها دارن این ارز رو جمع می‌کنن — پتانسیل پامپ 🚀"
                                            f24.sell > f24.buy * 1.5 -> "💡 نهنگ‌ها دارن خالی می‌کنن — احتیاط 🩸"
                                            else -> "💡 تعادل خرید/فروش — منتظر شکست بمون ⚖️"
                                        },
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (f24.buy > f24.sell * 1.5) WGreen
                                        else if (f24.sell > f24.buy * 1.5) WRed else WGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= ۲) مهمترین نهنگ‌ها =================
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("👑 مهمترین نهنگ‌ها — الان دارن چی می‌خرن/می‌فروشن؟", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(selected = threshold == 50_000.0, onClick = { threshold = 50_000.0 },
                                label = { Text("۵۰ هزار", fontSize = 10.sp) })
                            FilterChip(selected = threshold == 100_000.0, onClick = { threshold = 100_000.0 },
                                label = { Text("۱۰۰ هزار", fontSize = 10.sp) })
                            FilterChip(selected = threshold == 500_000.0, onClick = { threshold = 500_000.0 },
                                label = { Text("۵۰ هزار", fontSize = 10.sp) })
                            FilterChip(selected = threshold == 1_000_000.0, onClick = { threshold = 1_000_000.0 },
                                label = { Text("۱ میلیون", fontSize = 10.sp) })
                        }

                        Text(lastUpdate, fontSize = 9.sp, color = WGray)

                        if (loadingList && leaders.isEmpty()) {
                            Text("⏳ در حال دریافت...", fontSize = 11.sp, color = WGray)
                        } else if (leaders.isEmpty()) {
                            Text("😴 فعلاً خرید نهنگی سنگینی ثبت نشده", fontSize = 11.sp, color = WGray)
                        } else {
                            leaders.forEach { l ->
                                val r1 = ratio(l.buysH1, l.sellsH1)
                                val r6 = ratio(l.buysH6, l.sellsH6)
                                val r24 = ratio(l.buysH24, l.sellsH24)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(chainEmoji(l.chain), fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(l.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            String.format(Locale.US, "$%.6f", l.price),
                                            fontSize = 11.sp, color = WGray
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "${String.format(Locale.US, "%+.1f%%", l.changeH1)} 🕐",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (l.changeH1 >= 0) WGreen else WRed
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "➕ اضافه کردن (خرید ۱س): ${l.buysH1.toInt()} معامله",
                                            fontSize = 10.sp, color = WGreen, fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "➖ فروش ۱س: ${l.sellsH1.toInt()} معامله",
                                            fontSize = 10.sp, color = WRed
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("فشار خرید ۱س: ${String.format(Locale.US, "%.0f", r1 * 100)}٪", fontSize = 10.sp, color = if (r1 >= 0.6) WGreen else WGold)
                                        Text("۶س: ${String.format(Locale.US, "%.0f", r6 * 100)}٪", fontSize = 10.sp, color = WGray)
                                        Text("۲۴س: ${String.format(Locale.US, "%.0f", r24 * 100)}٪", fontSize = 10.sp, color = WGray)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("💧 موجودی استخر: ${compact(l.liquidity)}", fontSize = 10.sp, color = WBlue)
                                        Text("حجم ۱س: ${compact(l.volH1)}", fontSize = 10.sp, color = WGray)
                                    }
                                    Text(
                                        when {
                                            r1 > r6 && r6 > r24 -> "🐳 نهنگ‌ها تازه شروع به خرید کردن — شتاب فزاینده 📈"
                                            r1 >= 0.6 -> "🐳 نهنگ‌ها در حال جمع‌کردن این ارزن 🚀"
                                            else -> "⚖️ خرید معمولی"
                                        },
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (r1 >= 0.6) WGreen else WGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= ۳) تازه‌واردهای مورد تأیید =================
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🌱 تازه‌واردهایی که نهنگ‌ها حمله کردن — با تأیید ایمنی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "امتیاز اعتبار از ۱۰۰: نقدینگی + معامله دوطرفه + فشار خرید + سن + FDV سالم",
                            fontSize = 9.sp, color = WGray
                        )

                        if (loadingList && fresh.isEmpty()) {
                            Text("⏳ در حال دریافت...", fontSize = 11.sp, color = WGray)
                        } else if (fresh.isEmpty()) {
                            Text("😴 فعلاً تازه‌وارد مورد تأییدی پیدا نشد — بعداً سر بزن", fontSize = 11.sp, color = WGray)
                        } else {
                            fresh.forEach { f ->
                                val r1 = ratio(f.buysH1, f.sellsH1)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(chainEmoji(f.chain), fontSize = 18.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(f.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "اعتبار: ${f.credScore}/۱۰۰",
                                            fontSize = 11.sp, fontWeight = FontWeight.Black,
                                            color = if (f.credScore >= 70) WGreen else WGold
                                        )
                                    }
                                    Text(
                                        "قیمت: ${String.format(Locale.US, "$%.8f", f.price)} • سن: ${ageText(f.ageHours)}",
                                        fontSize = 10.sp, color = WGray
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("💧 نقدینگی: ${compact(f.liquidity)}", fontSize = 10.sp, color = WBlue)
                                        Text("حجم ۱س: ${compact(f.volH1)}", fontSize = 10.sp, color = WGray)
                                        Text(
                                            "${String.format(Locale.US, "%+.1f%%", f.changeH1)} 🕐",
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = if (f.changeH1 >= 0) WGreen else WRed
                                        )
                                    }
                                    Text(
                                        "🐳 فشار خرید: ${String.format(Locale.US, "%.0f", r1 * 100)}٪ — نهنگ‌ها در حال خریدن",
                                        fontSize = 10.sp, color = WGreen, fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (f.liquidity >= 100_000) Text("✅ نقدینگی قوی", fontSize = 9.sp, color = WGreen)
                                        if (f.buysH1 > 0 && f.sellsH1 > 0) Text("✅ معامله دوطرفه", fontSize = 9.sp, color = WGreen)
                                        if (f.fdv in 100_000.0..10_000_000.0) Text("✅ FDV سالم", fontSize = 9.sp, color = WGreen)
                                        if (f.ageHours >= 24) Text("✅ جاافتاده", fontSize = 9.sp, color = WGreen)
                                    }
                                }
                            }
                            Text(
                                "⚠️ امتیاز اعتبار فقط معیارهای آن‌چین رو می‌سنجه — تیم/نقشه راه رو خودت هم بررسی کن. میم‌کوین = ریسک بالا!",
                                fontSize = 10.sp, color = WGold
                            )
                        }
                    }
                }
            }
        }
    }
}
