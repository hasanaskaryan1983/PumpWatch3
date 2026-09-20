package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.store.TradeStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val JGreen = Color(0xFF00E676)
private val JRed = Color(0xFFFF5252)
private val JGold = Color(0xFFFFC107)
private val JGray = Color(0xFF8B949E)
private val JBlue = Color(0xFF40C4FF)
private val JCard = Color(0xFF1A0E0E)

private data class FutJournalStats(
    val total: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val totalPnl: Double,
    val avgPnl: Double,
    val avgWin: Double,
    val avgLoss: Double,
    val profitFactor: Double,
    val expectancy: Double,
    val maxDrawdown: Double,
    val longs: Int,
    val shorts: Int,
    val stopExits: Int,
    val targetExits: Int,
    val trailExits: Int,
    val timeExits: Int,
    val volSpikeExits: Int,
    val momentumExits: Int
)

private fun computeStats(trades: List<Trade>): FutJournalStats {
    val closed = trades.filter { it.status == "CLOSED" && it.exitPrice != null }
    if (closed.isEmpty()) {
        return FutJournalStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0)
    }

    val pnls = closed.map { t ->
        val exit = t.exitPrice!!
        if (t.side == "PUMP") (exit - t.entryPrice) / t.entryPrice * 100.0
        else (t.entryPrice - exit) / t.entryPrice * 100.0
    }
    val wins = pnls.count { it > 0 }
    val losses = pnls.count { it < 0 }
    val winRate = if (closed.isNotEmpty()) wins * 100.0 / closed.size else 0.0

    val totalPnl = pnls.sum()
    val avgPnl = pnls.average()
    val winPnls = pnls.filter { it > 0 }
    val lossPnls = pnls.filter { it < 0 }
    val avgWin = if (winPnls.isNotEmpty()) winPnls.average() else 0.0
    val avgLoss = if (lossPnls.isNotEmpty()) abs(lossPnls.average()) else 0.0

    val sumWins = winPnls.sum()
    val sumLosses = abs(lossPnls.sum())
    val pf = if (sumLosses > 0) sumWins / sumLosses else if (sumWins > 0) Double.POSITIVE_INFINITY else 0.0
    val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)

    // Max Drawdown (percent-based equity curve)
    var equity = 100.0
    var peak = equity
    var maxDD = 0.0
    for (p in pnls) {
        equity *= (1 + p / 100.0)
        if (equity > peak) peak = equity
        val dd = (peak - equity) / peak * 100.0
        if (dd > maxDD) maxDD = dd
    }

    val exitReasons = closed.mapNotNull { it.exitReason }
    return FutJournalStats(
        total = closed.size,
        wins = wins,
        losses = losses,
        winRate = winRate,
        totalPnl = totalPnl,
        avgPnl = avgPnl,
        avgWin = avgWin,
        avgLoss = avgLoss,
        profitFactor = pf,
        expectancy = expectancy,
        maxDrawdown = maxDD,
        longs = closed.count { it.side == "PUMP" },
        shorts = closed.count { it.side == "DUMP" },
        stopExits = exitReasons.count { it == "STOP" },
        targetExits = exitReasons.count { it == "TARGET" },
        trailExits = exitReasons.count { it == "TRAIL" },
        timeExits = exitReasons.count { it == "TIME" },
        volSpikeExits = exitReasons.count { it == "VOL_SPIKE" },
        momentumExits = exitReasons.count { it == "MOMENTUM_RSI" }
    )
}

