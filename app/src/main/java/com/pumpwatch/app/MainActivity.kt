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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.MarketMeta
import com.pumpwatch.app.data.NetErr
import com.pumpwatch.app.data.NetError
import com.pumpwatch.app.data.ServedFrom
import com.pumpwatch.app.data.cmcUrl
import com.pumpwatch.app.data.platformContractOf
import com.pumpwatch.app.store.WatchlistScheduler
import com.pumpwatch.app.ui.FuturesWorkspace
import com.pumpwatch.app.ui.MarketPulseHeader
import com.pumpwatch.app.ui.MarketViewModel
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.SpotWorkspace
import com.pumpwatch.app.worker.MonitorScheduler
import com.pumpwatch.app.worker.MonitorWorker
import com.pumpwatch.app.worker.SignalScannerWorker
import java.util.Locale
import java.util.concurrent.TimeUnit

private val SpotAccent = Color(0xFF00E676)
private val FuturesAccent = Color(0xFFFF5252)
private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)
private val ContractBlue = Color(0xFF40C4FF)
private val ContractGold = Color(0xFFFFC107)
private val FreshGreen = Color(0xFF00E676)
private val FreshYellow = Color(0xFFFFC107)
private val FreshRed = Color(0xFFFF5252)
private val FreshGray = Color(0xFF8B949E)

class MainActivity : ComponentActivity() {

    companion object {
        private const val SIGNAL_SCANNER_WORK_NAME = "SignalScanner"
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
        WatchlistScheduler.start(this)

        setContent {
            PumpWatchTheme {
                MainApp(onModeChanged = {
                    MonitorScheduler.start(this)
                    scheduleSignalScanner()
                    WatchlistScheduler.start(this)
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
}

@Composable
fun PumpWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SpotAccent,
            onPrimary = Color.Black,
            background = DarkBackground,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
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

    val accent = if (isFutures) FuturesAccent else SpotAccent

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "PumpDump",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    }
                    
                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .background(DarkCard, RoundedCornerShape(10.dp))
                            .padding(2.dp),
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
                                containerColor = if (!isFutures) SpotAccent else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "اسپات",
                                color = if (!isFutures) Color.Black else TextSecondary,
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
                                containerColor = if (isFutures) FuturesAccent else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "فیوچرز",
                                color = if (isFutures) Color.Black else TextSecondary,
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
private fun ContractRow(ctx: Context, contract: String?) {
    if (contract.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("⛓️ بومی — بدون کانترکت", fontSize = 9.sp, color = TextSecondary)
        }
        return
    }
    val copied = remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text("📋 ", fontSize = 9.sp, color = TextSecondary)
        Text(
            if (contract.length > 24) "${contract.take(12)}...${contract.takeLast(8)}" else contract,
            fontSize = 9.sp, color = ContractBlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                try {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("contract", contract))
                    copied.value = true
                } catch (_: Exception) { }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (copied.value) SpotAccent else ContractGold),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) { Text(if (copied.value) "✅" else " کپی", fontSize = 9.sp, color = Color.Black) }
    }
}

@Composable
private fun FreshnessBadge(meta: MarketMeta) {
    val (emoji, color, text) = when {
        meta.observedAtMs <= 0L -> Triple("⚪", FreshGray, "نامشخص")
        meta.servedFrom == ServedFrom.DISK_CACHE -> Triple("🟠", FreshYellow, "آفلاین (${meta.ageSec() / 60}د)")
        meta.ageSec() <= 130 -> Triple("", FreshGreen, "زنده")
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
    val viewModel: MarketViewModel = viewModel()
    
    val coins by viewModel.coins.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    val meta by viewModel.meta.collectAsState()
    val platformMap by viewModel.platformMap.collectAsState()

    var query by remember { mutableStateOf("") }

    val shown = if (query.isBlank()) coins
    else coins.filter { it.symbol.contains(query, true) || it.name.contains(query, true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("قیمت لحظه‌ای", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            FreshnessBadge(meta)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { viewModel.refresh() }) { Text("بروزرسانی") }
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
            isLoading && coins.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SpotAccent)
            }
            errorMsg != null && coins.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    errorMsg ?: "",
                    color = FuturesAccent,
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
                    val contract = platformContractOf(platformMap, coin.id)
                    CoinCard(coin = coin, contract = contract, onClick = { onCoinClick(coin) })
                }
            }
        }
    }
}

@Composable
fun CoinCard(coin: CoinMarket, contract: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val change = coin.price_change_percentage_24h ?: 0.0
    val isUp = change >= 0
    val rank = coin.market_cap_rank ?: 0
    Surface(
        color = DarkCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "#$rank  ${coin.symbol.uppercase(Locale.US)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(coin.name, color = TextSecondary, fontSize = 12.sp)
                    Text("کپ: ${fmtMarketCap(coin.market_cap)}", color = TextSecondary, fontSize = 11.sp)
                }

                Text(
                    "📊",
                    fontSize = 18.sp,
                    modifier = Modifier.clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cmcUrl(coin.id))))
                        } catch (_: Exception) { }
                    }.padding(8.dp)
                )

                Spacer(Modifier.width(4.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtPrice(coin.current_price), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        String.format(Locale.US, "%+.2f%%", change),
                        color = if (isUp) SpotAccent else FuturesAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            ContractRow(context, contract)
        }
    }
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
