package com.pumpwatch.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BacktestEngine
import com.pumpwatch.app.data.BacktestResult
import com.pumpwatch.app.data.ChartClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.SimulatedTrade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

// ---------- رنگ‌های تم ----------
private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)
private val WarningYellow = Color(0xFFFFC107)

// ---------- تب‌ها ----------
enum class Tab(val title: String, val emoji: String) {
    MARKET("بازار", "📊"),
    ALERTS("هشدارها", "🔔"),
    BACKTEST("بک‌تست", "🧪")
}

private val backtestCoins = listOf(
    "bitcoin" to "بیت‌کوین",
    "ethereum" to "اتریوم",
    "solana" to "سولانا",
    "dogecoin" to "دوج",
    "ripple" to "ریپل",
    "tron" to "ترون"
)

// ---------- داده‌های ربات دستیار ----------
data class IndicatorResult(
    val rsi: Double,
    val macd: Double,
    val macdSignal: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val supports: List<Double>,
    val resistances: List<Double>,
    val signal: String,
    val confidence: Int,
    val explanation: String
)

object IndicatorEngine {

    fun calculate(ohlc: List<List<Double>>, currentPrice: Double): IndicatorResult {
        if (ohlc.size < 50) {
            return IndicatorResult(
                50.0, 0.0, 0.0, currentPrice, currentPrice, currentPrice,
                currentPrice * 1.05, currentPrice * 0.95,
                emptyList(), emptyList(), "HOLD", 0,
                "داده کافی برای تحلیل نیست (حداقل ۵۰ کندل لازم است)"
            )
        }

        val closes = ohlc.map { it[4] }
        val highs = ohlc.map { it[2] }
        val lows = ohlc.map { it[3] }

        val rsi = calculateRSI(closes)
        val (macd, macdSignal) = calculateMACD(closes)
        val ema20 = calculateEMA(closes, 20)
        val ema50 = calculateEMA(closes, 50)
        val ema200 = if (closes.size >= 200) calculateEMA(closes, 200) else ema50
        val (bbUpper, _, bbLower) = calculateBollinger(closes)
        val (supports, resistances) = findSupportResistance(highs, lows)

        val signal = determineSignal(rsi, macd, macdSignal, currentPrice, ema20, ema50, bbUpper, bbLower, supports, resistances)
        val confidence = calculateConfidence(rsi, macd, macdSignal, currentPrice, ema20, ema50, supports, resistances)
        val explanation = generateExplanation(signal, rsi, macd, macdSignal, currentPrice, ema20, ema50, supports, resistances)

        return IndicatorResult(
            rsi, macd, macdSignal, ema20, ema50, ema200,
            bbUpper, bbLower, supports, resistances,
            signal, confidence, explanation
        )
    }

