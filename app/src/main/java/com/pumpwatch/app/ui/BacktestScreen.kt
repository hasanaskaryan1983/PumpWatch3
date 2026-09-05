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
import com.pumpwatch.app.engine.PumpDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

data class BacktestResult(
    val symbol: String,
    val rank: Int,
    val side: String,
    val pnl: Double,
    val score: Int,
    val result: String
)

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
    var results by remember { mutableStateOf<List<BacktestResult>>(emptyList()) }
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
                if (isFutures) "⚡ فیوچرز: کوتاه‌مدت، خروج روی CLOSE کندل"
                else "🏦 اسپات: امتیاز ≥۷۰ + هفتگی مثبت + OBV مثبت",
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
                    errorMsg = null
                    scope.launch {
                        val allResults = mutableListOf<BacktestResult>()

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
                                    val closes = klines.map { it[4].asDouble }
                                    val volumes = klines.map { it[5].asDouble }
                                    val start = max(48, closes.size - tf.evalLast)
                                    val end = closes.size - tf.hold
                                    var prev = 0
                                    for (i in start until end) {
                                        val score = computeScore(closes.subList(i - 24, i + 1), volumes.subList(i - 24, i + 1))
                                        val freshBuy = score >= 60 && prev < 60
                                        val freshSell = score <= -60 && prev > -60
                                        if (freshBuy || freshSell) {
                                            val entry = closes[i]
                                            val side = if (score > 0) "BUY" else "SELL"
                                            val atr = atrAt(closes, i)
                                            val risk = if (atr > 0) atr * 2.5 else entry * 0.05
                                            val stop = if (side == "BUY") entry - risk else entry + risk
                                            val target = if (side == "BUY") entry + risk * 1.5 else entry - risk * 1.5
                                            var result = "EXP"
                                            var exit = closes[min(i + tf.hold, closes.size - 1)]
                                            for (j in (i + 1)..min(i + tf.hold, closes.size - 1)) {
                                                val cj = closes[j]
                                                if (side == "BUY") {
                                                    if (cj <= stop) { result = "LOSS"; exit = stop; break }
                                                    if (cj >= target) { result = "WIN"; exit = target; break }
                                                } else {
                                                    if (cj >= stop) { result = "LOSS"; exit = stop; break }
                                                    if (cj <= target) { result = "WIN"; exit = target; break }
                                                }
                                            }
                                            val pnl = if (side == "BUY") (exit - entry) / entry * 100 else (entry - exit) / entry * 100
                                            allResults.add(BacktestResult(symbol, idx + 1, side, pnl, score, result))
                                        }
                                        prev = score
                                    }
                                }
                            } else {
                                val hold = SPOT_HORIZONS.find { it.first == selectedHorizon }?.second ?: 30
                                val klines = withContext(Dispatchers.IO) {
                                    getKlinesCached("${symbol}USDT", "1d", 300)
                                }
                                if (klines.size >= 210) {
                                    analyzed++
                                    val highs = klines.map { it[2].asDouble }
                                    val lows = klines.map { it[3].asDouble }
                                    val closes = klines.map { it[4].asDouble }
                                    val volumes = klines.map { it[5].asDouble }
                                    val weekly = closes.chunked(7).map { it.last() }
                                    val e50s = emaSeries(closes, 50)
                                    var prevSig = false
                                    for (i in 200 until closes.size) {
                                        var score = spotScore(closes.subList(0, i + 1), volumes.subList(0, i + 1), weekly)
                                        val sixty = PumpDetector.analyzeSixtySecond(
                                            highs.subList(0, i + 1),
                                            lows.subList(0, i + 1),
                                            closes.subList(0, i + 1)
                                        )
                                        score += when (sixty.signal) {
                                            "BUY" -> 15
                                            "SELL" -> -15
                                            else -> 0
                                        }
                                        score = score.coerceIn(-100, 100)

                                        // فیلتر کیفیت: امتیاز ۷۰ + هفتگی مثبت + OBV مثبت
                                        val wScore = weeklyScore(closes.subList(0, i + 1).chunked(7).map { it.last() })
                                        val oScore = obvScore(closes.subList(0, i + 1), volumes.subList(0, i + 1))
                                        val sig = score >= 70 && wScore > 0 && oScore > 0

                                        if (sig && !prevSig) {
                                            val entry = closes[i]
                                            val atr = atrAt(closes, i)
                                            val atrPct = if (entry > 0) atr / entry * 100 else 10.0
                                            val stopPct = (atrPct * 2.5).coerceIn(7.0, 15.0)
                                            var trail = entry * (1.0 - stopPct / 100.0)
                                            val target = entry * (1.0 + stopPct * 2.0 / 100.0)

                                            var result = "EXP"
                                            var exit = closes[min(i + hold, closes.size - 1)]
                                            for (j in (i + 1)..min(i + hold, closes.size - 1)) {
                                                if (lows[j] <= trail) { result = "LOSS"; exit = trail; break }
                                                if (highs[j] >= target) { result = "WIN"; exit = target; break }
                                                if (closes[j] < e50s[j]) {
                                                    exit = closes[j]
                                                    result = if (exit >= entry) "WIN" else "LOSS"
                                                    break
                                                }
                                                val nt = closes[j] * (1.0 - stopPct / 100.0)
                                                if (nt > trail) trail = nt
                                            }
                                            val pnl = (exit - entry) / entry * 100
                                            allResults.add(BacktestResult(symbol, idx + 1, "BUY", pnl, score, result))
                                        }
                                        prevSig = sig
                                    }
                                }
                            }
                            processed++
                            delay(150)
                        }

                        analyzedInfo = "ارزهای تحلیل‌شده: $analyzed از ${coinsToTest.size}"
                        results = allResults
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

        if (results.isNotEmpty()) {
            val wins = results.count { it.result == "WIN" }
            val losses = results.count { it.result == "LOSS" }
            val expired = results.count { it.result == "EXP" }
            val decided = wins + losses
            val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
            val avgPnl = results.map { it.pnl }.average()
            val totalPnl = results.sumOf { it.pnl }

            Surface(color = LC, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "📊 نتایج ${if (isFutures) "فیوچرز $selectedTf" else "اسپات $selectedHorizon"} — ${selectedRanges.sorted().joinToString(", ")}",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                    Text(analyzedInfo, fontSize = 10.sp, color = LY)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("تعداد: ${results.size}", fontSize = 11.sp, color = LGr)
                        Text("✅ برد: $wins", fontSize = 11.sp, color = LG)
                        Text("❌ باخت: $losses", fontSize = 11.sp, color = LR)
                        Text("⌛ منقضی: $expired", fontSize = 11.sp, color = LY)
                    }
                    Text("وین‌ریت: ${String.format(Locale.US, "%.1f%%", winRate)}", fontWeight = FontWeight.Bold, color = if (winRate >= 55) LG else LR)
                    Text("میانگین PnL: ${String.format(Locale.US, "%+.2f%%", avgPnl)}", fontWeight = FontWeight.Bold, color = if (avgPnl >= 0) LG else LR)
                    Text("مجموع PnL: ${String.format(Locale.US, "%+.2f%%", totalPnl)}", fontWeight = FontWeight.Bold, color = if (totalPnl >= 0) LG else LR)
                }
            }

            Text("📋 ۳۰ سیگنال آخر (${results.size} کل):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                    "#${r.rank} ${r.symbol} • ${if (r.side == "BUY") "🟢" else "🔴"} • ${when (r.result) { "WIN" -> "✅"; "LOSS" -> "❌"; else -> "⌛" }}",
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
        } else if (!isRunning && results.isEmpty()) {
            Text("بازه‌ها رو انتخاب کن و شروع رو بزن", color = LGr, modifier = Modifier.padding(24.dp))
        }
    }
}

