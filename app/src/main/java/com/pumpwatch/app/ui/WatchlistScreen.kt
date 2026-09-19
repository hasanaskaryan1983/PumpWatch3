package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.platformContractOf
import com.pumpwatch.app.store.WatchAlert
import com.pumpwatch.app.store.WatchItem
import com.pumpwatch.app.store.WatchlistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val WGreen = Color(0xFF00E676)
private val WRed = Color(0xFFFF5252)
private val WGold = Color(0xFFFFC107)
private val WGray = Color(0xFF8B949E)
private val WBlue = Color(0xFF40C4FF)
private val WCard = Color(0xFF1A2230)

@Composable
fun WatchlistScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<WatchItem>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<WatchAlert>>(emptyList()) }
    var prices by remember { mutableStateOf<Map<String, CoinMarket>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }

    var above by remember { mutableStateOf(true) }
    var thresholdText by remember { mutableStateOf("") }
    var editingAlertId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        items = WatchlistStore.items(context)
        alerts = WatchlistStore.loadAlerts(context)
    }

    fun refresh() {
        scope.launch {
            loading = true
            try {
                val coins = withContext(Dispatchers.IO) { ApiClient.getTop1000Coins() }
                prices = coins.associateBy { it.id }
                val fired = withContext(Dispatchers.IO) { WatchlistStore.checkAndFire(context) }
                if (fired > 0) msg = "🔔 $fired هشدار فعال شد"
                reload()
            } catch (_: Exception) {
                msg = "⚠️ خطا در دریافت قیمت‌ها"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
        refresh()
    }

    val active = alerts.filter { it.triggeredAt == null }
    val triggered = alerts.filter { it.triggeredAt != null }
    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐ واچ‌لیست من", fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { refresh() },
                colors = ButtonDefaults.buttonColors(containerColor = WBlue.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(10.dp)
            ) { Text("🔄", fontSize = 12.sp) }
        }

        Text(
            "ارزهای دلخواهت را با نماد، نام یا آدرس کانترکت اضافه کن (فقط تا رتبه ۱۰۰). " +
                "هشدارها هر ۱۵ دقیقه بررسی می‌شوند؛ هنگام باز بودن این تب، آنی.",
            fontSize = 9.sp, color = WGray, lineHeight = 15.sp
        )

        if (msg.isNotEmpty()) Text(msg, fontSize = 10.sp, color = WGold, fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("نماد / نام / کانترکت...", fontSize = 11.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    scope.launch {
                        val q = input.trim()
                        if (q.isEmpty()) return@launch
                        loading = true
                        try {
                            val (coins, map) = withContext(Dispatchers.IO) {
                                val c = ApiClient.getTop1000Coins()
                                val m = try { ApiClient.getPlatformMap() } catch (_: Exception) { emptyMap() }
                                c to m
                            }
                            val hit = coins.firstOrNull { it.symbol.equals(q, true) }
                                ?: coins.firstOrNull { it.name.equals(q, true) }
                                ?: coins.firstOrNull { c -> map[c.id]?.values?.any { it.equals(q, true) } == true }
                            if (hit == null) {
                                msg = "❌ پیدا نشد — فقط ارزهای تا رتبه ۱۰۰۰"
                            } else {
                                val ok = WatchlistStore.addItem(
                                    context,
                                    WatchItem(
                                        id = hit.id,
                                        symbol = hit.symbol.uppercase(Locale.US),
                                        name = hit.name,
                                        contract = platformContractOf(map, hit.id),
                                        rank = hit.market_cap_rank
                                    )
                                )
                                msg = if (ok) "✅ ${hit.symbol.uppercase(Locale.US)} اضافه شد"
                                else "⚠️ قبلاً اضافه شده یا لیست پر است"
                                if (ok) input = ""
                                reload()
                            }
                        } catch (_: Exception) {
                            msg = "⚠️ خطا در جستجو"
                        }
                        loading = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WGreen.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(10.dp)
            ) { Text("＋ افزودن", fontSize = 11.sp) }
        }

        if (items.isEmpty()) {
            Text("هنوز ارزی اضافه نکرده‌ای ⭐", fontSize = 11.sp, color = WGray)
        }

        items.forEach { item ->
            val coin = prices[item.id]
            val myAlerts = active.filter { it.coinId == item.id }
            Card(colors = CardDefaults.cardColors(containerColor = WCard), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${item.symbol}  #${item.rank ?: "—"}",
                                fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White
                            )
                            Text(item.name, fontSize = 10.sp, color = WGray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                coin?.let { String.format(Locale.US, "$%.6f", it.current_price) } ?: "—",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                coin?.let { String.format(Locale.US, "%+.1f%%", it.price_change_percentage_24h ?: 0.0) } ?: "",
                                fontSize = 10.sp,
                                color = if ((coin?.price_change_percentage_24h ?: 0.0) >= 0) WGreen else WRed
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                expanded = if (expanded == item.id) null else item.id
                                editingAlertId = null
                                thresholdText = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WGold.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("🔔 هشدار (${myAlerts.size})", fontSize = 10.sp) }
                        Button(
                            onClick = {
                                WatchlistStore.removeItem(context, item.id)
                                reload()
                                msg = "🗑️ ${item.symbol} حذف شد"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WRed.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("حذف", fontSize = 10.sp) }
                    }

                    if (expanded == item.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = above,
                                onClick = { above = true },
                                label = { Text("بالای", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = !above,
                                onClick = { above = false },
                                label = { Text("زیرِ", fontSize = 10.sp) }
                            )
                            TextField(
                                value = thresholdText,
                                onValueChange = { thresholdText = it },
                                placeholder = { Text("قیمت هدف ($)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                        Button(
                            onClick = {
                                val th = thresholdText.toDoubleOrNull()
                                if (th == null || th <= 0.0) {
                                    msg = "❌ قیمت معتبر وارد کن"
                                } else {
                                    val eid = editingAlertId
                                    if (eid != null) {
                                        WatchlistStore.updateAlert(context, eid, above, th)
                                        msg = "✏️ هشدار به‌روز شد"
                                    } else {
                                        WatchlistStore.addAlert(
                                            context,
                                            WatchAlert(
                                                id = UUID.randomUUID().toString(),
                                                coinId = item.id,
                                                symbol = item.symbol,
                                                name = item.name,
                                                above = above,
                                                threshold = th,
                                                createdAt = System.currentTimeMillis()
                                            )
                                        )
                                        msg = "✅ هشدار ثبت شد"
                                    }
                                    editingAlertId = null
                                    thresholdText = ""
                                    reload()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WGreen.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (editingAlertId != null) "💾 ذخیره تغییر" else "➕ ثبت هشدار", fontSize = 10.sp) }

                        myAlerts.forEach { a ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${if (a.above) "⬆️ بالای" else "⬇️ زیرِ"} ${String.format(Locale.US, "$%.6f", a.threshold)}",
                                    fontSize = 10.sp, color = WBlue, fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            editingAlertId = a.id
                                            above = a.above
                                            thresholdText = a.threshold.toString()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WCard),
                                        shape = RoundedCornerShape(6.dp)
                                    ) { Text("✏️", fontSize = 9.sp) }
                                    Button(
                                        onClick = {
                                            WatchlistStore.removeAlert(context, a.id)
                                            reload()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WCard),
                                        shape = RoundedCornerShape(6.dp)
                                    ) { Text("🗑️", fontSize = 9.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("🔔 هشدارهای فعال‌شده (${triggered.size})", fontWeight = FontWeight.Black, fontSize = 13.sp, color = WGold)
        if (triggered.isEmpty()) {
            Text("هنوز هیچ هشداری فعال نشده — وقتی قیمت به بازهٔ تو برسد، اینجا و در نوتیفیکیشن می‌بینی.", fontSize = 9.sp, color = WGray)
        }
        triggered.sortedByDescending { it.triggeredAt ?: 0L }.forEach { a ->
            Card(colors = CardDefaults.cardColors(containerColor = WCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${a.name} (${a.symbol})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "${if (a.above) "⬆️ بالای" else "⬇️ زیرِ"} ${String.format(Locale.US, "$%.6f", a.threshold)} " +
                                "• فعال در ${a.triggeredPrice?.let { String.format(Locale.US, "$%.6f", it) } ?: "—"}",
                            fontSize = 9.sp, color = WGold
                        )
                        Text(
                            a.triggeredAt?.let { timeFmt.format(Date(it)) } ?: "",
                            fontSize = 8.sp, color = WGray
                        )
                    }
                    Button(
                        onClick = {
                            WatchlistStore.removeAlert(context, a.id)
                            reload()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WRed.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("حذف", fontSize = 10.sp) }
                }
            }
        }
    }
}