    private fun calculateRSI(closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1) return 50.0
        var gains = 0.0
        var losses = 0.0
        val start = closes.size - period
        for (i in start until closes.size) {
            val diff = closes[i] - closes[i - 1]
            if (diff > 0) gains += diff else losses -= diff
        }
        val avgGain = gains / period
        val avgLoss = losses / period
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateEMA(data: List<Double>, period: Int): Double {
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) {
            ema = (data[i] * multiplier) + (ema * (1 - multiplier))
        }
        return ema
    }

    private fun calculateMACD(closes: List<Double>): Pair<Double, Double> {
        val macdValues = mutableListOf<Double>()
        for (i in 26 until closes.size) {
            val slice = closes.subList(0, i + 1)
            val ema12 = calculateEMA(slice, 12)
            val ema26 = calculateEMA(slice, 26)
            macdValues.add(ema12 - ema26)
        }
        if (macdValues.isEmpty()) return Pair(0.0, 0.0)
        val macd = macdValues.last()
        val signal = calculateEMA(macdValues, 9)
        return Pair(macd, signal)
    }

    private fun calculateBollinger(closes: List<Double>, period: Int = 20): Triple<Double, Double, Double> {
        val slice = closes.takeLast(period)
        val middle = slice.average()
        val variance = slice.map { (it - middle) * (it - middle) }.average()
        val stdDev = sqrt(variance)
        return Triple(middle + 2 * stdDev, middle, middle - 2 * stdDev)
    }

    private fun findSupportResistance(highs: List<Double>, lows: List<Double>, window: Int = 3): Pair<List<Double>, List<Double>> {
        val supports = mutableListOf<Double>()
        val resistances = mutableListOf<Double>()
        for (i in window until highs.size - window) {
            val lowWindow = lows.subList(i - window, i + window + 1)
            if (lows[i] == lowWindow.minOrNull()) supports.add(lows[i])
            val highWindow = highs.subList(i - window, i + window + 1)
            if (highs[i] == highWindow.maxOrNull()) resistances.add(highs[i])
        }
        return Pair(supports.takeLast(3), resistances.takeLast(3))
    }

    private fun determineSignal(
        rsi: Double, macd: Double, macdSignal: Double, price: Double,
        ema20: Double, ema50: Double, bbUpper: Double, bbLower: Double,
        supports: List<Double>, resistances: List<Double>
    ): String {
        var buyScore = 0
        var sellScore = 0

        if (rsi < 30) buyScore += 2
        if (rsi > 70) sellScore += 2
        if (rsi < 45) buyScore += 1
        if (rsi > 55) sellScore += 1

        if (macd > macdSignal) buyScore += 2
        if (macd < macdSignal) sellScore += 2

        if (price > ema20 && ema20 > ema50) buyScore += 2
        if (price < ema20 && ema20 < ema50) sellScore += 2

        if (price < bbLower) buyScore += 1
        if (price > bbUpper) sellScore += 1

        val nearSupport = supports.any { abs(price - it) / it < 0.03 }
        val nearResistance = resistances.any { abs(price - it) / it < 0.03 }
        if (nearSupport) buyScore += 2
        if (nearResistance) sellScore += 2

        return when {
            buyScore >= 6 -> "STRONG_BUY"
            buyScore >= 4 -> "BUY"
            sellScore >= 6 -> "STRONG_SELL"
            sellScore >= 4 -> "SELL"
            else -> "HOLD"
        }
    }

    private fun calculateConfidence(
        rsi: Double, macd: Double, macdSignal: Double, price: Double,
        ema20: Double, ema50: Double, supports: List<Double>, resistances: List<Double>
    ): Int {
        var score = 0
        if (rsi < 30 || rsi > 70) score += 20
        if (abs(macd - macdSignal) > abs(macd) * 0.1) score += 20
        val nearSupport = supports.any { abs(price - it) / it < 0.03 }
        val nearResistance = resistances.any { abs(price - it) / it < 0.03 }
        if (nearSupport || nearResistance) score += 20
        if (price > ema20 && ema20 > ema50 || price < ema20 && ema20 < ema50) score += 20
        score += 20 // base
        return score.coerceIn(0, 100)
    }

    private fun generateExplanation(
        signal: String, rsi: Double, macd: Double, macdSignal: Double,
        price: Double, ema20: Double, ema50: Double,
        supports: List<Double>, resistances: List<Double>
    ): String {
        val parts = mutableListOf<String>()

        when {
            rsi < 30 -> parts.add("RSI در محدوده اشباع فروش ($rsi)")
            rsi > 70 -> parts.add("RSI در محدوده اشباع خرید ($rsi)")
            else -> parts.add("RSI در وضعیت متعادل ($rsi)")
        }

        if (macd > macdSignal) parts.add("MACD تقاطع صعودی داده")
        else if (macd < macdSignal) parts.add("MACD تقاطع نزولی داده")

        when {
            price > ema20 && ema20 > ema50 -> parts.add("قیمت بالای EMA20 و EMA50 است (روند صعودی)")
            price < ema20 && ema20 < ema50 -> parts.add("قیمت زیر EMA20 و EMA50 است (روند نزولی)")
            else -> parts.add("قیمت در نزدیکی میانگین‌ها است")
        }

        val nearSupport = supports.any { abs(price - it) / it < 0.03 }
        val nearResistance = resistances.any { abs(price - it) / it < 0.03 }
        if (nearSupport) parts.add("قیمت نزدیک حمایت کلیدی است")
        if (nearResistance) parts.add("قیمت نزدیک مقاومت کلیدی است")

        return parts.joinToString(" • ")
    }
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

    if (selectedCoin != null) {
        CoinDetailScreen(
            coin = selectedCoin!!,
            onBack = { selectedCoin = null }
        )
        return
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
                            icon = { Text(tab.emoji, fontSize = 20.sp) },
                            label = { Text(tab.title, fontSize = 12.sp) },
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
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (selectedTab) {
                    Tab.MARKET -> MarketScreen(onCoinClick = { selectedCoin = it })
                    Tab.ALERTS -> AlertsScreen(onCoinClick = { selectedCoin = it })
                    Tab.BACKTEST -> BacktestScreen()
                }
            }
        }
    }
}

