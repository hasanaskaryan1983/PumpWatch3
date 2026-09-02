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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.cmcUrl
import com.pumpwatch.app.ui.AssistantScreen
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
import kotlinx.coroutines.launch
import java.util.Locale

// ---------- رنگ‌های تم ----------
private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

// ---------- تب‌ها ----------
enum class Tab(val title: String, val emoji: String) {
    MARKET("بازار", "📊"),
    ALERTS("هشدارها", "🔔"),
    WHALE("نهنگ‌ها", "🐳"),
    ASSISTANT("دستیار", "🤖"),
    BACKTEST("بک‌تست", "🧪"),
    TOP("برترین‌ها", "🏆"),
    MEME("میم", "🐸"),
    LOG("سیگنال‌ها", "📓"),
    TRADES("معاملات", "📈"),
    HISTORY("تاریخچه", "📚")
}

// ---------- اکتیویتی ----------
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

// ---------- صفحه اصلی ----------
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
                    NavigationBar(containerColor = DarkSurface) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Text(tab.emoji, fontSize = 16.sp) },
                                label = { Text(tab.title, fontSize = 9.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentGreen,
                                    selectedTextColor = AccentGreen,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = DarkCard
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
