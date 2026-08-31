package com.pumpwatch.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.WhaleClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val WGreen = Color(0xFF00E676)
private val WRed = Color(0xFFFF5252)
private val WBlue = Color(0xFF40C4FF)
private val WGold = Color(0xFFFFC107)
private val WGray = Color(0xFF8B949E)
private val WCard = Color(0xFF1A2230)

private data class WhaleTrade(
    val symbol: String,
    val side: String,
    val value: Double,
    val price: Double,
    val time: Long
)

private data class FlowStats(
    val buyQuote: Double,
    val sellQuote: Double,
    val changePct: Double,
    val whaleBuys: Int,
    val whaleSells: Int
)

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun timeAgo(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        s < 60 -> "لحظاتی پیش"
        s < 3600 -> "${s / 60} دقیقه پیش"
        else -> "${s / 3600} ساعت پیش"
    }
}

@Composable
fun WhaleRadarScreen() {
    val scope = rememberCoroutineScope()
    var trades by remember { mutableStateOf<List<WhaleTrade>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var threshold by remember { mutableStateOf(100_000.0) }
    var maxAge by remember { mutableStateOf(3_600_000.0) }
    var lastUpdate by remember { mutableStateOf("") }

    // ---------- تحلیل ارز دلخواه ----------
    var searchInput by remember { mutableStateOf("") }
    var analysisSymbol by remember { mutableStateOf("BTC") }
    var window by remember { mutableStateOf("4h") }
    var flow by remember { mutableStateOf<FlowStats?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    fun analyze(symbol: String, win: String) {
        scope.launch {
            analyzing = true
            analysisError = null
            try {
                val sym = symbol.uppercase(Locale.US).let {
                    if (it.endsWith("USDT")) it else it + "USDT"
                }
                val (interval, limit) = when (win) {
                    "1h" -> "1m" to 60
                    "4h" -> "5m" to 48
                    "12h" -> "15m" to 48
                    "1d" -> "1h" to 24
                    "3d" -> "4h" to 18
                    else -> "1d" to 7
                }
                val kl = BinanceClient.api.klines(sym, interval, limit)
                if (kl.isEmpty()) throw Exception("empty")
                var buyQ = 0.0
                var sellQ = 0.0
                for (k in kl) {
                    val qv = k[7].asDouble
                    val tb = k[10].asDouble
                    buyQ += tb
                    sellQ += (qv - tb)
                }
                val first = kl.first()[1].asDouble
                val last = kl.last()[4].asDouble
                val chg = if (first > 0) (last - first) / first * 100 else 0.0

                var wb = 0
                var ws = 0
                try {
                    val now = System.currentTimeMillis()
                    WhaleClient.api.aggTrades(sym, 1000).forEach { t ->
                        val p = t.price?.toDoubleOrNull() ?: return@forEach
                        val q = t.qty?.toDoubleOrNull() ?: return@forEach
                        if (p * q >= 100_000 && (now - (t.time ?: now)) <= 3_600_000) {
                            if (t.buyerIsMaker == true) ws++ else wb++
                        }
                    }
                } catch (_: Exception) { }

                analysisSymbol = sym.removeSuffix("USDT")
                flow = FlowStats(buyQ, sellQ, chg, wb, ws)
            } catch (_: Exception) {
                flow = null
                analysisError = "ارز پیدا نشد یا اتصال برقرار نشد 🤔"
            }
            analyzing = false
        }
    }

    fun scan() {
        scope.launch {
            loading = true
            try {
                val symbols = listOf(
                    "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
                    "XRPUSDT", "DOGEUSDT", "PEPEUSDT", "SHIBUSDT"
                )
                val now = System.currentTimeMillis()
                val all = coroutineScope {
                    symbols.map { sym ->
                        async(Dispatchers.IO) {
                            try {
                                WhaleClient.api.aggTrades(sym, 1000).mapNotNull { t ->
                                    val p = t.price?.toDoubleOrNull() ?: return@mapNotNull null
                                    val q = t.qty?.toDoubleOrNull() ?: return@mapNotNull null
                                    val v = p * q
                                    if (v < 50_000) return@mapNotNull null
                                    val ts = t.time ?: now
                                    if (now - ts > 3_600_000) return@mapNotNull null
                                    WhaleTrade(
                                        symbol = sym.removeSuffix("USDT"),
                                        side = if (t.buyerIsMaker == true) "SELL" else "BUY",
                                        value = v,
                                        price = p,
                                        time = ts
                                    )
                                }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }.flatten()
                trades = all.sortedByDescending { it.value }.take(60)
                lastUpdate = "بروزرسانی: " +
                        java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                            .format(java.util.Date())
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        scan()
        analyze("BTC", "4h")
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            scan()
        }
    }

    val now = System.currentTimeMillis()
    val shown = trades.filter {
        it.value >= threshold && (now - it.time) <= maxAge
    }
    val buys = shown.count { it.side == "BUY" }
    val sells = shown.size - buys

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- سربرگ ----------
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
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ================= بخش تحلیل ارز دلخواه =================
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

                        // جستجو
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

                        // میان‌بر ارزها
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("BTC", "ETH", "SOL", "XRP", "DOGE", "PEPE").forEach { s ->
                                FilterChip(
                                    selected = analysisSymbol == s,
                                    onClick = { analyze(s, window) },
                                    label = { Text(s, fontSize = 10.sp) }
                                )
                            }
                        }

                        // بازه زمانی
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                        // نتیجه
                        when {
                            analyzing -> Text("⏳ در حال تحلیل...", color = WGray, fontSize = 12.sp)
                            analysisError != null -> Text(analysisError ?: "", color = WRed, fontSize = 12.sp)
                            flow != null -> {
                                val f = flow!!
                                val total = f.buyQuote + f.sellQuote
                                val ratio = if (total > 0) f.buyQuote / total else 0.5

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🟢 خرید: ${compact(f.buyQuote)}", fontSize = 12.sp, color = WGreen, fontWeight = FontWeight.Bold)
                                    Text("🔴 فروش: ${compact(f.sellQuote)}", fontSize = 12.sp, color = WRed, fontWeight = FontWeight.Bold)
                                }

                                // نوار فشار خرید
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(modifier = Modifier.weight(if (ratio > 0.01) ratio.toFloat() else 0.01f)) {
                                        Surface(color = WGreen, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxSize()) { }
                                    }
                                    Box(modifier = Modifier.weight(if (ratio < 0.99) (1 - ratio).toFloat() else 0.01f)) {
                                        Surface(color = WRed, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxSize()) { }
                                    }
                                }
                                Text(
                                    "فشار خرید: ${String.format(Locale.US, "%.0f", ratio * 100)}٪",
                                    fontSize = 11.sp, color = WGray
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "جریان خالص: ${if (f.buyQuote >= f.sellQuote) "+" else "-"}${compact(kotlin.math.abs(f.buyQuote - f.sellQuote))}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (f.buyQuote >= f.sellQuote) WGreen else WRed
                                    )
                                    Text(
                                        "تغییر قیمت: ${String.format(Locale.US, "%+.2f%%", f.changePct)}",
                                        fontSize = 12.sp,
                                        color = if (f.changePct >= 0) WGreen else WRed
                                    )
                                }

                                Text(
                                    "🐳 معاملات نهنگی (۱ ساعت اخیر): خرید ${f.whaleBuys} / فروش ${f.whaleSells}",
                                    fontSize = 11.sp, color = WBlue
                                )

                                Text(
                                    when {
                                        ratio >= 0.6 -> "💡 نهنگ‌ها در حال جمع‌کردن این ارزن — پتانسیل پامپ 🚀"
                                        ratio <= 0.4 -> "💡 فشار فروش نهنگی سنگینه — احتیاط 🩸"
                                        else -> "💡 تعادل خرید/فروش — منتظر شکست بمون ⚖️"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ratio >= 0.6) WGreen else if (ratio <= 0.4) WRed else WGold
                                )
                            }
                        }
                    }
                }
            }

            // ================= بخش لیست زنده =================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📡 معاملات زنده نهنگ‌ها", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = threshold == 50_000.0, onClick = { threshold = 50_000.0 },
                            label = { Text("۵۰ هزار", fontSize = 10.sp) })
                        FilterChip(selected = threshold == 100_000.0, onClick = { threshold = 100_000.0 },
                            label = { Text("۱۰۰ هزار", fontSize = 10.sp) })
                        FilterChip(selected = threshold == 500_000.0, onClick = { threshold = 500_000.0 },
                            label = { Text("۵۰ هزار", fontSize = 10.sp) })
                        FilterChip(selected = threshold == 1_000_000.0, onClick = { threshold = 1_000_000.0 },
                            label = { Text("۱ میلیون", fontSize = 10.sp) })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = maxAge == 300_000.0, onClick = { maxAge = 300_000.0 },
                            label = { Text("⏱ ۵ دقیقه", fontSize = 10.sp) })
                        FilterChip(selected = maxAge == 900_000.0, onClick = { maxAge = 900_000.0 },
                            label = { Text("⏱ ۵ دقیقه", fontSize = 10.sp) })
                        FilterChip(selected = maxAge == 3_600_000.0, onClick = { maxAge = 3_600_000.0 },
                            label = { Text("⏱ ۱ ساعت", fontSize = 10.sp) })
                    }

                    if (shown.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("🟢 خرید: $buys", fontSize = 11.sp, color = WGreen, fontWeight = FontWeight.Bold)
                            Text("🔴 فروش: $sells", fontSize = 11.sp, color = WRed, fontWeight = FontWeight.Bold)
                            if (buys > sells * 2) Text("💡 فشار خرید سنگین", fontSize = 10.sp, color = WGold)
                            if (sells > buys * 2) Text("💡 فشار فروش سنگین", fontSize = 10.sp, color = WRed)
                        }
                    }
                    Text(lastUpdate, fontSize = 9.sp, color = WGray)
                }
            }

            if (shown.isEmpty() && !loading) {
                item {
                    Text(
                        "😴 فعلاً معامله‌ای بالای این آستانه در این بازه ثبت نشده\nرادار هر ۲۰ ثانیه خودکار آپدیت می‌شه",
                        textAlign = TextAlign.Center,
                        color = WGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }

            items(shown) { t -> WhaleCard(t) }
        }
    }
}

@Composable
private fun WhaleCard(t: WhaleTrade) {
    val mega = t.value >= 1_000_000
    val isBuy = t.side == "BUY"
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (mega) "🐋" else "🐳", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.symbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isBuy) "خرید نهنگی 🟢" else "فروش نهنگی 🔴",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBuy) WGreen else WRed
                    )
                }
                Text(
                    "قیمت: ${String.format(Locale.US, "$%,.4f", t.price)} • ${timeAgo(t.time)}",
                    fontSize = 10.sp,
                    color = WGray
                )
            }
            Text(
                compact(t.value),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = if (mega) WGold else WBlue
            )
        }
    }
}
