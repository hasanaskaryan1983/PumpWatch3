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
import androidx.compose.material3.CircularProgressIndicator
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
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import kotlinx.coroutines.launch
import java.util.Locale

private val TGreen = Color(0xFF00E676)
private val TRed = Color(0xFFFF5252)
private val TGold = Color(0xFFFFC107)
private val TGray = Color(0xFF8B949E)

// ---------- مدل سیگنال ----------

data class TPick(
    val symbol: String,
    val name: String,
    val side: String,
    val score: Int,
    val golden: Boolean,
    val entry: Double,
    val stopLoss: Double,
    val target1: Double,
    val reasons: List<String>,
    val change1h: Double,
    val change24h: Double
)

// ---------- کش نتایج برای ربات دستیار ----------

object TodayPicksCache {
    var spot: List<TPick> = emptyList()
    var fut: List<TPick> = emptyList()
}

// ---------- امتیازدهی سریع (بدون درخواست اضافه) ----------

private fun evalPick(c: CoinMarket): TPick? {
    val c1 = c.change1h ?: 0.0
    val c24 = c.price_change_percentage_24h ?: 0.0
    val c7 = c.change7d ?: 0.0
    val cap = c.market_cap
    val turnover = if (cap > 0) c.total_volume / cap else 0.0
    val high = c.high24h ?: 0.0
    val low = c.low24h ?: 0.0
    val rangePos = if (high > low) (c.current_price - low) / (high - low) else 0.5

    var pump = 0
    val pr = mutableListOf<String>()
    if (c1 >= 1.0) { pump += 25; pr.add("شتاب ۱ ساعته 🚀 (1-hour acceleration 🚀)") }
    if (c1 >= 3.0) pump += 15
    if (c24 in 2.0..35.0) { pump += 20; pr.add("حرکت مثبت ۲۴ ساعته (positive 24h move)") }
    if (turnover >= 0.15) { pump += 20; pr.add("حجم غیرعادی 💥 (abnormal volume 💥)") }
    if (rangePos >= 0.85) { pump += 20; pr.add("شکست سقف ۲۴س 📈 (24h high break 📈)") }
    if (c7 > 10) pump += 5

    var dump = 0
    val dr = mutableListOf<String>()
    if (c1 <= -1.0) { dump += 25; dr.add("ریزش ۱ ساعته 🩸 (1-hour drop 🩸)") }
    if (c1 <= -3.0) dump += 15
    if (c24 in -35.0..-2.0) { dump += 20; dr.add("حرکت منفی ۲۴ ساعته (negative 24h move)") }
    if (turnover >= 0.15) { dump += 20; dr.add("حجم غیرعادی 💥 (abnormal volume 💥)") }
    if (rangePos <= 0.15) { dump += 20; dr.add("شکست کف ۲۴س 📉 (24h low break 📉)") }
    if (c7 < -10) dump += 5

    val side = if (pump >= dump) "PUMP" else "DUMP"
    val score = maxOf(pump, dump).coerceAtMost(100)
    if (score < 40) return null

    val price = c.current_price
    val risk = if (high > low) (high - low) * 0.3 else price * 0.03
    val (stop, target) = if (side == "PUMP")
        Pair(price - risk, price + risk * 2)
    else
        Pair(price + risk, price - risk * 2)

    return TPick(
        symbol = c.symbol.uppercase(Locale.US),
        name = c.name,
        side = side,
        score = score,
        golden = score >= 80 && turnover >= 0.2,
        entry = price,
        stopLoss = stop,
        target1 = target,
        reasons = if (side == "PUMP") pr else dr,
        change1h = c1,
        change24h = c24
    )
}

// ---------- صفحه برترین‌ها ----------

