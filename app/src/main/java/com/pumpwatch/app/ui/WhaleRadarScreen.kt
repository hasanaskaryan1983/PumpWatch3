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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private data class WhaleTrade(
    val symbol: String,
    val side: String,
    val value: Double,
    val price: Double,
    val time: Long
)

private fun compact(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "$%.2fM", v / 1_000_000)
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
    var lastUpdate by remember { mutableStateOf("") }

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
                                WhaleClient.api.aggTrades(sym, 500).mapNotNull { t ->
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
                trades = all.sortedByDescending { it.value }.take(50)
                lastUpdate = "بروزرسانی: " +
                        java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                            .format(java.util.Date())
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    // آپدیت خودکار هر ۲۰ ثانیه
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            scan()
        }
    }

    val shown = trades.filter { it.value >= threshold }
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

        Text(
            "معاملات غول‌پیکر لحظه‌ای — ردپای پول هوشمند • $lastUpdate",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 10.sp,
            color = WGray
        )

        // ---------- آستانه ----------
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(50_000.0 to "۵۰K", 100_000.0 to "۱۰K", 500_000.0 to "۵۰K", 1_000_000.0 to "۱M").forEach { (v, label) ->
                FilterChip(
                    selected = threshold == v,
                    onClick = { threshold = v },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        // ---------- فشار نهنگی ----------
        if (shown.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🟢 خرید نهنگی: $buys", fontSize = 11.sp, color = WGreen, fontWeight = FontWeight.Bold)
                Text("🔴 فروش نهنگی: $sells", fontSize = 11.sp, color = WRed, fontWeight = FontWeight.Bold)
                if (buys > sells * 2) Text("💡 فشار خرید سنگین — پتانسیل پامپ", fontSize = 10.sp, color = WGold)
            }
        }

        // ---------- لیست ----------
        when {
            shown.isEmpty() && loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text("📡 در حال شنود معاملات...", color = WGray) }

            shown.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "😴 فعلاً معامله‌ای بالای ${compact(threshold)} ثبت نشده\nچند لحظه صبر کن — رادار خودکار آپدیت می‌شه",
                    textAlign = TextAlign.Center,
                    color = WGray,
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown) { t -> WhaleCard(t) }
            }
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
