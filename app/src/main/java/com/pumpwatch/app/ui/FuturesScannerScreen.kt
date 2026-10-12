package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.FuturesScannerEngine
import com.pumpwatch.app.ui.components.GlassCard
import java.util.Locale

@Composable
fun FuturesScannerScreen() {
    val scope = rememberCoroutineScope()
    
    // 🔗 اتصال به هاب
    var results by remember { mutableStateOf(FuturesHub.scannerResults) }
    var loading by remember { mutableStateOf(FuturesHub.scannerLoading && FuturesHub.scannerResults.isEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf(FuturesHub.scannerLastMs) }

    fun scan(force: Boolean) {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                results = FuturesHub.scanScanner(force)
                lastUpdate = FuturesHub.scannerLastMs
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در اسکن: ${e.message?.take(80) ?: "نامشخص"}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (FuturesHub.scannerResults.isEmpty()) scan(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorialPageHeader(
                eyebrow = "FUTURES · SCANNER",
                title = "اسکنر فیوچرز",
                subtitle = "Regime 4H+1H • Setup 15M • Trigger 5M",
                accent = PumpWatchPalette.TabWhale
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                HubRefreshRow(lastUpdate, loading, PumpWatchPalette.TabWhale) { scan(true) }
            }
        }

        Text(
            "کاندیدها: ۱۰۰ ارز برتر ∩ perp • استاپ = ۲×ATR • تارگت = ۳×ATR\n" +
            "⚠️ بدون Setup = بدون سیگنال. Trigger نیامده = «در انتظار»",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 15.sp
        )

        if (loading && results.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PumpWatchPalette.TabWhale)
                Text("در حال اسکن عمیق...", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(top = 8.dp))
            }
        } else if (errorMsg != null && results.isEmpty()) {
            Text(errorMsg ?: "", color = PumpWatchPalette.Negative, modifier = Modifier.padding(24.dp))
        } else if (results.isEmpty()) {
            Text(
                "😴 هیچ کاندیدی Setup معتبر ندارد — بازار در انتظار است.",
                color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), fontSize = 12.sp
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results) { r -> ScannerCard(r) }
                item {
                    Text(
                        "⚠️ فیوچرز = اهرم + ریسک لیکوئیدیشن. این خروجی تحلیل است نه توصیه.",
                        fontSize = 10.sp, color = PumpWatchPalette.Warning
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerCard(r: FuturesScannerEngine.ScanResult) {
    val isLong = r.direction == "LONG"
    val dirColor = if (isLong) PumpWatchPalette.Positive else PumpWatchPalette.Negative

    GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.base, fontWeight = FontWeight.Black, fontSize = 16.sp, color = PumpWatchPalette.Text)
                Spacer(Modifier.width(8.dp))
                Surface(color = dirColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (isLong) "🚀 LONG" else "🩸 SHORT",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Black, color = dirColor
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${r.score}/100",
                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                    color = if (r.score >= 70) PumpWatchPalette.Positive else PumpWatchPalette.Warning
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "4H: ${if (r.regime4h == FuturesScannerEngine.Regime.BULL) "🟢" else "🔴"}",
                    fontSize = 10.sp, color = PumpWatchPalette.Muted
                )
                Text(
                    "1H: ${if (r.regime1h == r.regime4h) "✅" else "⚠️"}",
                    fontSize = 10.sp, color = PumpWatchPalette.Muted
                )
                Text(
                    "Trig: ${if (r.trigger5m) "✅" else "⏳"}",
                    fontSize = 10.sp, color = if (r.trigger5m) PumpWatchPalette.Positive else PumpWatchPalette.Muted
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ورود: ${fmtP(r.entry)}", fontSize = 11.sp, color = PumpWatchPalette.Text, fontWeight = FontWeight.Bold)
                Text("SL: ${fmtP(r.stop)}", fontSize = 11.sp, color = PumpWatchPalette.Negative, fontWeight = FontWeight.Bold)
                Text("TP: ${fmtP(r.target)}", fontSize = 11.sp, color = PumpWatchPalette.Positive, fontWeight = FontWeight.Bold)
            }

            if (r.fundingPct != null) {
                Text(
                    "فاندینگ: ${String.format(Locale.US, "%+.4f%%", r.fundingPct)}",
                    fontSize = 9.sp, color = PumpWatchPalette.Info
                )
            }

            r.reasons.forEach { reason ->
                Text("• $reason", fontSize = 10.sp, color = PumpWatchPalette.Muted)
            }
        }
    }
}

private fun fmtP(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}
