package com.pumpwatch.app

import android.Manifest
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.pumpwatch.app.ui.FuturesWorkspace
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.SpotWorkspace
import com.pumpwatch.app.worker.MonitorScheduler
import com.pumpwatch.app.worker.MonitorWorker
import com.pumpwatch.app.worker.SignalScannerWorker
import java.util.concurrent.TimeUnit

private val SpotAccent = Color(0xFF00E676)
private val FuturesAccent = Color(0xFFFF5252)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val TextSecondary = Color(0xFF8B949E)

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

        setContent {
            MainApp(onModeChanged = {
                MonitorScheduler.start(this)
                scheduleSignalScanner()
            })
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
            // TopBar
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
                    Text("🚀", fontSize = 26.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "PumpDump",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                    Spacer(Modifier.weight(1f))

                    // Mode Toggle
                    Surface(
                        modifier = Modifier.clickable {
                            isFutures = !isFutures
                            prefs.edit().putString("mode", if (isFutures) "FUTURES" else "SPOT").apply()
                            onModeChanged()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isFutures) FuturesAccent.copy(alpha = 0.15f) else SpotAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isFutures) "🔥 فیوچرز" else "💚 اسپات",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (isFutures) FuturesAccent else SpotAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Content - Workspace Switcher
            Box(modifier = Modifier.weight(1f)) {
                if (isFutures) {
                    FuturesWorkspace()
                } else {
                    SpotWorkspace(onCoinClick = { selectedCoin = it })
                }
            }
        }

        // Coin Detail Overlay (shared across both workspaces)
        if (selectedCoin != null) {
            Surface(
                color = Color(0xFF0B0F14),
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
