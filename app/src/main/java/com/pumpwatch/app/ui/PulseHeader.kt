package com.pumpwatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.FngClient
import com.pumpwatch.app.data.PulseClient
import com.pumpwatch.app.data.TrendingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

private val HGreen = Color(0xFF00E676)
private val HRed = Color(0xFFFF5252)
private val HOrange = Color(0xFFFFA726)
private val HYellow = Color(0xFFFFC107)
private val HCyan = Color(0xFF26C6DA)
private val HGray = Color(0xFF8B949E)
private val HCard = Color(0xFF1A2230)

// ---------- ترجمه فارسی ترس و طمع ----------

private fun translateFng(s: String?): String = when (s) {
    "Extreme Fear" -> "ترس شدید 😱"
    "Fear" -> "ترس 😨"
    "Neutral" -> "خنثی 😐"
    "Greed" -> "طمع 🤑"
    "Extreme Greed" -> "طمع شدید 🚀"
    else -> ""
}

private fun fngColor(v: Int): Color = when {
    v < 0 -> HGray
    v <= 25 -> HRed
    v <= 45 -> HOrange
    v <= 55 -> HYellow
    v <= 75 -> HGreen
    else -> HGreen
}

// ---------- گیج ترس و طمع ----------

@Composable
private fun FngGauge(value: Int, label: String) {
    val color = fngColor(value)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(72.dp)) {
                drawArc(
                    color = Color(0xFF2A3442),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
                if (value >= 0) {
                    drawArc(
                        color = color,
                        startAngle = 135f,
                        sweepAngle = value.coerceIn(0, 100) / 100f * 270f,
                        useCenter = false,
                        style = Stroke(width = 10f, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                if (value >= 0) "$value" else "--",
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                color = color
            )
        }
        Text(
            label.ifEmpty { "ترس و طمع" },
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------- سربرگ نبض بازار ----------

@Composable
fun MarketPulseHeader() {
    var fng by remember { mutableStateOf(-1) }
    var fngLabel by remember { mutableStateOf("") }
    var btcDom by remember { mutableStateOf(0.0) }
    var ethDom by remember { mutableStateOf(0.0) }
    var capChange by remember { mutableStateOf(0.0) }
    var trending by remember { mutableStateOf<List<TrendingItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                val f = async(Dispatchers.IO) { runCatching { FngClient.api.index() }.getOrNull() }
                val g = async(Dispatchers.IO) { runCatching { PulseClient.api.global() }.getOrNull() }
                val t = async(Dispatchers.IO) { runCatching { PulseClient.api.trending() }.getOrNull() }

                f.await()?.data?.firstOrNull()?.let {
                    fng = it.value?.toIntOrNull() ?: -1
                    fngLabel = translateFng(it.classification)
                }
                g.await()?.data?.let {
                    btcDom = it.marketCapPercentage?.get("btc") ?: 0.0
                    ethDom = it.marketCapPercentage?.get("eth") ?: 0.0
                    capChange = it.capChange24h ?: 0.0
                }
                trending = t.await()?.coins?.mapNotNull { it.item } ?: emptyList()
            }
        } catch (_: Exception) { }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---------- ردیف عنوان ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📡 نبض بازار", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%+.2f%% 🌍", capChange),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (capChange >= 0) HGreen else HRed
                )
            }

            // ---------- ردیف گیج + دامیننس ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FngGauge(fng, fngLabel)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("دامیننس BTC:", fontSize = 11.sp, color = HGray)
                        Text(
                            String.format(Locale.US, "%.1f%%", btcDom),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HOrange
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("دامیننس ETH:", fontSize = 11.sp, color = HGray)
                        Text(
                            String.format(Locale.US, "%.1f%%", ethDom),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HCyan
                        )
                    }
                    Text(
                        when {
                            fng in 0..25 -> "💡 بازار ترسیده — معمولاً فرصت خرید برای جسورها"
                            fng >= 75 -> "💡 بازار حریصه — احتیاط، اصلاح نزدیکه"
                            else -> "💡 بازار متعادله — منتظر سیگنال بمون"
                        },
                        fontSize = 10.sp,
                        color = HGray
                    )
                }
            }

            // ---------- ردیف ترندها ----------
            if (trending.isNotEmpty()) {
                Text("🔥 الان دنیا داره سرچ می‌کنه:", fontSize = 11.sp, color = HGray)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trending.take(10).forEach { item ->
                        Surface(color = HCard, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "${item.name} (${item.symbol?.uppercase(Locale.US)})",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 10.sp,
                                color = HYellow
                            )
                        }
                    }
                }
            }
        }
    }
}
