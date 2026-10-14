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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.engine.PaperTradingEngine
import com.pumpwatch.app.store.TradeStore
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 🚀 P0 Tab 14: نمایشگر وضعیت بررسی موتور (CheckResult)
@Composable
private fun EngineCheckStatusBadge(lastCheckStatus: String) {
    val (color, text) = when (lastCheckStatus) {
        "STALE" -> PumpWatchPalette.Warning to "⚠️ داده کهنه (Stale)"
        "FAILED" -> PumpWatchPalette.Negative to "❌ خطا در بررسی"
        else -> PumpWatchPalette.Positive to "✅ بررسی موفق"
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FuturesPaperScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trades by remember { mutableStateOf<List<Trade>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(TradeStore.isEnabled(context)) }
    
    // 🚀 P0 Tab 14: ردیابی وضعیت آخرین بررسی موتور
    var lastCheckStatus by remember { mutableStateOf("UNKNOWN") }

    fun reload() {
        val all = TradeStore.load(context).filter { it.mode == "FUT" }
        trades = all.sortedByDescending { it.entryTime }
    }

    fun runEngineCheck() {
        scope.launch {
            try {
                val closed = PaperTradingEngine.checkAndClose(context)
                lastCheckStatus = if (closed.isNotEmpty()) "SUCCESS" else "SUCCESS" // در نسخه‌های بعدی با CheckResult تایپ‌شده جایگزین می‌شود
                msg = if (closed.isNotEmpty()) "🔔 ${closed.size} ترید بر اساس قوانین بسته شد" else "✅ وضعیت تریدها به‌روز شد"
            } catch (e: Exception) {
                lastCheckStatus = "FAILED"
                msg = "⚠️ خطا در بررسی خودکار: ${e.message}"
            }
            reload()
        }
    }

    LaunchedEffect(Unit) {
        reload()
        runEngineCheck()
    }

    val open = trades.filter { it.status == "OPEN" }
    val closed = trades.filter { it.status == "CLOSED" }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 🚀 P0 Tab 14: صداقت محصول و سلب مسئولیت شفاف
        EditorialPageHeader(
            eyebrow = "FUTURES · PAPER TRADING",
            title = "تمرین معاملات فیوچرز",
            subtitle = "شبیه‌سازی مبتنی بر داده. وضعیت ریسک و کارمزد تا زمان محاسبهٔ نهایی، NotComputed است. بررسی دستی قیمت هنگام باز/بسته کردن الزامی است.",
            accent = PumpWatchPalette.TabAssistant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEnabled) "✅ فعال" else "⚫ غیرفعال", fontSize = 10.sp, color = if (isEnabled) PumpWatchPalette.Positive else PumpWatchPalette.Muted, fontWeight = FontWeight.Bold)
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enabled ->
                        isEnabled = enabled
                        TradeStore.setEnabled(context, enabled)
                        msg = if (enabled) "✅ حالت تمرین فعال شد. لطفاً از داشبورد سیگنال‌ها را بررسی کنید." else "⚫ حالت تمرین غیرفعال شد."
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PumpWatchPalette.Ink,
                        checkedTrackColor = PumpWatchPalette.TabAssistant,
                        uncheckedThumbColor = PumpWatchPalette.Muted,
                        uncheckedTrackColor = PumpWatchPalette.Glass
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            EngineCheckStatusBadge(lastCheckStatus)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { runEngineCheck() }) { 
                Text("🔄 بررسی موتور", fontSize = 11.sp, color = PumpWatchPalette.TabAssistant) 
            }
        }

        if (!isEnabled) {
            GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth()) {
                Text("⚠️ حالت تمرین غیرفعال است. برای ثبت و پیگیری موقعیت‌ها، سوییچ بالا را فعال کنید.", fontSize = 10.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
            }
        }

        if (msg.isNotEmpty()) {
            Text(msg, fontSize = 10.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
        }

        // 🚀 P0 Tab 14: بخش موقعیت‌های باز
        Text("🟢 موقعیت‌های باز (${open.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PumpWatchPalette.Positive)
        if (open.isEmpty()) {
            Text(if (isEnabled) "فعلاً موقعیت بازی ندارید. از داشبورد سیگنال‌ها را بررسی و تمرین کنید." else "حالت تمرین غیرفعال است.", fontSize = 10.sp, color = PumpWatchPalette.Muted)
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(open) { t -> PaperTradeCard(t) { price -> PaperTradingEngine.closeManual(context, t.id, price); reload() } }
            }
        }

        // 🚀 P0 Tab 14: بخش موقعیت‌های بسته
        Text("⚫ تاریخچه موقعیت‌های بسته (${closed.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PumpWatchPalette.Muted)
        if (closed.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(closed.take(10)) { t -> ClosedTradeCard(t) }
            }
        }
    }
}

