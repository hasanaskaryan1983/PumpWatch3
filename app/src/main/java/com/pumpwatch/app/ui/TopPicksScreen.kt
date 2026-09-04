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
import com.pumpwatch.app.engine.SpotEngine
import kotlinx.coroutines.launch
import java.util.Locale

private val TGreen = Color(0xFF00E676)
private val TRed = Color(0xFFFF5252)
private val TGold = Color(0xFFFFC107)
private val TGray = Color(0xFF8B949E)
private val TCard = Color(0xFF1A2230)
private val TBlue = Color(0xFF40C4FF)

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

/**
 * صفحه «برترین» — بسته به حالت، دو دنیای کاملاً جدا:
 *  SPOT → SpotEngine: کندل روزانه، کوین‌های قوی برای نگهداری هفته‌ها تا ماه‌ها
 *  FUT  → لیست نوسانی کوتاه‌مدت (موتور ۱ ساعته در تب سیگنال کار می‌کند)
 */
@Composable
fun TopPicksScreen(mode: String) {
    if (mode == "SPOT") SpotPicksScreen() else FutSwingScreen(mode)
}

// ============================================================
//  اسپات — نگهداری میان/بلندمدت
// ============================================================

@Composable
private fun SpotPicksScreen() {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<SpotEngine.SpotReport?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun scan() {
        scope.launch {
            loading = true
            report = try {
                SpotEngine.scan(listOf(
                    "ETH","BNB","SOL","XRP","DOGE","ADA","TRX","AVAX","LINK","DOT",
                    "LTC","BCH","UNI","ATOM","ETC","XLM","FIL","NEAR","APT","ARB",
                    "OP","INJ","SUI","TIA","RUNE","FET","AAVE","CRV","LDO","PEPE",
                    "WIF","BONK","FLOKI","TON","JUP","WLD","SEI","ORDI","IMX","TAO"
                ))
            } catch (_: Exception) { null }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🏦 اسپات بلندمدت (روزانه)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scan() }, enabled = !loading) { Text("اسکن مجدد") }
        }

        Text(
            "🎯 فلسفه: فقط کوین‌های «قوی» در بازار کلان صعودی — خروج با شکست EMA50 روزانه. تحمل ریسک لازم را داشته باش: آلت‌کوین‌ها حتی در روند صعودی نوسان شدید دارند.",
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
            val rep = report
            if (rep == null || !rep.macroOn) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🛑", fontSize = 44.sp)
                    Text(
                        "بازار کلان نزولی است (BTC زیر EMA200 روزانه)",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TRed
                    )
                    Text(
                        "در این رژیم، آمار تاریخی نگهداری آلت‌کوین بسیار بد است.\nاستراتژی: نقد بمان یا فقط BTC — تا روشن‌شدن رژیم صعودی.",
                        fontSize = 12.sp, color = TGray, lineHeight = 18.sp
                    )
                }
            } else {
                val picks = rep.picks
                if (picks.isEmpty()) {
                    Text(
                        "فعلاً کوینی شرایط «قوی + پولبک تازه» را ندارد — صبر",
                        color = TGray, modifier = Modifier.padding(24.dp)
                    )
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(picks) { p -> SpotPickCard(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotPickCard(p: SpotEngine.SpotPick) {
    Surface(color = TCard, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.symbol, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("قیمت: ${fmtP(p.price)}", fontSize = 12.sp, color = TGray)
                }
                Text(medalOf(p.score), fontSize = 11.sp, color = TGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${p.score}/100",
                    fontSize = 15.sp, fontWeight = FontWeight.Black,
                    color = if (p.score >= 80) TGreen else TGold
                )
            }

            Surface(color = TGreen.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    "🏦 کاندید نگهداری (هفته‌ها تا ماه‌ها)",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Black, color = TGreen
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("خط خروج (EMA50): ${fmtP(p.stop)}", fontSize = 11.sp, color = TRed, fontWeight = FontWeight.Bold)
                Text("فاصله تا خروج: ${String.format(Locale.US, "%+.1f%%", p.distEma50)}", fontSize = 11.sp, color = TGray)
            }

            Text(
                "💡 نگهداری شرطی است: تا وقتی قیمت بالای EMA50 روزانه بسته می‌شود، روند سالم است. بستن زیر آن = خروج، بدون «شاید برگردد».",
                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TBlue
            )
            Text(p.reasons.joinToString(" • "), fontSize = 10.sp, color = TGray)
        }
    }
}

// ============================================================
//  فیوچرز — نوسانی کوتاه‌مدت (لیست سریع؛ موتور اصلی در تب سیگنال)
// ============================================================

private data class FutPick(
    val coin: CoinMarket,
    val score: Int,
    val isPump: Boolean,
    val reasons: List<String>
)

@Composable
private fun FutSwingScreen(mode: String) {
    val scope = rememberCoroutineScope()
    var picks by remember { mutableStateOf<List<FutPick>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun scan() {
        scope.launch {
            loading = true
            try {
                val coins = ApiClient.getTop1000Coins()
                picks = coins.mapNotNull { c ->
                    val h1 = c.change1h ?: 0.0
                    val h24 = c.price_change_percentage_24h ?: 0.0
                    val pump = h1 >= 1.0 && h24 > 0
                    val drop = h1 <= -1.0 && h24 < 0
                    if (!pump && !drop) return@mapNotNull null
                    val vol24 = c.total_volume
                    var score = 0
                    val reasons = mutableListOf<String>()
                    if (pump) {
                        score += 30; reasons.add("شتاب ۱ ساعته 🚀")
                        if (h1 >= 3.0) score += 20
                        if (h24 >= 5.0) { score += 25; reasons.add("حرکت ۲۴ ساعته 📈") }
                    } else {
                        score += 30; reasons.add("ریزش ۱ ساعته 🩸 (شورت فیوچرز)")
                        if (h1 <= -3.0) score += 20
                        if (h24 <= -5.0) { score += 25; reasons.add("حرکت منفی ۲۴ ساعته 📉") }
                    }
                    if (vol24 > 50_000_000) { score += 15; reasons.add("نقدشوندگی بالا 💧") }
                    FutPick(c, score.coerceAtMost(100), pump, reasons)
                }.sortedByDescending { it.score }
            } catch (_: Exception) { }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚡ فیوچرز نوسانی (کوتاه‌مدت)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scan() }, enabled = !loading) { Text("اسکن مجدد") }
        }

        Text(
            "⏱ افق: چند ساعت تا چند روز • ورود/خروج دقیق با استاپ ATR از موتور سیگنال 📓 می‌آید • این لیست فقط «چه چیزی همین الان می‌جنبد» را نشان می‌دهد",
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(picks.take(25)) { p ->
                    Surface(color = TCard, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.coin.symbol.uppercase(Locale.US), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    Text(p.coin.name, fontSize = 11.sp, color = TGray)
                                }
                                Text(
                                    "${p.score}/100",
                                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                                    color = if (p.score >= 80) TGreen else TGold
                                )
                            }
                            Surface(
                                color = (if (p.isPump) TGreen else TRed).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    if (p.isPump) "🚀 مومنتوم لانگ (فقط فیوچرز)" else "🩸 مومنتوم شورت (فقط فیوچرز)",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 12.sp, fontWeight = FontWeight.Black,
                                    color = if (p.isPump) TGreen else TRed
                                )
                            }
                            Text(p.reasons.joinToString(" • "), fontSize = 10.sp, color = TGray)
                        }
                    }
                }
            }
        }
    }
}
