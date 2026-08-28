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
import com.pumpwatch.app.ui.MemeCoinsScreen
import com.pumpwatch.app.ui.TopPicksScreen
import com.pumpwatch.app.ui.TradesScreen
import com.pumpwatch.app.worker.MonitorScheduler
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

private val DarkBackground = Color(0xFF0B0F14)
private val DarkSurface = Color(0xFF121820)
private val DarkCard = Color(0xFF1A2230)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)

enum class Tab(val title: String, val emoji: String) {
    MARKET("بازار", "📊"),
    MEMES("میم‌کوین‌ها", "🐸"),
    TRADES("معاملات", "💰"),
    ALERTS("هشدارها", "🔔"),
    BACKTEST("بک‌تست", "🧪"),
    TOP("برترین‌ها", "🏆"),
    HISTORY("تاریخچه", "📚")
}

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
    val explanation: String,
    val mfi: Double,
    val vwap: Double,
    val vwapDeviation: Double,
    val obvDivergence: String,
    val adx: Double,
    val diPlus: Double,
    val diMinus: Double,
    val atr: Double,
    val volumeRatio: Double,
    val trailingStop: Double
)

object IndicatorEngine {

    fun calculate(ohlc: List<List<Double>>, volumes: List<Double>, currentPrice: Double): IndicatorResult {
        if (ohlc.size < 90) {
            return IndicatorResult(
                50.0, 0.0, 0.0, currentPrice, currentPrice, currentPrice,
                currentPrice * 1.05, currentPrice * 0.95,
                emptyList(), emptyList(), "HOLD", 0,
                "داده کافی نیست (۹۰ کندل لازم)",
                50.0, currentPrice, 0.0, "NONE", 0.0, 0.0, 0.0, 0.0, 0.0, currentPrice
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
        val adx = calculateADX(highs, lows, closes)
        val diPlus = calculateDIPlus(highs, lows, closes)
        val diMinus = calculateDIMinus(highs, lows, closes)
        val atr = calculateATR(highs, lows, closes)
        val mfi = calculateMFI(highs, lows, closes, volumes)
        val vwap = calculateVWAP(highs, lows, closes, volumes)
        val vwapDev = if (vwap > 0) abs(currentPrice - vwap) / vwap * 100 else 0.0
        val obvDiv = calculateOBVDivergence(closes, volumes)
        val volRatio = calculateVolumeRatio(volumes)
        val trailingStop = currentPrice - atr * 2.5

        val signal = determineSignal(rsi, macdVal.first, macdVal.second, currentPrice, ema20, ema50,
            bb.first, bb.third, sr.first, sr.second, mfi, vwap, adx, diPlus, diMinus, obvDiv)
        val confidence = calculateConfidence(rsi, macdVal.first, macdVal.second, currentPrice, ema20, ema50,
            sr.first, sr.second, mfi, adx, obvDiv, volRatio)
        val explanation = generateExplanation(signal, rsi, macdVal.first, macdVal.second, currentPrice,
            ema20, ema50, sr.first, sr.second, mfi, vwap, adx, diPlus, diMinus, obvDiv, volRatio)

        return IndicatorResult(rsi, macdVal.first, macdVal.second, ema20, ema50, ema200,
            bb.first, bb.third, sr.first, sr.second, signal, confidence, explanation,
            mfi, vwap, vwapDev, obvDiv, adx, diPlus, diMinus, atr, volRatio, trailingStop)
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

    private fun calculateATR(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1) return 0.0
        var sum = 0.0
        for (i in 1..period) {
            val tr = maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            sum += tr
        }
        var atr = sum / period
        for (i in period + 1 until closes.size) {
            val tr = maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1]))
            atr = (atr * (period - 1) + tr) / period
        }
        return atr
    }

    private fun calculateADX(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period * 2) return 0.0
        val plusDM = mutableListOf<Double>()
        val minusDM = mutableListOf<Double>()
        val trs = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val up = highs[i] - highs[i - 1]
            val dn = lows[i - 1] - lows[i]
            plusDM.add(if (up > dn && up > 0) up else 0.0)
            minusDM.add(if (dn > up && dn > 0) dn else 0.0)
            trs.add(maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])))
        }
        var sTR = trs.take(period).sum()
        var sP = plusDM.take(period).sum()
        var sM = minusDM.take(period).sum()
        val dxList = mutableListOf<Double>()
        for (i in period until trs.size) {
            sTR = sTR - sTR / period + trs[i]
            sP = sP - sP / period + plusDM[i]
            sM = sM - sM / period + minusDM[i]
            val pDI = if (sTR > 0) 100 * sP / sTR else 0.0
            val mDI = if (sTR > 0) 100 * sM / sTR else 0.0
            val d = pDI + mDI
            dxList.add(if (d > 0) 100 * abs(pDI - mDI) / d else 0.0)
        }
        if (dxList.isEmpty()) return 0.0
        if (dxList.size < period) return dxList.last()
        var a = dxList.take(period).average()
        for (i in period until dxList.size) {
            a = (a * (period - 1) + dxList[i]) / period
        }
        return a
    }

    private fun calculateDIPlus(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period * 2) return 0.0
        val plusDM = mutableListOf<Double>()
        val trs = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val up = highs[i] - highs[i - 1]
            val dn = lows[i - 1] - lows[i]
            plusDM.add(if (up > dn && up > 0) up else 0.0)
            trs.add(maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])))
        }
        var sTR = trs.take(period).sum()
        var sP = plusDM.take(period).sum()
        for (i in period until trs.size) {
            sTR = sTR - sTR / period + trs[i]
            sP = sP - sP / period + plusDM[i]
        }
        return if (sTR > 0) 100 * sP / sTR else 0.0
    }

    private fun calculateDIMinus(highs: List<Double>, lows: List<Double>, closes: List<Double>, period: Int = 14): Double {
        if (closes.size < period * 2) return 0.0
        val minusDM = mutableListOf<Double>()
        val trs = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val up = highs[i] - highs[i - 1]
            val dn = lows[i - 1] - lows[i]
            minusDM.add(if (dn > up && dn > 0) dn else 0.0)
            trs.add(maxOf(highs[i] - lows[i], abs(highs[i] - closes[i - 1]), abs(lows[i] - closes[i - 1])))
        }
        var sTR = trs.take(period).sum()
        var sM = minusDM.take(period).sum()
        for (i in period until trs.size) {
            sTR = sTR - sTR / period + trs[i]
            sM = sM - sM / period + minusDM[i]
        }
        return if (sTR > 0) 100 * sM / sTR else 0.0
    }

    private fun calculateMFI(highs: List<Double>, lows: List<Double>, closes: List<Double>, volumes: List<Double>, period: Int = 14): Double {
        if (closes.size < period + 1 || volumes.size < closes.size) return 50.0
        var posFlow = 0.0
        var negFlow = 0.0
        val offset = closes.size - volumes.size
        for (i in closes.size - period until closes.size) {
            val tp = (highs[i] + lows[i] + closes[i]) / 3.0
            val vi = i - offset
            if (vi >= 0 && vi < volumes.size) {
                val mf = tp * volumes[vi]
                if (i > 0) {
                    val prevTP = (highs[i - 1] + lows[i - 1] + closes[i - 1]) / 3.0
                    if (tp > prevTP) posFlow += mf else negFlow += mf
                }
            }
        }
        return if (negFlow == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + posFlow / negFlow))
    }

    private fun calculateVWAP(highs: List<Double>, lows: List<Double>, closes: List<Double>, volumes: List<Double>): Double {
        if (volumes.isEmpty() || closes.size != volumes.size) {
            val start = maxOf(0, closes.size - 20)
            var cumTPV = 0.0
            var cumVol = 0.0
            for (i in start until closes.size) {
                val tp = (highs[i] + lows[i] + closes[i]) / 3.0
                cumTPV += tp * volumes.getOrElse(i) { 1.0 }
                cumVol += volumes.getOrElse(i) { 1.0 }
            }
            return if (cumVol > 0) cumTPV / cumVol else closes.lastOrNull() ?: 0.0
        }
        var cumTPV = 0.0
        var cumVol = 0.0
        for (i in closes.indices) {
            val tp = (highs[i] + lows[i] + closes[i]) / 3.0
            cumTPV += tp * volumes[i]
            cumVol += volumes[i]
        }
        return if (cumVol > 0) cumTPV / cumVol else closes.lastOrNull() ?: 0.0
    }

    private fun calculateOBVDivergence(closes: List<Double>, volumes: List<Double>): String {
        if (closes.size < 20 || volumes.size < closes.size) return "NONE"
        var obv = 0.0
        val obvSeries = mutableListOf<Double>()
        for (i in closes.indices) {
            if (i == 0) {
                obv = volumes.getOrElse(i) { 0.0 }
            } else {
                obv += when {
                    closes[i] > closes[i - 1] -> volumes.getOrElse(i) { 0.0 }
                    closes[i] < closes[i - 1] -> -volumes.getOrElse(i) { 0.0 }
                    else -> 0.0
                }
            }
            obvSeries.add(obv)
        }
        val obvLast10 = obvSeries.takeLast(10)
        val closeLast10 = closes.takeLast(10)
        val obvTrend = if (obvLast10.last() > obvLast10.first()) "UP" else "DOWN"
        val priceTrend = if (closeLast10.last() > closeLast10.first()) "UP" else "DOWN"
        return when {
            priceTrend == "UP" && obvTrend == "DOWN" -> "BEARISH"
            priceTrend == "DOWN" && obvTrend == "UP" -> "BULLISH"
            else -> "NONE"
        }
    }

    private fun calculateVolumeRatio(volumes: List<Double>, period: Int = 20): Double {
        if (volumes.size <= period) return 0.0
        val last = volumes.last()
        val avg = volumes.dropLast(1).takeLast(period).average()
        return if (avg > 0) last / avg else 0.0
    }

    private fun determineSignal(
        rsi: Double, macd: Double, macdSignal: Double, price: Double,
        ema20: Double, ema50: Double, bbUpper: Double, bbLower: Double,
        supports: List<Double>, resistances: List<Double>,
        mfi: Double, vwap: Double, adx: Double, diPlus: Double, diMinus: Double, obvDiv: String
    ): String {
        var buyScore = 0
        var sellScore = 0

        if (rsi < 30) buyScore += 2
        if (rsi > 70) sellScore += 2
        if (rsi < 35) buyScore += 1
        if (rsi > 65) sellScore += 1

        if (mfi < 25) buyScore += 2
        if (mfi > 75) sellScore += 2
        if (mfi < 35) buyScore += 1
        if (mfi > 65) sellScore += 1

        if (macd > macdSignal) buyScore += 2
        if (macd < macdSignal) sellScore += 2

        if (price > ema20 && ema20 > ema50) buyScore += 2
        if (price < ema20 && ema20 < ema50) sellScore += 2

        if (price > vwap) buyScore += 1
        if (price < vwap) sellScore += 1

        if (price < bbLower) buyScore += 1
        if (price > bbUpper) sellScore += 1

        val nearSupport = supports.any { abs(price - it) / it < 0.03 }
        val nearResistance = resistances.any { abs(price - it) / it < 0.03 }
        if (nearSupport) buyScore += 2
        if (nearResistance) sellScore += 2

        if (adx >= 25) {
            if (diPlus > diMinus) buyScore += 2
            if (diMinus > diPlus) sellScore += 2
        }

        if (obvDiv == "BULLISH") buyScore += 2
        if (obvDiv == "BEARISH") sellScore += 2

        return when {
            buyScore >= 7 -> "STRONG_BUY"
            buyScore >= 5 -> "BUY"
            sellScore >= 7 -> "STRONG_SELL"
            sellScore >= 5 -> "SELL"
            else -> "HOLD"
        }
    }

    private fun calculateConfidence(
        rsi: Double, macd: Double, macdSignal: Double, price: Double,
        ema20: Double, ema50: Double, supports: List<Double>, resistances: List<Double>,
        mfi: Double, adx: Double, obvDiv: String, volRatio: Double
    ): Int {
        var score = 0
        if (rsi < 30 || rsi > 70) score += 15
        if (mfi < 25 || mfi > 75) score += 15
        if (abs(macd - macdSignal) > abs(macd) * 0.1) score += 15
        val nearSupport = supports.any { abs(price - it) / it < 0.03 }
        val nearResistance = resistances.any { abs(price - it) / it < 0.03 }
        if (nearSupport || nearResistance) score += 15
        if (price > ema20 && ema20 > ema50 || price < ema20 && ema20 < ema50) score += 15
        if (adx >= 25) score += 10
        if (obvDiv != "NONE") score += 10
        if (volRatio >= 2.0) score += 5
        return score.coerceIn(0, 100)
    }

    private fun generateExplanation(
        signal: String, rsi: Double, macd: Double, macdSignal: Double,
        price: Double, ema20: Double, ema50: Double,
        supports: List<Double>, resistances: List<Double>,
        mfi: Double, vwap: Double, adx: Double, diPlus: Double, diMinus: Double,
        obvDiv: String, volRatio: Double
    ): String {
        val parts = mutableListOf<String>()

        when {
            rsi < 30 -> parts.add("RSI اشباع فروش (${String.format(Locale.US, "%.1f", rsi)})")
            rsi > 70 -> parts.add("RSI اشباع خرید (${String.format(Locale.US, "%.1f", rsi)})")
            else -> parts.add("RSI متعادل (${String.format(Locale.US, "%.1f", rsi)})")
        }

        when {
            mfi < 25 -> parts.add("MFI اشباع فروش (${String.format(Locale.US, "%.1f", mfi)})")
            mfi > 75 -> parts.add("MFI اشباع خرید (${String.format(Locale.US, "%.1f", mfi)})")
            else -> parts.add("MFI متعادل (${String.format(Locale.US, "%.1f", mfi)})")
        }

        if (macd > macdSignal) parts.add("MACD صعودی")
        else if (macd < macdSignal) parts.add("MACD نزولی")

        when {
            price > ema20 && ema20 > ema50 -> parts.add("روند صعودی EMA")
            price < ema20 && ema20 < ema50 -> parts.add("روند نزولی EMA")
            else -> parts.add("EMA متقاطع")
        }

        if (price > vwap) parts.add("قیمت بالای VWAP")
        else if (price < vwap) parts.add("قیمت زیر VWAP")

        if (adx >= 25) {
            parts.add("ADX قوی (${String.format(Locale.US, "%.1f", adx)})")
            if (diPlus > diMinus) parts.add("DI+ > DI-")
            else if (diMinus > diPlus) parts.add("DI- > DI+")
        }

        val nearSupport = supports.any { abs(price - it) / it < 0.03 }
        val nearResistance = resistances.any { abs(price - it) / it < 0.03 }
        if (nearSupport) parts.add("نزدیک حمایت")
        if (nearResistance) parts.add("نزدیک مقاومت")

        if (obvDiv == "BULLISH") parts.add(" divergence صعودی OBV")
        if (obvDiv == "BEARISH") parts.add(" divergence نزولی OBV")

        if (volRatio >= 2.0) parts.add("حجم بالا (${String.format(Locale.US, "%.1f", volRatio)}x)")

        return parts.joinToString(" • ")
    }
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
                    Tab.MEMES -> MemeCoinsScreen(onCoinClick = { selectedCoin = it })
                    Tab.TRADES -> TradesScreen()
                    Tab.ALERTS -> AlertsScreen(onCoinClick = { selectedCoin = it })
                    Tab.BACKTEST -> BacktestScreen()
                    Tab.TOP -> TopPicksScreen(if (isFutures) "FUT" else "SPOT")
                    Tab.HISTORY -> HistoryScreen(if (isFutures) "FUT" else "SPOT")
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
                    color = if (isPump) AccentGreen else AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatPrice(price: Double?): String {
    if (price == null) return "$0.00"
    return if (price >= 1) String.format(Locale.US, "$%,.2f", price)
    else String.format(Locale.US, "$%.6f", price)
}

fun formatMarketCap(cap: Double?): String {
    if (cap == null) return "N/A"
    return when {
        cap >= 1_000_000_000_000 -> String.format(Locale.US, "%.1fT", cap / 1_000_000_000_000)
        cap >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", cap / 1_000_000_000)
        cap >= 1_000_000 -> String.format(Locale.US, "%.1fM", cap / 1_000_000)
        else -> String.format(Locale.US, "%.0f", cap)
    }
}