@Composable
fun FuturesJournalScreen() {
    val context = LocalContext.current
    var trades by remember { mutableStateOf<List<Trade>>(emptyList()) }

    LaunchedEffect(Unit) {
        trades = TradeStore.load(context).filter { it.mode == "FUT" }
    }

    val stats = remember(trades) { computeStats(trades) }
    val closed = trades.filter { it.status == "CLOSED" && it.exitPrice != null }
        .sortedByDescending { it.exitTime ?: 0L }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("📓 ژورنال فیوچرز", fontWeight = FontWeight.Black, fontSize = 18.sp)

        Text(
            "آمار واقعی معاملات کاغذی فیوچرز • بر اساس exitReason (ExitEngine) • رمزنگاری‌شده",
            fontSize = 9.sp, color = JGray, lineHeight = 15.sp
        )

        if (stats.total == 0) {
            Text(
                "هنوز ترید بسته‌ای در حالت فیوچرز نداری.\nسیگنال‌های طلایی اسکنر → Paper → اینجا آمارش ظاهر می‌شود.",
                fontSize = 11.sp, color = JGray, modifier = Modifier.padding(24.dp)
            )
        } else {
            // ---------- کارت آمار اصلی ----------
            Surface(color = JCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊 خلاصه عملکرد", fontWeight = FontWeight.Black, fontSize = 13.sp, color = JBlue)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat("تعداد", "${stats.total}", JGray)
                        Stat("برد", "${stats.wins}", JGreen)
                        Stat("باخت", "${stats.losses}", JRed)
                    }

                    Text(
                        "وین‌ریت: ${String.format(Locale.US, "%.1f%%", stats.winRate)}",
                        fontSize = 12.sp, fontWeight = FontWeight.Black,
                        color = if (stats.winRate >= 55) JGreen else JRed
                    )
                    Text(
                        "مجموع PnL: ${String.format(Locale.US, "%+.2f%%", stats.totalPnl)}",
                        fontSize = 12.sp, fontWeight = FontWeight.Black,
                        color = if (stats.totalPnl >= 0) JGreen else JRed
                    )
                    Text("میانگین سود: ${String.format(Locale.US, "%+.2f%%", stats.avgWin)}", fontSize = 10.sp, color = JGreen)
                    Text("میانگین ضرر: ${String.format(Locale.US, "%-.2f%%", stats.avgLoss)}", fontSize = 10.sp, color = JRed)
                    Text(
                        "امید ریاضی: ${String.format(Locale.US, "%+.2f%%", stats.expectancy)}",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (stats.expectancy > 0) JGreen else JRed
                    )
                    Text(
                        "Profit Factor: ${if (stats.profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", stats.profitFactor)}",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (stats.profitFactor >= 1.5) JGreen else if (stats.profitFactor >= 1.0) JGold else JRed
                    )
                    Text(
                        "📉 Max Drawdown: ${String.format(Locale.US, "%.2f%%", stats.maxDrawdown)}",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (stats.maxDrawdown < 20) JGreen else JRed
                    )
                }
            }

            // ---------- Long vs Short ----------
            Surface(color = JCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🎯 جهت تریدها", fontWeight = FontWeight.Black, fontSize = 12.sp, color = JBlue)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat("LONG", "${stats.longs}", JGreen)
                        Stat("SHORT", "${stats.shorts}", JRed)
                    }
                }
            }

            // ---------- Exit Reasons ----------
            Surface(color = JCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🚪 دلایل خروج (ExitEngine)", fontWeight = FontWeight.Black, fontSize = 12.sp, color = JBlue)
                    ExitRow("🛑 STOP (استاپ)", stats.stopExits, stats.total, JRed)
                    ExitRow("🎯 TARGET (تارگت)", stats.targetExits, stats.total, JGreen)
                    ExitRow("📈 TRAIL/TIGHTEN (تریل)", stats.trailExits, stats.total, JBlue)
                    ExitRow("⏰ TIME (توقف زمانی)", stats.timeExits, stats.total, JGold)
                    ExitRow("⚡ VOL_SPIKE (اسپایک)", stats.volSpikeExits, stats.total, JRed)
                    ExitRow("🔄 MOMENTUM_RSI (چرخش)", stats.momentumExits, stats.total, JRed)
                }
            }

            // ---------- لیست تریدهای بسته ----------
            Text("📋 ${closed.size} ترید اخیر:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = JBlue)
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(closed.take(50)) { t -> JournalTradeRow(t) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = JGray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun ExitRow(label: String, count: Int, total: Int, color: Color) {
    val pct = if (total > 0) count * 100.0 / total else 0.0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = JGray, modifier = Modifier.weight(1f))
        Text(
            "$count (${String.format(Locale.US, "%.0f%%", pct)})",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color
        )
    }
}

@Composable
private fun JournalTradeRow(t: Trade) {
    val pnl = t.exitPrice?.let { exit ->
        if (t.side == "PUMP") (exit - t.entryPrice) / t.entryPrice * 100.0
        else (t.entryPrice - exit) / t.entryPrice * 100.0
    } ?: 0.0
    val pnlColor = if (pnl >= 0) JGreen else JRed
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
    val exitEmoji = when (t.exitReason) {
        "TARGET" -> "🎯"
        "TRAIL" -> "📈"
        "TIGHTEN" -> "🔧"
        "STOP" -> "🛑"
        "TIME" -> "⏰"
        "VOL_SPIKE" -> "⚡"
        "MOMENTUM_RSI" -> "🔄"
        "MANUAL" -> "👋"
        else -> "❓"
    }

    Surface(color = JCard.copy(alpha = 0.8f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$exitEmoji ${t.symbol} • ${if (t.side == "PUMP") "LONG" else "SHORT"} • ${t.exitReason ?: "?"}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    t.entryTime.let { timeFmt.format(Date(it)) } +
                        (t.exitTime?.let { " → " + timeFmt.format(Date(it)) } ?: ""),
                    fontSize = 8.sp, color = JGray
                )
            }
            Text(
                String.format(Locale.US, "%+.2f%%", pnl),
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = pnlColor
            )
        }
    }
}
