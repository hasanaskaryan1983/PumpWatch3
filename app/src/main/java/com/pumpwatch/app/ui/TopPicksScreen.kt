package com.pumpwatch.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
private val TCard = Color(0xFF1A2230)

private data class Pick(
    val coin: CoinMarket,
    val score: Int,
    val isPump: Boolean,
    val reasons: List<String>
)

private fun medalOf(score: Int): String = when {
    score >= 80 -> "🏆 طلایی"
    score >= 60 -> "🥈 نقره‌ای"
    else -> "🥉 برنزی"
}

private fun fmtP(price: Double): String = when {
    price >= 1000 -> String.format(Locale.US, "$%,.2f", price)
    price >= 1 -> String.format(Locale.US, "$%.4f", price)
    else -> String.format(Locale.US, "$%.6f", price)
}

// ---------- صفحه برترین‌ها ----------

@Composable
fun TopPicksScreen(mode: String) {
    val scope = rememberCoroutineScope()
    var picks by remember { mutableStateOf<List<Pick>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("all") }

    fun scan() {
        scope.launch {
            loading = true
            try {
                val coins = ApiClient.getTop1000Coins()
                picks = coins.mapNotNull { c ->
                    val h1 = c.change1h ?: 0.0
                    val h24 = c.price_change_percentage_24h ?: 0.0
                    val turnover = if (c.market_cap > 0) c.total_volume / c.market_cap * 100 else 0.0
                    val pump = h1 >= 1.0 && h24 > 0
                    val drop = h1 <= -1.0 && h24 < 0
                    if (!pump && !drop) return@mapNotNull null

                    var score = 0
                    val reasons = mutableListOf<String>()
                    if (pump) {
                        if (h1 >= 1.0) { score += 30; reasons.add("شتاب ۱ ساعته 🚀") }
                        if (h1 >= 3.0) score += 20
                        if (h24 >= 5.0) { score += 30; reasons.add("حرکت مثبت ۲۴ ساعته 📈") }
                        if (turnover >= 10.0) { score += 20; reasons.add("فعالیت/حجم بالا 💥") }
                    } else {
                        if (h1 <= -1.0) { score += 30; reasons.add("ریزش ۱ ساعته 🩸") }
                        if (h1 <= -3.0) score += 20
                        if (h24 <= -5.0) { score += 30; reasons.add("حرکت منفی ۲۴ ساعته 📉") }
                        if (turnover >= 10.0) { score += 20; reasons.add("فعالیت/حجم بالا 💥") }
                    }
                    Pick(c, score.coerceAtMost(100), pump, reasons)
                }.sortedByDescending { it.score }

                // ---------- پر کردن کش برای دستیار 🤖 ----------
                val cache = picks.map { p ->
                    val price = p.coin.current_price
                    TodayPick(
                        symbol = p.coin.symbol.uppercase(Locale.US),
                        side = if (p.isPump) "PUMP" else "DUMP",
                        score = p.score,
                        golden = p.score >= 80,
                        entry = price,
                        stopLoss = if (p.isPump) price * 0.93 else price * 1.07,
                        target1 = if (p.isPump) price * 1.15 else price * 0.85,
                        reasons = p.reasons
                    )
                }
                if (mode == "FUT") TodayPicksCache.fut = cache
                else TodayPicksCache.spot = cache
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    val pumps = picks.count { it.isPump }
    val drops = picks.size - pumps
    val golds = picks.count { it.score >= 80 }

    val shown = when (filter) {
        "pump" -> picks.filter { it.isPump }
        "drop" -> picks.filter { !it.isPump }
        "gold" -> picks.filter { it.score >= 80 }
        else -> picks
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
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text("اسکن مجدد (rescan)")
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = filter == "all", onClick = { filter = "all" },
                label = { Text("همه ${picks.size}", fontSize = 11.sp) })
            FilterChip(selected = filter == "pump", onClick = { filter = "pump" },
                label = { Text("🚀 $pumps", fontSize = 11.sp) })
            FilterChip(selected = filter == "drop", onClick = { filter = "drop" },
                label = { Text("🩸 $drops", fontSize = 11.sp) })
            FilterChip(selected = filter == "gold", onClick = { filter = "gold" },
                label = { Text("🏆 $golds", fontSize = 11.sp) })
        }

        Text(
            "🏆 = قدرت سیگنال در جهت خودش • 🚀 = ستاپ لانگ (خرید) • 🩸 = ستاپ شورت (فروش) — طلاییِ 🩸 یعنی «نخر، یا شورت کن»!",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = TGray
        )

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator(color = TGreen) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown) { p -> PickCard(p, mode) }
            }
        }
    }
}

// ---------- کارت سیگنال ----------

@Composable
private fun PickCard(p: Pick, mode: String) {
    val price = p.coin.current_price
    val entry = price
    val stop = if (p.isPump) price * 0.93 else price * 1.07
    val target = if (p.isPump) price * 1.15 else price * 0.85
    val h1 = p.coin.change1h ?: 0.0
    val h24 = p.coin.price_change_percentage_24h ?: 0.0

    Surface(
        color = TCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        p.coin.symbol.uppercase(Locale.US),
                        fontWeight = FontWeight.Black, fontSize = 16.sp
                    )
                    Text(p.coin.name, fontSize = 11.sp, color = TGray)
                }
                Text(medalOf(p.score), fontSize = 11.sp, color = TGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${p.score}/100",
                    fontSize = 15.sp, fontWeight = FontWeight.Black,
                    color = if (p.score >= 80) TGreen else if (p.score >= 60) TGold else TGray
                )
            }

            Surface(
                color = (if (p.isPump) TGreen else TRed).copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    if (p.isPump) "🚀 ستاپ لانگ (خرید)" else "🩸 ستاپ شورت (فروش) — نه خرید!",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                    color = if (p.isPump) TGreen else TRed
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "۱س: ${String.format(Locale.US, "%+.2f%%", h1)}",
                    fontSize = 11.sp, color = if (h1 >= 0) TGreen else TRed, fontWeight = FontWeight.Bold
                )
                Text(
                    "۲۴س: ${String.format(Locale.US, "%+.2f%%", h24)}",
                    fontSize = 11.sp, color = if (h24 >= 0) TGreen else TRed, fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${if (p.isPump) "ورود" else "ورود شورت"}: ${fmtP(entry)}",
                    fontSize = 11.sp
                )
                Text(
                    "${if (p.isPump) "استاپ" else "استاپ شورت"}: ${fmtP(stop)}",
                    fontSize = 11.sp, color = TRed, fontWeight = FontWeight.Bold
                )
                Text(
                    "${if (p.isPump) "هدف" else "هدف شورت"}: ${fmtP(target)}",
                    fontSize = 11.sp, color = TGreen, fontWeight = FontWeight.Bold
                )
            }

            Text(
                if (p.isPump) {
                    "💡 چه کار کنی: اسپات = خرید پله‌ای • فیوچرز = لانگ — حتماً با استاپ زیر ورود"
                } else {
                    if (mode == "SPOT") "💡 چه کار کنی: کاربر اسپات = این ارز رو الان نخر! 🩸 یعنی ستاپ شورت؛ منتظر نشانه برگشت بمون"
                    else "💡 چه کار کنی: ستاپ شورت با استاپ بالای ورود — مدیریت ریسک فراموش نشه"
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (p.isPump) TGreen else TGold
            )

            Text(
                p.reasons.joinToString(" • "),
                fontSize = 10.sp, color = TGray
            )
        }
    }
}