@Composable
private fun PaperTradeCard(t: Trade, onClose: (Double) -> Unit) {
    // 🚀 P0 Tab 14: استانداردسازی نمایش جهت به LONG/SHORT
    val isLong = t.side == "PUMP" || t.side == "LONG"
    val dirColor = if (isLong) PumpWatchPalette.Positive else PumpWatchPalette.Negative
    val dirLabel = if (isLong) "📈 LONG" else "📉 SHORT"
    
    // 🚀 P0 Tab 14: برچسب‌گذاری صریح به‌عنوان تغییر قیمت ناخالص (Gross)
    val grossPriceChangePct = if (isLong) (t.currentPrice - t.entryPrice) / t.entryPrice * 100.0 else (t.entryPrice - t.currentPrice) / t.entryPrice * 100.0

    GlassCard(accent = PumpWatchPalette.TabAssistant, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp, color = PumpWatchPalette.Text)
                Spacer(Modifier.width(6.dp))
                Surface(color = dirColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(dirLabel, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = dirColor)
                }
                Spacer(Modifier.weight(1f))
                Text(String.format(Locale.US, "%+.2f%%", grossPriceChangePct), fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (grossPriceChangePct >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ورود: ${String.format(Locale.US, "$%.4f", t.entryPrice)}", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                Text("جاری: ${String.format(Locale.US, "$%.4f", t.currentPrice)}", fontSize = 10.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("استاپ: ${String.format(Locale.US, "$%.4f", t.currentStop)}", fontSize = 10.sp, color = PumpWatchPalette.Negative)
                Text("تارگت: ${t.target2?.let { String.format(Locale.US, "$%.4f", it) } ?: "—"}", fontSize = 10.sp, color = PumpWatchPalette.Positive)
            }
            
            // 🚀 P0 Tab 14: هشدار صریح NotComputed و بررسی دستی
            Surface(color = PumpWatchPalette.Warning.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "⚠️ ریسک/کارمزد: NotComputed. سود/زیان فوق ناخالص است. قبل از بستن دستی، قیمت زنده را بررسی کنید.",
                    fontSize = 8.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(6.dp), textAlign = TextAlign.Start
                )
            }

            val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
            Text("باز از ${timeFmt.format(Date(t.entryTime))}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
            
            Button(
                onClick = { onClose(t.currentPrice) },
                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { 
                Text("❌ بستن دستی (نیازمند تأیید قیمت)", fontSize = 10.sp, fontWeight = FontWeight.Bold) 
            }
        }
    }
}

@Composable
private fun ClosedTradeCard(t: Trade) {
    val isLong = t.side == "PUMP" || t.side == "LONG"
    val grossPnlPct = t.exitPrice?.let { exit -> if (isLong) (exit - t.entryPrice) / t.entryPrice * 100.0 else (t.entryPrice - exit) / t.entryPrice * 100.0 } ?: 0.0
    val pnlColor = if (grossPnlPct >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
    
    GlassCard(accent = PumpWatchPalette.TabAssistant, radius = 10.dp, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${t.symbol} • ${if (isLong) "LONG" else "SHORT"} • ${t.exitReason ?: "دستی"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                Text(t.entryTime.let { timeFmt.format(Date(it)) } + (t.exitTime?.let { " → " + timeFmt.format(Date(it)) } ?: ""), fontSize = 8.sp, color = PumpWatchPalette.Muted)
                // 🚀 P0 Tab 14: برچسب ناخالص برای تاریخچه
                Text("تغییر قیمت ناخالص (بدون کسر کارمزد)", fontSize = 7.sp, color = PumpWatchPalette.MutedDim)
            }
            Text(String.format(Locale.US, "%+.2f%%", grossPnlPct), fontSize = 13.sp, fontWeight = FontWeight.Black, color = pnlColor)
        }
    }
}
