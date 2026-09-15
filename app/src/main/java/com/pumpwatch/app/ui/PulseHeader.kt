package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.FngClient
import com.pumpwatch.app.data.PulseClient
import com.pumpwatch.app.data.TrendingItem
import com.pumpwatch.app.data.cmcUrl
import com.pumpwatch.app.data.geckoPoolUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private val HGreen = Color(0xFF00E676)
private val HRed = Color(0xFFFF5252)
private val HOrange = Color(0xFFFFA726)
private val HYellow = Color(0xFFFFC107)
private val HCyan = Color(0xFF26C6DA)
private val HGray = Color(0xFF8B949E)
private val HCard = Color(0xFF1A2230)

private fun translateFng(s: String?): String = when (s) {
    "Extreme Fear" -> "ترس شدید 😱"
    "Fear" -> "ترس 😨"
    "Neutral" -> "خنثی 😐"
    "Greed" -> "طمع 🤑"
    "Extreme Greed" -> "طمع شدید 🚀"
    else -> ""
}

private fun fngColor(v: Int?): Color = when {
    v == null -> HGray
    v <= 25 -> HRed
    v <= 45 -> HOrange
    v <= 55 -> HYellow
    else -> HGreen
}

// 🚀 Sprint 7 (W4): توابع pure و تست‌پذیر برای نمایش صادقانهٔ دامیننس
// سه حالت: تازه / کش‌شده(با برچسب) / ناموجود — هرگز 0.0 جعلی ساخته نمی‌شود
internal fun dominanceText(fresh: Double?, cached: Double?): String = when {
    fresh != null -> String.format(Locale.US, "%.1f%%", fresh)
    cached != null -> "⚠️ ${String.format(Locale.US, "%.1f%%", cached)} (cached)"
    else -> "⚠️ ناموجود"
}

internal fun capChangeText(fresh: Double?, cached: Double?): String = when {
    fresh != null -> String.format(Locale.US, "%+.2f%% 🌍", fresh)
    cached != null -> "⚠️ ${String.format(Locale.US, "%+.2f%%", cached)} (cached)"
    else -> "⚠️ ناموجود"
}

