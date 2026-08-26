package com.pumpwatch.app.ui

import android.text.format.DateUtils
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
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.SignalResult
import com.pumpwatch.app.store.PicksStore
import kotlinx.coroutines.launch
import java.util.Locale

private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Gold = Color(0xFFFFC107)
private val Blue = Color(0xFF4FC3F7)

// ---------- صفحه برترین‌های روز ----------

@Composable
fun TopPicksScreen(mode: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var picks by remember { mutableStateOf<List<SignalResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var lastScan by remember { mutableStateOf(0L) }

    fun reload() {
        picks = PicksStore.loadToday(context, mode)?.picks ?: emptyList()
        lastScan = PicksStore.lastScan(context, mode)
    }

    LaunchedEffect(mode) { reload() }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            try {
                val params = PicksStore.loadParams(context, mode)
                val result = BatchScanner.scan(mode, params) { p, t ->
                    progress = p
                    progressText = t
                }
                PicksStore.saveScan(context, mode, result)
                reload()
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    val shown = when (filter) {
        "PUMP" -> picks.filter { it.side == "PUMP" }
        "DUMP" -> picks.filter { it.side == "DUMP" }
        "GOLDEN" -> picks.filter { it.golden }
        else -> picks
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
                if (mode == "SPOT") "🏆 برترین‌های اسپات" else "⚡ برترین‌های فیوچرز",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { refresh() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن اکنون 🔄")
            }
        }

        // ---------- زمان آخرین اسکن ----------
        Text(
            text = if (lastScan > 0)
                "آخرین اسکن: ${DateUtils.getRelativeTimeSpanString(lastScan)}"
            else "هنوز اسکنی انجام نشده",
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
                label = { Text("همه ${picks.size}") }
            )
            FilterChip(
                selected = filter == "PUMP",
                onClick = { filter = "PUMP" },
                label = { Text("🚀 ${picks.count { it.side == "PUMP" }}") }
            )
            FilterChip(
                selected = filter == "DUMP",
                onClick = { filter = "DUMP" },
                label = { Text("🩸 ${picks.count { it.side == "DUMP" }}") }
            )
            FilterChip(
                selected = filter == "GOLDEN",
                onClick = { filter = "GOLDEN" },
                label = { Text("🏆 ${picks.count { it.golden }}") }
            )
        }

        // ---------- نوار پیشرفت اسکن ----------
        if (loading) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Green
                )
                Spacer(Modifier.height(4.dp))
                Text("$progress% — $progressText", fontSize = 11.sp, color = Green)
                Spacer(Modifier.height(8.dp))
            }
        }

        // ---------- محتوا ----------
        when {
            shown.isEmpty() && !loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "😴 هنوز سیگنالی ثبت نشده",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    TextButton(onClick = { refresh() }) { Text("اولین اسکن رو شروع کن 🚀") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown) { s -> SignalCard(s) }
            }
        }
    }
}

// ---------- کارت سیگنال ----------

@Composable
fun SignalCard(s: SignalResult) {
    val sideColor = if (s.side == "PUMP") Green else Red
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ---------- ردیف اول ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.side == "PUMP") "🚀" else "🩸", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (s.golden) {
                            Spacer(Modifier.width(6.dp))
                            Text("🏆 طلایی", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        s.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s.score}/100", color = sideColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(priceText(s.price), fontSize = 12.sp)
                }
            }

            // ---------- برنامه معامله ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ورود: ${priceText(s.entry)}", fontSize = 11.sp)
                Text("هدف۱: ${priceText(s.target1)}", fontSize = 11.sp, color = Green)
                Text("استاپ: ${priceText(s.stopLoss)}", fontSize = 11.sp, color = Red)
            }

            // ---------- اندیکاتورها (جدید) ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "RSI ${String.format(Locale.US, "%.0f", s.rsi)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    "MFI ${String.format(Locale.US, "%.0f", s.mfi)}",
                    fontSize = 11.sp,
                    color = if (s.mfi < 30 || s.mfi > 70) sideColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    "ADX ${String.format(Locale.US, "%.0f", s.adx)}",
                    fontSize = 11.sp,
                    color = if (s.adx >= 25) sideColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    "حجم ${String.format(Locale.US, "%.1f", s.volumeRatio)}x",
                    fontSize = 11.sp,
                    color = if (s.volumeRatio >= 2.0) sideColor
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // ---------- VWAP و OBV ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val vwapStatus = if (s.price > s.vwap) "بالا" else "پایین"
                Text(
                    "VWAP: $vwapStatus (${String.format(Locale.US, "%.1f%%", s.vwapDeviation)})",
                    fontSize = 11.sp,
                    color = if (s.price > s.vwap) Green else Red
                )
                Text(
                    "MTF: ${s.mtfTrend}",
                    fontSize = 11.sp,
                    color = if (s.mtfAligned) Green
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (s.obvDivergence != "NONE") {
                    Text(
                        "OBV: ${s.obvDivergence}",
                        fontSize = 11.sp,
                        color = if (s.obvDivergence == "BULLISH") Green else Red
                    )
                }
            }

            // ---------- تریلینگ استاپ ----------
            if (s.trailingStop > 0) {
                Text(
                    "تریلینگ: ${priceText(s.trailingStop)}",
                    fontSize = 11.sp,
                    color = Blue
                )
            }

            // ---------- دلایل ----------
            s.reasons.take(3).forEach { r ->
                Text(
                    "• $r",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ---------- فرمت قیمت ----------

private fun priceText(price: Double): String {
    return if (price >= 1) String.format(Locale.US, "$%,.2f", price)
    else String.format(Locale.US, "$%.6f", price)
}
