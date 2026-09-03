package com.pumpwatch.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.cmcUrl
import com.pumpwatch.app.ui.AssistantScreen
import com.pumpwatch.app.ui.BacktestScreen
import com.pumpwatch.app.ui.HistoryScreen
import com.pumpwatch.app.ui.MarketPulseHeader
import com.pumpwatch.app.ui.MemeRadarScreen
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.SignalLogScreen
import com.pumpwatch.app.ui.SmartAlertsScreen
import com.pumpwatch.app.ui.TopPicksScreen
import com.pumpwatch.app.ui.TradesScreen
import com.pumpwatch.app.ui.WhaleRadarScreen
import com.pumpwatch.app.worker.MonitorScheduler
import com.pumpwatch.app.worker.SignalScannerWorker
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

enum class Tab(val title: String, val emoji: String) {
    MARKET("بازار", "📊"),
    ALERTS("هشدار", ""),
    WHALE("نهنگ", "🐳"),
    ASSISTANT("دستیار", "🤖"),
    BACKTEST("بک‌تست", "🧪"),
    TOP("برترین", "🏆"),
    MEME("میم", "🐸"),
    LOG("سیگنال", "📓"),
    TRADES("معامله", "📈"),
    HISTORY("تاریخچه", "📚")
}

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        MonitorScheduler.start(this)

        val scanRequest = PeriodicWorkRequestBuilder<SignalScannerWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SignalScanner",
            ExistingPeriodicWorkPolicy.KEEP,
            scanRequest
        )

        setContent {
            PumpWatchTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun PumpWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentGreen,
            onPrimary = Color.Black,
            background = DarkBackground,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
            secondaryContainer = DarkCard,
            onSecondaryContainer = TextPrimary,
            error = AccentRed
        ),
        content = content
    )
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var isFutures by remember {
        mutableStateOf(prefs.getString("mode", "SPOT") == "FUTURES")
    }
    var selectedTab by remember { mutableStateOf(Tab.MARKET) }
    var selectedCoin by remember { mutableStateOf<CoinMarket?>(null) }
    var onboarded by remember {
        mutableStateOf(prefs.getBoolean("onboarded", false))
    }

    if (!onboarded) {
        OnboardingScreen(onDone = {
            prefs.edit().putBoolean("onboarded", true).apply()
            onboarded = true
        })
        return
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚀", fontSize = 26.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PumpDump",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = AccentGreen
                        )
                        Spacer(Modifier.weight(1f))
                        Surface(
                            modifier = Modifier.clickable {
                                isFutures = !isFutures
                                prefs.edit()
                                    .putString("mode", if (isFutures) "FUTURES" else "SPOT")
                                    .apply()
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isFutures) AccentRed.copy(alpha = 0.15f)
                                    else AccentGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isFutures) "فیوچرز" else "اسپات",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = if (isFutures) AccentRed else AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                bottomBar = {
                    Surface(color = DarkSurface, modifier = Modifier.height(120.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Tab.entries.take(5).forEach { tab ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedTab = tab }
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            tab.emoji,
                                            fontSize = 20.sp,
                                            color = if (selectedTab == tab) AccentGreen else TextSecondary
                                        )
                                        Text(
                                            tab.title,
                                            fontSize = 8.sp,
                                            color = if (selectedTab == tab) AccentGreen else TextSecondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Tab.entries.drop(5).forEach { tab ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedTab = tab }
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            tab.emoji,
                                            fontSize = 20.sp,
                                            color = if (selectedTab == tab) AccentGreen else TextSecondary
                                        )
                                        Text(
                                            tab.title,
                                            fontSize = 8.sp,
                                            color = if (selectedTab == tab) AccentGreen else TextSecondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (selectedTab) {
                        Tab.MARKET -> MarketScreen(onCoinClick = { selectedCoin = it })
                        Tab.ALERTS -> SmartAlertsScreen(onCoinClick = { selectedCoin = it })
                        Tab.WHALE -> WhaleRadarScreen()
                        Tab.ASSISTANT -> AssistantScreen(onOpenCoin = { selectedCoin = it })
                        Tab.BACKTEST -> BacktestScreen()
                        Tab.TOP -> TopPicksScreen(if (isFutures) "FUT" else "SPOT")
                        Tab.MEME -> MemeRadarScreen()
                        Tab.LOG -> SignalLogScreen()
                        Tab.TRADES -> TradesScreen()
                        Tab.HISTORY -> HistoryScreen(if (isFutures) "FUT" else "SPOT")
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
}

@Composable
fun MarketScreen(onCoinClick: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
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
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val shown = if (query.isBlank()) coins
    else coins.filter {
        it.symbol.contains(query, true) || it.name.contains(query, true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "قیمت لحظه‌ای",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { load() }) { Text("بروزرسانی") }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(" جستجوی ارز (نماد یا اسم)...", fontSize = 12.sp, color = TextSecondary) },
                shape = RoundedCornerShape(12.dp)
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            MarketPulseHeader()
        }

        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGreen)
            }

            errorMsg != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMsg ?: "",
                    color = AccentRed,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown) { coin ->
                    CoinCard(coin = coin, onClick = { onCoinClick(coin) })
                }
            }
        }
    }
}

@Composable
fun CoinCard(coin: CoinMarket, onClick: () -> Unit) {
    val context = LocalContext.current
    val change = coin.price_change_percentage_24h ?: 0.0
    val isUp = change >= 0
    val rank = coin.market_cap_rank ?: 0
    Surface(
        color = DarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#$rank  ${coin.symbol.uppercase(Locale.US)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(coin.name, color = TextSecondary, fontSize = 12.sp)
                Text(
                    "کپ: ${formatMarketCap(coin.market_cap)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                "📊",
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(cmcUrl(coin.id)))
                            )
                        } catch (_: Exception) { }
                    }
                    .padding(8.dp)
            )

            Spacer(Modifier.width(4.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatPrice(coin.current_price),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    String.format(Locale.US, "%+.2f%%", change),
                    color = if (isUp) AccentGreen else AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
