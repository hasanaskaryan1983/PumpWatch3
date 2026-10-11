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
import androidx.compose.ui.graphics.Color
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
import com.pumpwatch.app.data.HistoricalUniverseRepository
import com.pumpwatch.app.data.MarketMeta
import com.pumpwatch.app.data.NetErr
import com.pumpwatch.app.data.NetError
import com.pumpwatch.app.data.ServedFrom
import com.pumpwatch.app.data.WatchlistMigration
import com.pumpwatch.app.ui.FuturesWorkspace
import com.pumpwatch.app.ui.HomeDashboard
import com.pumpwatch.app.ui.HubNavigation
import com.pumpwatch.app.ui.MarketPulseHeader
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.SpotWorkspace
import com.pumpwatch.app.ui.components.CoinRow
import com.pumpwatch.app.ui.components.CoinRowView
import com.pumpwatch.app.ui.design.TabPalette
import com.pumpwatch.app.worker.MonitorScheduler
import com.pumpwatch.app.worker.MonitorWorker
import com.pumpwatch.app.worker.SignalScannerWorker
import com.pumpwatch.app.worker.TraderMonitorScheduler
import com.pumpwatch.app.worker.WhaleMemeWorker
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

private val SpotAccent = Color(0xFF00E676)
private val FuturesAccent = Color(0xFFFF5252)
private val StudioAccent = Color(0xFFE8833A)
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val prefs = getSharedPreferences("pumpwatch_prefs", 0)
        prefs.edit().putBoolean("notifications_granted", granted).apply()
        if (!granted) {
            Toast.makeText(this, "برای دریافت هشدارها، لطفاً مجوز اعلان را از تنظیمات فعال کنید", Toast.LENGTH_LONG).show()
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
        val currentMode = prefs.getString("mode", "STUDIO") ?: "STUDIO"
        val inputData = Data.Builder().putString(MonitorWorker.KEY_MODE, currentMode).build()
        val scanRequest = PeriodicWorkRequestBuilder<SignalScannerWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(SIGNAL_SCANNER_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, scanRequest)
    }

    private fun scheduleWhaleMemeWorker() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val workRequest = PeriodicWorkRequestBuilder<WhaleMemeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(WHALE_MEME_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)
    }
}

@Composable
fun PumpWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SpotAccent, onPrimary = Color.Black, background = DarkBackground,
            onBackground = TextPrimary, surface = DarkSurface, onSurface = TextPrimary,
            secondaryContainer = DarkCard, onSecondaryContainer = TextPrimary, error = FuturesAccent
        ),
        content = content
    )
}

// 🚀 قدم ۲: هاب مداری از صفحهٔ اصلی رفت؛ استودیو پیش‌فرض شد. هیچ فایلی حذف نشد.
private enum class AppMode { STUDIO, SPOT, FUTURES }

