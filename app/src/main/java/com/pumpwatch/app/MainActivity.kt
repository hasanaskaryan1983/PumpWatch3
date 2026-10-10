package com.pumpwatch.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.WatchlistMigration
import com.pumpwatch.app.ui.FuturesWorkspace
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.OrbitalHub
import com.pumpwatch.app.ui.SpotWorkspace
import com.pumpwatch.app.worker.MonitorScheduler
import com.pumpwatch.app.worker.MonitorWorker
import com.pumpwatch.app.worker.SignalScannerWorker
import com.pumpwatch.app.worker.TraderMonitorScheduler
import com.pumpwatch.app.worker.WhaleMemeWorker
import java.util.concurrent.TimeUnit

private val SpotAccent = Color(0xFF00E676)
private val FuturesAccent = Color(0xFFFF5252)
private val HubAccent = Color(0xFFFFD700)
private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

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

// 🚀 Commit 159: سه حالت — اسپات | فیوچرز | هاب مداری
private enum class AppMode { SPOT, FUTURES, HUB }

@Composable
fun MainApp(onModeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }

    var appMode by remember {
        mutableStateOf(
            when (prefs.getString("mode", "SPOT")) {
                "FUTURES" -> AppMode.FUTURES
                "HUB" -> AppMode.HUB
                else -> AppMode.SPOT
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
            // هدر با لوگوی دوتُن pumpdump + سوئیچر سه‌تایی
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // لوگوی pumpdump
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "pump",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = SpotAccent
                            )
                            Text(
                                "dump",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = FuturesAccent
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // سوئیچر سه‌تایی: اسپات | فیوچرز | هاب
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCard, RoundedCornerShape(10.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = {
                                appMode = AppMode.SPOT
                                prefs.edit().putString("mode", "SPOT").apply()
                                onModeChanged()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (appMode == AppMode.SPOT) SpotAccent else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "اسپات",
                                color = if (appMode == AppMode.SPOT) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                appMode = AppMode.FUTURES
                                prefs.edit().putString("mode", "FUTURES").apply()
                                onModeChanged()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (appMode == AppMode.FUTURES) FuturesAccent else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "فیوچرز",
                                color = if (appMode == AppMode.FUTURES) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                appMode = AppMode.HUB
                                prefs.edit().putString("mode", "HUB").apply()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (appMode == AppMode.HUB) HubAccent else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "هاب",
                                color = if (appMode == AppMode.HUB) Color.Black else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // محتوای اصلی بر اساس mode
            Box(modifier = Modifier.weight(1f)) {
                when (appMode) {
                    AppMode.SPOT -> SpotWorkspace(onCoinClick = { selectedCoin = it })
                    AppMode.FUTURES -> FuturesWorkspace()
                    AppMode.HUB -> OrbitalHub(
                        onTabClick = { tabId ->
                            // بازگشت به mode مناسب بر اساس تب انتخاب‌شده
                            when {
                                tabId.startsWith("futures_") -> {
                                    appMode = AppMode.FUTURES
                                    prefs.edit().putString("mode", "FUTURES").apply()
                                }
                                else -> {
                                    appMode = AppMode.SPOT
                                    prefs.edit().putString("mode", "SPOT").apply()
                                }
                            }
                        }
                    )
                }
            }
        }

        // صفحه جزئیات ارز
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
