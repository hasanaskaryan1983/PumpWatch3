package com.pumpwatch.app.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.engine.PaperTradingEngine
import com.pumpwatch.app.store.TradeStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val FGreen = Color(0xFF00E676)
private val FRed = Color(0xFFFF5252)
private val FGold = Color(0xFFFFC107)
private val FGray = Color(0xFF8B949E)
private val FBlue = Color(0xFF40C4FF)
private val FCard = Color(0xFF1A0E0E)

@Composable
fun FuturesPaperScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var trades by remember { mutableStateOf<List<Trade>>(emptyList()) }
    var msg by remember { mutableStateOf("") }

    fun reload() {
        val all = TradeStore.load(context).filter { it.mode == "FUT" }
        trades = all.sortedByDescending { it.entryTime }
    }

    LaunchedEffect(Unit) {
        reload()
        // چک‌کردن استاپ/تارگت تریدهای باز
        try {
            val closed = PaperTradingEngine.checkAndClose(context)
            if (closed.isNotEmpty()) msg = "🔔 ${closed.size} ترید بسته شد"
        } catch (_: Exception) { }
        reload()
    }

    val open = trades.filter { it.status == "OPEN" }
    val closed = trades.filter { it.status == "CLOSED" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📝 Paper فیوچرز", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    try {
                        val closed = PaperTradingEngine.checkAndClose(context)
                        if (closed.isNotEmpty()) msg = "🔔 ${closed.size} ترید بسته شد"
                        else msg = "✅ وضعیت تریدها به‌روز شد"
                    } catch (_: Exception) { msg = "⚠️ خطا" }
                    reload()
                }
            }) { Text("🔄 بررسی", fontSize = 11.sp) }
        }

        Text(
            "معاملات کاغذی خودکار از سیگنال‌های طلایی فیوچرز • استاپ شناور (ExitEngine) • رمزنگاری‌شده",
            fontSize = 9.sp, color = FGray, lineHeight = 15.sp
        )

        if (msg.isNotEmpty()) Text(msg, fontSize = 10.sp, color = FGold, fontWeight = FontWeight.Bold)

        Text("🟢 باز (${open.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FGreen)
        if (open.isEmpty()) {
            Text("فعلاً ترید باز FUT نداری — سیگنال‌های طلایی اسکنر اینجا باز می‌شوند", fontSize = 10.sp, color = FGray)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(open) { t -> PaperTradeCard(t) { price ->
                    PaperTradingEngine.closeManual(context, t.id, price)
                    reload()
                } }
            }
        }

        Text("⚫ بسته اخیر (${closed.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FGray)
        if (closed.isEmpty()) {
            Text("هنوز تریدی بسته نشده", fontSize = 10.sp, color = FGray)
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(closed.take(10)) { t -> ClosedTradeCard(t) }
            }
        }
    }
}

@Composable
private fun PaperTradeCard(t: Trade, onClose: (Double) -> Unit) {
    val dirColor = if (t.side == "PUMP") FGreen else FRed
    val dirLabel = if (t.side == "PUMP") "🚀 LONG" else "🩸 SHORT"
    val unrealizedPnlPct = if (t.side == "PUMP")
        (t.currentPrice - t.entryPrice) / t.entryPrice * 100.0
    else
        (t.entryPrice - t.currentPrice) / t.entryPrice * 100.0

    Surface(color = FCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.width(6.dp))
                Surface(color = dirColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        dirLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = dirColor
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    String.format(Locale.US, "%+.2f%%", unrealizedPnlPct),
                    fontSize = 13.sp, fontWeight = FontWeight.Black,
                    color = if (unrealizedPnlPct >= 0) FGreen else FRed
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ورود: ${String.format(Locale.US, "$%.4f", t.entryPrice)}", fontSize = 10.sp, color = FGray)
                Text("جاری: ${String.format(Locale.US, "$%.4f", t.currentPrice)}", fontSize = 10.sp, color = FBlue, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("استاپ: ${String.format(Locale.US, "$%.4f", t.currentStop)}", fontSize = 10.sp, color = FRed)
                Text("تارگت: ${t.target2?.let { String.format(Locale.US, "$%.4f", it) } ?: "—"}", fontSize = 10.sp, color = FGreen)
            }

            val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
            Text(
                "باز از ${timeFmt.format(Date(t.entryTime))}",
                fontSize = 9.sp, color = FGray
            )

            Button(
                onClick = { onClose(t.currentPrice) },
                colors = ButtonDefaults.buttonColors(containerColor = FRed.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("❌ بستن دستی", fontSize = 10.sp) }
        }
    }
}

@Composable
private fun ClosedTradeCard(t: Trade) {
    val pnl = t.exitPrice?.let { exit ->
        if (t.side == "PUMP") (exit - t.entryPrice) / t.entryPrice * 100.0
        else (t.entryPrice - exit) / t.entryPrice * 100.0
    } ?: 0.0
    val pnlColor = if (pnl >= 0) FGreen else FRed
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    Surface(color = FCard.copy(alpha = 0.8f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${t.symbol} • ${if (t.side == "PUMP") "LONG" else "SHORT"} • ${t.exitReason ?: "?"}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    t.entryTime.let { timeFmt.format(Date(it)) } +
                        (t.exitTime?.let { " → " + timeFmt.format(Date(it)) } ?: ""),
                    fontSize = 8.sp, color = FGray
                )
            }
            Text(
                String.format(Locale.US, "%+.2f%%", pnl),
                fontSize = 13.sp, fontWeight = FontWeight.Black, color = pnlColor
            )
        }
    }
}
