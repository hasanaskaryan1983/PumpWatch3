package com.pumpwatch.app

import android.Manifest
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
import androidx.compose.material3.FilterChip
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
import com.pumpwatch.app.ui.CoinDetailScreen
import com.pumpwatch.app.ui.HistoryScreen
import com.pumpwatch.app.ui.MemeRadarScreen
import com.pumpwatch.app.ui.OnboardingScreen
import com.pumpwatch.app.ui.SmartAlertsScreen
import com.pumpwatch.app.ui.TopPicksScreen
import com.pumpwatch.app.ui.TradesScreen
import com.pumpwatch.app.worker.MonitorScheduler
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
    BACKTEST("بک‌تست", "🧪"),
    TOP("برترین‌ها", "🏆"),
    MEME("رادار میم", "🐸"),
    TRADES("معاملات", "📈"),
    HISTORY("تاریخچه", "📚")
}

// ---------- داده‌های ربات دستیار ----------
data class IndicatorResult(
    val rsi: Double,
    val mfi: Double,
    val adx: Double,
    val macd: Double,
    val macdSignal: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val vwap: Double,
    val vwapDeviation: Double,
    val obvDivergence: String,
    val atr: Double,
    val trailingStop: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val supports: List<Double>,
    val resistances: List<Double>,
    val signal: String,
    val confidence: Int,
    val explanation: String
)

object IndicatorEngine {

    fun calculate(
        ohlc: List<List<Double>>,
        volumes: List<Double> = emptyList(),
        currentPrice: Double
    ): IndicatorResult {
        if (ohlc.size < 50) {
            return IndicatorResult(
                rsi = 50.0, mfi = 50.0, adx = 0.0,
                macd = 0.0, macdSignal = 0.0,
                ema20 = currentPrice, ema50 = currentPrice, ema200 = currentPrice,
                vwap = currentPrice, vwapDeviation = 0.0, obvDivergence = "NONE",
                atr = 0.0, trailingStop = currentPrice,
                bbUpper = currentPrice * 1.05, bbLower = currentPrice * 0.95,
                supports = emptyList(), resistances = emptyList(),
                signal = "HOLD", confidence = 0,
                explanation = "داده کافی برای تحلیل نیست (حداقل ۵۰ کندل لازم است)"
            )
        }

        val closes = ohlc.map { it[4] }
        val highs = ohlc.map { it[2] }
        val lows = ohlc.map { it[3] }

        val rsi = calculateRSI(closes)
        val macdVal = calculateMACD(closes)
        val ema20 = calculateEMA(closes, 20)
        val ema50 = calculateEMA(closes, 50)
        val ema200 = if (closes.size >= 200) calculateEMA(closes, 200) else ema50
        val bb = calculateBollinger(closes)
        val sr = findSupportResistance(highs, lows)
        val adx = calculateADX(closes, highs, lows)
        val atr = calculateATR(highs, lows, closes)

        val vwap = if (volumes.isNotEmpty() && volumes.size == closes.size) {
            (closes.zip(volumes).sumOf { it.first * it.second } / volumes.sum())
        } else closes.takeLast(20).average()
        val vwapDeviation = if (vwap > 0) (currentPrice - vwap) / vwap * 100 else 0.0

        val obvDiv = detectObvDivergence(closes, volumes)

        val signal = determineSignal(
            rsi, macdVal.first, macdVal.second, currentPrice,
            ema20, ema50, bb.first, bb.third, sr.first, sr.second
        )
        val confidence = calculateConfidence(
            rsi, macdVal.first, macdVal.second, currentPrice,
            ema20, ema50, sr.first, sr.second
        )
        val explanation = generateExplanation(
            signal, rsi, macdVal.first, macdVal.second, currentPrice,
            ema20, ema50, sr.first, sr.second
        )

        val trailingStop = if (signal.contains("BUY")) currentPrice * 0.95 else currentPrice * 1.05

        return IndicatorResult(
            rsi = rsi, mfi = 50.0, adx = adx,
            macd = macdVal.first, macdSignal = macdVal.second,
            ema20 = ema20, ema50 = ema50, ema200 = ema200,
            vwap = vwap, vwapDeviation = vwapDeviation, obvDivergence = obvDiv,
            atr = atr, trailingStop = trailingStop,
            bbUpper = bb.first, bbLower = bb.third,
            supports = sr.first, resistances = sr.second,
            signal = signal, confidence = confidence, explanation = explanation
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
        if (data.size < period) return data.last()
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) {
            ema = (data[i] * multiplier) + (ema * (1 - multiplier))
        }
        return ema
    }

