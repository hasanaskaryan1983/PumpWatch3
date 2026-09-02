package com.pumpwatch.app.ui
import com.pumpwatch.app.formatMarketCap
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinGeckoSearch
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.GeckoSearchCoin
import com.pumpwatch.app.data.cmcUrlByName
import com.pumpwatch.app.data.geckoPoolUrl
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import com.pumpwatch.app.formatMarketCap
import kotlinx.coroutines.launch
import java.util.Locale

private val MGreen = Color(0xFF00E676)
private val MRed = Color(0xFFFF5252)
private val MGold = Color(0xFFFFC107)
private val MBlue = Color(0xFF40C4FF)
private val MGray = Color(0xFF8B949E)
private val MCard = Color(0xFF1A2230)

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.1fK", v / 1_000)
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
    h < 48 -> "${h.toInt()} ساعت"
    else -> "${(h / 24).toInt()} روز"
}

// ---------- ۷ بررسی اعتماد ----------

private fun memeTrustChecks(s: MemeSignal, listed: Boolean): List<Pair<String, Boolean>> = listOf(
    "نقدینگی ≥ ۱۰K" to (s.liquidity >= 100_000),
    "حجم واقعی ۱س ≥ ۵۰K" to (s.volumeH1 >= 50_000),
    "معامله دوطرفه (ضد هانی‌پات)" to (s.buyRatio > 0.0 && s.buyRatio < 1.0),
    "فشار خرید مثبت ≥ ۵۵٪" to (s.buyRatio >= 0.55),
    "سن استخر ≥ ۲۴ ساعت" to (s.ageHours >= 24),
    "FDV سالم (۱۰۰K تا ۲۰M)" to (s.fdv in 100_000.0..20_000_000.0),
    "لیست‌شده در CoinGecko" to listed
)

private fun credLabel(passed: Int): Pair<String, Color> = when {
    passed >= 6 -> "اعتبار بالا ✅" to MGreen
    passed >= 4 -> "اعتبار متوسط ⚠️" to MGold
    else -> "اعتبار پایین — خطرناک! ☠️" to MRed
}

// ---------- صفحه رادار میم‌کوین ----------