@Composable
fun MainApp(onModeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }

    var appMode by remember {
        mutableStateOf(
            when (prefs.getString("mode", "STUDIO")) {
                "SPOT" -> AppMode.SPOT
                "FUTURES" -> AppMode.FUTURES
                else -> AppMode.STUDIO
            }
        )
    }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("onboarded", false)) }
    var selectedCoin by remember { mutableStateOf<CoinMarket?>(null) }

    if (!onboarded) {
        OnboardingScreen(onDone = {
            prefs.edit().putBoolean("onboarded", true).apply()
            onboarded = true
        })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = DarkSurface, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("dump", fontSize = 20.sp, fontWeight = FontWeight.Black, color = FuturesAccent)
                            Text("pump", fontSize = 20.sp, fontWeight = FontWeight.Black, color = SpotAccent)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(DarkCard, RoundedCornerShape(10.dp)).padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModeButton("استودیو", appMode == AppMode.STUDIO, StudioAccent) {
                            appMode = AppMode.STUDIO; prefs.edit().putString("mode", "STUDIO").apply()
                        }
                        ModeButton("اسپات", appMode == AppMode.SPOT, SpotAccent) {
                            appMode = AppMode.SPOT; prefs.edit().putString("mode", "SPOT").apply(); onModeChanged()
                        }
                        ModeButton("فیوچرز", appMode == AppMode.FUTURES, FuturesAccent) {
                            appMode = AppMode.FUTURES; prefs.edit().putString("mode", "FUTURES").apply(); onModeChanged()
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Crossfade(targetState = appMode, animationSpec = tween(320)) { mode ->
                    when (mode) {
                        AppMode.STUDIO -> HomeDashboard(onTabClick = { tabId ->
                            HubNavigation.pendingTab = tabId
                            when {
                                tabId.startsWith("futures_") -> {
                                    appMode = AppMode.FUTURES; prefs.edit().putString("mode", "FUTURES").apply()
                                }
                                else -> {
                                    appMode = AppMode.SPOT; prefs.edit().putString("mode", "SPOT").apply()
                                }
                            }
                        })
                        AppMode.SPOT -> SpotWorkspace(onCoinClick = { selectedCoin = it })
                        AppMode.FUTURES -> FuturesWorkspace()
                    }
                }
            }
        }

        if (selectedCoin != null) {
            Surface(color = DarkBackground, modifier = Modifier.fillMaxSize()) {
                CoinDetailScreen(coin = selectedCoin!!, onBack = { selectedCoin = null })
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(containerColor = if (selected) accent else Color.Transparent),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.Black else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text("$emoji $text", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
    var meta by remember { mutableStateOf(MarketMeta(0L, ServedFrom.UNKNOWN, 0)) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true; errorMsg = null
            try {
                val mode = prefs.getString("mode", "STUDIO")
                if (mode == "FUTURES") {
                    coins = ApiClient.getTop100Coins()
                } else {
                    coins = ApiClient.getQuickCoins(); loading = false
                    try { val full = ApiClient.getTop1000Coins(); if (full.size > coins.size) coins = full }
                    catch (e: Exception) { NetErr.log("MarketScreen", "coingecko/coins/markets?page=1..4", null, e) }
                }
                meta = ApiClient.marketMeta()
                if (coins.size > 100) HistoricalUniverseRepository.recordSnapshot(context, coins)
            } catch (e: Exception) {
                NetErr.log("MarketScreen", "coingecko/coins/markets", null, e); errorMsg = NetErr.msg(e)
            } finally { loading = false }
            if (errorMsg == null && coins.isEmpty()) {
                NetErr.logEmpty("MarketScreen", "coingecko/coins/markets", null); errorMsg = NetErr.msg(NetError.EmptyData)
            }
        }
    }

    LaunchedEffect(Unit) { load() }
    val shown = if (query.isBlank()) coins else coins.filter { it.symbol.contains(query, true) || it.name.contains(query, true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("قیمت لحظه‌ای", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
            Spacer(Modifier.width(8.dp)); FreshnessBadge(meta); Spacer(Modifier.weight(1f))
            TextButton(onClick = { load() }, enabled = !loading) { Text(if (loading) "..." else "بروزرسانی", color = TabPalette.Market) }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextField(
                value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("🔍 جستجوی ارز (نماد یا اسم)...", fontSize = 12.sp, color = TextSecondary) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = DarkCard, unfocusedContainerColor = DarkCard, focusedTextColor = Color(0xFFE6EDF3), unfocusedTextColor = Color(0xFFE6EDF3))
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { MarketPulseHeader() }
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TabPalette.Market) }
            errorMsg != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(errorMsg ?: "", color = FuturesAccent, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center) }
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown) { coin ->
                    CoinRow(
                        c = CoinRowView(symbol = coin.symbol.uppercase(Locale.US), price = coin.current_price, change24h = coin.price_change_percentage_24h, spark = emptyList(), source = "CoinGecko", ageSec = meta.ageSec()),
                        tabKey = "market"
                    )
                }
            }
        }
    }
}
