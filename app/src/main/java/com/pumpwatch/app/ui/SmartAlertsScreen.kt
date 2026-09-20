package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.platformContractOf
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val AGold = Color(0xFFFFC107)
private val ABlue = Color(0xFF40C4FF)
private val AGray = Color(0xFF8B949E)

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

    var pump = 0
    val pr = mutableListOf<String>()
    if (c1 >= 1.0) { pump += 25; pr.add("شتاب ۱ ساعته 🚀") }
    if (c1 >= 3.0) pump += 15
    if (c24 in 2.0..35.0) { pump += 20; pr.add("حرکت مثبت ۲۴ ساعته") }
    if (turnover >= 0.15) { pump += 20; pr.add("حجم غیرعادی 💥") }
    if (rangePos >= 0.85) { pump += 20; pr.add("شکست سقف ۲۴ ساعته 📈") }
    if (c7 > 10) pump += 5

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

    val early = abs(c1) >= 1.5 && abs(c24) < 10

    return AlertEval(c, side, score, early, if (pump >= dump) pr else dr)
}

private fun levelOf(score: Int): String = when {
    score >= 70 -> "🔥 شدید"
    score >= 50 -> "⚠️ متوسط"
    else -> "👀 زودهنگام"
}

// 🚀 Sprint 10 (V3c): ردیف کانترکت برای کارت‌های هشدار
@Composable
private fun ContractRow(ctx: Context, contract: String?) {
    if (contract.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("⛓️ بومی — بدون کانترکت", fontSize = 9.sp, color = AGray)
        }
        return
    }
    val copied = remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("📋 ", fontSize = 9.sp, color = AGray)
        Text(
            if (contract.length > 24) "${contract.take(12)}...${contract.takeLast(8)}" else contract,
            fontSize = 9.sp, color = ABlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                try {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("contract", contract))
                    copied.value = true
                } catch (_: Exception) { }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (copied.value) AGreen else AGold),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) { Text(if (copied.value) "✅" else "📋 کپی", fontSize = 9.sp, color = Color.Black) }
    }
}

@Composable
fun SmartAlertsScreen(onCoinClick: (CoinMarket) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("ALL") }
    var platformMap by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }

    fun load() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                coins = ApiClient.getTop1000Coins(forceRefresh = false)
                platformMap = try { ApiClient.getPlatformMap() } catch (_: Exception) { emptyMap() }
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun forceRefresh() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                ApiClient.clearMemoryCache()
                coins = ApiClient.getTop1000Coins(forceRefresh = true)
                platformMap = try { ApiClient.getPlatformMap(forceRefresh = true) } catch (_: Exception) { emptyMap() }
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
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
            TextButton(onClick = { forceRefresh() }) { Text("بروزرسانی") }
        }

        Text(
            "تشخیص زودهنگام با شتاب ۱ ساعته + حجم + شکست سقف/کف",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        // 🚀 Sprint 15 (فاز ۴ / Commit 21): کارت کارنامهٔ دقت سیگنال‌ها
        SignalAccuracyCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            accent = AGreen
        )

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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alerts) { a ->
                    AlertSmartCard(a, platformMap = platformMap, ctx = ctx, onClick = { onCoinClick(a.coin) })
                }
            }
        }
    }
}

@Composable
private fun AlertSmartCard(
    a: AlertEval,
    platformMap: Map<String, Map<String, String>>,
    ctx: Context,
    onClick: () -> Unit
) {
    val isPump = a.side == "PUMP"
    val sideColor = if (isPump) AGreen else ARed
    val c = a.coin
    val contract = platformContractOf(platformMap, c.id)

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isPump) "🚀" else "🩸", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.symbol.uppercase(Locale.US),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
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

            ContractRow(ctx, contract)

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
