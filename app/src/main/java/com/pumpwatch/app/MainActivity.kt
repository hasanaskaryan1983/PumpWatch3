package com.pumpwatch.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.ContractRef
import com.pumpwatch.app.data.HistoricalUniverseRepository
import com.pumpwatch.app.data.MarketMeta
import com.pumpwatch.app.data.NetErr
import com.pumpwatch.app.data.NetError
import com.pumpwatch.app.data.ServedFrom
import com.pumpwatch.app.data.WatchlistMigration
import com.pumpwatch.app.data.cmcUrl
import com.pumpwatch.app.data.platformContractRef
import com.pumpwatch.app.ui.FuturesWorkspace
import com.pumpwatch.app.ui.CosmicBackdrop
import com.pumpwatch.app.ui.EditorialPageHeader
import com.pumpwatch.app.ui.MarketPulseHeader
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.PumpDumpMark
import com.pumpwatch.app.ui.PumpWatchPalette
import com.pumpwatch.app.ui.SpotWorkspace
import com.pumpwatch.app.worker.MonitorScheduler
import com.pumpwatch.app.worker.MonitorWorker
import com.pumpwatch.app.worker.SignalScannerWorker
import com.pumpwatch.app.worker.TraderMonitorScheduler
import com.pumpwatch.app.worker.WhaleMemeWorker
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

private val SpotAccent = PumpWatchPalette.Accent
private val FuturesAccent = PumpWatchPalette.Negative
private val FuturesModeAccent = PumpWatchPalette.Info
private val DarkBackground = PumpWatchPalette.Background
private val DarkSurface = PumpWatchPalette.Surface
private val DarkCard = PumpWatchPalette.Glass
private val TextPrimary = PumpWatchPalette.Text
private val TextSecondary = PumpWatchPalette.Muted
private val ContractBlue = PumpWatchPalette.Info
private val ContractGold = PumpWatchPalette.Warning
private val FreshGreen = PumpWatchPalette.Positive
private val FreshYellow = ContractGold
private val FreshRed = FuturesAccent
private val FreshGray = TextSecondary

class MainActivity : ComponentActivity() {

    companion object {
        private const val SIGNAL_SCANNER_WORK_NAME = "SignalScanner"
        private const val WHALE_MEME_WORK_NAME = "WhaleMeme"
        private const val LEGACY_WATCHLIST_WORK_NAME = "WatchlistAlerts"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val prefs = getSharedPreferences("pumpwatch_prefs", 0)
        prefs.edit().putBoolean("notifications_granted", granted).apply()
        if (!granted) {
            Toast.makeText(
                this,
                "برای دریافت هشدارها، لطفاً مجوز اعلان را از تنظیمات فعال کنید",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        MonitorScheduler.start(this)
        scheduleSignalScanner()
        scheduleWhaleMemeWorker()
        WorkManager.getInstance(this).cancelUniqueWork(LEGACY_WATCHLIST_WORK_NAME)
        TraderMonitorScheduler.start(this)
        WatchlistMigration.migrateIfNeeded(this)
        setContent {
            PumpWatchTheme {
                MainApp(onModeChanged = {
                    MonitorScheduler.start(this)
                    scheduleSignalScanner()
                    scheduleWhaleMemeWorker()
                    TraderMonitorScheduler.start(this)
                })
            }
        }
    }

    private fun scheduleSignalScanner() {
        val prefs = getSharedPreferences("pumpwatch_prefs", 0)
        val currentMode = prefs.getString("mode", "SPOT") ?: "SPOT"
        val inputData = Data.Builder()
            .putString(MonitorWorker.KEY_MODE, currentMode)
            .build()
        val scanRequest = PeriodicWorkRequestBuilder<SignalScannerWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SIGNAL_SCANNER_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            scanRequest
        )
    }

    private fun scheduleWhaleMemeWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<WhaleMemeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WHALE_MEME_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

@Composable
fun PumpWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SpotAccent,
            onPrimary = PumpWatchPalette.Ink,
            background = DarkBackground,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
            surfaceVariant = DarkCard,
            onSurfaceVariant = TextSecondary,
            outline = PumpWatchPalette.Border,
            tertiary = FuturesModeAccent,
            secondaryContainer = DarkCard,
            onSecondaryContainer = TextPrimary,
            error = FuturesAccent
        ),
        content = content
    )
}

