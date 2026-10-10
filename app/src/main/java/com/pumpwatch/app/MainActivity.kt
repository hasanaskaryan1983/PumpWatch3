package com.pumpwatch.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.pumpwatch.app.data.*
import com.pumpwatch.app.ui.*
import com.pumpwatch.app.worker.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

private val SpotAccent = Color(0xFF00E676)
private val FuturesAccent = Color(0xFFFF5252)
private val HubAccent = Color(0xFFFFD700)
private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)
private val FreshGreen = Color(0xFF00E676)
private val FreshYellow = Color(0xFFFFC107)
private val FreshRed = Color(0xFFFF5252)
private val FreshGray = Color(0xFF8B949E)

class MainActivity : ComponentActivity() {
    companion object {
        private const val SIGNAL_SCANNER_WORK_NAME = "SignalScanner"
        private const val WHALE_MEME_WORK_NAME = "WhaleMeme"
        private const val LEGACY_WATCHLIST_WORK_NAME = "WatchlistAlerts"
    }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        getSharedPreferences("pumpwatch_prefs", 0).edit().putBoolean("notifications_granted", granted).apply()
        if (!granted) Toast.makeText(this, "برای دریافت هشدارها، لطفاً مجوز اعلان را از تنظیمات فعال کنید", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        MonitorScheduler.start(this); scheduleSignalScanner(); scheduleWhaleMemeWorker()
        WorkManager.getInstance(this).cancelUniqueWork(LEGACY_WATCHLIST_WORK_NAME)
        TraderMonitorScheduler.start(this); WatchlistMigration.migrateIfNeeded(this)
        setContent { PumpWatchTheme { MainApp(onModeChanged = { MonitorScheduler.start(this); scheduleSignalScanner(); scheduleWhaleMemeWorker(); TraderMonitorScheduler.start(this) }) } }
    }
    private fun scheduleSignalScanner() {
        val mode = getSharedPreferences("pumpwatch_prefs", 0).getString("mode", "SPOT") ?: "SPOT"
        val req = PeriodicWorkRequestBuilder<SignalScannerWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putString(MonitorWorker.KEY_MODE, mode).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(SIGNAL_SCANNER_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
    private fun scheduleWhaleMemeWorker() {
        val req = PeriodicWorkRequestBuilder<WhaleMemeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(WHALE_MEME_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

@Composable
fun PumpWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = SpotAccent, onPrimary = Color.Black, background = DarkBackground, onBackground = TextPrimary, surface = DarkSurface, onSurface = TextPrimary, secondaryContainer = DarkCard, onSecondaryContainer = TextPrimary, error = FuturesAccent), content = content)
}

private enum class AppMode { SPOT, FUTURES, HUB }

@Composable
fun MainApp(onModeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var appMode by remember { mutableStateOf(when (prefs.getString("mode", "SPOT")) { "FUTURES" -> AppMode.FUTURES; "HUB" -> AppMode.HUB; else -> AppMode.SPOT }) }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    var selectedCoin by remember { mutableStateOf<CoinMarket?>(null) }

    if (!onboarded) { OnboardingScreen(onDone = { prefs.edit().putBoolean("onboarded", true).apply(); onboarded = true }); return }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 24.sp); Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("dump", fontSize = 20.sp, fontWeight = FontWeight.Black, color = FuturesAccent)
                            Text("pump", fontSize = 20.sp, fontWeight = FontWeight.Black, color = SpotAccent)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Row(Modifier.fillMaxWidth().background(DarkCard, RoundedCornerShape(10.dp)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ModeButton("اسپات", appMode == AppMode.SPOT, SpotAccent) { appMode = AppMode.SPOT; prefs.edit().putString("mode","SPOT").apply(); onModeChanged() }
                        ModeButton("فیوچرز", appMode == AppMode.FUTURES, FuturesAccent) { appMode = AppMode.FUTURES; prefs.edit().putString("mode","FUTURES").apply(); onModeChanged() }
                        ModeButton("هاب", appMode == AppMode.HUB, HubAccent) { appMode = AppMode.HUB; prefs.edit().putString("mode","HUB").apply() }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                Crossfade(targetState = appMode, animationSpec = tween(320)) { mode ->   // ✅ ترنزیشن نرم
                    when (mode) {
                        AppMode.SPOT -> SpotWorkspace(onCoinClick = { selectedCoin = it })
                        AppMode.FUTURES -> FuturesWorkspace()
                        AppMode.HUB -> OrbitalHub(onTabClick = { tabId ->
                            HubNavigation.pendingTab = tabId
                            when {
                                tabId.startsWith("futures_") -> { appMode = AppMode.FUTURES; prefs.edit().putString("mode","FUTURES").apply() }
                                else -> { appMode = AppMode.SPOT; prefs.edit().putString("mode","SPOT").apply() }
                            }
                        })
                    }
                }
            }
        }
        if (selectedCoin != null) Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) { CoinDetailScreen(coin = selectedCoin!!, onBack = { selectedCoin = null }) }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(containerColor = if (selected) accent else Color.Transparent), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = if (selected) Color.Black else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun FreshnessBadge(meta: MarketMeta) {
    val (emoji, color, text) = when {
        meta.observedAtMs <= 0L -> Triple("⚪", FreshGray, "نامشخص")
        meta.servedFrom == ServedFrom.DISK_CACHE -> Triple("🟠", FreshYellow, "آفلاین (${meta.ageSec()/60}د)")
        meta.ageSec() <= 130 -> Triple("🟢", FreshGreen, "زنده")
        meta.ageSec() <= 1800 -> Triple("🟡", FreshYellow, "کش (${meta.ageSec()/60}د)")
        else -> Triple("🔴", FreshRed, "مانده (${meta.ageSec()/60}د)")
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) { Text("$emoji $text", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
}

@Composable
fun MarketScreen(onCoinClick: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var meta by remember { mutableStateOf(MarketMeta(0L, ServedFrom.UNKNOWN, 0)) }
    val scope = rememberCoroutineScope()
    fun load() { scope.launch {
        loading = true; errorMsg = null
        try {
            if ((prefs.getString("mode","SPOT") ?: "SPOT") == "FUTURES") coins = ApiClient.getTop100Coins()
            else { coins = ApiClient.getQuickCoins(); loading = false; try { val f = ApiClient.getTop1000Coins(); if (f.size > coins.size) coins = f } catch (e: Exception) { NetErr.log("MarketScreen","coingecko/coins/markets?page=1..4",null,e) } }
            meta = ApiClient.marketMeta(); if (coins.size > 100) HistoricalUniverseRepository.recordSnapshot(context, coins)
        } catch (e: Exception) { NetErr.log("MarketScreen","coingecko/coins/markets",null,e); errorMsg = NetErr.msg(e) } finally { loading = false }
        if (errorMsg == null && coins.isEmpty()) { NetErr.logEmpty("MarketScreen","coingecko/coins/markets",null); errorMsg = NetErr.msg(NetError.EmptyData) }
    } }
    LaunchedEffect(Unit) { load() }
    val shown = if (query.isBlank()) coins else coins.filter { it.symbol.contains(query,true) || it.name.contains(query,true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("قیمت لحظه‌ای", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
            Spacer(Modifier.width(8.dp)); FreshnessBadge(meta); Spacer(Modifier.weight(1f))
            TextButton(onClick = { load() }, enabled = !loading) { Text(if (loading) "..." else "بروزرسانی", color = TabPalette.Market) }
        }
        Column(Modifier.padding(horizontal=16.dp, vertical=4.dp)) { TextField(value=query, onValueChange={query=it}, modifier=Modifier.fillMaxWidth(), placeholder={Text("🔍 جستجوی ارز (نماد یا اسم)...", fontSize=12.sp, color=TextSecondary)}, shape=RoundedCornerShape(12.dp), colors=TextFieldDefaults.colors(focusedContainerColor=DarkCard, unfocusedContainerColor=DarkCard, focusedTextColor=Color(0xFFE6EDF3), unfocusedTextColor=Color(0xFFE6EDF3))) }
        Column(Modifier.padding(horizontal=16.dp, vertical=4.dp)) { MarketPulseHeader() }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { CircularProgressIndicator(color=TabPalette.Market) }
            errorMsg != null -> Box(Modifier.fillMaxSize(), contentAlignment=Alignment.Center) { Text(errorMsg?:"", color=FuturesAccent, modifier=Modifier.padding(16.dp), textAlign=TextAlign.Center) }
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                items(shown) { coin -> CoinRow(c=CoinRowView(symbol=coin.symbol.uppercase(Locale.US), price=coin.current_price, change24h=coin.price_change_percentage_24h, spark=emptyList(), source="CoinGecko", ageSec=meta.ageSec()), tabKey="market") }
            }
        }
    }
}
