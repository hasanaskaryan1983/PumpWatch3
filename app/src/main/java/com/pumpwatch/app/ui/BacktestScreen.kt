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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.BinanceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LC = Color(0xFF1A2230)
private val LBlue = Color(0xFF40C4FF)

data class BacktestResult(
    val symbol: String,
    val rank: Int,
    val side: String,
    val entry: Double,
    val exit: Double,
    val pnl: Double,
    val score: Int
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
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<BacktestResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var selectedRanges by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🧪 بک‌تست استراتژی", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "یک یا چند بازه رتبه ارزها (بر اساس حجم ۲۴ ساعته) رو انتخاب کن:",
            fontSize = 12.sp, color = LGr
        )

        // انتخاب چند بازه همزمان
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RANGES.forEach { (label, range) ->
                FilterChip(
                    selected = label in selectedRanges,
                    onClick = {
                        selectedRanges = if (label in selectedRanges) {
                            selectedRanges - label
                        } else {
                            selectedRanges + label
                        }
                        errorMsg = null
                    },
                    label = {
                        Text("$label (${range.last - range.first + 1} ارز)", fontSize = 11.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (selectedRanges.isNotEmpty()) {
            Text(
                "✅ ${selectedRanges.size} بازه انتخاب شد: ${selectedRanges.sorted().joinToString(", ")}",
                fontSize = 11.sp,
                color = LG,
                fontWeight = FontWeight.Bold
            )
        }

        errorMsg?.let { msg ->
            Text(msg, color = LR, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                if (selectedRanges.isEmpty()) {
                    errorMsg = "⚠️ حداقل یک بازه انتخاب کن!"
                    return@Button
                }
                if (!isRunning) {
                    isRunning = true
                    results = emptyList()
                    errorMsg = null
                    scope.launch {
                        val allResults = mutableListOf<BacktestResult>()

                        // دریافت ۱۰۰ ارز پرحجم
                        val tickers = withContext(Dispatchers.IO) {
                            try {
                                BinanceClient.api.ticker24h()
                                    .filter { it.symbol.endsWith("USDT") }
                                    .sortedByDescending { it.quoteVolume?.toDoubleOrNull() ?: 0.0 }
                                    .take(100)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }

                        if (tickers.isEmpty()) {
                            errorMsg = "❌ خطا در دریافت لیست ارزها"
                            isRunning = false
                            return@launch
                        }

                        // جمع‌آوری تمام ایندکس‌های مورد نیاز از بازه‌های انتخابی
                        val allIndices = mutableSetOf<Int>()
                        selectedRanges.forEach { label ->
                            RANGES.find { it.first == label }?.second?.forEach { allIndices.add(it) }
                        }

                        val symbolsToTest = allIndices.map { idx ->
                            idx to tickers.getOrNull(idx)?.symbol?.replace("USDT", "")
                        }.filter { it.second != null }

                        var processed = 0
                        for ((rank, symbol) in symbolsToTest) {
                            progress = "در حال تحلیل $symbol (${processed + 1}/${symbolsToTest.size})..."
                            val klines = withContext(Dispatchers.IO) {
                                try {
                                    BinanceClient.api.klines("${symbol}USDT", "1h", 168)
                                } catch (e: Exception) {
                                    emptyList()
                                }
                            }
                            if (klines.size >= 48) {
                                val closes = klines.map { it[4].asDouble }
                                val volumes = klines.map { it[5].asDouble }
                                for (i in 24 until closes.size - 24) {
                                    val window = closes.subList(i - 24, i)
                                    val volWindow = volumes.subList(i - 24, i)
                                    val score = computeScore(window, volWindow)
                                    if (score >= 60 || score <= -60) {
                                        val entry = closes[i]
                                        val exit = closes[i + 24]
                                        val pnl = if (score > 0) {
                                            (exit - entry) / entry * 100
                                        } else {
                                            (entry - exit) / entry * 100
                                        }
                                        allResults.add(
                                            BacktestResult(
                                                symbol = symbol!!,
                                                rank = rank + 1,
                                                side = if (score > 0) "BUY" else "SELL",
                                                entry = entry,
                                                exit = exit,
                                                pnl = pnl,
                                                score = score
                                            )
                                        )
                                    }
                                }
                            }
                            processed++
                        }
                        results = allResults
                        isRunning = false
                        progress = ""
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedRanges.isEmpty()) LGr else LBlue
            )
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isRunning) "در حال اجرا..." else "▶ شروع بک‌تست (${selectedRanges.size} بازه)",
                fontSize = 13.sp
            )
        }

        if (isRunning) {
            Text(progress, color = LGr, fontSize = 12.sp)
        }

        if (results.isNotEmpty()) {
            val wins = results.count { it.pnl > 0 }
            val losses = results.count { it.pnl <= 0 }
            val total = results.size
            val winRate = if (total > 0) wins * 100.0 / total else 0.0
            val avgPnl = if (total > 0) results.map { it.pnl }.average() else 0.0
            val totalPnl = results.sumOf { it.pnl }

            Surface(
                color = LC,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "📊 نتایج بک‌تست (۷ روز) — بازه‌های: ${selectedRanges.sorted().joinToString(", ")}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                        Text("تعداد: $total", fontSize = 11.sp, color = LGr)
                        Text("✅ برد: $wins", fontSize = 11.sp, color = LG)
                        Text("❌ باخت: $losses", fontSize = 11.sp, color = LR)
                    }
                    Text(
                        "وین‌ریت: ${String.format(Locale.US, "%.1f%%", winRate)}",
                        fontWeight = FontWeight.Bold,
                        color = if (winRate >= 55) LG else LR
                    )
                    Text(
                        "میانگین PnL: ${String.format(Locale.US, "%+.2f%%", avgPnl)}",
                        fontWeight = FontWeight.Bold,
                        color = if (avgPnl >= 0) LG else LR
                    )
                    Text(
                        "مجموع PnL: ${String.format(Locale.US, "%+.2f%%", totalPnl)}",
                        fontWeight = FontWeight.Bold,
                        color = if (totalPnl >= 0) LG else LR
                    )
                }
            }

            val displayResults = results.takeLast(30)
            Text(
                "📋 ۳۰ سیگنال آخر (${results.size} کل):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(displayResults) { r ->
                    Surface(
                        color = LC,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "#${r.rank} ${r.symbol} • ${if (r.side == "BUY") "🟢" else ""}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text("امتیاز: ${r.score}", fontSize = 10.sp, color = LGr)
                            }
                            Text(
                                "${String.format(Locale.US, "%+.2f%%", r.pnl)}",
                                fontWeight = FontWeight.Bold,
                                color = if (r.pnl >= 0) LG else LR,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        } else if (!isRunning && results.isEmpty()) {
            Text(
                "یک یا چند بازه انتخاب کن و دکمه شروع بک‌تست رو بزن",
                color = LGr,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
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
