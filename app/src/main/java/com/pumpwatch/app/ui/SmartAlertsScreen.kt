package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.pumpwatch.app.engine.SignalLogger
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val AGold = Color(0xFFFFC107)
private val ABlue = Color(0xFF40C4FF)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A2230)

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

@Composable
private fun ContractRow(ctx: Context, contract: String?, coinId: String) {
    val displayAddr = contract ?: coinId
    val label = if (contract != null) "📋 کانترکت" else "📋 ID"
    val copied = remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    ) {
        Text(label, fontSize = 9.sp, color = AGray)
        Spacer(Modifier.width(4.dp))
        Text(
            if (displayAddr.length > 22) "${displayAddr.take(10)}...${displayAddr.takeLast(6)}" else displayAddr,
            fontSize = 9.sp,
            color = ABlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                try {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("contract", displayAddr))
                    copied.value = true
                } catch (_: Exception) { }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (copied.value) AGreen else AGold
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                if (copied.value) "✅" else "کپی",
                fontSize = 9.sp,
                color = Color.Black
            )
        }
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

    var accuracyExpanded by remember { mutableStateOf(false) }
    var accuracyResetKey by remember { mutableStateOf(0) }
    var resetMsg by remember { mutableStateOf<String?>(null) }

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

    fun resetAccuracy() {
        scope.launch {
            try {
                SignalLogger.clear(ctx)
                accuracyResetKey++
                resetMsg = "✅ آمار سیگنال‌ها پاک شد"
                kotlinx.coroutines.delay(2500)
                resetMsg = null
            } catch (e: Exception) {
                resetMsg = "⚠️ خطا در پاک‌سازی: ${e.message}"
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

        if (resetMsg != null) {
            Text(
                resetMsg ?: "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 10.sp,
                color = AGreen,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ACard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { accuracyExpanded = !accuracyExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (accuracyExpanded) "▼" else "▶",
                        fontSize = 12.sp,
                        color = AGreen
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "📊 کارنامهٔ دقت سیگنال‌ها",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { resetAccuracy() },
                        colors = ButtonDefaults.buttonColors(containerColor = ARed.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🔄 ریست", fontSize = 10.sp, color = Color.White)
                    }
                }

                AnimatedVisibility(visible = accuracyExpanded) {
                    SignalAccuracyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        accent = AGreen,
                        key = accuracyResetKey
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == "ALL",
                onClick = { filter = "ALL" },
                label = { Text("همه ${alerts.size}", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filter == "HOT",
                onClick = { filter = "HOT" },
                label = { Text("🔥 شدید", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filter == "MID",
                onClick = { filter = "MID" },
                label = { Text("⚠️ متوسط", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filter == "EARLY",
                onClick = { filter = "EARLY" },
                label = { Text("👀 زودهنگام", fontSize = 11.sp) }
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(alerts, key = { it.coin.id }) { a ->
                    AlertSmartCard(
                        a = a,
                        platformMap = platformMap,
                        ctx = ctx,
                        onClick = { onCoinClick(a.coin) }
                    )
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
        color = ACard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isPump) "🚀" else "🩸", fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    c.symbol.uppercase(Locale.US),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text(levelOf(a.score), fontSize = 9.sp, color = AGold)
                if (a.early) {
                    Spacer(Modifier.width(4.dp))
                    Text("⏰", fontSize = 9.sp, color = ABlue)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${a.score}/100",
                    color = sideColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    c.name,
                    fontSize = 10.sp,
                    color = AGray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    String.format(Locale.US, "$%,.4f", c.current_price),
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            ContractRow(ctx, contract, c.id)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "۱س: ${String.format(Locale.US, "%+.1f%%", c.change1h ?: 0.0)}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((c.change1h ?: 0.0) >= 0) AGreen else ARed
                )
                Text(
                    "۲۴س: ${String.format(Locale.US, "%+.1f%%", c.price_change_percentage_24h ?: 0.0)}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((c.price_change_percentage_24h ?: 0.0) >= 0) AGreen else ARed
                )
                Text(
                    "۷روز: ${String.format(Locale.US, "%+.1f%%", c.change7d ?: 0.0)}",
                    fontSize = 10.sp,
                    color = AGray
                )
            }

            val high = c.high24h ?: 0.0
            val low = c.low24h ?: 0.0
            if (high > low) {
                val pos = ((c.current_price - low) / (high - low)).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { pos },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = sideColor
                )
            }

            if (a.reasons.isNotEmpty()) {
                Text(
                    "• ${a.reasons.first()}",
                    fontSize = 10.sp,
                    color = AGold
                )
            }
        }
    }
}
