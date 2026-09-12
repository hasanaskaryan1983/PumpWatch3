package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.engine.BacktestEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LC = Color(0xFF1A2230)
private val LBlue = Color(0xFF40C4FF)

private object KlineCache {
    private val map = mutableMapOf<String, Pair<Long, List<JsonArray>>>()
    fun get(key: String): List<JsonArray>? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.first > 10 * 60 * 1000) return null
        return e.second
    }
    fun put(key: String, v: List<JsonArray>) {
        map[key] = System.currentTimeMillis() to v
    }
}

private suspend fun getKlinesCached(symbol: String, interval: String, limit: Int): List<JsonArray> {
    val key = "$symbol|$interval|$limit"
    KlineCache.get(key)?.let { return it }
    val data = try {
        BinanceClient.api.klines(symbol, interval, limit)
    } catch (e: Exception) {
        emptyList()
    }
    if (data.isNotEmpty()) KlineCache.put(key, data)
    return data
}

private data class Tf(val label: String, val interval: String, val limit: Int, val evalLast: Int, val hold: Int)

private val FUT_TIMEFRAMES = listOf(
    Tf("۴ ساعته", "15m", 120, 16, 8),
    Tf("۱۲ ساعته", "30m", 120, 24, 12),
    Tf("۱ روزه", "1h", 168, 168, 24),
    Tf("۳ روزه", "1h", 168, 72, 24),
    Tf("۷ روزه", "1h", 168, 168, 48),
    Tf("ماهیانه", "4h", 180, 180, 60)
)

private val SPOT_HORIZONS = listOf(
    "۱ هفته" to 7,
    "۲ هفته" to 14,
    "۱ ماه" to 30,
    "۳ ماه" to 90
)

private val RANGES = listOf(
    "1-10" to (0 until 10),
    "11-20" to (10 until 20),
    "21-30" to (20 until 30),
    "31-40" to (30 until 40),
    "41-50" to (40 until 50),
    "51-60" to (50 until 60),
    "61-70" to (60 until 70),
    "71-80" to (70 until 80),
    "81-90" to (80 until 90),
    "91-100" to (90 until 100),
    "1-50" to (0 until 50),
    "51-100" to (50 until 100)
)