@Composable
private fun FngGauge(value: Int?, label: String, lastKnown: Int?) {
    val displayValue = value ?: lastKnown
    val isCached = value == null && lastKnown != null
    val color = fngColor(displayValue)
    
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
                if (displayValue != null && displayValue >= 0) {
                    drawArc(
                        color = color,
                        startAngle = 135f,
                        sweepAngle = displayValue.coerceIn(0, 100) / 100f * 270f,
                        useCenter = false,
                        style = Stroke(width = 10f, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                when {
                    displayValue != null && displayValue >= 0 -> "$displayValue"
                    isCached -> "⚠️"
                    else -> "--"
                },
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                color = color
            )
        }
        Text(
            when {
                isCached -> "$label (cached)"
                label.isNotEmpty() -> label
                else -> "ترس و طمع"
            },
            fontSize = 10.sp,
            color = if (isCached) HGray else color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MarketPulseHeader() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    
    // State ها nullable هستند تا خطا از موفق متمایز شود
    var fng by remember { mutableStateOf<Int?>(null) }
    var fngLabel by remember { mutableStateOf("") }
    var btcDom by remember { mutableStateOf<Double?>(null) }
    var ethDom by remember { mutableStateOf<Double?>(null) }
    var capChange by remember { mutableStateOf<Double?>(null) }
    var trending by remember { mutableStateOf<List<TrendingItem>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    
    // Last known values برای fallback
    var lastFng by remember { 
        mutableStateOf(prefs.getInt("last_fng", -1).takeIf { it >= 0 }) 
    }
    var lastBtcDom by remember { 
        mutableStateOf(prefs.getFloat("last_btc_dom", Float.NaN).takeIf { !it.isNaN() }?.toDouble()) 
    }
    var lastEthDom by remember { 
        mutableStateOf(prefs.getFloat("last_eth_dom", Float.NaN).takeIf { !it.isNaN() }?.toDouble()) 
    }
    var lastCapChange by remember { 
        mutableStateOf(prefs.getFloat("last_cap_change", Float.NaN).takeIf { !it.isNaN() }?.toDouble()) 
    }

    // 🚀 Sprint 7 (W4): بارگذاری به تابع مستقل تبدیل شد تا دکمهٔ  بتواند دوباره صدایش کند
    fun load() {
        scope.launch {
            refreshing = true
            try {
                coroutineScope {
                    val f = async(Dispatchers.IO) {
                        try { FngClient.api.index() } catch (_: Exception) { null }
                    }
                    val g = async(Dispatchers.IO) {
                        try { PulseClient.api.global() } catch (_: Exception) { null }
                    }
                    val t = async(Dispatchers.IO) {
                        try { PulseClient.api.trending() } catch (_: Exception) { null }
                    }

                    f.await()?.data?.firstOrNull()?.let {
                        fng = it.value?.toIntOrNull()
                        fngLabel = translateFng(it.classification)
                        fng?.let { v -> 
                            prefs.edit().putInt("last_fng", v).apply()
                            lastFng = v
                        }
                    }
                    g.await()?.data?.let {
                        btcDom = it.marketCapPercentage?.get("btc")
                        ethDom = it.marketCapPercentage?.get("eth")
                        capChange = it.capChange24h
                        // Save to prefs for fallback
                        prefs.edit()
                            .putFloat("last_btc_dom", btcDom?.toFloat() ?: Float.NaN)
                            .putFloat("last_eth_dom", ethDom?.toFloat() ?: Float.NaN)
                            .putFloat("last_cap_change", capChange?.toFloat() ?: Float.NaN)
                            .apply()
                        lastBtcDom = btcDom
                        lastEthDom = ethDom
                        lastCapChange = capChange
                    }
                    trending = t.await()?.coins?.mapNotNull { it.item } ?: emptyList()
                }
            } catch (_: Exception) { }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📡 نبض بازار", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                // 🚀 Sprint 7 (W4): تلاش مجدد دستی — اگر fetch اولیه شکست خورد، کاربر گیر نمی‌کند
                Text(
                    if (refreshing) "⏳" else "🔄",
                    fontSize = 13.sp,
                    modifier = Modifier.clickable(enabled = !refreshing) { load() }
                )
                Spacer(Modifier.weight(1f))
                // P0-4: نمایش capChange با fallback صادق
                Text(
                    capChangeText(capChange, lastCapChange),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        capChange != null && capChange!! >= 0 -> HGreen
                        capChange != null -> HRed
                        else -> HGray
                    }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FngGauge(fng, fngLabel, lastFng)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // P0-4: BTC Dominance با fallback صادق
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("دامیننس BTC:", fontSize = 11.sp, color = HGray)
                        Text(
                            dominanceText(btcDom, lastBtcDom),
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = if (btcDom != null) HOrange else HGray
                        )
                    }
                    // P0-4: ETH Dominance با fallback صادق
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("دامیننس ETH:", fontSize = 11.sp, color = HGray)
                        Text(
                            dominanceText(ethDom, lastEthDom),
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = if (ethDom != null) HCyan else HGray
                        )
                    }
                    Text(
                        when {
                            fng in 0..25 -> "💡 بازار ترسیده — معمولاً فرصت خرید برای جسورها"
                            fng != null && fng!! >= 75 -> "💡 بازار حریصه — احتیاط، اصلاح نزدیکه"
                            fng != null -> "💡 بازار متعادله — منتظر سیگنال بمون"
                            lastFng in 0..25 -> "💡 بازار ترسیده (cached) — معمولاً فرصت خرید"
                            lastFng != null && lastFng!! >= 75 -> "💡 بازار حریص (cached) — احتیاط"
                            lastFng != null -> "💡 بازار متعادله (cached)"
                            else -> "⚠️ داده Fear & Greed در دسترس نیست"
                        },
                        fontSize = 10.sp, 
                        color = if (fng != null) HGray else HGray.copy(alpha = 0.7f)
                    )
                }
            }

            if (trending.isNotEmpty()) {
                Text("🔥 الان دنیا داره سرچ می‌کنه (بزن = نمودار ارز):", fontSize = 11.sp, color = HGray)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trending.take(10).forEach { item ->
                        Surface(
                            color = HCard,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val url = geckoPoolUrl(item.name ?: item.symbol ?: "")
                                        ?: cmcUrl(item.id ?: item.symbol ?: "")
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) { }
                                }
                            }
                        ) {
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
