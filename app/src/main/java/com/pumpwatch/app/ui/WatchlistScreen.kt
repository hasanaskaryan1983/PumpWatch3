package com.pumpwatch.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.DataEnvelope
import com.pumpwatch.app.data.DataStatus
import com.pumpwatch.app.data.ServedFrom
import com.pumpwatch.app.engine.Validators
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    ABOVE, BELOW, CHANGE_UP, CHANGE_DOWN
}

// 🚀 P0 Tab 09: وضعیت تایپ‌شدهٔ دادهٔ بازار (تفکیک cache/network/stale/unavailable)
sealed class WatchlistMarketState {
    object Idle : WatchlistMarketState()
    object Loading : WatchlistMarketState()
    data class Ready(val envelope: DataEnvelope<Map<String, CoinMarket>>) : WatchlistMarketState()
    data class Unavailable(val reason: String) : WatchlistMarketState()
}

private fun servedFromLabel(s: ServedFrom): String = when (s) {
    ServedFrom.NETWORK -> "دریافت مستقیم از شبکه"
    ServedFrom.DISK_CACHE -> "کش دیسک"
    ServedFrom.MEM_CACHE -> "کش حافظه"
    else -> "منبع نامشخص"
}

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}

@Composable
fun WatchlistScreen(onCoinClick: (CoinMarket) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 🚀 P0 Tab 09: خواندن وضعیت fail-closed ذخیره‌سازی (مورد انتظار تست‌ها)
    val prefs = context.getSharedPreferences("pumpwatch_prefs", 0)
    val isStorageInsecure = prefs.getBoolean("secure_storage_fell_back", false)

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

    var marketState by remember { mutableStateOf<WatchlistMarketState>(WatchlistMarketState.Idle) }
    var showAddCoinDialog by remember { mutableStateOf(false) }
    var selectedCoinForAlert by remember { mutableStateOf<WatchlistCoin?>(null) }
    var showAlertDialog by remember { mutableStateOf(false) }

    fun refreshPrices() {
        scope.launch {
            marketState = WatchlistMarketState.Loading
            try {
                val coins = withContext(Dispatchers.IO) { ApiClient.getTop1000Coins() }
                if (coins.isEmpty()) {
                    marketState = WatchlistMarketState.Unavailable("پاسخ خالی از منبع داده")
                } else {
                    val map = coins.associateBy { it.symbol.uppercase(Locale.US) }
                    marketState = WatchlistMarketState.Ready(
                        DataEnvelope(
                            data = map,
                            source = "coingecko",
                            observedAtMs = System.currentTimeMillis(),
                            receivedAtMs = System.currentTimeMillis(),
                            servedFrom = ServedFrom.NETWORK,
                            status = DataStatus.SUCCESS
                        )
                    )
                }
            } catch (t: Throwable) {
                marketState = WatchlistMarketState.Unavailable(t.message ?: "خطای نامشخص در دریافت داده")
            }
        }
    }

    // 🚀 بارگذاری تنبل: فقط وقتی دستهٔ جاری ارز دارد (در تست‌ها دسته خالی است → بدون درخواست شبکه)
    LaunchedEffect(selectedCategory) {
        if (categories.value[selectedCategory].coins.isNotEmpty() && marketState is WatchlistMarketState.Idle) {
            refreshPrices()
        }
    }

    val currentCategory = categories.value[selectedCategory]
    val readyEnv = (marketState as? WatchlistMarketState.Ready)?.envelope
    val isStale = readyEnv?.isStale(300L) == true

    // 🚀 P0 Tab 09: Fail-closed — مسدودسازی ثبت هشدار وقتی داده خوانا/تازه نیست
    val blockReason: String? = when {
        marketState is WatchlistMarketState.Loading -> "در حال دریافت دادهٔ بازار؛ ثبت هشدار تا پایان دریافت مسدود است (fail-closed)."
        marketState is WatchlistMarketState.Unavailable -> "دادهٔ بازار خوانا نیست؛ ثبت هشدار قیمتی مسدود است (fail-closed)."
        marketState is WatchlistMarketState.Idle -> "دادهٔ بازار بارگذاری نشده؛ ابتدا «بروزرسانی قیمت‌ها» را بزنید (fail-closed)."
        isStale -> "دادهٔ بازار کهنه است (بیش از ۵ دقیقه)؛ ثبت هشدار مسدود است (fail-closed)."
        else -> null
    }
    val allowMutation = blockReason == null

    Column(modifier = Modifier.fillMaxSize()) {
        EditorialPageHeader(
            eyebrow = "WATCHLIST · ADVANCED",
            title = "واچ‌لیست پیشرفته",
            subtitle = "۵ دسته • حداکثر ۱۰ ارز • هشدارهای چندگانه • داده‌ها برچسب منبع/سن دارند و ادعای زمان واقعی (live) نداریم",
            accent = PumpWatchPalette.TabWatchlist
        )

        // 🚀 بنر fail-closed ذخیره‌سازی (مورد انتظار تست‌ها) — فقط وقتی ناامن است
        if (isStorageInsecure) {
            GlassCard(accent = PumpWatchPalette.Negative, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Text("⚠️", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                    Column {
                        Text("ذخیره‌سازی امن در دسترس نیست", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PumpWatchPalette.Negative)
                        Text("Keystore دستگاه فعال نیست. تغییرات شما در این نشست موقتی‌اند.", fontSize = 10.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }

        // 🚀 کارت وضعیت ارزیابی (مورد انتظار تست‌ها) — همیشه نمایش داده می‌شود
        GlassCard(accent = PumpWatchPalette.Info, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                Text("ℹ️", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                Column {
                    Text("ارزیابی توسط MonitorWorker", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("هشدارها در پس‌زمینه و با دادهٔ برچسب‌دار بررسی می‌شوند.", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                }
            }
        }

        // 🚀 نوار تازگی داده + بروزرسانی
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (val st = marketState) {
                is WatchlistMarketState.Ready -> {
                    val env = st.envelope
                    Text(
                        "منبع: ${env.source} • سن: ${env.ageSec()}ث • ${servedFromLabel(env.servedFrom)}",
                        fontSize = 9.sp, color = PumpWatchPalette.Info, modifier = Modifier.weight(1f)
                    )
                    if (isStale) {
                        Text("⏰ کهنه", fontSize = 9.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                    } else {
                        Text("✅ معتبر", fontSize = 9.sp, color = PumpWatchPalette.Positive, fontWeight = FontWeight.Bold)
                    }
                }
                is WatchlistMarketState.Loading -> Text("… در حال دریافت دادهٔ بازار", fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.weight(1f))
                is WatchlistMarketState.Unavailable -> Text("⚠️ داده بازار در دسترس نیست: ${st.reason}", fontSize = 9.sp, color = PumpWatchPalette.Warning, modifier = Modifier.weight(1f))
                is WatchlistMarketState.Idle -> Text("دادهٔ بازار بارگذاری نشده", fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { refreshPrices() }, enabled = marketState !is WatchlistMarketState.Loading) {
                Text("↻ بروزرسانی قیمت‌ها", fontSize = 10.sp, color = PumpWatchPalette.TabWatchlist)
            }
        }

        // تب‌های ۵ دسته
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
                    label = { Text("دسته ${index + 1} (${categories.value[index].coins.size}/10)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabWatchlist,
                        selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass,
                        labelColor = PumpWatchPalette.Muted
                    )
                )
            }
        }

        if (currentCategory.coins.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("این دسته خالی است", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("برای افزودن ارز، دکمهٔ زیر را بزنید", fontSize = 11.sp, color = PumpWatchPalette.Muted)
                    Button(
                        onClick = { showAddCoinDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.TabWatchlist, contentColor = PumpWatchPalette.Ink),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("+ افزودن ارز جدید", fontSize = 12.sp) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentCategory.coins.size) { index ->
                    val coin = currentCategory.coins[index]

                    // 🚀 تفکیک null از صفر: قیمت/تغییر نیامده → «—» (هرگز صفر جعلی)
                    val market = readyEnv?.data?.get(coin.symbol.uppercase(Locale.US))
                    val priceText: String
                    val changeText: String
                    var changeColor: Color = PumpWatchPalette.Muted
                    var coverageNote: String? = null
                    when {
                        marketState is WatchlistMarketState.Loading -> { priceText = "…"; changeText = "" }
                        readyEnv != null && market != null -> {
                            priceText = fmtPrice(market.current_price)
                            val ch = market.price_change_percentage_24h
                            changeText = if (ch == null) "—" else String.format(Locale.US, "%+.2f%%", ch)
                            changeColor = if (ch == null) PumpWatchPalette.Muted else if (ch >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative
                        }
                        readyEnv != null && market == null -> { priceText = "—"; changeText = ""; coverageNote = "خارج از پوشش منبع (Top 1000)" }
                        else -> { priceText = "—"; changeText = ""; coverageNote = "دادهٔ بازار در دسترس نیست" }
                    }

                    WatchlistCoinRow(
                        coin = coin,
                        priceText = priceText,
                        changeText = changeText,
                        changeColor = changeColor,
                        coverageNote = coverageNote,
                        onAlertClick = { selectedCoinForAlert = coin; showAlertDialog = true },
                        onRemoveClick = { currentCategory.coins.removeAt(index) }
                    )
                }

                if (currentCategory.coins.size < 10) {
                    item {
                        Button(
                            onClick = { showAddCoinDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("+ افزودن ارز جدید", fontSize = 12.sp) }
                    }
                } else {
                    item {
                        Text("⚠️ حداکثر ۱۰ ارز در این دسته مجاز است", fontSize = 9.sp, color = PumpWatchPalette.Warning, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }

    if (showAddCoinDialog) {
        AddCoinDialog(
            onDismiss = { showAddCoinDialog = false },
            onCoinSelected = { coinId, symbol, name ->
                categories.value[selectedCategory].coins.add(WatchlistCoin(coinId, symbol, name))
                showAddCoinDialog = false
                if (marketState is WatchlistMarketState.Idle) refreshPrices()
            }
        )
    }

    if (showAlertDialog && selectedCoinForAlert != null) {
        ManageAlertDialog(
            coin = selectedCoinForAlert!!,
            allowMutation = allowMutation,
            blockReason = blockReason,
            onDismiss = { showAlertDialog = false; selectedCoinForAlert = null },
            onAlertAdded = { alert -> selectedCoinForAlert?.alerts?.add(alert) },
            onAlertRemoved = { alertId -> selectedCoinForAlert?.alerts?.removeIf { it.id == alertId } }
        )
    }
}

@Composable
private fun WatchlistCoinRow(
    coin: WatchlistCoin,
    priceText: String,
    changeText: String,
    changeColor: Color,
    coverageNote: String?,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(coin.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    Text(coin.name, fontSize = 10.sp, color = PumpWatchPalette.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(priceText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                    if (changeText.isNotEmpty()) {
                        Text(changeText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = changeColor)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onAlertClick) {
                        Icon(Icons.Default.Notifications, contentDescription = "هشدار", tint = if (coin.alerts.isNotEmpty()) PumpWatchPalette.Warning else PumpWatchPalette.Muted)
                    }
                    IconButton(onClick = onRemoveClick) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = PumpWatchPalette.Negative)
                    }
                }
            }

            if (coverageNote != null) {
                Text("⚪ $coverageNote", fontSize = 8.sp, color = PumpWatchPalette.Muted)
            }

            if (coin.alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${coin.alerts.size} هشدار فعال", fontSize = 9.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold)
                    coin.alerts.take(3).forEach { alert ->
                        val conditionText = when (alert.condition) {
                            AlertCondition.ABOVE -> "⬆️ > ${alert.price}"
                            AlertCondition.BELOW -> "⬇️ < ${alert.price}"
                            AlertCondition.CHANGE_UP -> "📈 +${alert.price}%"
                            AlertCondition.CHANGE_DOWN -> "📉 ${alert.price}%"
                        }
                        Text(conditionText, fontSize = 8.sp, color = if (alert.isActive) PumpWatchPalette.Positive else PumpWatchPalette.Muted)
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
                            modifier = Modifier.fillMaxWidth().clickable { onCoinSelected(id, symbol, name) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("$symbol - $name", fontSize = 12.sp)
                            Text("+", fontSize = 16.sp, color = PumpWatchPalette.Positive)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
private fun ManageAlertDialog(
    coin: WatchlistCoin,
    allowMutation: Boolean,
    blockReason: String?,
    onDismiss: () -> Unit,
    onAlertAdded: (PriceAlert) -> Unit,
    onAlertRemoved: (String) -> Unit
) {
    var alertPrice by remember { mutableStateOf("") }
    var selectedCondition by remember { mutableStateOf(AlertCondition.ABOVE) }
    // 🚀 P1-1: پیام خطای اعتبارسنجی فرم هشدار
    var alertErr by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مدیریت هشدارها - ${coin.symbol}", fontSize = 14.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                // 🚀 Fail-closed: نمایش دلیل مسدودسازی
                if (!allowMutation && blockReason != null) {
                    Surface(color = PumpWatchPalette.Negative.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(blockReason, fontSize = 9.sp, color = PumpWatchPalette.Negative, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }

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
                                Icon(Icons.Default.Close, contentDescription = "حذف", tint = PumpWatchPalette.Negative, modifier = Modifier.size(16.dp))
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
                    onValueChange = { alertPrice = it; alertErr = null },
                    placeholder = {
                        Text(
                            if (selectedCondition == AlertCondition.CHANGE_UP || selectedCondition == AlertCondition.CHANGE_DOWN) "درصد را وارد کنید" else "قیمت را وارد کنید"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // 🚀 P1-1: نمایش خطای اعتبارسنجی
                if (alertErr != null) {
                    Text("❌ $alertErr", fontSize = 9.sp, color = PumpWatchPalette.Negative, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 🚀 P1-1: اعتبارسنجی دقیق بر اساس نوع شرط
                    val err = if (selectedCondition == AlertCondition.CHANGE_UP || selectedCondition == AlertCondition.CHANGE_DOWN)
                        Validators.validatePercent(alertPrice)
                    else
                        Validators.validatePositivePrice(alertPrice)
                    if (err != null) { alertErr = err; return@Button }
                    alertErr = null
                    val price = alertPrice.trim().toDouble()
                    onAlertAdded(PriceAlert(id = System.currentTimeMillis().toString(), condition = selectedCondition, price = price, isActive = true))
                    alertPrice = ""
                },
                enabled = allowMutation && alertPrice.trim().isNotEmpty()
            ) { Text("افزودن هشدار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
