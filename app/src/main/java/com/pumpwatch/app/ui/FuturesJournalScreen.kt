package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.store.TradeStore
import com.pumpwatch.app.ui.components.GlassCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// 🚀 P0 Tab 15: وضعیت خواندن تایپ‌شده برای مدیریت خطا و حالت خالی
internal sealed class JournalLoadState {
    object Loading : JournalLoadState()
    data class Loaded(val trades: List<Trade>) : JournalLoadState()
    object Unreadable : JournalLoadState()
}

// 🚀 P0 Tab 15: محاسبه‌گر مشترک با تعریف شفاف واحدها و محدودیت‌ها
internal object FuturesJournalCalculator {
    /**
     * محاسبه آمار ژورنال فیوچرز
     * ⚠️ توجه: تمامی مقادیر PnL در اینجا به صورت "تغییر قیمت ناخالص" (Gross Price Change) بر حسب درصد محاسبه می‌شوند.
     * کارمزد (Fees)، اسلیپیج (Slippage) و تأمین وجه (Funding) در این نسخه محاسبه نشده و وضعیت Net PnL برابر با NotComputed است.
     * Drawdown واقعی تا زمان فراهم شدن دفترکل سرمایه (Capital Ledger)، Unavailable است.
     */
    fun calculate(trades: List<Trade>): FutJournalStats {
        val closed = trades.filter { it.status == "CLOSED" && it.exitPrice != null }
        if (closed.isEmpty()) return FutJournalStats(0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, 0, 0)
        
        val pnls = closed.map { t -> 
            val exit = t.exitPrice!!
            val isLong = t.side == "PUMP" || t.side == "LONG"
            if (isLong) (exit - t.entryPrice) / t.entryPrice * 100.0 else (t.entryPrice - exit) / t.entryPrice * 100.0 
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
            longs = closed.count { it.side == "PUMP" || it.side == "LONG" },
            shorts = closed.count { it.side == "DUMP" || it.side == "SHORT" },
            stopExits = exitReasons.count { it == "STOP" },
            targetExits = exitReasons.count { it == "TARGET" },
            trailExits = exitReasons.count { it == "TRAIL" },
            timeExits = exitReasons.count { it == "TIME" },
            volSpikeExits = exitReasons.count { it == "VOL_SPIKE" },
            momentumExits = exitReasons.count { it == "MOMENTUM_RSI" }
        )
    }
}

// 🚀 P0 Tab 15: حذف هرگونه modifier دسترسی (public پیش‌فرض) برای جلوگیری از خطای visibility
data class FutJournalStats(
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
    // maxDrawdown حذف شد
    val longs: Int, 
    val shorts: Int, 
    val stopExits: Int, 
    val targetExits: Int, 
    val trailExits: Int, 
    val timeExits: Int, 
    val volSpikeExits: Int, 
    val momentumExits: Int
)

