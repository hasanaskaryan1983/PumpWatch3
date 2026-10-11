package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
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

private val HGreen = PumpWatchPalette.Positive
private val HRed = PumpWatchPalette.Negative
private val HOrange = PumpWatchPalette.Orange
private val HYellow = PumpWatchPalette.Warning
private val HCyan = PumpWatchPalette.Info
private val HGray = PumpWatchPalette.Muted
private val HCard = PumpWatchPalette.Glass

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

internal fun pendingGate(loaded: Boolean, fresh: Double?, cached: Double?, actual: String): String =
    if (!loaded && fresh == null && cached == null) "..." else actual

@Composable
private fun FngGauge(value: Int?, label: String, lastKnown: Int?, loading: Boolean) {
    val displayValue = value ?: lastKnown
    val isCached = value == null && lastKnown != null
    val color = fngColor(displayValue)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(98.dp)
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)), CircleShape)
        ) {
            Canvas(modifier = Modifier.size(88.dp)) {
                drawArc(
                    color = PumpWatchPalette.Border,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )
                if (displayValue != null && displayValue >= 0) {
                    drawArc(
                        color = color,
                        startAngle = 135f,
                        sweepAngle = displayValue.coerceIn(0, 100) / 100f * 270f,
                        useCenter = false,
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                when {
                    displayValue != null && displayValue >= 0 -> "$displayValue"
                    isCached -> "⚠️"
                    loading -> "..."
                    else -> "--"
                },
                fontWeight = FontWeight.Black,
                fontSize = 23.sp,
                color = color
            )
        }
        Text(
            when {
                isCached -> "ذخیره‌شده"
                label.isNotEmpty() -> "ترس و طمع"
                else -> "شاخص احساس"
            },
            fontSize = 9.sp,
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
    var fng by remember { mutableStateOf<Int?>(null) }
    var fngLabel by remember { mutableStateOf("") }
    var btcDom by remember { mutableStateOf<Double?>(null) }
    var ethDom by remember { mutableStateOf<Double?>(null) }
    var capChange by remember { mutableStateOf<Double?>(null) }
    var trending by remember { mutableStateOf<List<TrendingItem>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
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
            loaded = true
        }
    }

    LaunchedEffect(Unit) { load() }

    val displayedFng = fng ?: lastFng
    val sentimentLabel = fngLabel.ifBlank {
        when {
            displayedFng == null && !loaded -> "در انتظار داده"
            displayedFng == null -> "داده در دسترس نیست"
            displayedFng <= 25 -> "ترس شدید"
            displayedFng <= 45 -> "ترس"
            displayedFng <= 55 -> "خنثی"
            displayedFng < 75 -> "طمع"
            else -> "طمع شدید"
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    PumpWatchPalette.Accent.copy(alpha = 0.48f),
                    PumpWatchPalette.Info.copy(alpha = 0.38f),
                    PumpWatchPalette.Purple.copy(alpha = 0.34f)
                )
            )
        ),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            PumpWatchPalette.GlassRaised.copy(alpha = 0.56f),
                            PumpWatchPalette.Glass.copy(alpha = 0.74f),
                            PumpWatchPalette.GlassDeep.copy(alpha = 0.84f)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("GLOBAL MARKET  /  01", fontSize = 7.sp, letterSpacing = 1.1.sp, color = HGray)
                    Text("نبض جهانی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                val marketChangeColor = when {
                    capChange != null && capChange!! >= 0 -> HGreen
                    capChange != null -> HRed
                    else -> HGray
                }
                Surface(color = marketChangeColor.copy(alpha = 0.12f), shape = RoundedCornerShape(9.dp)) {
                    Text(
                        pendingGate(loaded, capChange, lastCapChange, capChangeText(capChange, lastCapChange)),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = marketChangeColor
                    )
                }
                Spacer(Modifier.width(6.dp))
                Surface(
                    color = HCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable(enabled = !refreshing) { load() }
                ) {
                    Text(
                        if (refreshing) "…" else "↻",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        color = PumpWatchPalette.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FngGauge(fng, fngLabel, lastFng, !loaded)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("شاخص احساس بازار", fontSize = 9.sp, color = HGray)
                    Text(
                        sentimentLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = fngColor(displayedFng)
                    )
                    Text(
                        if (!loaded && fng == null && lastFng == null) "شاخص در حال دریافت است"
                        else when {
                            fng in 0..25 -> "تمایل بازار به سمت ترس است؛ پیش از تصمیم، ریسک را بسنج."
                            fng != null && fng!! >= 75 -> "هیجان بالاست؛ از دنبال‌کردن حرکت‌های تند پرهیز کن."
                            fng != null -> "فضای بازار متعادل است؛ به داده‌های خودت تکیه کن."
                            lastFng in 0..25 -> "آخرین دادهٔ ذخیره‌شده: فضای بازار متمایل به ترس."
                            lastFng != null && lastFng!! >= 75 -> "آخرین دادهٔ ذخیره‌شده: هیجان بازار بالاست."
                            lastFng != null -> "آخرین دادهٔ ذخیره‌شده: فضای بازار متعادل است."
                            else -> "شاخص احساس بازار در دسترس نیست."
                        },
                        fontSize = 9.sp,
                        color = HGray,
                        lineHeight = 14.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("سهم بازار", fontSize = 9.sp, color = HGray)
                    Spacer(Modifier.weight(1f))
                    Text("DOMINANCE", fontSize = 7.sp, letterSpacing = 1.sp, color = HGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("BTC", fontSize = 8.sp, color = HGray)
                        Text(
                            pendingGate(loaded, btcDom, lastBtcDom, dominanceText(btcDom, lastBtcDom)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PumpWatchPalette.Accent
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("ETH", fontSize = 8.sp, color = HGray)
                        Text(
                            pendingGate(loaded, ethDom, lastEthDom, dominanceText(ethDom, lastEthDom)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = HCyan
                        )
                    }
                }
                if (btcDom != null || ethDom != null || lastBtcDom != null || lastEthDom != null) {
                    MarketDominanceBar(btcDom ?: lastBtcDom, ethDom ?: lastEthDom)
                }
            }
            if (trending.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("در کانون توجه", fontSize = 9.sp, color = HGray)
                    Spacer(Modifier.width(6.dp))
                    Text("·", color = PumpWatchPalette.Accent)
                    Text("لمس برای بررسی", fontSize = 8.sp, color = HGray)
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    trending.take(10).forEach { item ->
                        Surface(
                            color = HCard.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(9.dp),
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
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                fontSize = 9.sp,
                                color = PumpWatchPalette.Text
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketDominanceBar(btcDom: Double?, ethDom: Double?) {
    val btc = (btcDom ?: 0.0).coerceIn(0.0, 100.0)
    val eth = (ethDom ?: 0.0).coerceIn(0.0, 100.0 - btc)
    val other = (100.0 - btc - eth).coerceAtLeast(0.0)
    Row(
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        if (btc > 0.0) Box(modifier = Modifier.weight(btc.toFloat()).fillMaxHeight().background(PumpWatchPalette.Accent))
        if (eth > 0.0) Box(modifier = Modifier.weight(eth.toFloat()).fillMaxHeight().background(HCyan))
        if (other > 0.0) Box(modifier = Modifier.weight(other.toFloat()).fillMaxHeight().background(PumpWatchPalette.Border))
    }
}