    private fun calculateMACD(closes: List<Double>): Pair<Double, Double> {
        if (closes.size < 26) return Pair(0.0, 0.0)
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
        if (closes.size < period) return Triple(closes.last() * 1.05, closes.last(), closes.last() * 0.95)
        val slice = closes.takeLast(period)
        val middle = slice.average()
        val variance = slice.map { (it - middle) * (it - middle) }.average()
        val stdDev = sqrt(variance)
        return Triple(middle + 2 * stdDev, middle, middle - 2 * stdDev)
    }

    private fun calculateADX(closes: List<Double>, highs: List<Double>, lows: List<Double>, period: Int = 14): Double {
        if (closes.size < period * 2) return 0.0
        var sumTR = 0.0
        for (i in 1..period) {
            val tr = maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            sumTR += tr
        }
        return if (closes.last() > 0) (sumTR / period) / closes.last() * 100 else 0.0
    }

    private fun calculateATR(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1) return 0.0
        var sum = 0.0
        for (i in 1..period) {
            sum += maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
        }
        return sum / period
    }

    private fun detectObvDivergence(closes: List<Double>, volumes: List<Double>): String {
        if (closes.size < 20 || volumes.size < 20) return "NONE"
        val priceUp = closes.last() > closes[closes.size - 10]
        val volUp = volumes.takeLast(5).average() > volumes.dropLast(5).takeLast(5).average()
        return when {
            priceUp && !volUp -> "BEARISH"
            !priceUp && volUp -> "BULLISH"
            else -> "NONE"
        }
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
        score += 20
        return score.coerceIn(0, 100)
    }

    private fun generateExplanation(
        signal: String, rsi: Double, macd: Double, macdSignal: Double,
        price: Double, ema20: Double, ema50: Double,
        supports: List<Double>, resistances: List<Double>
    ): String {
        val parts = mutableListOf<String>()

        when {
            rsi < 30 -> parts.add("RSI در محدوده اشباع فروش (${String.format(Locale.US, "%.1f", rsi)})")
            rsi > 70 -> parts.add("RSI در محدوده اشباع خرید (${String.format(Locale.US, "%.1f", rsi)})")
            else -> parts.add("RSI در وضعیت متعادل (${String.format(Locale.US, "%.1f", rsi)})")
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

    // ---------- دروازه ورود پرانرژی (فقط بار اول) ----------
    if (!onboarded) {
        OnboardingScreen(onDone = {
            prefs.edit().putBoolean("onboarded", true).apply()
            onboarded = true
        })
        return
    }

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
                            icon = { Text(tab.emoji, fontSize = 18.sp) },
                            label = { Text(tab.title, fontSize = 10.sp) },
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
                    Tab.ALERTS -> SmartAlertsScreen(onCoinClick = { selectedCoin = it })
                    Tab.BACKTEST -> BacktestScreen()
                    Tab.TOP -> TopPicksScreen(if (isFutures) "FUT" else "SPOT")
                    Tab.MEME -> MemeRadarScreen()
                    Tab.TRADES -> TradesScreen()
                    Tab.HISTORY -> HistoryScreen(if (isFutures) "FUT" else "SPOT")
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

// ---------- توابع کمکی ----------

fun formatMarketCap(cap: Double?): String {
    if (cap == null) return "-"
    return when {
        cap >= 1_000_000_000_000 -> String.format(Locale.US, "$%.2fT", cap / 1_000_000_000_000)
        cap >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", cap / 1_000_000_000)
        cap >= 1_000_000 -> String.format(Locale.US, "$%.2fM", cap / 1_000_000)
        else -> String.format(Locale.US, "$%,.0f", cap)
    }
}

fun formatPrice(price: Double): String {
    return when {
        price >= 1000 -> String.format(Locale.US, "$%,.2f", price)
        price >= 1 -> String.format(Locale.US, "$%.4f", price)
        price >= 0.01 -> String.format(Locale.US, "$%.6f", price)
        else -> String.format(Locale.US, "$%.8f", price)
    }
}
