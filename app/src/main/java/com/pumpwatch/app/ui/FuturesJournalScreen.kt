package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.store.TradeStore
import com.pumpwatch.app.ui.components.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun FuturesJournalScreen() {
    val context = LocalContext.current
    var trades by remember { mutableStateOf<List<Trade>>(emptyList()) }
    LaunchedEffect(Unit) { trades = TradeStore.load(context).filter { it.mode == "FUT" } }
    val stats = remember(trades) { computeStats(trades) }
    val closed = trades.filter { it.status == "CLOSED" && it.exitPrice != null }.sortedByDescending { it.exitTime ?: 0L }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorialPageHeader(eyebrow = "FUTURES · JOURNAL", title = "ژورنال فیوچرز", subtitle = "آمار واقعی معاملات کاغذی", accent = PumpWatchPalette.TabWatchlist)

        if (stats.total == 0) {
            GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.Text("هنوز ترید بسته‌ای در حالت فیوچرز نداری.", fontSize = 11.sp, color = PumpWatchPalette.Muted)
            }
        } else {
            GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Text("📊 خلاصه عملکرد", fontWeight = FontWeight.Black, fontSize = 13.sp, color = PumpWatchPalette.Info)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat("تعداد", "${stats.total}", PumpWatchPalette.Muted)
                        Stat("برد", "${stats.wins}", PumpWatchPalette.Positive)
                        Stat("باخت", "${stats.losses}", PumpWatchPalette.Negative)
                    }
                    androidx.compose.material3.Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", stats.winRate)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (stats.winRate >= 55) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    androidx.compose.material3.Text("مجموع PnL: ${String.format(Locale.US, "%+.2f%%", stats.totalPnl)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (stats.totalPnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    androidx.compose.material3.Text("امید ریاضی: ${String.format(Locale.US, "%+.2f%%", stats.expectancy)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (stats.expectancy > 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    androidx.compose.material3.Text("Profit Factor: ${if (stats.profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", stats.profitFactor)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (stats.profitFactor >= 1.5) PumpWatchPalette.Positive else if (stats.profitFactor >= 1.0) PumpWatchPalette.Warning else PumpWatchPalette.Negative)
                }
            }
            GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(" دلایل خروج", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PumpWatchPalette.Info)
                    ExitRow("🛑 STOP", stats.stopExits, stats.total, PumpWatchPalette.Negative)
                    ExitRow(" TARGET", stats.targetExits, stats.total, PumpWatchPalette.Positive)
                    ExitRow(" TRAIL", stats.trailExits, stats.total, PumpWatchPalette.Info)
                }
            }
            androidx.compose.material3.Text(" ${closed.size} ترید اخیر:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PumpWatchPalette.Info)
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(closed.take(50)) { t -> JournalTradeRow(t) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(label, fontSize = 9.sp, color = color)
        androidx.compose.material3.Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun ExitRow(label: String, count: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    val pct = if (total > 0) count * 100.0 / total else 0.0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.material3.Text(label, fontSize = 10.sp, color = PumpWatchPalette.Muted, modifier = Modifier.weight(1f))
        androidx.compose.material3.Text("$count (${String.format(Locale.US, "%.0f%%", pct)})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun JournalTradeRow(t: Trade) {
    val pnl = t.exitPrice?.let { exit -> if (t.side == "PUMP") (exit - t.entryPrice) / t.entryPrice * 100.0 else (t.entryPrice - exit) / t.entryPrice * 100.0 } ?: 0.0
    val pnlColor = if (pnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
    val exitEmoji = when (t.exitReason) { "TARGET" -> ""; "TRAIL" -> "📈"; "STOP" -> "🛑"; "TIME" -> "⏰"; else -> "" }
    GlassCard(accent = PumpWatchPalette.TabWatchlist, radius = 10.dp, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Text("$exitEmoji ${t.symbol} • ${if (t.side == "PUMP") "LONG" else "SHORT"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                androidx.compose.material3.Text(timeFmt.format(Date(t.entryTime)), fontSize = 8.sp, color = PumpWatchPalette.Muted)
            }
            androidx.compose.material3.Text(String.format(Locale.US, "%+.2f%%", pnl), fontSize = 13.sp, fontWeight = FontWeight.Black, color = pnlColor)
        }
    }
}

private fun computeStats(trades: List<Trade>): FutJournalStats {
    val closed = trades.filter { it.status == "CLOSED" && it.exitPrice != null }
    if (closed.isEmpty()) return FutJournalStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0)
    val pnls = closed.map { t -> val exit = t.exitPrice!!; if (t.side == "PUMP") (exit - t.entryPrice) / t.entryPrice * 100.0 else (t.entryPrice - exit) / t.entryPrice * 100.0 }
    val wins = pnls.count { it > 0 }; val losses = pnls.count { it < 0 }
    val winRate = if (closed.isNotEmpty()) wins * 100.0 / closed.size else 0.0
    val totalPnl = pnls.sum(); val avgPnl = pnls.average()
    val winPnls = pnls.filter { it > 0 }; val lossPnls = pnls.filter { it < 0 }
    val avgWin = if (winPnls.isNotEmpty()) winPnls.average() else 0.0
    val avgLoss = if (lossPnls.isNotEmpty()) abs(lossPnls.average()) else 0.0
    val sumWins = winPnls.sum(); val sumLosses = abs(lossPnls.sum())
    val pf = if (sumLosses > 0) sumWins / sumLosses else if (sumWins > 0) Double.POSITIVE_INFINITY else 0.0
    val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)
    var equity = 100.0; var peak = equity; var maxDD = 0.0
    for (p in pnls) { equity *= (1 + p / 100.0); if (equity > peak) peak = equity; val dd = (peak - equity) / peak * 100.0; if (dd > maxDD) maxDD = dd }
    val exitReasons = closed.mapNotNull { it.exitReason }
    return FutJournalStats(closed.size, wins, losses, winRate, totalPnl, avgPnl, avgWin, avgLoss, pf, expectancy, maxDD, closed.count { it.side == "PUMP" }, closed.count { it.side == "DUMP" }, exitReasons.count { it == "STOP" }, exitReasons.count { it == "TARGET" }, exitReasons.count { it == "TRAIL" }, exitReasons.count { it == "TIME" }, exitReasons.count { it == "VOL_SPIKE" }, exitReasons.count { it == "MOMENTUM_RSI" })
}

private data class FutJournalStats(val total: Int, val wins: Int, val losses: Int, val winRate: Double, val totalPnl: Double, val avgPnl: Double, val avgWin: Double, val avgLoss: Double, val profitFactor: Double, val expectancy: Double, val maxDrawdown: Double, val longs: Int, val shorts: Int, val stopExits: Int, val targetExits: Int, val trailExits: Int, val timeExits: Int, val volSpikeExits: Int, val momentumExits: Int)