@Composable
fun MainApp(onModeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var isFutures by remember { mutableStateOf(prefs.getString("mode", "SPOT") == "FUTURES") }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    var selectedCoin by remember { mutableStateOf<CoinMarket?>(null) }

    if (!onboarded) {
        OnboardingScreen(onDone = {
            prefs.edit().putBoolean("onboarded", true).apply()
            onboarded = true
        })
        return
    }

    val accent = if (isFutures) FuturesModeAccent else SpotAccent
    CosmicBackdrop(accent = accent) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = DarkSurface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, PumpWatchPalette.Border.copy(alpha = 0.45f)),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PumpDumpMark(accent)
                        Column {
                            Text("PumpDump", fontSize = 17.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            Text(
                                if (isFutures) "PERPETUALS · RISK FIRST" else "MARKET INTELLIGENCE",
                                fontSize = 8.sp,
                                letterSpacing = 1.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .background(DarkBackground, RoundedCornerShape(14.dp))
                            .border(1.dp, PumpWatchPalette.Border.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (isFutures) {
                                    isFutures = false
                                    prefs.edit().putString("mode", "SPOT").apply()
                                    onModeChanged()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (!isFutures) SpotAccent else Color.Transparent,
                                contentColor = if (!isFutures) PumpWatchPalette.Ink else TextSecondary
                            ),
                            modifier = Modifier.border(
                                1.dp,
                                if (!isFutures) SpotAccent.copy(alpha = 0.42f) else Color.Transparent,
                                RoundedCornerShape(11.dp)
                            ),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "اسپات",
                                color = if (!isFutures) PumpWatchPalette.Ink else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                if (!isFutures) {
                                    isFutures = true
                                    prefs.edit().putString("mode", "FUTURES").apply()
                                    onModeChanged()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (isFutures) FuturesModeAccent else Color.Transparent,
                                contentColor = if (isFutures) PumpWatchPalette.Ink else TextSecondary
                            ),
                            modifier = Modifier.border(
                                1.dp,
                                if (isFutures) FuturesModeAccent.copy(alpha = 0.46f) else Color.Transparent,
                                RoundedCornerShape(11.dp)
                            ),
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Text(
                                "فیوچرز",
                                color = if (isFutures) PumpWatchPalette.Ink else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (isFutures) {
                    FuturesWorkspace()
                } else {
                    SpotWorkspace(onCoinClick = { selectedCoin = it })
                }
            }
        }
        if (selectedCoin != null) {
            Surface(
                color = DarkBackground,
                modifier = Modifier.fillMaxSize()
            ) {
                CoinDetailScreen(
                    coin = selectedCoin!!,
                    onBack = { selectedCoin = null }
                )
            }
        }
    }
}

@Composable
private fun ContractRow(ctx: Context, contractRef: ContractRef?) {
    if (contractRef == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text("⛓️ بومی — بدون کانترکت", fontSize = 9.sp, color = TextSecondary)
        }
        return
    }
    val copied = remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    ) {
        Text(
            "${contractRef.chain}: ",
            fontSize = 9.sp,
            color = ContractGold,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (contractRef.address.length > 24) {
                "${contractRef.address.take(12)}...${contractRef.address.takeLast(8)}"
            } else {
                contractRef.address
            },
            fontSize = 9.sp,
            color = ContractBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                try {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("contract", contractRef.address))
                    copied.value = true
                } catch (_: Exception) { }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (copied.value) SpotAccent else ContractGold
            ),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) { Text(if (copied.value) "✅" else "📋 کپی", fontSize = 9.sp, color = PumpWatchPalette.Ink) }
    }
}