@Composable
fun BacktestScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pumpwatch_prefs", 0) }
    val isFutures = prefs.getString("mode", "SPOT") == "FUTURES"

    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<BacktestEngine.Trade>>(emptyList()) }
    var metrics by remember { mutableStateOf<BacktestEngine.BacktestMetrics?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var selectedRanges by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTf by remember { mutableStateOf("۱ روزه") }
    var selectedHorizon by remember { mutableStateOf("۱ ماه") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var analyzedInfo by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🧪 بک‌تست استراتژی", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        Surface(
            color = if (isFutures) LR.copy(alpha = 0.15f) else LG.copy(alpha = 0.15f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isFutures) "⚡ فیوچرز: کوتاه‌مدت، خروج روی CLOSE کندل | کارمزد 0.2% + Slippage 0.1%"
                else "🏦 اسپات: امتیاز ≥۰ + هفتگی مثبت + OBV مثبت | کارمزد 0.2% + Slippage 0.1%",
                fontSize = 11.sp,
                color = if (isFutures) LR else LG,
                modifier = Modifier.padding(10.dp)
            )
        }

        if (isFutures) {
            Text("⏱ بازه زمانی:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FUT_TIMEFRAMES.take(3).forEach { tf ->
                    FilterChip(
                        selected = selectedTf == tf.label,
                        onClick = { selectedTf = tf.label },
                        label = { Text(tf.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LY.copy(alpha = 0.3f))
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FUT_TIMEFRAMES.drop(3).forEach { tf ->
                    FilterChip(
                        selected = selectedTf == tf.label,
                        onClick = { selectedTf = tf.label },
                        label = { Text(tf.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LY.copy(alpha = 0.3f))
                    )
                }
            }
        } else {
            Text("📅 افق نگهداری:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SPOT_HORIZONS.forEach { (label, _) ->
                    FilterChip(
                        selected = selectedHorizon == label,
                        onClick = { selectedHorizon = label },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LY.copy(alpha = 0.3f))
                    )
                }
            }
        }

        Text("🏆 بازه رتبه ارزها:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RANGES.forEach { (label, range) ->
                FilterChip(
                    selected = label in selectedRanges,
                    onClick = {
                        selectedRanges = if (label in selectedRanges) selectedRanges - label else selectedRanges + label
                        errorMsg = null
                    },
                    label = { Text("$label (${range.last - range.first + 1} ارز)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedRanges.isNotEmpty()) {
            Text(
                "✅ ${if (isFutures) selectedTf else selectedHorizon} | ${selectedRanges.sorted().joinToString(", ")}",
                fontSize = 11.sp, color = LG, fontWeight = FontWeight.Bold
            )
        }

        errorMsg?.let { msg -> Text(msg, color = LR, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

        Button(
            onClick = {
                if (selectedRanges.isEmpty()) {
                    errorMsg = "⚠️ حداقل یک بازه رتبه انتخاب کن!"
                    return@Button
                }
                if (!isRunning) {
                    isRunning = true
                    results = emptyList()
                    metrics = null
                    errorMsg = null
                    scope.launch {
                        val allTrades = mutableListOf<BacktestEngine.Trade>()

                        val allCoins = withContext(Dispatchers.IO) {
                            try {
                                ApiClient.getTop1000Coins()
                                    .sortedByDescending { it.total_volume ?: 0.0 }
                                    .take(100)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }

                        if (allCoins.isEmpty()) {
                            errorMsg = "❌ خطا در دریافت لیست ارزها"
                            isRunning = false
                            return@launch
                        }

                        val allIndices = mutableSetOf<Int>()
                        selectedRanges.forEach { label ->
                            RANGES.find { it.first == label }?.second?.forEach { allIndices.add(it) }
                        }
                        val coinsToTest = allIndices.mapNotNull { idx -> allCoins.getOrNull(idx)?.let { idx to it } }

                        var processed = 0
                        var analyzed = 0
                        for ((idx, coin) in coinsToTest) {
                            val symbol = coin.symbol.uppercase(Locale.US)
                            progress = "در حال تحلیل $symbol (${processed + 1}/${coinsToTest.size})..."

                            if (isFutures) {
                                val tf = FUT_TIMEFRAMES.find { it.label == selectedTf } ?: FUT_TIMEFRAMES[2]
                                val klines = withContext(Dispatchers.IO) {
                                    getKlinesCached("${symbol}USDT", tf.interval, tf.limit)
                                }
                                if (klines.size >= 60) {
                                    analyzed++
                                    val klinesList = klines.map { k ->
                                        listOf(k[1].asDouble, k[2].asDouble, k[3].asDouble, k[4].asDouble, k[5].asDouble)
                                    }
                                    val (trades, _) = BacktestEngine.runFutures(
                                        symbol, klinesList, tf.evalLast, tf.hold, 0.0001
                                    )
                                    allTrades.addAll(trades)
                                }
                            } else {
                                val hold = SPOT_HORIZONS.find { it.first == selectedHorizon }?.second ?: 30
                                val klines = withContext(Dispatchers.IO) {
                                    getKlinesCached("${symbol}USDT", "1d", 300)
                                }
                                if (klines.size >= 210) {
                                    analyzed++
                                    val klinesList = klines.map { k ->
                                        listOf(k[1].asDouble, k[2].asDouble, k[3].asDouble, k[4].asDouble, k[5].asDouble)
                                    }
                                    val (trades, _) = BacktestEngine.runSpot(symbol, klinesList, hold)
                                    allTrades.addAll(trades)
                                }
                            }
                            processed++
                            delay(150)
                        }

                        analyzedInfo = "ارزهای تحلیل‌شده: $analyzed از ${coinsToTest.size}"
                        results = allTrades
                        
                        // محاسبه metrics با استفاده از تابع computeMetrics در BacktestEngine
                        // اما چون private است، اینجا دوباره محاسبه می‌کنیم
                        val wins = allTrades.count { it.result == "WIN" }
                        val losses = allTrades.count { it.result == "LOSS" }
                        val expired = allTrades.count { it.result == "EXP" }
                        val decided = wins + losses
                        val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
                        val avgPnl = allTrades.map { it.pnl }.average()
                        val totalPnl = allTrades.sumOf { it.pnl }
                        
                        val winningTrades = allTrades.filter { it.pnl > 0 }
                        val losingTrades = allTrades.filter { it.pnl < 0 }
                        val avgWin = if (winningTrades.isNotEmpty()) winningTrades.map { it.pnl }.average() else 0.0
                        val avgLoss = if (losingTrades.isNotEmpty()) kotlin.math.abs(losingTrades.map { it.pnl }.average()) else 0.0
                        val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)
                        
                        val totalWins = winningTrades.sumOf { it.pnl }
                        val totalLosses = kotlin.math.abs(losingTrades.sumOf { it.pnl })
                        val profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) Double.POSITIVE_INFINITY else 0.0
                        
                        val equityCurve = mutableListOf(100.0)
                        var equity = 100.0
                        allTrades.forEach { t ->
                            equity *= (1 + t.pnl / 100.0)
                            equityCurve.add(equity)
                        }
                        
                        var maxDrawdown = 0.0
                        var peak = equityCurve[0]
                        for (e in equityCurve) {
                            if (e > peak) peak = e
                            val dd = (peak - e) / peak * 100.0
                            if (dd > maxDrawdown) maxDrawdown = dd
                        }

                        metrics = BacktestEngine.BacktestMetrics(
                            totalTrades = allTrades.size,
                            wins = wins,
                            losses = losses,
                            expired = expired,
                            winRate = winRate,
                            profitFactor = profitFactor,
                            avgPnl = avgPnl,
                            avgWin = avgWin,
                            avgLoss = avgLoss,
                            expectancy = expectancy,
                            totalPnl = totalPnl,
                            maxDrawdown = maxDrawdown,
                            equityCurve = equityCurve,
                            inSampleMetrics = null,
                            outOfSampleMetrics = null
                        )
                        
                        isRunning = false
                        progress = ""
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (selectedRanges.isEmpty()) LGr else LBlue)
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isRunning) "در حال اجرا..." else "▶ شروع بک‌تست ${if (isFutures) selectedTf else selectedHorizon}",
                fontSize = 13.sp
            )
        }

        if (isRunning) Text(progress, color = LGr, fontSize = 12.sp)

        metrics?.let { m ->
            Surface(color = LC, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "📊 نتایج ${if (isFutures) "فیوچرز $selectedTf" else "اسپات $selectedHorizon"} — ${selectedRanges.sorted().joinToString(", ")}",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    Text(analyzedInfo, fontSize = 10.sp, color = LY)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("تعداد: ${m.totalTrades}", fontSize = 11.sp, color = LGr)
                        Text("✅ برد: ${m.wins}", fontSize = 11.sp, color = LG)
                        Text("❌ باخت: ${m.losses}", fontSize = 11.sp, color = LR)
                        Text("⌛ منقضی: ${m.expired}", fontSize = 11.sp, color = LY)
                    }

                    Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", m.winRate)}", fontWeight = FontWeight.Bold, color = if (m.winRate >= 55) LG else LR)
                    Text("میانگین PnL: ${String.format(Locale.US, "%+.2f%%", m.avgPnl)}", fontWeight = FontWeight.Bold, color = if (m.avgPnl >= 0) LG else LR)
                    Text("مجموع PnL: ${String.format(Locale.US, "%+.2f%%", m.totalPnl)}", fontWeight = FontWeight.Bold, color = if (m.totalPnl >= 0) LG else LR)
                    
                    // نمایش معیارهای جدید
                    Text("میانگین سود: ${String.format(Locale.US, "%+.2f%%", m.avgWin)}", fontSize = 11.sp, color = LG)
                    Text("میانگین ضرر: ${String.format(Locale.US, "%+.2f%%", m.avgLoss)}", fontSize = 11.sp, color = LR)
                    Text("امید ریاضی: ${String.format(Locale.US, "%+.2f%%", m.expectancy)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.expectancy > 0) LG else LR)
                    Text("Profit Factor: ${String.format(Locale.US, "%.2f", m.profitFactor)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.profitFactor >= 1.5) LG else LR)
                    Text("📉 Max Drawdown: ${String.format(Locale.US, "%.2f%%", m.maxDrawdown)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.maxDrawdown < 20) LG else LR)

                    Text("💰 کارمزد: 0.1% + Slippage: 0.05% هر طرف", fontSize = 10.sp, color = LGr)
                }
            }

            Text("📋 ۳۰ سیگنال آخر (${m.totalTrades} کل):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                items(results.takeLast(30)) { r ->
                    Surface(color = LC, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${r.symbol} • ${if (r.side == "BUY") "🟢" else ""} • ${when (r.result) { "WIN" -> "✅"; "LOSS" -> "❌"; else -> "⌛" }}",
                                    fontWeight = FontWeight.Bold, fontSize = 12.sp
                                )
                                Text("امتیاز: ${r.score}", fontSize = 10.sp, color = LGr)
                            }
                            Text(
                                "${String.format(Locale.US, "%+.2f%%", r.pnl)}",
                                fontWeight = FontWeight.Bold,
                                color = if (r.pnl >= 0) LG else LR, fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (!isRunning && results.isEmpty()) {
            Text("بازه‌ها رو انتخاب کن و شروع رو بزن", color = LGr, modifier = Modifier.padding(24.dp))
        }
    }
}