private fun emaSeries(data: List<Double>, period: Int): List<Double> {
    val out = MutableList(data.size) { 0.0 }
    if (data.size < period) return out
    var ema = data.take(period).average()
    out[period - 1] = ema
    val k = 2.0 / (period + 1)
    for (i in period until data.size) {
        ema = data[i] * k + ema * (1 - k)
        out[i] = ema
    }
    return out
}

private fun spotScore(closes: List<Double>, volumes: List<Double>, weekly: List<Double>): Int {
    if (closes.size < 210) return 0
    val price = closes.last()
    val e50 = emaLast(closes, 50)
    val e200 = emaLast(closes, 200)
    var s = 0
    s += when {
        price > e50 && e50 > e200 -> 50
        price > e50 -> 25
        price < e50 && e50 < e200 -> -50
        else -> -25
    }
    s += if (macdUp(closes)) 20 else -20
    val r = rsiOf(closes)
    s += when {
        r in 45.0..65.0 -> 15
        r < 35 -> 20
        r > 75 -> -25
        else -> 5
    }
    if (volumes.size > 40) {
        val recent = volumes.takeLast(20).average()
        val prior = volumes.dropLast(20).takeLast(20).average()
        if (prior > 0 && recent > prior * 1.2) s += 10
    }
    s += weeklyScore(weekly)
    s += obvScore(closes, volumes)
    return s.coerceIn(-100, 100)
}

