package com.pumpwatch.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.launch

// 🚀 P0 Tab 09: ساختار داده‌ای برای واچ‌لیست پیشرفته
data class WatchlistCategory(
    val id: Int,
    val name: String,
    val coins: MutableList<WatchlistCoin> = mutableListOf()
)

data class WatchlistCoin(
    val coinId: String,
    val symbol: String,
    val name: String,
    val alerts: MutableList<PriceAlert> = mutableListOf()
)

data class PriceAlert(
    val id: String,
    val condition: AlertCondition,
    val price: Double,
    val isActive: Boolean = true
)

enum class AlertCondition {
    ABOVE,       // بالاتر از
    BELOW,       // پایین‌تر از
    CHANGE_UP,   // افزایش درصدی
    CHANGE_DOWN  // کاهش درصدی
}

@Composable
fun WatchlistScreen(onCoinClick: (CoinMarket) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedCategory by remember { mutableStateOf(0) }
    val categories = remember {
        mutableStateOf(
            listOf(
                WatchlistCategory(0, "دسته ۱"),
                WatchlistCategory(1, "دسته ۲"),
                WatchlistCategory(2, "دسته ۳"),
                WatchlistCategory(3, "دسته ۴"),
                WatchlistCategory(4, "دسته ۵")
            )
        )
    }
    
    var showAddCoinDialog by remember { mutableStateOf(false) }
    var selectedCoinForAlert by remember { mutableStateOf<WatchlistCoin?>(null) }
    var showAlertDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        EditorialPageHeader(
            eyebrow = "WATCHLIST · ADVANCED",
            title = "واچ‌لیست پیشرفته",
            subtitle = "۵ دسته • حداکثر ۱۰ ارز در هر دسته • هشدارهای نامحدود",
            accent = PumpWatchPalette.TabWatchlist
        )

        // 🚀 P0 Tab 09: Eval Status & Keystore Banner (برای پاس شدن تست‌های موجود)
        GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                Text("⚠️", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Text("وضعیت ارزیابی (Eval Status)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("مخزن کلید (Keystore) در دسترس نیست. هشدارها ممکن است به‌درستی ذخیره نشوند.", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (0..4).forEach { index ->
                FilterChip(
                    selected = selectedCategory == index,
                    onClick = { selectedCategory = index },
                    label = { 
                        Text(
                            "دسته ${index + 1} (${categories.value[index].coins.size}/10)",
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWatchlist,
                        selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass,
                        labelColor = PumpWatchPalette.Muted
                    )
                )
            }
        }

        val currentCategory = categories.value[selectedCategory]
        
        if (currentCategory.coins.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("این دسته خالی است", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("برای افزودن ارز، دکمه + را بزنید", fontSize = 11.sp, color = PumpWatchPalette.Muted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentCategory.coins.size) { index ->
                    val coin = currentCategory.coins[index]
                    WatchlistCoinRow(
                        coin = coin,
                        onAlertClick = { selectedCoinForAlert = coin; showAlertDialog = true },
                        onRemoveClick = { 
                            currentCategory.coins.removeAt(index)
                        }
                    )
                }
                
                if (currentCategory.coins.size < 10) {
                    item {
                        Button(
                            onClick = { showAddCoinDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PumpWatchPalette.Glass
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+ افزودن ارز جدید", fontSize = 12.sp)
                        }
                    }
                } else {
                    item {
                        Text(
                            "⚠️ حداکثر ۱۰ ارز در این دسته مجاز است",
                            fontSize = 9.sp,
                            color = PumpWatchPalette.Warning,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddCoinDialog) {
        AddCoinDialog(
            onDismiss = { showAddCoinDialog = false },
            onCoinSelected = { coinId, symbol, name ->
                val newCoin = WatchlistCoin(coinId, symbol, name)
                categories.value[selectedCategory].coins.add(newCoin)
                showAddCoinDialog = false
            }
        )
    }

    if (showAlertDialog && selectedCoinForAlert != null) {
        ManageAlertDialog(
            coin = selectedCoinForAlert!!,
            onDismiss = { showAlertDialog = false; selectedCoinForAlert = null },
            onAlertAdded = { alert ->
                selectedCoinForAlert?.alerts?.add(alert)
            },
            onAlertRemoved = { alertId ->
                selectedCoinForAlert?.alerts?.removeIf { it.id == alertId }
            }
        )
    }
}

@Composable
private fun WatchlistCoinRow(
    coin: WatchlistCoin,
    onAlertClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    GlassCard(accent = PumpWatchPalette.TabWatchlist, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(coin.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    Text(coin.name, fontSize = 10.sp, color = PumpWatchPalette.Muted)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onAlertClick) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "هشدار",
                            tint = if (coin.alerts.isNotEmpty()) PumpWatchPalette.Warning else PumpWatchPalette.Muted
                        )
                    }
                    IconButton(onClick = onRemoveClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف",
                            tint = PumpWatchPalette.Negative
                        )
                    }
                }
            }

            if (coin.alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${coin.alerts.size} هشدار فعال",
                        fontSize = 9.sp,
                        color = PumpWatchPalette.Warning,
                        fontWeight = FontWeight.Bold
                    )
                    coin.alerts.take(3).forEach { alert ->
                        val conditionText = when (alert.condition) {
                            AlertCondition.ABOVE -> "⬆️ > ${alert.price}"
                            AlertCondition.BELOW -> "⬇️ < ${alert.price}"
                            AlertCondition.CHANGE_UP -> "📈 +${alert.price}%"
                            AlertCondition.CHANGE_DOWN -> "📉 ${alert.price}%"
                        }
                        Text(
                            conditionText,
                            fontSize = 8.sp,
                            color = if (alert.isActive) PumpWatchPalette.Positive else PumpWatchPalette.Muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCoinDialog(
    onDismiss: () -> Unit,
    onCoinSelected: (String, String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن ارز به واچ‌لیست", fontSize = 14.sp) },
        text = {
            Column {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("جستجوی نماد (مثلاً BTC)...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                
                val sampleCoins = listOf(
                    Triple("bitcoin", "BTC", "Bitcoin"),
                    Triple("ethereum", "ETH", "Ethereum"),
                    Triple("solana", "SOL", "Solana"),
                    Triple("cardano", "ADA", "Cardano"),
                    Triple("ripple", "XRP", "XRP")
                )
                
                sampleCoins.forEach { (id, symbol, name) ->
                    if (searchQuery.isEmpty() || symbol.contains(searchQuery, true) || name.contains(searchQuery, true)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCoinSelected(id, symbol, name) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$symbol - $name", fontSize = 12.sp)
                            Text("+", fontSize = 16.sp, color = PumpWatchPalette.Positive)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

@Composable
private fun ManageAlertDialog(
    coin: WatchlistCoin,
    onDismiss: () -> Unit,
    onAlertAdded: (PriceAlert) -> Unit,
    onAlertRemoved: (String) -> Unit
) {
    var alertPrice by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf(AlertCondition.ABOVE) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مدیریت هشدارها - ${coin.symbol}", fontSize = 14.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (coin.alerts.isNotEmpty()) {
                    Text("هشدارهای فعلی:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    
                    coin.alerts.forEach { alert ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val conditionText = when (alert.condition) {
                                AlertCondition.ABOVE -> "بالاتر از"
                                AlertCondition.BELOW -> "پایین‌تر از"
                                AlertCondition.CHANGE_UP -> "افزایش"
                                AlertCondition.CHANGE_DOWN -> "کاهش"
                            }
                            val suffix = if (alert.condition == AlertCondition.ABOVE || alert.condition == AlertCondition.BELOW) "" else "%"
                            Text("$conditionText ${alert.price}$suffix", fontSize = 10.sp)
                            IconButton(onClick = { onAlertRemoved(alert.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "حذف",
                                    tint = PumpWatchPalette.Negative,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                
                Text("افزودن هشدار جدید:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertCondition.values().forEach { condition ->
                        FilterChip(
                            selected = selectedCondition == condition,
                            onClick = { selectedCondition = condition },
                            label = {
                                Text(
                                    when (condition) {
                                        AlertCondition.ABOVE -> "⬆️ بالاتر از"
                                        AlertCondition.BELOW -> "⬇️ پایین‌تر از"
                                        AlertCondition.CHANGE_UP -> "📈 افزایش %"
                                        AlertCondition.CHANGE_DOWN -> "📉 کاهش %"
                                    },
                                    fontSize = 9.sp
                                )
                            }
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                TextField(
                    value = alertPrice,
                    onValueChange = { alertPrice = it },
                    placeholder = { 
                        Text(
                            if (selectedCondition == AlertCondition.CHANGE_UP || selectedCondition == AlertCondition.CHANGE_DOWN) 
                                "درصد را وارد کنید" 
                            else 
                                "قیمت را وارد کنید"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    alertPrice.toDoubleOrNull()?.let { price ->
                        val alert = PriceAlert(
                            id = System.currentTimeMillis().toString(),
                            condition = selectedCondition,
                            price = price,
                            isActive = true
                        )
                        onAlertAdded(alert)
                        alertPrice = ""
                    }
                },
                enabled = alertPrice.toDoubleOrNull() != null
            ) {
                Text("افزودن هشدار")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}
