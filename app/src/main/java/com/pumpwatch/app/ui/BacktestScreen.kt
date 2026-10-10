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
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.HistoricalUniverseRepository
import com.pumpwatch.app.data.KlineCache as SharedKlineCache
import com.pumpwatch.app.data.klineSourceLabel
import com.pumpwatch.app.engine.BacktestEngine
import com.pumpwatch.app.engine.Bar
import com.pumpwatch.app.engine.ExitComparator
import com.pumpwatch.app.ui.components.GlassCard // 🚀 اضافه شد
import com.pumpwatch.app.ui.design.TabPalette // 🚀 اضافه شد
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LBlue = Color(0xFF40C4FF)

private suspend fun getKlinesCached(symbol: String, interval: String, limit: Int): List<JsonArray> {
    return try { SharedKlineCache.klines(symbol, interval, limit) } catch (_: Exception) { emptyList() }
}

private data class Tf(val label: String, val interval: String, val limit: Int, val evalLast: Int, val hold: Int)

private val FUT_TIMEFRAMES = listOf(
    Tf("۴ ساعته", "15m", 120, 16, 8), Tf("۱۲ ساعته", "30m", 120, 24, 12), Tf("۱ روزه", "1h", 168, 168, 24),
    Tf("۳ روزه", "1h", 168, 72, 24), Tf("۷ روزه", "1h", 168, 168, 48), Tf("ماهیانه", "4h", 180, 180, 60)
)

private val SPOT_HORIZONS = listOf("۱ هفته" to 7, "۲ هفته" to 14, "۱ ماه" to 30, "۳ ماه" to 90)

private val RANGES = listOf(
    "1-10" to (0 until 10), "11-20" to (10 until 20), "21-30" to (20 until 30), "31-40" to (30 until 40),
    "41-50" to (40 until 50), "51-60" to (50 until 60), "61-70" to (60 until 70), "71-80" to (70 until 80),
    "81-90" to (80 until 90), "91-100" to (90 until 100), "1-50" to (0 until 50), "51-100" to (50 until 100)
)

private data class ABResult(val symbol: String, val legacyR: Double, val engineR: Double, val legacyReason: String, val engineReason: String, val enginePartial: Boolean)