@Composable
private fun FreshnessBadge(meta: MarketMeta) {
    val (emoji, color, text) = when {
        meta.observedAtMs <= 0L -> Triple("⚪", FreshGray, "نامشخص")
        meta.servedFrom == ServedFrom.DISK_CACHE -> Triple("🟠", FreshYellow, "آفلاین (${meta.ageSec() / 60}د)")
        meta.ageSec() <= 130 -> Triple("🟢", FreshGreen, "زنده")
        meta.ageSec() <= 1800 -> Triple("🟡", FreshYellow, "کش (${meta.ageSec() / 60}د)")
        else -> Triple("🔴", FreshRed, "مانده (${meta.ageSec() / 60}د)")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            "$emoji $text",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MarketScreen(onCoinClick: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var platformMap by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var meta by remember { mutableStateOf(MarketMeta(0L, ServedFrom.UNKNOWN, 0)) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val mode = prefs.getString("mode", "SPOT")
                if (mode == "FUTURES") {
                    coins = ApiClient.getTop100Coins()
                } else {
                    coins = ApiClient.getQuickCoins()
                    loading = false
                    try {
                        val full = ApiClient.getTop1000Coins()
                        if (full.size > coins.size) coins = full
                    } catch (e: Exception) {
                        NetErr.log("MarketScreen", "coingecko/coins/markets?page=1..4", null, e)
                    }
                }
                meta = ApiClient.marketMeta()
                platformMap = try { ApiClient.getPlatformMap() } catch (_: Exception) { emptyMap() }
                if (coins.size > 100) {
                    HistoricalUniverseRepository.recordSnapshot(context, coins)
                }
            } catch (e: Exception) {
                NetErr.log("MarketScreen", "coingecko/coins/markets", null, e)
                errorMsg = NetErr.msg(e)
            } finally {
                loading = false
            }
            if (errorMsg == null && coins.isEmpty()) {
                NetErr.logEmpty("MarketScreen", "coingecko/coins/markets", null)
                errorMsg = NetErr.msg(NetError.EmptyData)
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val shown = if (query.isBlank()) coins
    else coins.filter { it.symbol.contains(query, true) || it.name.contains(query, true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBackground),
        contentPadding = PaddingValues(start = 16.dp, top = 17.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            EditorialPageHeader(
                eyebrow = "MARKET LETTER  /  GLOBAL",
                title = "بازار، بی‌واسطه",
                subtitle = "ریتم امروز دارایی‌های دیجیتال",
                accent = SpotAccent,
                trailing = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        TextButton(onClick = { load() }, enabled = !loading, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(if (loading) "در حال به‌روزرسانی" else "↻  تازه‌سازی", fontSize = 10.sp, color = SpotAccent)
                        }
                        FreshnessBadge(meta)
                    }
                }
            )
        }
        item { MarketPulseHeader() }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("جست‌وجوی دارایی یا نماد", fontSize = 11.sp, color = TextSecondary) },
                shape = RoundedCornerShape(15.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    disabledContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = SpotAccent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("بازار تحت نظر", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("قیمت و جایگاه در بازهٔ ۴ ساعت", fontSize = 9.sp, color = TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                Text("${shown.size} دارایی", fontSize = 9.sp, color = SpotAccent, fontWeight = FontWeight.Bold)
            }
        }
        when {
            loading && coins.isEmpty() -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(136.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = SpotAccent) }
            }
            errorMsg != null && coins.isEmpty() -> item {
                Surface(color = DarkSurface, shape = RoundedCornerShape(18.dp)) {
                    Text(
                        errorMsg ?: "",
                        color = FuturesAccent,
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> items(shown) { coin ->
                val contractRef = platformContractRef(platformMap, coin.id)
                CoinCard(coin = coin, contractRef = contractRef, onClick = { onCoinClick(coin) })
            }
        }
    }
}

@Composable
fun CoinCard(coin: CoinMarket, contractRef: ContractRef?, onClick: () -> Unit) {
    val context = LocalContext.current
    val change = coin.price_change_percentage_24h
    val changeText = if (change == null) "—" else String.format(Locale.US, "%+.2f%%", change)
    val changeColor = when {
        change == null -> TextSecondary
        change >= 0 -> PumpWatchPalette.Positive
        else -> FuturesAccent
    }
    val rankText = coin.market_cap_rank?.let { "#$it" } ?: "#—"
    val rangeLow = coin.low24h
    val rangeHigh = coin.high24h
    val hasRange = rangeLow != null && rangeHigh != null && rangeHigh > rangeLow
    Surface(
        color = PumpWatchPalette.GlassDeep,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, PumpWatchPalette.Border.copy(alpha = 0.34f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = SpotAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(15.dp)) {
                    Text(
                        coin.symbol.take(1).uppercase(Locale.US),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = SpotAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(coin.symbol.uppercase(Locale.US), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        Text("· $rankText", color = TextSecondary, fontSize = 9.sp)
                    }
                    Text("${coin.name}  ·  ${fmtMarketCap(coin.market_cap)}", color = TextSecondary, fontSize = 10.sp, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtPrice(coin.current_price), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    Surface(color = changeColor.copy(alpha = 0.11f), shape = RoundedCornerShape(8.dp)) {
                        Text(changeText, color = changeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
            if (hasRange) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("بازهٔ ۲۴ ساعت", color = TextSecondary, fontSize = 9.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${fmtPrice(rangeLow!!)}  —  ${fmtPrice(rangeHigh!!)}", color = TextSecondary, fontSize = 9.sp)
                    }
                    MarketRangeBar(rangeLow!!, rangeHigh!!, coin.current_price, changeColor)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ارزش بازار", color = TextSecondary, fontSize = 9.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(fmtMarketCap(coin.market_cap), color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    MarketLink(coin, context)
                }
            }
            if (hasRange) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("ارزش بازار · ${fmtMarketCap(coin.market_cap)}", color = TextSecondary, fontSize = 9.sp)
                    Spacer(Modifier.weight(1f))
                    MarketLink(coin, context)
                }
            }
            if (contractRef != null) ContractRow(context, contractRef)
        }
    }
}

@Composable
private fun MarketRangeBar(low: Double, high: Double, current: Double, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        val inset = 4.dp.toPx()
        val y = size.height / 2f
        val end = size.width - inset
        val position = (inset + (end - inset) * ((current - low) / (high - low)).toFloat().coerceIn(0f, 1f))
        drawLine(PumpWatchPalette.Border, Offset(inset, y), Offset(end, y), 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color.copy(alpha = 0.72f), Offset(inset, y), Offset(position, y), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(TextPrimary, 3.dp.toPx(), Offset(position, y))
    }
}

@Composable
private fun MarketLink(coin: CoinMarket, context: Context) {
    Text(
        "↗",
        color = ContractBlue,
        fontSize = 16.sp,
        modifier = Modifier.clickable {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cmcUrl(coin.id))))
            } catch (_: Exception) { }
        }.padding(start = 8.dp, top = 1.dp, bottom = 1.dp)
    )
}

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}

private fun fmtMarketCap(cap: Double?): String = when {
    cap == null -> "—"
    cap >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", cap / 1_000_000_000)
    cap >= 1_000_000 -> String.format(Locale.US, "$%.1fM", cap / 1_000_000)
    else -> String.format(Locale.US, "$%.0f", cap)
}
