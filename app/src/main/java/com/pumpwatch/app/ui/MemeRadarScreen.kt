package com.pumpwatch.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import kotlinx.coroutines.launch
import java.util.Locale

private val MGreen = Color(0xFF00E676)
private val MRed = Color(0xFFFF5252)
private val MGold = Color(0xFFFFC107)

// ---------- صفحه رادار میم‌کوین ----------

@Composable
fun MemeRadarScreen() {
    val scope = rememberCoroutineScope()

    var signals by remember { mutableStateOf<List<MemeSignal>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("") }
    var scanned by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) { refresh() }

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
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        // ---------- پیشرفت ----------
        if (loading) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MGreen
                )
                Spacer(Modifier.height(4.dp))
                Text("$progress% — $progressText", fontSize = 11.sp, color = MGreen)
            }
        }

        // ---------- محتوا ----------
        when {
            signals.isEmpty() && !loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (!scanned) "در حال اسکن ترندهای DexScreener..."
                    else if (MemeRadar.lastScanFailed) "⚠️ اتصال به DexScreener برقرار نشد\nاینترنت/فیلترشکن رو چک کن و دوباره اسکن کن"
                    else "😴 الان میم‌کوین مستعدی پیدا نشد\nبعداً دوباره اسکن کن",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(24.dp)
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(signals) { s -> MemeCard(s) }
            }
        }
    }
}

// ---------- کارت میم‌سیگنال ----------

@Composable
private fun MemeCard(s: MemeSignal) {
    val scoreColor = if (s.score >= 70) MGreen else if (s.score >= 50) MGold else MRed

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
                Text(chainEmoji(s.chain), fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${s.chain} • ${s.dex}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Text(
                        s.name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${s.score}/100",
                        color = scoreColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(priceText(s.price), fontSize = 12.sp)
                }
            }

            // ---------- ردیف نهنگ‌ها ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "فشار خرید: ${String.format(Locale.US, "%.0f", s.buyRatio * 100)}٪ 🐳",
                    fontSize = 11.sp,
                    color = if (s.buyRatio >= 0.6) MGreen else MGold
                )
                Text("حجم ۱س: ${compact(s.volumeH1)}", fontSize = 11.sp)
                Text("نقدینگی: ${compact(s.liquidity)}", fontSize = 11.sp)
            }

            // ---------- ردیف تغییرات ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "۱س: ${String.format(Locale.US, "%+.1f%%", s.changeH1)}",
                    fontSize = 11.sp,
                    color = if (s.changeH1 >= 0) MGreen else MRed
                )
                Text(
                    "۶س: ${String.format(Locale.US, "%+.1f%%", s.changeH6)}",
                    fontSize = 11.sp,
                    color = if (s.changeH6 >= 0) MGreen else MRed
                )
                Text(
                    "۲۴س: ${String.format(Locale.US, "%+.1f%%", s.changeH24)}",
                    fontSize = 11.sp,
                    color = if (s.changeH24 >= 0) MGreen else MRed
                )
                Text(
                    "سن: ${ageText(s.ageHours)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // ---------- برنامه معامله ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ورود: ${priceText(s.entry)}", fontSize = 11.sp)
                Text("هدف۱: ${priceText(s.target1)}", fontSize = 11.sp, color = MGreen)
                Text("هدف۲: ${priceText(s.target2)}", fontSize = 11.sp, color = MGreen)
                Text("استاپ: ${priceText(s.stopLoss)}", fontSize = 11.sp, color = MRed)
            }

            // ---------- دلایل ----------
            s.reasons.take(3).forEach { r ->
                Text(
                    "• $r",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Text(
                "⚠️ میم‌کوین = ریسک بالا | فقط پولی که تحمل از دست دادنش رو داری",
                fontSize = 10.sp,
                color = MGold
            )
        }
    }
}

// ---------- توابع کمکی ----------

private fun chainEmoji(chain: String): String = when (chain) {
    "solana" -> "🟣"
    "bsc" -> "🟡"
    "base" -> "🔵"
    "ethereum" -> "⚪"
    else -> "⛓️"
}

private fun compact(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.1fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun ageText(h: Double): String = when {
    h < 48 -> "${h.toInt()} ساعت"
    else -> "${(h / 24).toInt()} روز"
}

private fun priceText(price: Double): String {
    return if (price >= 1) String.format(Locale.US, "$%,.2f", price)
    else String.format(Locale.US, "$%.6f", price)
}