private data class ABSummary(val total: Int, val legacyAvgR: Double, val engineAvgR: Double, val legacyWins: Int, val engineWins: Int, val legacyPF: Double, val enginePF: Double, val deltaR: Double, val deltaWinRate: Double, val deltaPF: Double)

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
    var provInfo by remember { mutableStateOf("") }

    var abResults by remember { mutableStateOf<List<ABResult>>(emptyList()) }
    var abSummary by remember { mutableStateOf<ABSummary?>(null) }
    var abRunning by remember { mutableStateOf(false) }
    var abProgress by remember { mutableStateOf("") }
    var universeLabel by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🧪 بک‌تست استراتژی", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFE6EDF3))

        GlassCard(accent = TabPalette.Backtest, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (isFutures) "⚡ فیوچرز: ورود next-bar + خروج intrabar با اولویت استاپ | هزینهٔ رفت‌وبرگشت: ۰.۱۸٪ + فاندینگ ۰.۰۱٪/۸س | اهرم ۱۰× + لیکوئیدیشن"
                else "🏦 اسپات: امتیاز ≥۰ + هفتگی مثبت + OBV مثبت | ورود next-bar + خروج intrabar | هزینهٔ رفت‌وبرگشت: ۰.۳٪",
                fontSize = 11.sp, color = if (isFutures) LR else LG
            )
        }

        if (universeLabel.isNotEmpty()) {
            GlassCard(accent = TabPalette.Backtest, modifier = Modifier.fillMaxWidth()) {
                Text(universeLabel, fontSize = 9.sp, color = LY, lineHeight = 14.sp)
            }
        }

        if (isFutures) {
            Text("⏱ بازه زمانی:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FUT_TIMEFRAMES.take(3).forEach { tf ->
                    FilterChip(selected = selectedTf == tf.label, onClick = { selectedTf = tf.label }, label = { Text(tf.label, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LY.copy(alpha = 0.3f)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FUT_TIMEFRAMES.drop(3).forEach { tf ->
                    FilterChip(selected = selectedTf == tf.label, onClick = { selectedTf = tf.label }, label = { Text(tf.label, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LY.copy(alpha = 0.3f)))
                }
            }
        } else {
            Text("📅 افق نگهداری:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SPOT_HORIZONS.forEach { (label, _) ->
                    FilterChip(selected = selectedHorizon == label, onClick = { selectedHorizon = label }, label = { Text(label, fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LY.copy(alpha = 0.3f)))
                }
            }
        }

        Text("🏆 بازه رتبه ارزها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RANGES.forEach { (label, range) ->
                FilterChip(selected = label in selectedRanges, onClick = { selectedRanges = if (label in selectedRanges) selectedRanges - label else selectedRanges + label; errorMsg = null }, label = { Text("$label (${range.last - range.first + 1} ارز)", fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LBlue), modifier = Modifier.fillMaxWidth())
            }
        }

        if (selectedRanges.isNotEmpty()) {
            Text("✅ ${if (isFutures) selectedTf else selectedHorizon} | ${selectedRanges.sorted().joinToString(", ")}", fontSize = 11.sp, color = LG, fontWeight = FontWeight.Bold)
        }

        errorMsg?.let { msg -> Text(msg, color = LR, fontSize = 12.sp, fontWeight = FontWeight.Bold) }

        Button(onClick = {
            if (selectedRanges.isEmpty()) { errorMsg = "⚠️ حداقل یک بازه رتبه انتخاب کن!"; return@Button }
            if (!isRunning) {
                isRunning = true; results = emptyList(); metrics = null; errorMsg = null
                scope.launch {
                    val allTrades = mutableListOf<BacktestEngine.Trade>()
                    val sourcesUsed = mutableSetOf<String>()
                    val allCoins = withContext(Dispatchers.IO) { try { ApiClient.getTop1000Coins() } catch (e: Exception) { emptyList() } }
                    if (allCoins.isEmpty()) { errorMsg = "❌ خطا در دریافت لیست ارزها"; isRunning = false; return@launch }

                    HistoricalUniverseRepository.recordSnapshot(ctx, allCoins)
                    val periodStartMs = System.currentTimeMillis() - (if (isFutures) 30L else 90L) * 86_400_000L
                    val universe = HistoricalUniverseRepository.resolve(ctx, periodStartMs, allCoins)
                    val coinById = allCoins.associateBy { it.id }

                    universeLabel = when (universe.source) {
                        HistoricalUniverseRepository.UniverseSource.POINT_IN_TIME -> "🌐 universe: snapshot نقطه‌درزمان روز شروع بازه • پوشش تاریخی: ${universe.coveragePct}٪"
                        HistoricalUniverseRepository.UniverseSource.NEAREST_SNAPSHOT -> "🌐 universe: نزدیک‌ترین snapshot قبلی (نه دقیق روز شروع) • پوشش: ${universe.coveragePct}٪"
                        HistoricalUniverseRepository.UniverseSource.TODAY_BIASED -> "⚠️ universe: عضویت امروز بازار (سوگیری بقا) — هنوز snapshot تاریخی کافی جمع نشده • پوشش: ${universe.coveragePct}٪"
                    }

                    val allIndices = mutableSetOf<Int>()
                    selectedRanges.forEach { label -> RANGES.find { it.first == label }?.second?.forEach { allIndices.add(it) } }

                    var untestable = 0
                    val coinsToTest = mutableListOf<Pair<Int, CoinMarket>>()
                    for (idx in allIndices.sorted()) {
                        val id = universe.rankedIds.getOrNull(idx)
                        if (id == null) { untestable++; continue }
                        val coin = coinById[id]
                        if (coin == null) { untestable++; continue }
                        coinsToTest.add(idx to coin)
                    }

                    if (coinsToTest.isEmpty()) { errorMsg = "❌ هیچ ارز قابل‌آزمونی در بازهٔ انتخابی نیست (universe: ${universe.source})"; isRunning = false; return@launch }

                    var processed = 0; var analyzed = 0
                    for ((idx, coin) in coinsToTest) {
                        val symbol = coin.symbol.uppercase(Locale.US)
                        progress = "در حال تحلیل $symbol (${processed + 1}/${coinsToTest.size})..."
                        if (isFutures) {
                            val tf = FUT_TIMEFRAMES.find { it.label == selectedTf } ?: FUT_TIMEFRAMES[2]
                            val klines = withContext(Dispatchers.IO) { getKlinesCached("${symbol}USDT", tf.interval, tf.limit) }
                            sourcesUsed.add(klineSourceLabel(BinanceClient.api.lastSource(symbol)))
                            if (klines.size >= 60) {
                                analyzed++
                                val klinesList = klines.map { k -> listOf(k[1].asDouble, k[2].asDouble, k[3].asDouble, k[4].asDouble, k[5].asDouble) }
                                val (trades, _) = BacktestEngine.runFutures(symbol, klinesList, tf.evalLast, tf.hold, feeRate = 0.0004, leverage = 10, fundingRate = 0.0001)
                                allTrades.addAll(trades)
                            }
                        } else {
                            val hold = SPOT_HORIZONS.find { it.first == selectedHorizon }?.second ?: 30
                            val klines = withContext(Dispatchers.IO) { getKlinesCached("${symbol}USDT", "1d", 300) }
                            sourcesUsed.add(klineSourceLabel(BinanceClient.api.lastSource(symbol)))
                            if (klines.size >= 210) {
                                analyzed++
                                val klinesList = klines.map { k -> listOf(k[1].asDouble, k[2].asDouble, k[3].asDouble, k[4].asDouble, k[5].asDouble) }
                                val (trades, _) = BacktestEngine.runSpot(symbol, klinesList, hold)
                                allTrades.addAll(trades)
                            }
                        }
                        processed++; delay(150)
                    }

                    analyzedInfo = "ارزهای تحلیل‌شده: $analyzed از ${coinsToTest.size}" + (if (untestable > 0) " • غیرقابل‌آزمون (حذف‌شده/بدون داده): $untestable" else "")
                    results = allTrades
                    provInfo = "🕯️ منابع کندل: ${sourcesUsed.sorted().joinToString("، ")} • " + (if (isFutures) "هزینهٔ رفت‌وبرگشت: ۰.۱۸٪ + فاندینگ ۰.۰۱٪/۸س | اهرم ۱۰× + لیکوئیدیشن" else "هزینهٔ رفت‌وبرگشت: ۰.۳٪ (۰.۱٪ کارمزد + ۰.۰۵٪ اسلیپیج هر طرف)") + " • ورود: next-bar • خروج: intrabar با اولویت استاپ"

                    val wins = allTrades.count { it.result == "WIN" }; val losses = allTrades.count { it.result == "LOSS" }
                    val expired = allTrades.count { it.result == "EXP" }; val liquidated = allTrades.count { it.result == "LIQUIDATED" }
                    val decided = wins + losses; val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
                    val avgPnl = if (allTrades.isEmpty()) 0.0 else allTrades.map { it.pnl }.average(); val totalPnl = allTrades.sumOf { it.pnl }

                    val winningTrades = allTrades.filter { it.pnl > 0 }; val losingTrades = allTrades.filter { it.pnl < 0 }
                    val avgWin = if (winningTrades.isNotEmpty()) winningTrades.map { it.pnl }.average() else 0.0
                    val avgLoss = if (losingTrades.isNotEmpty()) kotlin.math.abs(losingTrades.map { it.pnl }.average()) else 0.0
                    val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)

                    val totalWins = winningTrades.sumOf { it.pnl }; val totalLosses = kotlin.math.abs(losingTrades.sumOf { it.pnl })
                    val profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) Double.POSITIVE_INFINITY else 0.0

                    val equityCurve = mutableListOf(100.0); var equity = 100.0
                    allTrades.forEach { t -> equity *= (1 + t.pnl / 100.0); equityCurve.add(equity) }

                    var maxDrawdown = 0.0; var peak = equityCurve[0]
                    for (e in equityCurve) { if (e > peak) peak = e; val dd = (peak - e) / peak * 100.0; if (dd > maxDrawdown) maxDrawdown = dd }

                    metrics = BacktestEngine.BacktestMetrics(totalTrades = allTrades.size, wins = wins, losses = losses, expired = expired, liquidated = liquidated, winRate = winRate, profitFactor = profitFactor, avgPnl = avgPnl, avgWin = avgWin, avgLoss = avgLoss, expectancy = expectancy, totalPnl = totalPnl, maxDrawdown = maxDrawdown, equityCurve = equityCurve, inSampleMetrics = null, outOfSampleMetrics = null)
                    isRunning = false; progress = ""
                }
            }
        }, enabled = !isRunning, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (selectedRanges.isEmpty()) LGr else TabPalette.Backtest)) {
            if (isRunning) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (isRunning) "در حال اجرا..." else "▶ شروع بک‌تست ${if (isFutures) selectedTf else selectedHorizon}", fontSize = 13.sp, color = Color.Black)
        }

        if (isRunning) Text(progress, color = LGr, fontSize = 12.sp)

        if (!isRunning && selectedRanges.isNotEmpty()) {
            Button(onClick = {
                if (!abRunning) {
                    abRunning = true; abResults = emptyList(); abSummary = null
                    scope.launch {
                        val allCoins = withContext(Dispatchers.IO) { try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() } }
                        if (allCoins.isEmpty()) { abRunning = false; return@launch }

                        HistoricalUniverseRepository.recordSnapshot(ctx, allCoins)
                        val periodStartMs = System.currentTimeMillis() - 90L * 86_400_000L
                        val universe = HistoricalUniverseRepository.resolve(ctx, periodStartMs, allCoins)
                        val coinById = allCoins.associateBy { it.id }

                        val allIndices = mutableSetOf<Int>()
                        selectedRanges.forEach { label -> RANGES.find { it.first == label }?.second?.forEach { allIndices.add(it) } }
                        val coinsToTest = mutableListOf<Pair<Int, CoinMarket>>()
                        for (idx in allIndices.sorted()) {
                            val id = universe.rankedIds.getOrNull(idx) ?: continue
                            val coin = coinById[id] ?: continue
                            coinsToTest.add(idx to coin)
                        }

                        val abList = mutableListOf<ABResult>(); var processed = 0
                        for ((idx, coin) in coinsToTest) {
                            val symbol = coin.symbol.uppercase(Locale.US)
                            abProgress = "مقایسه $symbol (${processed + 1}/${coinsToTest.size})..."
                            val tf = if (isFutures) FUT_TIMEFRAMES.find { it.label == selectedTf } ?: FUT_TIMEFRAMES[2] else Tf(selectedHorizon, "1d", 100, 0, SPOT_HORIZONS.find { it.first == selectedHorizon }?.second ?: 30)
                            val klines = withContext(Dispatchers.IO) { getKlinesCached("${symbol}USDT", tf.interval, tf.limit) }
                            if (klines.size >= 60) {
                                val bars = klines.map { k -> Bar(k[1].asDouble, k[2].asDouble, k[3].asDouble, k[4].asDouble) }
                                val entryBar = 20
                                if (entryBar < bars.size - 10) {
                                    val entry = bars[entryBar].c; val stop = entry * 0.95; val t1 = entry * 1.10; val t2 = entry * 1.20; val side = "PUMP"
                                    val postBars = bars.subList(entryBar + 1, bars.size)
                                    val legacy = ExitComparator.replayLegacy(postBars, side, entry, stop, t1, t2)
                                    val engine = ExitComparator.replayEngine(postBars, side, entry, stop, t1, t2)
                                    abList.add(ABResult(symbol = symbol, legacyR = legacy.realizedR, engineR = engine.realizedR, legacyReason = legacy.exitReason, engineReason = engine.exitReason, enginePartial = engine.partialTaken))
                                }
                            }
                            processed++; delay(100)
                        }

                        abResults = abList
                        abSummary = if (abList.isNotEmpty()) {
                            val legacyAvgR = abList.map { it.legacyR }.average(); val engineAvgR = abList.map { it.engineR }.average()
                            val legacyWins = abList.count { it.legacyR > 0 }; val engineWins = abList.count { it.engineR > 0 }
                            val legacyTotalWins = abList.filter { it.legacyR > 0 }.sumOf { it.legacyR }; val legacyTotalLosses = kotlin.math.abs(abList.filter { it.legacyR < 0 }.sumOf { it.legacyR })
                            val engineTotalWins = abList.filter { it.engineR > 0 }.sumOf { it.engineR }; val engineTotalLosses = kotlin.math.abs(abList.filter { it.engineR < 0 }.sumOf { it.engineR })
                            val legacyPF = if (legacyTotalLosses > 0) legacyTotalWins / legacyTotalLosses else if (legacyTotalWins > 0) Double.POSITIVE_INFINITY else 0.0
                            val enginePF = if (engineTotalLosses > 0) engineTotalWins / engineTotalLosses else if (engineTotalWins > 0) Double.POSITIVE_INFINITY else 0.0
                            ABSummary(total = abList.size, legacyAvgR = legacyAvgR, engineAvgR = engineAvgR, legacyWins = legacyWins, engineWins = engineWins, legacyPF = legacyPF, enginePF = enginePF, deltaR = engineAvgR - legacyAvgR, deltaWinRate = (engineWins * 100.0 / abList.size) - (legacyWins * 100.0 / abList.size), deltaPF = enginePF - legacyPF)
                        } else null
                        abRunning = false; abProgress = ""
                    }
                }
            }, enabled = !abRunning, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = LY.copy(alpha = 0.3f))) {
                if (abRunning) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (abRunning) "در حال مقایسه..." else "🔬 مقایسهٔ A/B سیاست‌های خروج", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        if (abRunning) Text(abProgress, color = LGr, fontSize = 12.sp)

        abSummary?.let { s ->
            GlassCard(accent = TabPalette.Backtest, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📊 مقایسهٔ سیاست‌های خروج (${s.total} ترید)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE6EDF3))
                    Text("LEGACY (قبل از Commit 10):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LGr)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("بردها: ${s.legacyWins}", fontSize = 10.sp, color = LG); Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", s.legacyWins * 100.0 / s.total)}", fontSize = 10.sp, color = if (s.legacyWins * 100.0 / s.total >= 50) LG else LR); Text("PF: ${String.format(Locale.US, "%.2f", s.legacyPF)}", fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("میانگین R: ${String.format(Locale.US, "%+.2f", s.legacyAvgR)}", fontSize = 10.sp, color = if (s.legacyAvgR >= 0) LG else LR)
                    }
                    Text("ENGINE (الان):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LBlue)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("بردها: ${s.engineWins}", fontSize = 10.sp, color = LG); Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", s.engineWins * 100.0 / s.total)}", fontSize = 10.sp, color = if (s.engineWins * 100.0 / s.total >= 50) LG else LR); Text("PF: ${String.format(Locale.US, "%.2f", s.enginePF)}", fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("میانگین R: ${String.format(Locale.US, "%+.2f", s.engineAvgR)}", fontSize = 10.sp, color = if (s.engineAvgR >= 0) LG else LR)
                    }
                    HorizontalDivider(color = LGr.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("🎯 بهبود:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LY)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("ΔR: ${String.format(Locale.US, "%+.2f", s.deltaR)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.deltaR > 0) LG else LR)
                        Text("Δوین‌ریت: ${String.format(Locale.US, "%+.1f%%", s.deltaWinRate)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.deltaWinRate > 0) LG else LR)
                        Text("ΔPF: ${String.format(Locale.US, "%+.2f", s.deltaPF)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.deltaPF > 0) LG else LR)
                    }
                    if (s.deltaR > 0 || s.deltaWinRate > 0 || s.deltaPF > 0) { Text("✅ سیاست جدید بهتر است", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LG, modifier = Modifier.padding(top = 4.dp)) }
                    else { Text("⚠️ سیاست جدید مزیت قابل‌توجهی ندارد (روی این داده‌ها)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LY, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }

        metrics?.let { m ->
            GlassCard(accent = TabPalette.Backtest, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊 نتایج ${if (isFutures) "فیوچرز $selectedTf" else "اسپات $selectedHorizon"} — ${selectedRanges.sorted().joinToString(", ")}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE6EDF3))
                    Text(analyzedInfo, fontSize = 10.sp, color = LY)
                    if (provInfo.isNotEmpty()) Text(provInfo, fontSize = 9.sp, color = LGr, lineHeight = 14.sp)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("تعداد: ${m.totalTrades}", fontSize = 11.sp, color = LGr); Text("✅ برد: ${m.wins}", fontSize = 11.sp, color = LG); Text("❌ باخت: ${m.losses}", fontSize = 11.sp, color = LR); Text("⌛ منقضی: ${m.expired}", fontSize = 11.sp, color = LY)
                    }
                    if (isFutures) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) { Text("💀 لیکوئید: ${m.liquidated}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (m.liquidated > 0) Color(0xFFBA68C8) else LGr) } }

                    Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", m.winRate)}", fontWeight = FontWeight.Bold, color = if (m.winRate >= 55) LG else LR)
                    Text("میانگین PnL: ${String.format(Locale.US, "%+.2f%%", m.avgPnl)}", fontWeight = FontWeight.Bold, color = if (m.avgPnl >= 0) LG else LR)
                    Text("مجموع PnL: ${String.format(Locale.US, "%+.2f%%", m.totalPnl)}", fontWeight = FontWeight.Bold, color = if (m.totalPnl >= 0) LG else LR)
                    Text("میانگین سود: ${String.format(Locale.US, "%+.2f%%", m.avgWin)}", fontSize = 11.sp, color = LG)
                    Text("میانگین ضرر: ${String.format(Locale.US, "%+.2f%%", m.avgLoss)}", fontSize = 11.sp, color = LR)
                    Text("امید ریاضی: ${String.format(Locale.US, "%+.2f%%", m.expectancy)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.expectancy > 0) LG else LR)
                    Text("Profit Factor: ${String.format(Locale.US, "%.2f", m.profitFactor)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.profitFactor >= 1.5) LG else LR)
                    Text("📉 Max Drawdown: ${String.format(Locale.US, "%.2f%%", m.maxDrawdown)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.maxDrawdown < 20) LG else LR)

                    if (isFutures && m.liquidated > m.totalTrades * 0.1 && m.totalTrades > 0) {
                        GlassCard(accent = TabPalette.Backtest, modifier = Modifier.fillMaxWidth()) {
                            Text("⚠️ هشدار: ${m.liquidated} لیکوئیدیشن از ${m.totalTrades} ترید — اهرم یا فاصلهٔ استاپ نیاز به بازنگری دارد", fontSize = 10.sp, color = Color(0xFFBA68C8), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text("📋 ۳۰ سیگنال آخر (${m.totalTrades} کل):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE6EDF3))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                items(results.takeLast(30)) { r ->
                    GlassCard(accent = TabPalette.Backtest, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                val resultEmoji = when (r.result) { "WIN" -> "✅"; "LOSS" -> "❌"; "LIQUIDATED" -> "💀"; else -> "⌛" }
                                val resultColor = when (r.result) { "WIN" -> LG; "LOSS" -> LR; "LIQUIDATED" -> Color(0xFFBA68C8); else -> LY }
                                Text("${r.symbol} • ${if (r.side == "BUY") "🟢" else "🔴"} • $resultEmoji ${r.result}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = resultColor)
                                Text("امتیاز: ${r.score}", fontSize = 10.sp, color = LGr)
                            }
                            Text("${String.format(Locale.US, "%+.2f%%", r.pnl)}", fontWeight = FontWeight.Bold, color = if (r.pnl >= 0) LG else (if (r.result == "LIQUIDATED") Color(0xFFBA68C8) else LR), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (!isRunning && results.isEmpty() && !abRunning && abResults.isEmpty()) {
            Text("بازه‌ها رو انتخاب کن و شروع رو بزن", color = LGr, modifier = Modifier.padding(24.dp))
        }
    }
}