// ---------- صفحه بازار ----------
@Composable
fun MarketScreen(onCoinClick: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val mode = prefs.getString("mode", "SPOT")
                coins = if (mode == "FUTURES") ApiClient.getTop100Coins() else ApiClient.getTop1000Coins()
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

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
                items(coins) { coin ->
                    CoinCard(coin = coin, onClick = { onCoinClick(coin) })
                }
            }
        }
    }
}

@Composable
fun CoinCard(coin: CoinMarket, onClick: () -> Unit) {
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

// ---------- صفحه هشدارها ----------
@Composable
fun AlertsScreen(onCoinClick: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pumpwatch_prefs", 0) }
    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var threshold by remember {
        mutableStateOf(prefs.getFloat("alert_threshold", 5f).toDouble())
    }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val mode = prefs.getString("mode", "SPOT")
                coins = if (mode == "FUTURES") ApiClient.getTop100Coins() else ApiClient.getTop1000Coins()
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val alerts = coins
        .filter { abs(it.price_change_percentage_24h ?: 0.0) >= threshold }
        .sortedByDescending { abs(it.price_change_percentage_24h ?: 0.0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "هشدار پامپ / دامپ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { load() }) { Text("بروزرسانی") }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(3.0, 5.0, 10.0).forEach { t ->
                FilterChip(
                    selected = threshold == t,
                    onClick = {
                        threshold = t
                        prefs.edit().putFloat("alert_threshold", t.toFloat()).apply()
                    },
                    label = { Text("${t.toInt()}٪") }
                )
            }
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

            alerts.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "😴 هنوز هشداری نیست — بازار آرومه",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alerts) { coin ->
                    AlertCard(coin = coin, onClick = { onCoinClick(coin) })
                }
            }
        }
    }
}