@Composable
fun TopPicksScreen(mode: String) {
    val scope = rememberCoroutineScope()
    var picks by remember { mutableStateOf<List<TPick>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }

    fun scan() {
        scope.launch {
            scanning = true
            step = "دریافت لیست ارزها... (fetching coin list...)"
            try {
                val coins = if (mode == "FUT") ApiClient.getTop100Coins() else ApiClient.getTop1000Coins()
                step = "امتیازدهی به ${coins.size} ارز... (scoring ${coins.size} coins...)"
                val result = coins.mapNotNull { evalPick(it) }.sortedByDescending { it.score }
                if (mode == "FUT") TodayPicksCache.fut = result else TodayPicksCache.spot = result
                picks = result
                step = ""
            } catch (e: Exception) {
                step = "خطا: ${e.message} (error: ${e.message})"
            }
            scanning = false
        }
    }

    LaunchedEffect(mode) {
        val cached = if (mode == "FUT") TodayPicksCache.fut else TodayPicksCache.spot
        if (cached.isNotEmpty()) picks = cached else scan()
    }

    val shown = picks.filter { p ->
        when (filter) {
            "PUMP" -> p.side == "PUMP"
            "DUMP" -> p.side == "DUMP"
            "GOLD" -> p.golden
            else -> true
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
                "🏆 برترین‌های ${if (mode == "FUT") "فیوچرز (Futures)" else "اسپات (Spot)"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            if (scanning) Text("در حال اسکن... (scanning...)", fontSize = 11.sp, color = TGray)
            else TextButton(onClick = { scan() }) { Text("اسکن مجدد (rescan)") }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" },
                label = { Text("همه ${picks.size} (all ${picks.size})") })
            FilterChip(selected = filter == "PUMP", onClick = { filter = "PUMP" },
                label = { Text("🚀 ${picks.count { it.side == "PUMP" }}") })
            FilterChip(selected = filter == "DUMP", onClick = { filter = "DUMP" },
                label = { Text("🩸 ${picks.count { it.side == "DUMP" }}") })
            FilterChip(selected = filter == "GOLD", onClick = { filter = "GOLD" },
                label = { Text("🏆 ${picks.count { it.golden }}") })
        }

        if (step.isNotEmpty()) {
            Text(
                step,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 12.sp,
                color = TGreen
            )
        }

        when {
            scanning && picks.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = TGreen) }

            shown.isEmpty() && !scanning -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "فعلاً سیگنال قوی نیست — بازار آرومه 😴 (no strong signal right now — market is calm 😴)",
                    color = TGray,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown) { p -> PickCard(p) }
            }
        }
    }
}

// ---------- کارت سیگنال ----------

@Composable
private fun PickCard(p: TPick) {
    val sideColor = if (p.side == "PUMP") TGreen else TRed
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.side == "PUMP") "🚀" else "🩸", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (p.golden) {
                            Spacer(Modifier.width(6.dp))
                            Text("🏅 طلایی (golden)", fontSize = 10.sp, color = TGold)
                        }
                    }
                    Text(p.name, fontSize = 12.sp, color = TGray)
                }
                Text("${p.score}/100", color = sideColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("۱س (1h): ${String.format(Locale.US, "%+.2f%%", p.change1h)}", fontSize = 11.sp,
                    color = if (p.change1h >= 0) TGreen else TRed)
                Text("۲۴س (24h): ${String.format(Locale.US, "%+.2f%%", p.change24h)}", fontSize = 11.sp,
                    color = if (p.change24h >= 0) TGreen else TRed)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ورود (entry): ${String.format(Locale.US, "$%.6f", p.entry)}", fontSize = 11.sp)
                Text("استاپ (stop): ${String.format(Locale.US, "$%.6f", p.stopLoss)}", fontSize = 11.sp, color = TRed)
                Text("هدف (target): ${String.format(Locale.US, "$%.6f", p.target1)}", fontSize = 11.sp, color = TGreen)
            }

            Text(
                p.reasons.take(2).joinToString(" • "),
                fontSize = 10.sp,
                color = TGray
            )
        }
    }
}