@Composable
fun MemeRadarScreen() {
    val scope = rememberCoroutineScope()

    var signals by remember { mutableStateOf<List<MemeSignal>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf(false) }
    var markets by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var extra by remember { mutableStateOf<Map<String, GeckoSearchCoin>>(emptyMap()) }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            try {
                signals = MemeRadar.scan { p, t ->
                    progress = p
                    progressText = t
                }
                scanned = true
            } catch (_: Exception) {
                MemeRadar.lastScanFailed = true
                scanned = true
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        scope.launch {
            try {
                markets = ApiClient.getTop1000Coins()
            } catch (_: Exception) { }
        }
    }

    // ---------- رتبه واقعی از جستجوی CoinGecko (مثل KTA #326) ----------
    LaunchedEffect(signals) {
        if (signals.isEmpty()) return@LaunchedEffect
        scope.launch {
            val missing = signals
                .map { it.symbol.uppercase(Locale.US) }
                .distinct()
                .filter { sym -> markets.none { it.symbol.equals(sym, true) } }
                .take(8)
            missing.forEach { sym ->
                try {
                    val hit = CoinGeckoSearch.api.search(sym)
                        .coins?.firstOrNull { it.symbol.equals(sym, true) }
                    if (hit != null) extra = extra + (sym to hit)
                } catch (_: Exception) { }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- سربرگ ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🐸 رادار میم‌کوین",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refresh() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        Text(
            "شناسایی قبل از پامپ • خروج قبل از دامپ",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 11.sp, color = MGray
        )
        Text(
            "📊 ضربه روی هر کارت = نمودار ارز در CoinMarketCap",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 9.sp, color = MGray
        )

        if (loading) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MGreen)
                Spacer(Modifier.height(4.dp))
                Text("$progress% — $progressText", fontSize = 11.sp, color = MGreen)
            }
        }

        when {
            signals.isEmpty() && !loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (!scanned) "در حال اسکن استخرهای داغ..."
                    else if (MemeRadar.lastScanFailed) "⚠️ اتصال به سرورهای رادار برقرار نشد\nاینترنت/فیلترشکن رو چک کن و دوباره اسکن کن"
                    else "😴 الان میم‌کوین مستعدی پیدا نشد\nبعداً دوباره اسکن کن",
                    textAlign = TextAlign.Center,
                    color = MGray,
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(signals) { s -> MemeCard(s, markets, extra) }
                item {
                    Text(
                        "⚠️ میم‌کوین = ریسک بالا | فقط پولی که تحمل از دست دادنش رو داری",
                        fontSize = 10.sp, color = MGold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ---------- کارت میم‌سیگنال ----------

@Composable
private fun MemeCard(
    s: MemeSignal,
    markets: List<CoinMarket>,
    extra: Map<String, GeckoSearchCoin>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    val symUp = s.symbol.uppercase(Locale.US)
    val mk = markets.firstOrNull { it.symbol.equals(symUp, true) }
    val ex = extra[symUp]
    val listed = mk != null || ex != null
    val rank = mk?.market_cap_rank ?: ex?.rank
    val coinName = mk?.name ?: ex?.name ?: s.name

    val checks = memeTrustChecks(s, listed)
    val passed = checks.count { it.second }
    val (credText, credColor) = credLabel(passed)
    val scoreColor = if (s.score >= 80) MGreen else if (s.score >= 60) MGold else MRed

    Surface(
        color = MCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    val url = if (listed) cmcUrlByName(coinName)
                    else (geckoPoolUrl(s.symbol) ?: cmcUrlByName(s.name))
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) { }
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ---------- خلاصه: نماد + امتیاز ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chainEmoji(s.chain), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.symbol, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("${s.chain} • ${s.dex}", fontSize = 10.sp, color = MGray)
                    }
                    Text(s.name, fontSize = 12.sp, color = MGray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${s.score}/100",
                        color = scoreColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text("امتیاز سیگنال", fontSize = 9.sp, color = MGray)
                }
            }

            // ---------- خلاصه: قیمت + رتبه/مارکت ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    String.format(Locale.US, "$%.6f", s.price),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    if (rank != null) {
                        val capText = if (mk?.market_cap != null) " • کپ ${formatMarketCap(mk.market_cap)}" else ""
                        "🏦 رتبه #${rank}$capText"
                    } else "🏦 بدون رتبه — فقط در DEX",
                    fontSize = 10.sp, color = MBlue
                )
            }

            // ---------- خلاصه: اعتبار ----------
            Text(
                "🛡️ اعتبار: $passed از ۷ — $credText",
                fontSize = 12.sp, fontWeight = FontWeight.Black, color = credColor
            )
            if (passed <= 3) {
                Text(
                    "☠️ این ارز اعتبار پایینی داره — ریسک اسکم/راگ بالا! سمتش نرو یا فقط با پول خیلی کم.",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MRed
                )
            }

            // ---------- خلاصه: دلیل‌ها ----------
            s.reasons.take(3).forEach { r ->
                Text("• $r", fontSize = 11.sp, color = MGray)
            }

            // ---------- فلش جزئیات ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲ بستن" else "▼ جزئیات", fontSize = 10.sp)
                }
            }

            // ---------- جزئیات بازشو ----------
            if (expanded) {
                checks.forEach { (label, ok) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (ok) "✅" else "⚠️", fontSize = 10.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 10.sp, color = if (ok) MGreen else MGold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "فشار خرید: ${String.format(Locale.US, "%.0f", s.buyRatio * 100)}٪ 🐳",
                        fontSize = 11.sp,
                        color = if (s.buyRatio >= 0.6) MGreen else MGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text("حجم ۱س: ${compact(s.volumeH1)}", fontSize = 11.sp, color = MGray)
                    Text("نقدینگی: ${compact(s.liquidity)}", fontSize = 11.sp, color = MBlue)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "۱س: ${String.format(Locale.US, "%+.1f%%", s.changeH1)}",
                        fontSize = 11.sp, color = if (s.changeH1 >= 0) MGreen else MRed
                    )
                    Text(
                        "۶س: ${String.format(Locale.US, "%+.1f%%", s.changeH6)}",
                        fontSize = 11.sp, color = if (s.changeH6 >= 0) MGreen else MRed
                    )
                    Text(
                        "۲۴س: ${String.format(Locale.US, "%+.1f%%", s.changeH24)}",
                        fontSize = 11.sp, color = if (s.changeH24 >= 0) MGreen else MRed
                    )
                    Text("سن: ${ageText(s.ageHours)}", fontSize = 11.sp, color = MGray)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ورود: ${String.format(Locale.US, "$%.6f", s.entry)}", fontSize = 10.sp)
                    Text(
                        "هدف۱: ${String.format(Locale.US, "$%.6f", s.target1)}",
                        fontSize = 10.sp, color = MGreen
                    )
                    Text(
                        "هدف۲: ${String.format(Locale.US, "$%.6f", s.target2)}",
                        fontSize = 10.sp, color = MGreen
                    )
                    Text(
                        "استاپ: ${String.format(Locale.US, "$%.6f", s.stopLoss)}",
                        fontSize = 10.sp, color = MRed
                    )
                }
            }
        }
    }
}
