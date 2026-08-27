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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.engine.PaperTradingEngine
import com.pumpwatch.app.store.TradeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val TGreen = Color(0xFF00E676)
private val TRed = Color(0xFFFF5252)
private val TGold = Color(0xFFFFC107)

// ---------- صفحه معاملات شبیه‌سازی ----------

@Composable
fun TradesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(TradeStore.isEnabled(context)) }
    var trades by remember { mutableStateOf<List<Trade>>(emptyList()) }
    var stats by remember { mutableStateOf(TradeStore.stats(context)) }
    var filter by remember { mutableStateOf("ALL") }
    var deleteTarget by remember { mutableStateOf<Trade?>(null) }
    var closeTarget by remember { mutableStateOf<Trade?>(null) }

    fun reload() {
        trades = TradeStore.load(context)
        stats = TradeStore.stats(context)
    }

    LaunchedEffect(Unit) { reload() }

    // به‌روزرسانی خودکار هر ۳۰ ثانیه
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            if (enabled) {
                try {
                    PaperTradingEngine.checkAndClose(context)
                    PaperTradingEngine.syncFromToday(context)
                } catch (_: Exception) { }
                reload()
            }
        }
    }

    val shown = when (filter) {
        "OPEN" -> trades.filter { it.status == "OPEN" }
        "CLOSED" -> trades.filter { it.status == "CLOSED" }
        else -> trades
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- سربرگ ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📈 معاملات شبیه‌سازی",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    TradeStore.setEnabled(context, it)
                    if (it) scope.launch {
                        PaperTradingEngine.syncFromToday(context)
                        PaperTradingEngine.checkAndClose(context)
                        reload()
                    }
                }
            )
        }

        // ---------- آمار کلی ----------
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCell(
                    "باز",
                    "${stats.openCount}",
                    if (stats.openCount > 0) TGold else Color.Unspecified
                )
                StatCell("بسته", "${stats.closedCount}")
                StatCell(
                    "برد",
                    "${stats.winCount}",
                    if (stats.winCount > 0) TGreen else Color.Unspecified
                )
                StatCell(
                    "وین‌ریت",
                    String.format(Locale.US, "%.0f%%", stats.winRate),
                    if (stats.winRate >= 50) TGreen else TRed
                )
                StatCell(
                    "PnL",
                    String.format(Locale.US, "%+.2f%%", stats.totalPnl),
                    if (stats.totalPnl >= 0) TGreen else TRed
                )
            }
        }

        // ---------- فیلترها ----------
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == "ALL",
                onClick = { filter = "ALL" },
                label = { Text("همه ${trades.size}") }
            )
            FilterChip(
                selected = filter == "OPEN",
                onClick = { filter = "OPEN" },
                label = { Text("باز ${stats.openCount}") }
            )
            FilterChip(
                selected = filter == "CLOSED",
                onClick = { filter = "CLOSED" },
                label = { Text("بسته ${stats.closedCount}") }
            )
        }

        // ---------- محتوا ----------
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (!enabled) "🔕 سیستم خاموشه — سوئیچ بالا رو روشن کن"
                    else "هنوز معامله‌ای ثبت نشده\nصبر کن تا سیگنال طلایی بیاد 🏆",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown) { trade ->
                    TradeCard(
                        trade = trade,
                        onDelete = { deleteTarget = trade },
                        onClose = { closeTarget = trade }
                    )
                }
            }
        }
    }

    // ---------- دیالوگ حذف ----------
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف معامله") },
            text = { Text("معامله ${t.symbol} حذف بشه؟ این کار قابل برگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    TradeStore.remove(context, t.id)
                    deleteTarget = null
                    reload()
                }) { Text("حذف", color = TRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("لغو") }
            }
        )
    }

    // ---------- دیالوگ بستن دستی ----------
    closeTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { closeTarget = null },
            title = { Text("بستن دستی") },
            text = { Text("معامله ${t.symbol} با قیمت فعلی (${priceText(t.currentPrice)}) بسته بشه؟") },
            confirmButton = {
                TextButton(onClick = {
                    PaperTradingEngine.closeManual(context, t.id, t.currentPrice)
                    closeTarget = null
                    reload()
                }) { Text("بستن") }
            },
            dismissButton = {
                TextButton(onClick = { closeTarget = null }) { Text("لغو") }
            }
        )
    }
}

// ---------- کارت معامله ----------

@Composable
private fun TradeCard(
    trade: Trade,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    val isOpen = trade.status == "OPEN"
    val isLong = trade.side == "PUMP"
    val pnl = if (isOpen) trade.unrealizedPnl() else trade.realizedPnl()
    val pnlColor = if (pnl >= 0) TGreen else TRed

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ---------- ردیف اول: آیکون + نماد + دکمه‌ها ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isLong) "🚀" else "🩸", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            trade.symbol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isOpen) "🟢 باز" else if (trade.exitReason == "TARGET") "🎯 هدف"
                            else if (trade.exitReason == "BE") "🛡️ بیک‌ایون"
                            else if (trade.exitReason == "MANUAL") "✋ دستی"
                            else "🛑 استاپ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOpen) TGold else
                                if (trade.exitReason == "TARGET") TGreen else TRed
                        )
                    }
                    Text(
                        "${trade.name} • ${if (trade.mode == "SPOT") "اسپات" else "فیوچرز"}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        String.format(Locale.US, "%+.2f%%", pnl),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = pnlColor
                    )
                    Text(
                        if (isOpen) "PnL" else "نهایی",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // ---------- ردیف دوم: قیمت‌ها ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ورود: ${priceText(trade.entryPrice)}", fontSize = 11.sp)
                Text(
                    "فعلی: ${priceText(trade.currentPrice)}",
                    fontSize = 11.sp,
                    color = pnlColor
                )
                if (isOpen) {
                    Text(
                        "استاپ: ${priceText(trade.currentStop)}",
                        fontSize = 11.sp,
                        color = TRed
                    )
                } else {
                    trade.exitPrice?.let {
                        Text(
                            "خروج: ${priceText(it)}",
                            fontSize = 11.sp,
                            color = TGreen
                        )
                    }
                }
            }

            // ---------- ردیف سوم: اهداف و زمان ----------
            if (isOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("هدف۱: ${priceText(trade.target1)}", fontSize = 11.sp, color = TGreen)
                    Text("هدف۲: ${priceText(trade.target2)}", fontSize = 11.sp, color = TGreen)
                }
                Text(
                    "باز شده: ${DateUtils.getRelativeTimeSpanString(trade.entryTime)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                trade.exitTime?.let {
                    Text(
                        "مدت: ${formatDuration(trade.entryTime, it)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // ---------- ردیف چهارم: دکمه‌های عملیات ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOpen) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    ) { Text("بستن دستی ✋", fontSize = 12.sp) }
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) { Text("حذف 🗑️", color = TRed, fontSize = 12.sp) }
            }
        }
    }
}

// ---------- سلول آماری ----------

@Composable
private fun StatCell(label: String, value: String, color: Color = Color.Unspecified) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = color.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------- فرمت قیمت ----------

private fun priceText(price: Double): String {
    return if (price >= 1) String.format(Locale.US, "$%,.2f", price)
    else String.format(Locale.US, "$%.6f", price)
}

// ---------- فرمت مدت زمان ----------

private fun formatDuration(start: Long, end: Long): String {
    val mins = (end - start) / 60_000
    return when {
        mins < 60 -> "${mins} دقیقه"
        mins < 1440 -> "${mins / 60} ساعت"
        else -> "${mins / 1440} روز"
    }
}