private fun weeklyScore(weekly: List<Double>): Int {
    if (weekly.size < 25) return 0
    val w = weekly.last()
    val e10 = emaLast(weekly, 10)
    val e20 = emaLast(weekly, 20)
    return when {
        w > e10 && e10 > e20 -> 15
        w > e10 -> 8
        w < e10 && e10 < e20 -> -20
        else -> -8
    }
}

private fun obvScore(closes: List<Double>, volumes: List<Double>): Int {
    if (closes.size < 30) return 0
    var obv = 0.0
    val series = mutableListOf<Double>()
    for (i in 1 until closes.size) {
        obv += when {
            closes[i] > closes[i - 1] -> volumes[i]
            closes[i] < closes[i - 1] -> -volumes[i]
            else -> 0.0
        }
        series.add(obv)
    }
    if (series.size < 21) return 0
    val now = series.last()
    val past = series[series.size - 21]
    return when {
        now > past * 1.05 -> 10
        now > past -> 5
        now < past * 0.95 -> -10
        else -> -5
    }
}

private fun atrAt(data: List<Double>, index: Int, period: Int = 14): Double {
    if (index < period) return 0.0
    var s = 0.0
    for (k in (index - period + 1)..index) s += abs(data[k] - data[k - 1])
    return s / period
}

private fun emaLast(data: List<Double>, period: Int): Double {
    if (data.size < period) return data.lastOrNull() ?: 0.0
    val k = 2.0 / (period + 1)
    var ema = data.take(period).average()
    for (i in period until data.size) ema = data[i] * k + ema * (1 - k)
    return ema
}

private fun rsiOf(data: List<Double>, period: Int = 14): Double {
    if (data.size <= period) return 50.0
    var g = 0.0
    var l = 0.0
    for (i in 1..period) {
        val d = data[i] - data[i - 1]
        if (d > 0) g += d else l -= d
    }
    var ag = g / period
    var al = l / period
    for (i in period + 1 until data.size) {
        val d = data[i] - data[i - 1]
        ag = (ag * (period - 1) + max(d, 0.0)) / period
        al = (al * (period - 1) + max(-d, 0.0)) / period
    }
    if (al == 0.0) return 100.0
    return 100.0 - 100.0 / (1.0 + ag / al)
}

private fun macdUp(data: List<Double>): Boolean {
    if (data.size < 35) return false
    val prev = data.dropLast(1)
    return (emaLast(data, 12) - emaLast(data, 26)) > (emaLast(prev, 12) - emaLast(prev, 26))
}

private fun bollinger(data: List<Double>, period: Int = 20): Pair<Double, Double> {
    if (data.size < period) return Pair(0.0, 0.0)
    val win = data.takeLast(period)
    val m = win.average()
    val sd = sqrt(win.map { (it - m) * (it - m) }.average())
    return Pair(m + 2 * sd, m - 2 * sd)
}

private fun computeScore(closes: List<Double>, volumes: List<Double>): Int {
    val price = closes.last()
    val e20 = emaLast(closes, 20)
    val e50 = emaLast(closes, 50)
    val ema = when {
        price > e20 && e20 > e50 -> 25
        price < e20 && e20 < e50 -> -25
        else -> 0
    }
    val r = rsiOf(closes)
    val rsi = when {
        r <= 35 -> 20
        r >= 65 -> -20
        else -> 0
    }
    val macd = if (macdUp(closes)) 25 else -25

    val (bu, bl) = bollinger(closes)
    val prev = closes.dropLast(1)
    val (pbu, pbl) = bollinger(prev)
    val c = price
    val pc = prev.lastOrNull() ?: c
    val boll = when {
        pc <= pbl && c > bl -> 15
        pc >= pbu && c < bu -> -15
        c <= bl * 1.01 -> 15
        c >= bu * 0.99 -> -15
        c > (bu + bl) / 2 && macd == 25 -> 15
        c < (bu + bl) / 2 && macd == -25 -> -15
        else -> 0
    }
    val vol = if (volumes.size > 15) {
        val lv = volumes.last()
        val av = volumes.dropLast(1).takeLast(14).average()
        val bd = if (c >= pc) 15 else -15
        if (av > 0 && lv >= 1.5 * av) bd else 0
    } else 0
    return (ema + rsi + macd + vol + boll).coerceIn(-100, 100)
}
