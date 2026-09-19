package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.pumpwatch.app.store.WatchCoin
import com.pumpwatch.app.store.WatchGroup
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

    var groups by remember { mutableStateOf<List<WatchGroup>>(emptyList()) }
    var prices by remember { mutableStateOf<Map<String, CoinMarket>>(emptyMap()) }
    var msg by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }
    var expandedGroupId by remember { mutableStateOf<String?>(null) }
    var expandedCoinId by remember { mutableStateOf<String?>(null) }

    var coinSearchInput by remember { mutableStateOf("") }
    var above by remember { mutableStateOf(true) }
    var thresholdText by remember { mutableStateOf("") }
    var editingAlertId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        groups = WatchlistStore.loadGroups(context)
    }

    fun refresh() {
        scope.launch {
            try {
                val coins = withContext(Dispatchers.IO) { ApiClient.getTop1000Coins() }
                prices = coins.associateBy { it.id }
                val fired = withContext(Dispatchers.IO) { WatchlistStore.checkAndFire(context) }
                if (fired > 0) msg = "🔔 $fired هشدار فعال شد"
                reload()
            } catch (_: Exception) {
                msg = "⚠️ خطا در دریافت قیمت‌ها"
            }
        }
    }

    LaunchedEffect(Unit) {
        reload()
        refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            "تا ${WatchlistStore.MAX_GROUPS} ردیف • هر ردیف تا ${WatchlistStore.MAX_COINS_PER_GROUP} ارز • هر ارز تا ${WatchlistStore.MAX_ALERTS_PER_COIN} هشدار\n" +
                "هشدارها هر ۱۵ دقیقه بررسی می‌شوند؛ هنگام باز بودن این تب، آنی.",
            fontSize = 9.sp, color = WGray, lineHeight = 15.sp
        )

        if (msg.isNotEmpty()) Text(msg, fontSize = 10.sp, color = WGold, fontWeight = FontWeight.Bold)

        // ---------- افزودن گروه جدید ----------
        if (groups.size < WatchlistStore.MAX_GROUPS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    placeholder = { Text("نام ردیف جدید...", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        if (newGroupName.isBlank()) {
                            msg = "❌ نام ردیف خالی است"
                        } else {
                            val ok = WatchlistStore.addGroup(context, newGroupName.trim())
                            msg = if (ok) "✅ ردیف «${newGroupName.trim()}» ساخته شد" else "⚠️ حداکثر ${WatchlistStore.MAX_GROUPS} ردیف"
                            if (ok) newGroupName = ""
                            reload()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WGreen.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("＋ ردیف", fontSize = 11.sp) }
            }
        }

        // ---------- لیست گروه‌ها ----------
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    Text("هنوز ردیفی نساخته‌ای — بالا یک ردیف جدید بساز ⭐", fontSize = 11.sp, color = WGray)
                }
            }

            items(groups) { group ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = WCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📁 ${group.name}",
                                fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${group.coins.size}/${WatchlistStore.MAX_COINS_PER_GROUP}",
                                fontSize = 10.sp, color = WGray
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    WatchlistStore.removeGroup(context, group.id)
                                    reload()
                                    msg = "🗑️ ردیف «${group.name}» حذف شد"
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WRed.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(6.dp)
                            ) { Text("🗑️", fontSize = 10.sp) }
                        }

                        // ---------- افزودن ارز به این گروه ----------
                        if (group.coins.size < WatchlistStore.MAX_COINS_PER_GROUP) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = coinSearchInput,
                                    onValueChange = { coinSearchInput = it },
                                    placeholder = { Text("نام/نماد/کانترکت...", fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Spacer(Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val q = coinSearchInput.trim()
                                            if (q.isEmpty()) return@launch
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
                                                    val ok = WatchlistStore.addCoin(
                                                        context, group.id,
                                                        WatchCoin(
                                                            id = hit.id,
                                                            symbol = hit.symbol.uppercase(Locale.US),
                                                            name = hit.name,
                                                            contract = platformContractOf(map, hit.id),
                                                            rank = hit.market_cap_rank
                                                        )
                                                    )
                                                    msg = if (ok) "✅ ${hit.symbol.uppercase(Locale.US)} به «${group.name}» اضافه شد"
                                                    else "⚠️ قبلاً اضافه شده یا ردیف پر است"
                                                    if (ok) coinSearchInput = ""
                                                    reload()
                                                }
                                            } catch (_: Exception) {
                                                msg = "⚠️ خطا در جستجو"
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = WGreen.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("＋", fontSize = 11.sp) }
                            }
                        }

                        // ---------- لیست ارزهای این گروه ----------
                        group.coins.forEach { coin ->
                            val price = prices[coin.id]
                            val activeAlerts = coin.alerts.filter { it.triggeredAt == null }
                            val triggeredAlerts = coin.alerts.filter { it.triggeredAt != null }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = WCard.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${coin.symbol}  #${coin.rank ?: "—"}",
                                                fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White
                                            )
                                            Text(coin.name, fontSize = 9.sp, color = WGray)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                price?.let { String.format(Locale.US, "$%.6f", it.current_price) } ?: "—",
                                                fontSize = 10.sp, fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                price?.let { String.format(Locale.US, "%+.1f%%", it.price_change_percentage_24h ?: 0.0) } ?: "",
                                                fontSize = 9.sp,
                                                color = if ((price?.price_change_percentage_24h ?: 0.0) >= 0) WGreen else WRed
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                expandedCoinId = if (expandedCoinId == coin.id) null else coin.id
                                                expandedGroupId = group.id
                                                editingAlertId = null
                                                thresholdText = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = WGold.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) { Text("🔔 (${activeAlerts.size})", fontSize = 9.sp) }
                                        Button(
                                            onClick = {
                                                WatchlistStore.removeCoin(context, group.id, coin.id)
                                                reload()
                                                msg = "🗑️ ${coin.symbol} از «${group.name}» حذف شد"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = WRed.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(6.dp)
                                        ) { Text("🗑️", fontSize = 9.sp) }
                                    }

                                    // ---------- ویرایشگر هشدار ----------
                                    if (expandedGroupId == group.id && expandedCoinId == coin.id) {
                                        if (activeAlerts.size < WatchlistStore.MAX_ALERTS_PER_COIN) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                FilterChip(selected = above, onClick = { above = true }, label = { Text("بالا", fontSize = 9.sp) })
                                                FilterChip(selected = !above, onClick = { above = false }, label = { Text("زیر", fontSize = 9.sp) })
                                                TextField(
                                                    value = thresholdText,
                                                    onValueChange = { thresholdText = it },
                                                    placeholder = { Text("$", fontSize = 9.sp) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    singleLine = true
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    val th = thresholdText.toDoubleOrNull()
                                                    if (th == null || th <= 0.0) {
                                                        msg = "❌ قیمت معتبر"
                                                    } else {
                                                        val eid = editingAlertId
                                                        if (eid != null) {
                                                            WatchlistStore.updateAlert(context, group.id, coin.id, eid, above, th)
                                                            msg = "✏️ هشدار به‌روز شد"
                                                        } else {
                                                            WatchlistStore.addAlert(
                                                                context, group.id, coin.id,
                                                                WatchAlert(
                                                                    id = UUID.randomUUID().toString(),
                                                                    above = above,
                                                                    threshold = th
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
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text(if (editingAlertId != null) "💾 ذخیره" else "➕ ثبت", fontSize = 9.sp) }
                                        } else {
                                            Text("حداکثر ${WatchlistStore.MAX_ALERTS_PER_COIN} هشدار", fontSize = 9.sp, color = WGray)
                                        }

                                        activeAlerts.forEach { a ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${if (a.above) "⬆️" else "⬇️"} ${String.format(Locale.US, "$%.6f", a.threshold)}",
                                                    fontSize = 9.sp, color = WBlue, fontWeight = FontWeight.Bold
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Button(
                                                        onClick = {
                                                            editingAlertId = a.id
                                                            above = a.above
                                                            thresholdText = a.threshold.toString()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = WCard),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) { Text("✏️", fontSize = 8.sp) }
                                                    Button(
                                                        onClick = {
                                                            WatchlistStore.removeAlert(context, group.id, coin.id, a.id)
                                                            reload()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = WCard),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) { Text("🗑️", fontSize = 8.sp) }
                                                }
                                            }
                                        }
                                    }

                                    // ---------- هشدارهای فعال‌شده ----------
                                    if (triggeredAlerts.isNotEmpty()) {
                                        Text("🔔 فعال‌شده:", fontSize = 9.sp, color = WGold, fontWeight = FontWeight.Bold)
                                        triggeredAlerts.sortedByDescending { it.triggeredAt ?: 0L }.forEach { a ->
                                            val timeFmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "${if (a.above) "⬆️" else "⬇️"} ${String.format(Locale.US, "$%.6f", a.threshold)} @ ${a.triggeredPrice?.let { String.format(Locale.US, "$%.6f", it) } ?: "—"}",
                                                        fontSize = 8.sp, color = WGold
                                                    )
                                                    Text(
                                                        a.triggeredAt?.let { timeFmt.format(Date(it)) } ?: "",
                                                        fontSize = 7.sp, color = WGray
                                                    )
                                                }
                                                Button(
                                                    onClick = {
                                                        WatchlistStore.removeAlert(context, group.id, coin.id, a.id)
                                                        reload()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = WRed.copy(alpha = 0.2f)),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) { Text("🗑️", fontSize = 8.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
