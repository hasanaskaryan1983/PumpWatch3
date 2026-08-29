package com.pumpwatch.app.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val AGold = Color(0xFFFFC107)
private val ABlue = Color(0xFF40C4FF)

// ---------- ارزیابی هشدار ----------

private data class AlertEval(
    val coin: CoinMarket,
    val side: String,
    val score: Int,
    val early: Boolean,
    val reasons: List<String>
)

private fun eval(c: CoinMarket): AlertEval? {
    val c1 = c.change1h ?: 0.0
    val c24 = c.price_change_percentage_24h ?: 0.0
    val c7 = c.change7d ?: 0.0
    val cap = c.market_cap
    val turnover = if (cap > 0) c.total_volume / cap else 0.0
    val high = c.high24h ?: 0.0
    val low = c.low24h ?: 0.0
    val rangePos = if (high > low) (c.current_price - low) / (high - low) else 0.5

    // ---------- امتیاز پامپ ----------
    var pump = 0
    val pr = mutableListOf<String>()
    if (c1 >= 1.0) { pump += 25; pr.add("شتاب ۱ ساعته 🚀") }
    if (c1 >= 3.0) pump += 15
    if (c24 in 2.0..35.0) { pump += 20; pr.add("حرکت مثبت ۲۴ ساعته") }
    if (turnover >= 0.15) { pump += 20; pr.add("حجم غیرعادی 💥") }
    if (rangePos >= 0.85) { pump += 20; pr.add("شکست سقف ۲۴ ساعته 📈") }
    if (c7 > 10) pump += 5

    // ---------- امتیاز دامپ ----------
    var dump = 0
    val dr = mutableListOf<String>()
    if (c1 <= -1.0) { dump += 25; dr.add("ریزش ۱ ساعته 🩸") }
    if (c1 <= -3.0) dump += 15
    if (c24 in -35.0..-2.0) { dump += 20; dr.add("حرکت منفی ۲۴ ساعته") }
    if (turnover >= 0.15) { dump += 20; dr.add("حجم غیرعادی 💥") }
    if (rangePos <= 0.15) { dump += 20; dr.add("شکست کف ۲۴ ساعته 📉") }
    if (c7 < -10) dump += 5

    val side = if (pump >= dump) "PUMP" else "DUMP"
    val score = max(pump, dump).coerceAtMost(100)
    if (score < 35) return null

    // زودهنگام: شتاب ۱ ساعته قوی ولی ۲۴ ساعته هنوز کوچک
    val early = abs(c1) >= 1.5 && abs(c24) < 10

    return AlertEval(c, side, score, early, if (pump >= dump) pr else dr)
}

private fun levelOf(score: Int): String = when {
    score >= 70 -> "🔥 شدید"
    score >= 50 -> "⚠️ متوسط"
    else -> "👀 زودهنگام"
}

// ---------- صفحه هشدارهای هوشمند ----------

@Composable
fun SmartAlertsScreen(onCoinClick: (CoinMarket) -> Unit) {
    val scope = rememberCoroutineScope()

    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("ALL") }

    fun load() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                // پاک کردن کش + refresh اجباری
                ApiClient.clearMemoryCache()
                coins = ApiClient.getTop1000Coins(forceRefresh = true)
            } catch (e: Exception) {
                errorMsg = "خطا: ${e.message}\n\nاگه 429 می‌بینی، ۱-۲ دقیقه صبر کن و دوباره بزن"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val alerts = coins
        .mapNotNull { eval(it) }
        .filter { a ->
            when (filter) {
                "HOT" -> a.score >= 70
                "MID" -> a.score in 50..69
                "EARLY" -> a.early
                else -> true
            }
        }
        .sortedByDescending { it.score }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- سربرگ ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🔔 هشدارهای هوشمند",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { load() }) { Text("بروزرسانی") }
        }

        Text(
            "تشخیص زودهنگام با شتاب ۱ ساعته + حجم + شکست سقف/کف",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        // ---------- فیلترها ----------
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == "ALL",
                onClick = { filter = "ALL" },
                label = { Text("همه ${alerts.size}") }
            )
            FilterChip(
                selected = filter == "HOT",
                onClick = { filter = "HOT" },
                label = { Text("🔥 شدید") }
            )
            FilterChip(
                selected = filter == "MID",
                onClick = { filter = "MID" },
                label = { Text("⚠️ متوسط") }
            )
            FilterChip(
                selected = filter == "EARLY",
                onClick = { filter = "EARLY" },
                label = { Text("👀 زودهنگام") }
            )
        }

        // ---------- محتوا ----------
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AGreen) }

            errorMsg != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMsg ?: "",
                    color = ARed,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            alerts.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "😴 بازار آرومه — هنوز سیگنالی نیست",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alerts) { a ->
                    AlertSmartCard(a, onClick = { onCoinClick(a.coin) })
                }
            }
        }
    }
}

// ---------- کارت هشدار ----------

@Composable
private fun AlertSmartCard(a: AlertEval, onClick: () -> Unit) {
    val isPump = a.side == "PUMP"
    val sideColor = if (isPump) AGreen else ARed
    val c = a.coin

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ---------- ردیف اول ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isPump) "🚀" else "🩸", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.symbol.uppercase(Locale.US),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(levelOf(a.score), fontSize = 10.sp, color = AGold)
                        if (a.early) {
                            Spacer(Modifier.width(4.dp))
                            Text("⏰ زودهنگام", fontSize = 10.sp, color = ABlue)
                        }
                    }
                    Text(
                        c.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${a.score}/100",
                        color = sideColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        String.format(Locale.US, "$%,.4f", c.current_price),
                        fontSize = 12.sp
                    )
                }
            }

            // ---------- ردیف تغییرات ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "۱س: ${String.format(Locale.US, "%+.2f%%", c.change1h ?: 0.0)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((c.change1h ?: 0.0) >= 0) AGreen else ARed
                )
                Text(
                    "۲۴س: ${String.format(Locale.US, "%+.2f%%", c.price_change_percentage_24h ?: 0.0)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((c.price_change_percentage_24h ?: 0.0) >= 0) AGreen else ARed
                )
                Text(
                    "۷روز: ${String.format(Locale.US, "%+.1f%%", c.change7d ?: 0.0)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // ---------- نوار موقعیت در range ----------
            val high = c.high24h ?: 0.0
            val low = c.low24h ?: 0.0
            if (high > low) {
                val pos = ((c.current_price - low) / (high - low)).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { pos },
                    modifier = Modifier.fillMaxWidth(),
                    color = sideColor
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("کف: ${String.format(Locale.US, "$%.4f", low)}", fontSize = 10.sp, color = AGreen)
                    Text("سقف: ${String.format(Locale.US, "$%.4f", high)}", fontSize = 10.sp, color = ARed)
                }
            }

            // ---------- دلایل ----------
            a.reasons.take(3).forEach { r ->
                Text(
                    "• $r",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
