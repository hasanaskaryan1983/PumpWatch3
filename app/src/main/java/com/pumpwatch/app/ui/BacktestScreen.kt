package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.pumpwatch.app.ui.EditorialPageHeader
import com.pumpwatch.app.ui.PumpWatchPalette
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

// 🚀 P0 Tab 05/16: وضعیت تایپ‌شدهٔ بک‌تست (Idle/Running/Loaded/Error)
internal sealed class BacktestLoadState {
    object Idle : BacktestLoadState()
    data class Running(val progress: String) : BacktestLoadState()
    data class Loaded(
        val results: List<BacktestEngine.Trade>,
        val metrics: BacktestEngine.BacktestMetrics,
        val analyzedInfo: String,
        val provInfo: String,
        val universeLabel: String
    ) : BacktestLoadState()
    data class Error(val message: String) : BacktestLoadState()
}

@Composable
fun BacktestScreen() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pumpwatch_prefs", 0) }
    val isFutures = prefs.getString("mode", "SPOT") == "FUTURES"

    val scope = rememberCoroutineScope()
    var loadState by remember { mutableStateOf<BacktestLoadState>(BacktestLoadState.Idle) }
    var selectedRanges by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTf by remember { mutableStateOf("۱ روزه") }
    var selectedHorizon by remember { mutableStateOf("۱ ماه") }

    var abResults by remember { mutableStateOf<List<ABResult>>(emptyList()) }
    var abSummary by remember { mutableStateOf<ABSummary?>(null) }
    var abRunning by remember { mutableStateOf(false) }
    var abProgress by remember { mutableStateOf("") }

    val isRunning = loadState is BacktestLoadState.Running

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 🚀 P0 Tab 05/16: عنوان پژوهشی + subtitle صادقانه
        EditorialPageHeader(
            eyebrow = "RESEARCH · BACKTEST",
            title = "آزمایش تاریخی استراتژی",
            subtitle = if (isFutures) "شبیه‌سازی گذشته بر اساس داده‌های تاریخی — ورود next-bar + خروج intrabar | این آزمون نتایج آینده را تضمین نمی‌کند"
            else "شبیه‌سازی گذشته بر اساس داده‌های تاریخی — امتیاز ≥۰ + هفتگی مثبت + OBV مثبت | این آزمون نتایج آینده را تضمین نمی‌کند",
            accent = PumpWatchPalette.TabBacktest
        )

        // 🚀 P0 Tab 05/16: Disclaimer شفاف دربارهٔ محدودیت‌های بک‌تست
        GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "⚠️ محدودیت‌های آزمایش تاریخی (حتماً بخوانید)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PumpWatchPalette.Warning
                )
                Text(
                    "• داده‌های تاریخی ممکن است **سوگیری بقا (Survivorship Bias)** داشته باشند؛ ارزهایی که حذف شده‌اند در نتایج نیستند.\n" +
                    "• هزینه‌های رفت‌وبرگشت (Fee + Slippage + Funding) **تقریبی** و ثابت در نظر گرفته شده‌اند.\n" +
                    "• **عملکرد گذشته هرگز تضمین‌کنندهٔ عملکرد آینده نیست**؛ بازار ساختار خود را تغییر می‌دهد.\n" +
                    "• اسلیپیج واقعی، تأخیر اجرایی، و رفتار OrderBook در این شبیه‌سازی مدل‌سازی نشده‌اند.\n" +
                    "• Max Drawdown بر اساس ترتیب زمانی معاملات محاسبه می‌شود و ممکن است با تجربهٔ واقعی متفاوت باشد.",
                    fontSize = 9.sp,
                    color = PumpWatchPalette.Muted,
                    lineHeight = 14.sp
                )
            }
        }

        GlassCard(accent = PumpWatchPalette.TabBacktest, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (isFutures) "⚡ فیوچرز: ورود next-bar + خروج intrabar با اولویت استاپ | هزینهٔ رفت‌وبرگشت **تقریبی**: ۰.۱۸٪ + فاندینگ ۰.۰۱٪/۸س | اهرم ۱۰× + لیکوئیدیشن"
                else "🏦 اسپات: امتیاز ≥۰ + هفتگی مثبت + OBV مثبت | ورود next-bar + خروج intrabar | هزینهٔ رفت‌وبرگشت **تقریبی**: ۰.۳٪",
                fontSize = 11.sp, color = if (isFutures) PumpWatchPalette.Info else PumpWatchPalette.Positive
            )
        }

        // نمایش وضعیت universe از آخرین اجرا
        val currentUniverseLabel = (loadState as? BacktestLoadState.Loaded)?.universeLabel
        if (!currentUniverseLabel.isNullOrEmpty()) {
            GlassCard(accent = PumpWatchPalette.TabBacktest, modifier = Modifier.fillMaxWidth()) {
                Text(currentUniverseLabel, fontSize = 9.sp, color = PumpWatchPalette.Warning, lineHeight = 14.sp)
            }
        }

        if (isFutures) {
            Text("⏱ بازه زمانی:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FUT_TIMEFRAMES.take(3).forEach { tf ->
                    FilterChip(
                        selected = selectedTf == tf.label,
                        onClick = { selectedTf = tf.label },
                        label = { Text(tf.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PumpWatchPalette.TabBacktest,
                            selectedLabelColor = PumpWatchPalette.Ink,
                            containerColor = PumpWatchPalette.Glass,
                            labelColor = PumpWatchPalette.Muted
                        )
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FUT_TIMEFRAMES.drop(3).forEach { tf ->
                    FilterChip(
                        selected = selectedTf == tf.label,
                        onClick = { selectedTf = tf.label },
                        label = { Text(tf.label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PumpWatchPalette.TabBacktest,
                            selectedLabelColor = PumpWatchPalette.Ink,
                            containerColor = PumpWatchPalette.Glass,
                            labelColor = PumpWatchPalette.Muted
                        )
                    )
                }
            }
        } else {
            Text("📅 افق نگهداری:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SPOT_HORIZONS.forEach { (label, _) ->
                    FilterChip(
                        selected = selectedHorizon == label,
                        onClick = { selectedHorizon = label },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PumpWatchPalette.TabBacktest,
                            selectedLabelColor = PumpWatchPalette.Ink,
                            containerColor = PumpWatchPalette.Glass,
                            labelColor = PumpWatchPalette.Muted
                        )
                    )
                }
            }
        }

        Text("🏆 بازه رتبه ارزها:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RANGES.forEach { (label, range) ->
                FilterChip(
                    selected = label in selectedRanges,
                    onClick = {
                        selectedRanges = if (label in selectedRanges) selectedRanges - label else selectedRanges + label
                    },
                    label = { Text("$label (${range.last - range.first + 1} ارز)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.Info,
                        selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass,
                        labelColor = PumpWatchPalette.Muted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedRanges.isNotEmpty()) {
            Text("✅ ${if (isFutures) selectedTf else selectedHorizon} | ${selectedRanges.sorted().joinToString(", ")}", fontSize = 11.sp, color = PumpWatchPalette.Positive, fontWeight = FontWeight.Bold)
        }

        // 🚀 P0 Tab 05/16: نمایش خطا در صورت وجود
        (loadState as? BacktestLoadState.Error)?.let { err ->
            Surface(color = PumpWatchPalette.Negative.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                Text(err.message, color = PumpWatchPalette.Negative, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp))
            }
        }

        Button(
            onClick = {
                if (selectedRanges.isEmpty()) {
                    loadState = BacktestLoadState.Error("⚠️ حداقل یک بازه رتبه انتخاب کنید")
                    return@Button
                }
                if (!isRunning) {
                    loadState = BacktestLoadState.Running("در حال آماده‌سازی...")
                    scope.launch {
                        try {
                            val allTrades = mutableListOf<BacktestEngine.Trade>()
                            val sourcesUsed = mutableSetOf<String>()
                            val allCoins = withContext(Dispatchers.IO) { try { ApiClient.getTop1000Coins() } catch (e: Exception) { emptyList() } }
                            if (allCoins.isEmpty()) {
                                loadState = BacktestLoadState.Error("❌ خطا در دریافت لیست ارزها (ممکن است دادهٔ بازار در دسترس نباشد)")
                                return@launch
                            }

                            HistoricalUniverseRepository.recordSnapshot(ctx, allCoins)
                            val periodStartMs = System.currentTimeMillis() - (if (isFutures) 30L else 90L) * 86_400_000L
                            val universe = HistoricalUniverseRepository.resolve(ctx, periodStartMs, allCoins)
                            val coinById = allCoins.associateBy { it.id }

                            val universeLabel = when (universe.source) {
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

                            if (coinsToTest.isEmpty()) {
                                loadState = BacktestLoadState.Error("❌ هیچ ارز قابل‌آزمونی در بازهٔ انتخابی نیست (universe: ${universe.source})")
                                return@launch
                            }

                            var processed = 0; var analyzed = 0
                            for ((idx, coin) in coinsToTest) {
                                val symbol = coin.symbol.uppercase(Locale.US)
                                loadState = BacktestLoadState.Running("در حال تحلیل $symbol (${processed + 1}/${coinsToTest.size})...")
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

                            val analyzedInfo = "ارزهای تحلیل‌شده: $analyzed از ${coinsToTest.size}" + (if (untestable > 0) " • غیرقابل‌آزمون (حذف‌شده/بدون داده): $untestable" else "")
                            val provInfo = "🕯️ منابع کندل: ${sourcesUsed.sorted().joinToString("، ")} • " + (if (isFutures) "هزینهٔ رفت‌وبرگشت **تقریبی**: ۰.۱۸٪ + فاندینگ ۰.۰۱٪/۸س | اهرم ۱۰× + لیکوئیدیشن" else "هزینهٔ رفت‌وبرگشت **تقریبی**: ۰.۳٪ (۰.۱٪ کارمزد + ۰.۰۵٪ اسلیپیج هر طرف)") + " • ورود: next-bar • خروج: intrabar با اولویت استاپ"

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

                            val metrics = BacktestEngine.BacktestMetrics(totalTrades = allTrades.size, wins = wins, losses = losses, expired = expired, liquidated = liquidated, winRate = winRate, profitFactor = profitFactor, avgPnl = avgPnl, avgWin = avgWin, avgLoss = avgLoss, expectancy = expectancy, totalPnl = totalPnl, maxDrawdown = maxDrawdown, equityCurve = equityCurve, inSampleMetrics = null, outOfSampleMetrics = null)
                            loadState = BacktestLoadState.Loaded(
                                results = allTrades,
                                metrics = metrics,
                                analyzedInfo = analyzedInfo,
                                provInfo = provInfo,
                                universeLabel = universeLabel
                            )
                        } catch (t: Throwable) {
                            loadState = BacktestLoadState.Error("⚠️ خطا در اجرای بک‌تست: ${t.message ?: "نامشخص"}")
                        }
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedRanges.isEmpty()) PumpWatchPalette.Muted else PumpWatchPalette.TabBacktest,
                contentColor = PumpWatchPalette.Ink
            )
        ) {
            if (isRunning) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PumpWatchPalette.Ink, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (isRunning) "در حال اجرا..." else "▶ شروع آزمایش تاریخی ${if (isFutures) selectedTf else selectedHorizon}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        if (isRunning) {
            Text((loadState as? BacktestLoadState.Running)?.progress ?: "", color = PumpWatchPalette.Muted, fontSize = 12.sp)
        }

        if (!isRunning && selectedRanges.isNotEmpty()) {
            Button(
                onClick = {
                    if (!abRunning) {
                        abRunning = true; abResults = emptyList(); abSummary = null
                        scope.launch {
                            try {
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
                            } catch (_: Throwable) { }
                            abRunning = false; abProgress = ""
                        }
                    }
                },
                enabled = !abRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PumpWatchPalette.Warning.copy(alpha = 0.9f),
                    contentColor = PumpWatchPalette.Ink
                )
            ) {
                if (abRunning) { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PumpWatchPalette.Ink, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (abRunning) "در حال مقایسه..." else "🔬 مقایسهٔ A/B سیاست‌های خروج", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (abRunning) Text(abProgress, color = PumpWatchPalette.Muted, fontSize = 12.sp)

        abSummary?.let { s ->
            GlassCard(accent = PumpWatchPalette.TabBacktest, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📊 مقایسهٔ سیاست‌های خروج (${s.total} آزمایش)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text)
                    Text("LEGACY (قبل از Commit 10):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Muted)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("بردها: ${s.legacyWins}", fontSize = 10.sp, color = PumpWatchPalette.Positive)
                        Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", s.legacyWins * 100.0 / s.total)}", fontSize = 10.sp, color = if (s.legacyWins * 100.0 / s.total >= 50) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                        Text("PF: ${String.format(Locale.US, "%.2f", s.legacyPF)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                        Text("میانگین R: ${String.format(Locale.US, "%+.2f", s.legacyAvgR)}", fontSize = 10.sp, color = if (s.legacyAvgR >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    }
                    Text("ENGINE (الان):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Info)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("بردها: ${s.engineWins}", fontSize = 10.sp, color = PumpWatchPalette.Positive)
                        Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", s.engineWins * 100.0 / s.total)}", fontSize = 10.sp, color = if (s.engineWins * 100.0 / s.total >= 50) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                        Text("PF: ${String.format(Locale.US, "%.2f", s.enginePF)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                        Text("میانگین R: ${String.format(Locale.US, "%+.2f", s.engineAvgR)}", fontSize = 10.sp, color = if (s.engineAvgR >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    }
                    HorizontalDivider(color = PumpWatchPalette.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("🎯 بهبود:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("ΔR: ${String.format(Locale.US, "%+.2f", s.deltaR)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.deltaR > 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                        Text("Δوین‌ریت: ${String.format(Locale.US, "%+.1f%%", s.deltaWinRate)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.deltaWinRate > 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                        Text("ΔPF: ${String.format(Locale.US, "%+.2f", s.deltaPF)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (s.deltaPF > 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    }
                    if (s.deltaR > 0 || s.deltaWinRate > 0 || s.deltaPF > 0) {
                        Text("✅ سیاست جدید روی داده‌های تاریخی بهتر عمل کرده است", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Positive, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text("⚠️ سیاست جدید مزیت قابل‌توجهی روی این داده‌ها ندارد (ممکن است در بازار واقعی متفاوت باشد)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        // 🚀 P0 Tab 05/16: نمایش نتایج از LoadState
        (loadState as? BacktestLoadState.Loaded)?.let { loaded ->
            val m = loaded.metrics
            GlassCard(accent = PumpWatchPalette.TabBacktest, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📊 نتایج ${if (isFutures) "فیوچرز $selectedTf" else "اسپات $selectedHorizon"} — ${selectedRanges.sorted().joinToString(", ")}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text)
                    Text(loaded.analyzedInfo, fontSize = 10.sp, color = PumpWatchPalette.Warning)
                    if (loaded.provInfo.isNotEmpty()) Text(loaded.provInfo, fontSize = 9.sp, color = PumpWatchPalette.Muted, lineHeight = 14.sp)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("تعداد: ${m.totalTrades}", fontSize = 11.sp, color = PumpWatchPalette.Muted)
                        Text("✅ برد: ${m.wins}", fontSize = 11.sp, color = PumpWatchPalette.Positive)
                        Text("❌ باخت: ${m.losses}", fontSize = 11.sp, color = PumpWatchPalette.Negative)
                        Text("⌛ منقضی: ${m.expired}", fontSize = 11.sp, color = PumpWatchPalette.Warning)
                    }
                    if (isFutures) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                            Text("💀 لیکوئید: ${m.liquidated}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (m.liquidated > 0) PumpWatchPalette.Purple else PumpWatchPalette.Muted)
                        }
                    }

                    Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", m.winRate)}", fontWeight = FontWeight.Bold, color = if (m.winRate >= 55) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    // 🚀 P0 Tab 05/16: برچسب تقریبی روی PnL‌ها
                    Text("میانگین تغییر قیمت (تقریبی): ${String.format(Locale.US, "%+.2f%%", m.avgPnl)}", fontWeight = FontWeight.Bold, color = if (m.avgPnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    Text("مجموع تغییر قیمت (تقریبی): ${String.format(Locale.US, "%+.2f%%", m.totalPnl)}", fontWeight = FontWeight.Bold, color = if (m.totalPnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    Text("میانگین سود: ${String.format(Locale.US, "%+.2f%%", m.avgWin)}", fontSize = 11.sp, color = PumpWatchPalette.Positive)
                    Text("میانگین ضرر: ${String.format(Locale.US, "%+.2f%%", m.avgLoss)}", fontSize = 11.sp, color = PumpWatchPalette.Negative)
                    Text("امید ریاضی (تقریبی): ${String.format(Locale.US, "%+.2f%%", m.expectancy)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.expectancy > 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    Text("Profit Factor (تقریبی): ${String.format(Locale.US, "%.2f", m.profitFactor)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.profitFactor >= 1.5) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    Text("📉 Max Drawdown: ${String.format(Locale.US, "%.2f%%", m.maxDrawdown)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.maxDrawdown < 20) PumpWatchPalette.Positive else PumpWatchPalette.Negative)

                    if (isFutures && m.liquidated > m.totalTrades * 0.1 && m.totalTrades > 0) {
                        GlassCard(accent = PumpWatchPalette.Purple, modifier = Modifier.fillMaxWidth()) {
                            Text("⚠️ هشدار: ${m.liquidated} لیکوئیدیشن از ${m.totalTrades} آزمایش — اهرم یا فاصلهٔ استاپ نیاز به بازنگری دارد", fontSize = 10.sp, color = PumpWatchPalette.Purple, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 🚀 P0 Tab 05/16: یادداشت شفاف دربارهٔ Max Drawdown
                    Surface(color = PumpWatchPalette.Info.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "💡 توجه: Max Drawdown بر اساس ترتیب زمانی معاملات تاریخی محاسبه شده و ممکن است با تجربهٔ واقعی (به‌ویژه با اسلیپیج و تأخیر اجرا) متفاوت باشد. این عدد یک **برآورد محافظه‌کارانه** است، نه یک تضمین.",
                            fontSize = 9.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp), textAlign = TextAlign.Start
                        )
                    }
                }
            }

            Text("📋 ۳۰ آزمایش آخر (${m.totalTrades} کل):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PumpWatchPalette.Text)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                items(loaded.results.takeLast(30)) { r ->
                    GlassCard(accent = PumpWatchPalette.TabBacktest, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                val resultEmoji = when (r.result) { "WIN" -> "✅"; "LOSS" -> "❌"; "LIQUIDATED" -> "💀"; else -> "⌛" }
                                val resultColor = when (r.result) { "WIN" -> PumpWatchPalette.Positive; "LOSS" -> PumpWatchPalette.Negative; "LIQUIDATED" -> PumpWatchPalette.Purple; else -> PumpWatchPalette.Warning }
                                Text("${r.symbol} • ${if (r.side == "BUY") "🟢" else "🔴"} • $resultEmoji ${r.result}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = resultColor)
                                Text("امتیاز: ${r.score}", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                            }
                            Text("${String.format(Locale.US, "%+.2f%%", r.pnl)}", fontWeight = FontWeight.Bold, color = if (r.pnl >= 0) PumpWatchPalette.Positive else (if (r.result == "LIQUIDATED") PumpWatchPalette.Purple else PumpWatchPalette.Negative), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (!isRunning && loadState !is BacktestLoadState.Loaded && !abRunning && abResults.isEmpty()) {
            Text("بازه‌ها را انتخاب کنید و دکمهٔ شروع آزمایش تاریخی را بزنید", color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
        }
    }
}
