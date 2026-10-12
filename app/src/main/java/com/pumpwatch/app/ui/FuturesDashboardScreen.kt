package com.pumpwatch.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.FutSignalForPaper
import com.pumpwatch.app.engine.PaperTradingEngine
import com.pumpwatch.app.store.TradeStore
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun FuturesDashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🔗 اتصال به هاب: بازگشت به تب = نمایش آنی
    var signals by remember { mutableStateOf(FuturesHub.dashboardSignals) }
    var loading by remember { mutableStateOf(FuturesHub.dashboardLoading && FuturesHub.dashboardSignals.isEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf(FuturesHub.dashboardLastMs) }

    fun scan(force: Boolean) {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                signals = FuturesHub.scanDashboard(force)
                lastUpdate = FuturesHub.dashboardLastMs
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در دریافت داده: ${e.message?.take(80) ?: "نامشخص"}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { if (FuturesHub.dashboardSignals.isEmpty()) scan(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditorialPageHeader(
                eyebrow = "FUTURES · SIGNALS",
                title = "تابلوی سیگنال فیوچرز",
                subtitle = "شتاب ۲۴س + فاندینگ + نقدشوندگی",
                accent = PumpWatchPalette.TabMarket
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                HubRefreshRow(lastUpdate, loading, PumpWatchPalette.TabMarket) { scan(true) }
            }
        }

        Text(
            "🎯 ورود/SL/TP با ریسک/پاداش ۲:۱ • برای تمرین، دکمهٔ «📝 تمرین» را بزن",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 15.sp
        )

        if (loading && signals.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PumpWatchPalette.TabMarket)
            }
        } else if (errorMsg != null && signals.isEmpty()) {
            Text(errorMsg ?: "", color = PumpWatchPalette.Negative, modifier = Modifier.padding(24.dp))
        } else if (signals.isEmpty()) {
            Text(
                "😴 هیچ سیگنالی فعال نیست — بازار در حالت عادی است.",
                color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), fontSize = 12.sp
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "${signals.size} سیگنال فعال",
                        fontSize = 11.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold
                    )
                }
                items(signals) { sig -> SignalCard(sig) }
            }
        }
    }
}

@Composable
private fun SignalCard(sig: FuturesHub.FutSignal) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dirColor = if (sig.direction == "LONG") PumpWatchPalette.Positive else PumpWatchPalette.Negative
    val dirEmoji = if (sig.direction == "LONG") "🚀" else "🩸"

    GlassCard(accent = PumpWatchPalette.TabMarket, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dirEmoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(sig.base, fontWeight = FontWeight.Black, fontSize = 16.sp, color = PumpWatchPalette.Text)
                Text(" #${sig.rank}", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                Spacer(Modifier.weight(1f))
                Text(
                    "${sig.score}/100",
                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                    color = if (sig.score >= 70) PumpWatchPalette.Positive else PumpWatchPalette.Warning
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = dirColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        sig.direction,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dirColor
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "شتاب: ${String.format(Locale.US, "%+.1f%%", sig.change24h)}",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dirColor
                )
                Spacer(Modifier.weight(1f))
                if (sig.fundingPct != null) {
                    Text(
                        "فاندینگ: ${String.format(Locale.US, "%+.4f%%", sig.fundingPct)}",
                        fontSize = 9.sp, color = PumpWatchPalette.Muted
                    )
                }
            }

            Surface(color = PumpWatchPalette.Info.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ورود", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                        Text(fmtPrice(sig.entry), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("استاپ", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                        Text(fmtPrice(sig.stopLoss), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تارگت", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                        Text(fmtPrice(sig.target), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Positive)
                    }
                }
            }

            Text(sig.reasons.joinToString(" • "), fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 14.sp)
            Text("💧 حجم: ${fmtVol(sig.volumeUsd)}", fontSize = 9.sp, color = PumpWatchPalette.Muted)

            Button(
                onClick = {
                    scope.launch {
                        if (!TradeStore.isEnabled(context)) {
                            Toast.makeText(context, "⚠️ اول از تب «Paper فیوچرز» قابلیت را فعال کن", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val futSig = FutSignalForPaper(
                            coinId = sig.coinId, symbol = sig.symbol, base = sig.base,
                            direction = sig.direction, entry = sig.entry, stopLoss = sig.stopLoss,
                            target = sig.target, score = sig.score
                        )
                        val success = PaperTradingEngine.openFromFutSignal(context, futSig)
                        Toast.makeText(context, if (success) "✅ ترید FUT برای ${sig.base} باز شد" else "⚠️ این ترید قبلاً باز است", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Positive.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📝 تمرین با این ستاپ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}

private fun fmtVol(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}