@Composable
fun AlertCard(coin: CoinMarket, onClick: () -> Unit) {
    val change = coin.price_change_percentage_24h ?: 0.0
    val isPump = change >= 0
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
            Text(if (isPump) "🚀" else "🩸", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#$rank  ${coin.symbol.uppercase(Locale.US)} ${if (isPump) "پامپ" else "دامپ"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isPump) AccentGreen else AccentRed
                )
                Text(coin.name, color = TextSecondary, fontSize = 12.sp)
                Text(
                    "کپ: ${formatMarketCap(coin.market_cap)}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    String.format(Locale.US, "%+.2f%%", change),
                    color = if (isPump) AccentGreen else AccentRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(formatPrice(coin.current_price), fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

// ---------- صفحه جزئیات کوین + نمودار + ربات دستیار ----------
@Composable
fun CoinDetailScreen(coin: CoinMarket, onBack: () -> Unit) {
    var ohlcCache by remember { mutableStateOf<Map<String, List<List<Double>>>>(emptyMap()) }
    var selectedTimeframe by remember { mutableStateOf("1h") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showAssistant by remember { mutableStateOf(false) }
    var indicatorResult by remember { mutableStateOf<IndicatorResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val timeframes = listOf(
        "1m" to "1", "5m" to "1", "15m" to "1", "1h" to "1",
        "4h" to "7", "8h" to "7", "12h" to "7",
        "1d" to "1", "1w" to "7", "1M" to "30", "1y" to "365", "ALL" to "max"
    )

    val currentData = ohlcCache[selectedTimeframe] ?: emptyList()

    fun loadChart(days: String, tf: String, force: Boolean = false) {
        if (!force && ohlcCache.containsKey(tf)) {
            errorMsg = null
            return
        }
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val data = ApiClient.api.getOhlc(coin.id, days = days)
                ohlcCache = ohlcCache.toMutableMap().apply { put(tf, data) }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                errorMsg = when {
                    msg.contains("429") -> "⏳ تعداد درخواست زیاد بود. لطفاً چند ثانیه صبر کنید."
                    else -> "خطا: $msg"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun analyze() {
        if (currentData.size < 20) return
        scope.launch {
            isAnalyzing = true
            delay(300) // حس تحلیل :)
            indicatorResult = IndicatorEngine.calculate(currentData, coin.current_price)
            isAnalyzing = false
        }
    }

    LaunchedEffect(selectedTimeframe) {
        val days = timeframes.find { it.first == selectedTimeframe }?.second ?: "1"
        loadChart(days, selectedTimeframe)
        while (true) {
            delay(30000)
            val d = timeframes.find { it.first == selectedTimeframe }?.second ?: "1"
            try {
                val data = ApiClient.api.getOhlc(coin.id, days = d)
                ohlcCache = ohlcCache.toMutableMap().apply { put(selectedTimeframe, data) }
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // بالا: دکمه بازگشت
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← بازگشت", color = AccentGreen, fontSize = 16.sp)
            }
        }

        // اطلاعات کوین
        val change = coin.price_change_percentage_24h ?: 0.0
        Text(
            "#${coin.market_cap_rank ?: 0} ${coin.symbol.uppercase(Locale.US)}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            coin.name,
            color = TextSecondary,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatPrice(coin.current_price),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            Text(
                String.format(Locale.US, "%+.2f%%", change),
                color = if (change >= 0) AccentGreen else AccentRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // آمار
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("مارکت کپ", formatMarketCap(coin.market_cap), TextPrimary)
            StatCard("حجم ۲۴h", formatMarketCap(coin.total_volume), TextPrimary)
        }

        Spacer(Modifier.height(8.dp))

        // دکمه‌های تایم‌فریم
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            timeframes.forEach { (label, _) ->
                FilterChip(
                    selected = selectedTimeframe == label,
                    onClick = { selectedTimeframe = label },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        // نمودار
        when {
            isLoading && currentData.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGreen)
            }

            errorMsg != null && currentData.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMsg!!, color = AccentRed, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val d = timeframes.find { it.first == selectedTimeframe }?.second ?: "1"
                        loadChart(d, selectedTimeframe, force = true)
                    }) {
                        Text("تلاش مجدد 🔄")
                    }
                }
            }

            currentData.size < 2 -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("داده کافی برای نمایش نمودار نیست", color = TextSecondary)
            }

            else -> Column {
                if (isLoading) {
                    Text(
                        "🔄 در حال بروزرسانی...",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                CandlestickChart(
                    ohlcData = currentData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // دکمه ربات دستیار
        Button(
            onClick = {
                showAssistant = !showAssistant
                if (showAssistant && indicatorResult == null && currentData.size >= 20) {
                    analyze()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = currentData.size >= 20
        ) {
            Text(
                if (showAssistant) "🤖 بستن دستیار" else "🤖 ربات دستیار تحلیلگر",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (currentData.size < 20) {
            Text(
                "برای فعال‌سازی دستیار، تایم‌فریم با داده بیشتر انتخاب کنید",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // پنل دستیار
        if (showAssistant && currentData.size >= 20) {
            RobotAssistantPanel(
                result = indicatorResult,
                isAnalyzing = isAnalyzing,
                onRefresh = { analyze() }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ---------- پنل ربات دستیار ----------
@Composable
fun RobotAssistantPanel(
    result: IndicatorResult?,
    isAnalyzing: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Surface(
            color = DarkCard,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "🤖 تحلیل تکنیکال",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentGreen
                )

                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentGreen)
                            Spacer(Modifier.height(8.dp))
                            Text("در حال تحلیل...", color = TextSecondary)
                        }
                    }
                } else if (result == null) {
                    Text(
                        "دکمه زیر را بزنید تا تحلیل انجام شود",
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                        Text("شروع تحلیل 📊")
                    }
                } else {
                    // سیگنال اصلی
                    val (signalText, signalColor, signalEmoji) = when (result.signal) {
                        "STRONG_BUY" -> Triple("خرید قوی ⭐⭐⭐", AccentGreen, "🟢")
                        "BUY" -> Triple("خرید ⭐⭐", AccentGreen.copy(alpha = 0.85f), "🟢")
                        "HOLD" -> Triple("نگهداری / صبر ⭐", WarningYellow, "🟡")
                        "SELL" -> Triple("فروش ⭐⭐", AccentRed.copy(alpha = 0.85f), "🔴")
                        "STRONG_SELL" -> Triple("فروش قوی ⭐⭐⭐", AccentRed, "🔴")
                        else -> Triple("نامشخص", TextSecondary, "⚪")
                    }

                    Surface(
                        color = signalColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(signalEmoji, fontSize = 36.sp)
                            Text(
                                signalText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = signalColor
                            )
                        }
                    }

                    // اعتماد
                    Text(
                        "اعتماد به سیگنال: ${result.confidence}٪",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    LinearProgressIndicator(
                        progress = { result.confidence / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = signalColor,
                        trackColor = Color(0xFF2A3441),
                    )

                    // توضیحات
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            result.explanation,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = TextPrimary
                        )
                    }

                    // جدول اندیکاتورها
                    Text(
                        "مقادیر اندیکاتورها:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndicatorValueCard("RSI", String.format(Locale.US, "%.1f", result.rsi), when {
                            result.rsi < 30 -> AccentGreen
                            result.rsi > 70 -> AccentRed
                            else -> TextSecondary
                        })
                        IndicatorValueCard("MACD", String.format(Locale.US, "%.2f", result.macd), when {
                            result.macd > result.macdSignal -> AccentGreen
                            result.macd < result.macdSignal -> AccentRed
                            else -> TextSecondary
                        })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IndicatorValueCard("EMA20", formatPriceShort(result.ema20), TextPrimary)
                        IndicatorValueCard("EMA50", formatPriceShort(result.ema50), TextPrimary)
                    }

                    // حمایت و مقاومت
                    if (result.supports.isNotEmpty()) {
                        Text(
                            "سطوح حمایت:",
                            fontSize = 13.sp,
                            color = AccentGreen
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.supports.forEach {
                                Surface(
                                    color = AccentGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        formatPriceShort(it),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = AccentGreen,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (result.resistances.isNotEmpty()) {
                        Text(
                            "سطوح مقاومت:",
                            fontSize = 13.sp,
                            color = AccentRed
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.resistances.forEach {
                                Surface(
                                    color = AccentRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        formatPriceShort(it),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = AccentRed,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 بروزرسانی تحلیل")
                    }
                }
            }
        }
    }
}

@Composable
fun IndicatorValueCard(title: String, value: String, color: Color) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = TextSecondary)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ---------- نمودار کندل استیک ----------
@Composable
fun CandlestickChart(ohlcData: List<List<Double>>, modifier: Modifier = Modifier) {
    val greenColor = AccentGreen
    val redColor = AccentRed
    val gridColor = Color(0xFF2A3441)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 8f

        val chartHeight = height - 2 * padding
        val chartWidth = width - 2 * padding

        val highs = ohlcData.map { it[2] }
        val lows = ohlcData.map { it[3] }
        val maxPrice = highs.maxOrNull() ?: return@Canvas
        val minPrice = lows.minOrNull() ?: return@Canvas
        val priceRange = maxPrice - minPrice

        if (priceRange == 0.0) return@Canvas

        val spacing = chartWidth / ohlcData.size
        val candleWidth = spacing * 0.65f

        for (i in 0..4) {
            val y = padding + (chartHeight / 4) * i
            drawLine(
                color = gridColor,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }

        ohlcData.forEachIndexed { index, candle ->
            val open = candle[1]
            val high = candle[2]
            val low = candle[3]
            val close = candle[4]

            val x = padding + index * spacing + spacing / 2

            val yHigh = padding + chartHeight - ((high - minPrice) / priceRange * chartHeight).toFloat()
            val yLow = padding + chartHeight - ((low - minPrice) / priceRange * chartHeight).toFloat()
            val yOpen = padding + chartHeight - ((open - minPrice) / priceRange * chartHeight).toFloat()
            val yClose = padding + chartHeight - ((close - minPrice) / priceRange * chartHeight).toFloat()

            val color = if (close >= open) greenColor else redColor

            drawLine(
                color = color,
                start = Offset(x, yHigh),
                end = Offset(x, yLow),
                strokeWidth = 1.5f
            )

            val bodyTop = minOf(yOpen, yClose)
            val bodyBottom = maxOf(yOpen, yClose)
            val bodyHeight = maxOf(bodyBottom - bodyTop, 2f)

            drawRect(
                color = color,
                topLeft = Offset(x - candleWidth / 2, bodyTop),
                size = Size(candleWidth, bodyHeight)
            )
        }
    }
}

// ---------- صفحه بک‌تست ----------
@Composable
fun BacktestScreen() {
    var coinId by remember { mutableStateOf("bitcoin") }
    var days by remember { mutableStateOf(30) }
    var buyDrop by remember { mutableStateOf(10.0) }
    var sellRise by remember { mutableStateOf(10.0) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<BacktestResult?>(null) }
    val scope = rememberCoroutineScope()

    fun run() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val chart = ChartClient.api.getMarketChart(coinId, days = days)
                val series = chart.prices.map { p -> p[0].toLong() to p[1] }
                result = BacktestEngine.run(series, buyDrop, sellRise)
            } catch (e: Exception) {
                errorMsg = "خطا: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "شبیه‌ساز بک‌تست",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backtestCoins.forEach { (id, label) ->
                FilterChip(
                    selected = coinId == id,
                    onClick = { coinId = id },
                    label = { Text(label) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { d ->
                FilterChip(
                    selected = days == d,
                    onClick = { days = d },
                    label = { Text("$d روز") }
                )
            }
        }

        Text("خرید پس از ریزش:", fontSize = 13.sp, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5.0, 10.0, 15.0).forEach { t ->
                FilterChip(
                    selected = buyDrop == t,
                    onClick = { buyDrop = t },
                    label = { Text("${t.toInt()}٪") }
                )
            }
        }

        Text("فروش پس از رشد / افت:", fontSize = 13.sp, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5.0, 10.0, 15.0).forEach { t ->
                FilterChip(
                    selected = sellRise == t,
                    onClick = { sellRise = t },
                    label = { Text("${t.toInt()}٪") }
                )
            }
        }

        Button(
            onClick = { run() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text(if (loading) "در حال اجرا..." else "اجرای بک‌تست 🚀")
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGreen)
            }
        }

        if (errorMsg != null) {
            Text(errorMsg ?: "", color = AccentRed, textAlign = TextAlign.Center)
        }

        result?.let { r ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard("معاملات", r.totalTrades.toString(), TextPrimary)
                StatCard(
                    "وین‌ریت",
                    String.format(Locale.US, "%.0f%%", r.winRatePercent),
                    if (r.winRatePercent >= 50) AccentGreen else AccentRed
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatCard(
                    "سود خالص",
                    String.format(Locale.US, "%+.1f%%", r.netPnlPercent),
                    if (r.netPnlPercent >= 0) AccentGreen else AccentRed
                )
                StatCard(
                    "بیشترین افت",
                    String.format(Locale.US, "%.1f%%", r.maxDrawdownPercent),
                    AccentRed
                )
            }

            Text("آخرین معاملات:", fontSize = 13.sp, color = TextSecondary)
            r.trades.takeLast(10).reversed().forEach { t -> TradeRow(t) }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color) {
    Surface(
        color = DarkCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = TextSecondary)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun TradeRow(t: SimulatedTrade) {
    Surface(color = DarkCard, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("خرید: ${formatPrice(t.entryPrice)}", fontSize = 12.sp)
                Text(
                    "فروش: ${formatPrice(t.exitPrice)}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Text(
                String.format(Locale.US, "%+.2f%%", t.pnlPercent),
                color = if (t.pnlPercent >= 0) AccentGreen else AccentRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------- توابع کمکی ----------
fun formatPrice(price: Double): String {
    return if (price >= 1) {
        String.format(Locale.US, "$%,.2f", price)
    } else {
        String.format(Locale.US, "$%.6f", price)
    }
}

fun formatPriceShort(price: Double): String {
    return when {
        price >= 1_000_000_000_000 -> String.format(Locale.US, "$%.1fT", price /