@Composable
fun FuturesJournalScreen() {
    val context = LocalContext.current
    var loadState by remember { mutableStateOf<JournalLoadState>(JournalLoadState.Loading) }

    fun loadTrades() {
        loadState = JournalLoadState.Loading
        try {
            val trades = TradeStore.load(context).filter { it.mode == "FUT" }
            loadState = JournalLoadState.Loaded(trades)
        } catch (e: Exception) {
            loadState = JournalLoadState.Unreadable
        }
    }

    LaunchedEffect(Unit) {
        loadTrades()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EditorialPageHeader(
            eyebrow = "FUTURES · JOURNAL",
            title = "ژورنال عملکرد (شبیه‌سازی)",
            subtitle = "آمار تغییرات قیمت ناخالص (بدون احتساب کارمزد/اسلیپیج). Drawdown واقعی تا زمان فراهم شدن دفترکل سرمایه، Unavailable است.",
            accent = PumpWatchPalette.TabWatchlist
        )

        when (val state = loadState) {
            is JournalLoadState.Loading -> {
                GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                    Text("⏳ در حال بارگذاری تاریخچه...", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(16.dp))
                }
            }
            is JournalLoadState.Unreadable -> {
                GlassCard(accent = PumpWatchPalette.Negative, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ خطا در خواندن تاریخچه معاملات", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                        Button(
                            onClick = { loadTrades() },
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("تلاش مجدد", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is JournalLoadState.Loaded -> {
                val trades = state.trades
                val stats = remember(trades) { FuturesJournalCalculator.calculate(trades) }
                val closed = trades.filter { it.status == "CLOSED" && it.exitPrice != null }.sortedByDescending { it.exitTime ?: 0L }

                if (stats.total == 0) {
                    GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                        Text("هنوز معاملهٔ بسته‌شده‌ای در حالت فیوچرز ثبت نشده است.", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📊 خلاصه عملکرد (Gross Price Change %)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = PumpWatchPalette.Info)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Stat("تعداد", "${stats.total}", PumpWatchPalette.Muted)
                                Stat("برد", "${stats.wins}", PumpWatchPalette.Positive)
                                Stat("باخت", "${stats.losses}", PumpWatchPalette.Negative)
                            }
                            Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", stats.winRate)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (stats.winRate >= 55) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                            Text("مجموع تغییر قیمت: ${String.format(Locale.US, "%+.2f%%", stats.totalPnl)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (stats.totalPnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                            Text("امید ریاضی: ${String.format(Locale.US, "%+.2f%%", stats.expectancy)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (stats.expectancy > 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                            Text("Profit Factor: ${if (stats.profitFactor.isInfinite()) "∞" else String.format(Locale.US, "%.2f", stats.profitFactor)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (stats.profitFactor >= 1.5) PumpWatchPalette.Positive else if (stats.profitFactor >= 1.0) PumpWatchPalette.Warning else PumpWatchPalette.Negative)
                            
                            Surface(color = PumpWatchPalette.Warning.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "⚠️ توجه: این آمار فقط تغییر قیمت ناخالص است. کارمزد، اسلیپیج و Funding محاسبه نشده (Net PnL = NotComputed). Drawdown واقعی تا زمان فراهم شدن دفترکل سرمایه، Unavailable است.",
                                    fontSize = 9.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(8.dp), textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                    
                    GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("📉 دلایل خروج", fontWeight = FontWeight.Black, fontSize = 12.sp, color = PumpWatchPalette.Info)
                            ExitRow("🛑 STOP", stats.stopExits, stats.total, PumpWatchPalette.Negative)
                            ExitRow("🎯 TARGET", stats.targetExits, stats.total, PumpWatchPalette.Positive)
                            ExitRow("📈 TRAIL", stats.trailExits, stats.total, PumpWatchPalette.Info)
                            ExitRow("⏰ TIME", stats.timeExits, stats.total, PumpWatchPalette.Muted)
                        }
                    }
                    
                    Text("📜 تاریخچه معاملات (نمایش ۵۰ مورد اخیر):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PumpWatchPalette.Info)
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp), 
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(closed.take(50)) { t -> JournalTradeRow(t) }
                        item {
                            if (closed.size > 50) {
                                Text(
                                    "... و ${closed.size - 50} معاملهٔ دیگر (محدودیت نمایش برای بهینه‌سازی عملکرد)", 
                                    fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "$label: $value" }
    ) {
        Text(label, fontSize = 9.sp, color = color)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun ExitRow(label: String, count: Int, total: Int, color: androidx.compose.ui.graphics.Color) {
    val pct = if (total > 0) count * 100.0 / total else 0.0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = PumpWatchPalette.Muted, modifier = Modifier.weight(1f))
        Text("$count (${String.format(Locale.US, "%.0f%%", pct)})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun JournalTradeRow(t: Trade) {
    val isLong = t.side == "PUMP" || t.side == "LONG"
    val pnl = t.exitPrice?.let { exit -> if (isLong) (exit - t.entryPrice) / t.entryPrice * 100.0 else (t.entryPrice - exit) / t.entryPrice * 100.0 } ?: 0.0
    val pnlColor = if (pnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
    val exitEmoji = when (t.exitReason) { "TARGET" -> "🎯"; "TRAIL" -> "📈"; "STOP" -> "🛑"; "TIME" -> "⏰"; else -> "❓" }
    
    GlassCard(accent = PumpWatchPalette.TabWatchlist, radius = 10.dp, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$exitEmoji ${t.symbol} • ${if (isLong) "LONG" else "SHORT"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                Text("ورود: ${timeFmt.format(Date(t.entryTime))}" + (t.exitTime?.let { " | خروج: ${timeFmt.format(Date(it))}" } ?: ""), fontSize = 8.sp, color = PumpWatchPalette.Muted)
                if (!t.exitReason.isNullOrBlank()) {
                    Text("دلیل: ${t.exitReason}", fontSize = 8.sp, color = PumpWatchPalette.Info)
                }
            }
            Text(String.format(Locale.US, "%+.2f%%", pnl), fontSize = 13.sp, fontWeight = FontWeight.Black, color = pnlColor)
        }
    }
}
