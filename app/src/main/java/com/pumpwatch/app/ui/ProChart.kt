package com.pumpwatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val PGreen = Color(0xFF00E676)
private val PRed = Color(0xFFFF5252)
private val POrange = Color(0xFFFFA726)
private val PCyan = Color(0xFF26C6DA)
private val PPurple = Color(0xFFAB47BC)
private val PGray = Color(0xFF8B949E)
private val PBg = Color(0xFF0B0F14)
private val PBlue = Color(0xFF40C4FF)
private val PGold = Color(0xFFFFD700)
private val PTeal = Color(0xFF26A69A)

private data class Candle(val o: Double, val h: Double, val l: Double, val c: Double, val v: Double = 0.0)

enum class IndicatorMode(val label: String, val emoji: String) {
    EMA("EMA 7/30", "📊"),
    RSI("RSI", "📈"),
    MACD("MACD", ""),
    BOLL("بولینگر", "🎯"),
    SIXTY("Sixty", "⚡"),
    OF("Order Flow", "💰"),
    NONE("بدون اندیکاتور", "⚪")
}

private fun modeColor(m: IndicatorMode): Color = when (m) {
    IndicatorMode.EMA -> POrange
    IndicatorMode.RSI -> PPurple
    IndicatorMode.MACD -> PBlue
    IndicatorMode.BOLL -> PGray
    IndicatorMode.SIXTY -> PCyan
    IndicatorMode.OF -> PTeal
    IndicatorMode.NONE -> PGray
}

private fun chunkEvery(v: List<Double>, n: Int): List<Double> =
    if (n <= 1) v else v.filterIndexed { i, _ -> (i + 1) % n == 0 }

private fun candlesFrom(prices: List<Double>): List<Candle> {
    if (prices.size < 2) return emptyList()
    val out = ArrayList<Candle>(prices.size - 1)
    for (i in 1 until prices.size) {
        val o = prices[i - 1]
        val c = prices[i]
        out.add(Candle(o = o, c = c, h = max(o, c) * 1.0015, l = min(o, c) * 0.9985, v = abs(c - o)))
    }
    return out
}

private suspend fun buildCandles(coinId: String, tf: String, symbol: String): List<Candle> =
    withContext(Dispatchers.IO) {
        if (symbol.isNotEmpty()) {
            try {
                val real = BinanceClient.candles(symbol, tf)
                if (real.size >= 50) {
                    return@withContext real.map { Candle(o = it.open, h = it.high, l = it.low, c = it.close, v = it.volume) }
                }
            } catch (_: Exception) { }
        }
        when (tf) {
            "15m" -> {
                val p = ApiClient.getCoinChart(coinId, 1).prices.map { it[1] }
                candlesFrom(chunkEvery(p, 3))
            }
            "1h" -> {
                val p = ApiClient.getCoinChart(coinId, 30).prices.map { it[1] }
                candlesFrom(p.takeLast(200))
            }
            "12h" -> {
                val p = ApiClient.getCoinChart(coinId, 90).prices.map { it[1] }
                candlesFrom(chunkEvery(p, 12).takeLast(200))
            }
            "4h" -> {
                val p = ApiClient.getCoinChart(coinId, 90).prices.map { it[1] }
                candlesFrom(chunkEvery(p, 4).takeLast(200))
            }
            "1d" -> {
                val p = ApiClient.getCoinChart(coinId, 365).prices.map { it[1] }
                candlesFrom(p.takeLast(200))
            }
            else -> {
                val p = ApiClient.getCoinChart(coinId, 365).prices.map { it[1] }
                candlesFrom(chunkEvery(p, 7).takeLast(200))
            }
        }
    }

private fun emaSeries(v: List<Double>, p: Int): List<Double> {
    if (v.size < p) return emptyList()
    val k = 2.0 / (p + 1)
    val out = ArrayList<Double>(v.size)
    var e = v.take(p).average()
    for (i in v.indices) {
        e = if (i < p) e else v[i] * k + e * (1 - k)
        out.add(e)
    }
    return out
}

private fun rsiSeries(v: List<Double>, p: Int = 14): List<Double> {
    if (v.size <= p) return emptyList()
    val out = ArrayList<Double>(v.size)
    for (i in 0 until p) out.add(50.0)
    var g = 0.0
    var l = 0.0
    for (i in 1..p) {
        val d = v[i] - v[i - 1]
        if (d > 0) g += d else l -= d
    }
    var ag = g / p
    var al = l / p
    out.add(if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al))
    for (i in p + 1 until v.size) {
        val d = v[i] - v[i - 1]
        ag = (ag * (p - 1) + maxOf(d, 0.0)) / p
        al = (al * (p - 1) + maxOf(-d, 0.0)) / p
        out.add(if (al == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + ag / al))
    }
    return out
}

private data class MacdSeries(val dif: List<Double>, val dea: List<Double>, val hist: List<Double>)

private fun macdSeries(v: List<Double>): MacdSeries {
    val e12 = emaSeries(v, 12)
    val e26 = emaSeries(v, 26)
    if (e12.isEmpty() || e26.isEmpty()) return MacdSeries(emptyList(), emptyList(), emptyList())
    val off12 = v.size - e12.size
    val off26 = v.size - e26.size
    val start = maxOf(off12, off26)
    val dif = ArrayList<Double>()
    for (i in start until v.size) dif.add(e12[i - off12] - e26[i - off26])
    val dea = emaSeries(dif, 9)
    val offD = dif.size - dea.size
    val hist = ArrayList<Double>()
    for (i in offD until dif.size) hist.add((dif[i] - dea[i - offD]) * 2)
    return MacdSeries(dif, dea, hist)
}

private fun bollingerSeries(v: List<Double>, p: Int = 20): Triple<List<Double>, List<Double>, List<Double>> {
    if (v.size < p) return Triple(emptyList(), emptyList(), emptyList())
    val mid = ArrayList<Double>()
    val upper = ArrayList<Double>()
    val lower = ArrayList<Double>()
    for (i in p - 1 until v.size) {
        val slice = v.subList(i - p + 1, i + 1)
        val m = slice.average()
        val sd = kotlin.math.sqrt(slice.map { (it - m) * (it - m) }.average())
        mid.add(m)
        upper.add(m + 2 * sd)
        lower.add(m - 2 * sd)
    }
    return Triple(upper, mid, lower)
}

private fun bsFromEma(closes: List<Double>): List<Pair<Int, Boolean>> {
    val e20 = emaSeries(closes, 20)
    val e50 = emaSeries(closes, 50)
    if (e20.isEmpty() || e50.isEmpty()) return emptyList()
    val o20 = closes.size - e20.size
    val o50 = closes.size - e50.size
    val out = mutableListOf<Pair<Int, Boolean>>()
    for (i in 1 until closes.size) {
        val a = i - o20
        val b = i - o50
        if (a < 1 || b < 1) continue
        if (e20[a - 1] <= e50[b - 1] && e20[a] > e50[b]) out.add(i to true)
        if (e20[a - 1] >= e50[b - 1] && e20[a] < e50[b]) out.add(i to false)
    }
    return out
}

private fun bsFromRsi(closes: List<Double>): List<Pair<Int, Boolean>> {
    val rsi = rsiSeries(closes)
    if (rsi.isEmpty()) return emptyList()
    val off = closes.size - rsi.size
    val out = mutableListOf<Pair<Int, Boolean>>()
    var lastSignal: Boolean? = null
    for (i in 1 until rsi.size) {
        val prev = rsi[i - 1]
        val cur = rsi[i]
        val idx = i + off
        if (prev < 30 && cur >= 30 && lastSignal != true) { out.add(idx to true); lastSignal = true }
        else if (prev > 70 && cur <= 70 && lastSignal != false) { out.add(idx to false); lastSignal = false }
    }
    return out
}

private fun bsFromMacd(closes: List<Double>): List<Pair<Int, Boolean>> {
    val m = macdSeries(closes)
    if (m.dif.isEmpty() || m.dea.isEmpty()) return emptyList()
    val offDif = closes.size - m.dif.size
    val offDea = m.dif.size - m.dea.size
    val out = mutableListOf<Pair<Int, Boolean>>()
    for (i in 1 until m.dif.size) {
        val b = i - offDea
        if (b < 1) continue
        val idx = i + offDif
        if (m.dif[i - 1] <= m.dea[b - 1] && m.dif[i] > m.dea[b]) out.add(idx to true)
        if (m.dif[i - 1] >= m.dea[b - 1] && m.dif[i] < m.dea[b]) out.add(idx to false)
    }
    return out
}

private fun bsFromBoll(closes: List<Double>): List<Pair<Int, Boolean>> {
    val (upper, mid, lower) = bollingerSeries(closes)
    if (upper.isEmpty()) return emptyList()
    val off = closes.size - upper.size
    val out = mutableListOf<Pair<Int, Boolean>>()
    var lastSignal: Boolean? = null
    for (i in 1 until upper.size) {
        val idx = i + off
        if (idx >= closes.size) continue
        val c = closes[idx]
        val prevC = closes[idx - 1]
        if (prevC <= lower[i - 1] && c > lower[i] && lastSignal != true) { out.add(idx to true); lastSignal = true }
        else if (prevC >= upper[i - 1] && c < upper[i] && lastSignal != false) { out.add(idx to false); lastSignal = false }
    }
    return out
}

private fun bsFromSixty(highs: List<Double>, lows: List<Double>, closes: List<Double>): List<Pair<Int, Boolean>> {
    val out = mutableListOf<Pair<Int, Boolean>>()
    if (closes.size < 40) return out
    for (i in 30 until closes.size) {
        val stoch = mutableListOf<Double>()
        for (j in max(0, i - 14)..i) {
            val slice = closes.subList(max(0, j - 13), j + 1)
            val low14 = slice.minOrNull() ?: closes[j]
            val high14 = slice.maxOrNull() ?: closes[j]
            stoch.add(if (high14 != low14) (closes[j] - low14) / (high14 - low14) * 100 else 50.0)
        }
        if (stoch.size < 2) continue
        val prev = stoch[stoch.size - 2]
        val cur = stoch.last()

        // فقط برگشت از اشباع — بدون نیاز به fractal
        if (prev < 20 && cur >= 20) out.add(i to true)
        else if (prev > 80 && cur <= 80) out.add(i to false)
    }
    return out
}

private fun bsFromOrderFlow(candles: List<Candle>): List<Pair<Int, Boolean>> {
    val out = mutableListOf<Pair<Int, Boolean>>()
    if (candles.size < 25) return out
    val hist = mutableListOf<Double>()
    var cvd = 0.0
    for (i in 1 until candles.size) {
        val c = candles[i]
        val range = c.h - c.l
        if (range > 0) {
            val body = abs(c.c - c.o)
            val buyRatio = if (c.c > c.o) 0.5 + (body / range) * 0.5 else 0.5 - (body / range) * 0.5
            cvd += c.v * (buyRatio - (1.0 - buyRatio))
        }
        hist.add(cvd)
    }
    // فقط تغییر CVD — بدون محدودیت priceChange
    for (i in 20 until candles.size) {
        val before = hist.getOrNull(i - 1 - 10) ?: continue
        val now = hist.getOrNull(i - 1) ?: continue
        if (now > before) out.add(i to true)
        else if (now < before) out.add(i to false)
    }
    return out
}

private fun bsPoints(candles: List<Candle>, mode: IndicatorMode): List<Pair<Int, Boolean>> {
    if (candles.isEmpty()) return emptyList()
    val closes = candles.map { it.c }
    return when (mode) {
        IndicatorMode.EMA -> bsFromEma(closes)
        IndicatorMode.RSI -> bsFromRsi(closes)
        IndicatorMode.MACD -> bsFromMacd(closes)
        IndicatorMode.BOLL -> bsFromBoll(closes)
        IndicatorMode.SIXTY -> bsFromSixty(candles.map { it.h }, candles.map { it.l }, closes)
        IndicatorMode.OF -> bsFromOrderFlow(candles)
        IndicatorMode.NONE -> emptyList()
    }
}

private fun consensusPoints(allSignals: Map<IndicatorMode, List<Pair<Int, Boolean>>>, minAgree: Int): List<Pair<Int, Boolean>> {
    val active = allSignals.values.filter { it.isNotEmpty() }
    if (active.size < 2) return emptyList()
    val allPoints = active.flatten().distinctBy { it.first to it.second }
    val out = mutableListOf<Pair<Int, Boolean>>()
    for (p in allPoints) {
        val agree = active.count { list ->
            list.any { q -> q.second == p.second && abs(q.first - p.first) <= 2 }
        }
        if (agree >= minAgree) out.add(p)
    }
    return out.sortedBy { it.first }
}

@Composable
private fun ChartCanvas(
    candles: List<Candle>,
    closes: List<Double>,
    selectedModes: Set<IndicatorMode>,
    allSignals: Map<IndicatorMode, List<Pair<Int, Boolean>>>,
    consensusSignals: List<Pair<Int, Boolean>>,
    modifier: Modifier
) {
    val e7 = remember(closes, selectedModes) { if (IndicatorMode.EMA in selectedModes) emaSeries(closes, 7) else emptyList() }
    val e30 = remember(closes, selectedModes) { if (IndicatorMode.EMA in selectedModes) emaSeries(closes, 30) else emptyList() }
    val rsiS = remember(closes, selectedModes) { if (IndicatorMode.RSI in selectedModes) rsiSeries(closes) else emptyList() }
    val macd = remember(closes, selectedModes) {
        if (IndicatorMode.MACD in selectedModes) macdSeries(closes) else MacdSeries(emptyList(), emptyList(), emptyList())
    }
    val (bUpper, bMid, bLower) = remember(closes, selectedModes) {
        if (IndicatorMode.BOLL in selectedModes) bollingerSeries(closes) else Triple(emptyList(), emptyList(), emptyList())
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height
        if (candles.size < 5) return@Canvas

        val showRsi = IndicatorMode.RSI in selectedModes && rsiS.isNotEmpty()
        val showMacd = IndicatorMode.MACD in selectedModes && macd.hist.isNotEmpty()
        val subCount = (if (showRsi) 1 else 0) + (if (showMacd) 1 else 0)
        val mainH = when (subCount) { 0 -> h * 0.80f; 1 -> h * 0.55f; else -> h * 0.42f }
        val subStart = mainH + h * 0.05f
        val subEach = if (subCount > 0) (h - subStart - h * 0.02f) / subCount else 0f

        val n = min(70, candles.size)
        val vis = candles.takeLast(n)
        val cw = w / n
        val bodyW = cw * 0.55f

        val min = vis.minOf { it.l }
        val max = vis.maxOf { it.h }
        val range = if (max > min) max - min else 1.0

        fun yMain(v: Double): Float = (mainH - ((v - min) / range * (mainH * 0.9f) + mainH * 0.05f)).toFloat()

        vis.forEachIndexed { i, c ->
            val x = i * cw + cw / 2
            val col = if (c.c >= c.o) PGreen else PRed
            drawLine(col, Offset(x, yMain(c.h)), Offset(x, yMain(c.l)), strokeWidth = 2f)
            val yO = yMain(c.o)
            val yC = yMain(c.c)
            val top = min(yO, yC)
            val bh = max(3f, abs(yO - yC))
            drawRect(col, topLeft = Offset(x - bodyW / 2, top), size = Size(bodyW, bh))
        }

        fun drawLineOnMain(series: List<Double>, color: Color) {
            if (series.isEmpty()) return
            var prev: Offset? = null
            for (i in 0 until n) {
                val r = n - 1 - i
                if (r >= series.size) continue
                val x = i * cw + cw / 2
                val y = yMain(series[series.size - 1 - r])
                val cur = Offset(x, y)
                if (prev != null) drawLine(color, prev!!, cur, strokeWidth = 2.5f)
                prev = cur
            }
        }

        if (IndicatorMode.EMA in selectedModes) {
            drawLineOnMain(e7, POrange)
            drawLineOnMain(e30, PCyan)
        }
        if (IndicatorMode.BOLL in selectedModes) {
            drawLineOnMain(bUpper, PRed)
            drawLineOnMain(bMid, PGray)
            drawLineOnMain(bLower, PGreen)
        }

        val paint = android.graphics.Paint().apply {
            textSize = 26f
            isFakeBoldText = true
        }

        // نقاط هر اندیکاتور با رنگ سبز/قرمز
        allSignals.forEach { (mode, signals) ->
            signals.forEach { (idx, isBuy) ->
                val r = closes.size - 1 - idx
                if (r >= n || r < 0) return@forEach
                val i = n - 1 - r
                if (i >= vis.size) return@forEach
                val c = vis[i]
                val x = i * cw + cw / 2
                val col = if (isBuy) PGreen else PRed
                if (isBuy) {
                    drawCircle(col, radius = 9f, center = Offset(x, yMain(c.l) + 26f))
                } else {
                    drawCircle(col, radius = 9f, center = Offset(x, yMain(c.h) - 26f))
                }
            }
        }

        // نقاط مشترک طلایی
        consensusSignals.forEach { (idx, isBuy) ->
            val r = closes.size - 1 - idx
            if (r >= n || r < 0) return@forEach
            val i = n - 1 - r
            if (i >= vis.size) return@forEach
            val c = vis[i]
            val x = i * cw + cw / 2
            if (isBuy) {
                val y = yMain(c.l) + 48f
                drawCircle(PGold, radius = 15f, center = Offset(x, y))
                paint.color = android.graphics.Color.BLACK
                drawContext.canvas.nativeCanvas.drawText("B", x - 9f, y + 10f, paint)
            } else {
                val y = yMain(c.h) - 48f
                drawCircle(PGold, radius = 15f, center = Offset(x, y))
                paint.color = android.graphics.Color.BLACK
                drawContext.canvas.nativeCanvas.drawText("S", x - 8f, y + 10f, paint)
            }
        }

        var subCursor = subStart
        if (showRsi) {
            val top = subCursor
            val sh = subEach
            drawLine(PGray, Offset(0f, top), Offset(w, top), strokeWidth = 1f)
            fun yRsi(v: Double): Float = (top + sh - (v / 100.0 * sh)).toFloat()
            drawLine(PGray, Offset(0f, yRsi(70.0)), Offset(w, yRsi(70.0)), strokeWidth = 1f)
            drawLine(PGray, Offset(0f, yRsi(30.0)), Offset(w, yRsi(30.0)), strokeWidth = 1f)
            var prev: Offset? = null
            for (i in 0 until n) {
                val r = n - 1 - i
                if (r >= rsiS.size) continue
                val cur = Offset(i * cw + cw / 2, yRsi(rsiS[rsiS.size - 1 - r]))
                if (prev != null) drawLine(POrange, prev!!, cur, strokeWidth = 2f)
                prev = cur
            }
            subCursor += subEach
        }

        if (showMacd) {
            val top = subCursor
            val sh = subEach
            drawLine(PGray, Offset(0f, top), Offset(w, top), strokeWidth = 1f)
            val all = macd.hist + macd.dif + macd.dea
            val mMax = all.maxOrNull() ?: 1.0
            val mMin = all.minOrNull() ?: -1.0
            val mRange = if (mMax > mMin) mMax - mMin else 1.0
            fun yM(v: Double): Float = (top + sh - ((v - mMin) / mRange * sh)).toFloat()
            val zero = yM(0.0)
            for (i in 0 until n) {
                val r = n - 1 - i
                if (r >= macd.hist.size) continue
                val v = macd.hist[macd.hist.size - 1 - r]
                val x = i * cw + cw / 2
                drawLine(if (v >= 0) PGreen else PRed, Offset(x, zero), Offset(x, yM(v)), strokeWidth = bodyW)
            }
            fun drawL(series: List<Double>, color: Color) {
                var prev: Offset? = null
                for (i in 0 until n) {
                    val r = n - 1 - i
                    if (r >= series.size) continue
                    val cur = Offset(i * cw + cw / 2, yM(series[series.size - 1 - r]))
                    if (prev != null) drawLine(color, prev!!, cur, strokeWidth = 2f)
                    prev = cur
                }
            }
            drawL(macd.dif, POrange)
            drawL(macd.dea, PPurple)
        }
    }
}

@Composable
private fun ChartBody(
    coinId: String,
    tf: String,
    symbol: String,
    candles: List<Candle>,
    loading: Boolean,
    selectedModes: Set<IndicatorMode>,
    height: androidx.compose.ui.unit.Dp?
) {
    val closes = remember(candles) { candles.map { it.c } }
    val allSignals = remember(candles, selectedModes) {
        selectedModes.filter { it != IndicatorMode.NONE }.associateWith { bsPoints(candles, it) }
    }
    val consensus = remember(allSignals) { consensusPoints(allSignals, 2) }
    val buys = consensus.count { it.second }
    val sells = consensus.count { !it.second }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${selectedModes.size} اندیکاتور", fontSize = 11.sp, color = PBlue, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("⭐ مشترک: 🟢$buys 🔴$sells", fontSize = 11.sp, color = PGold, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        when {
            loading -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height ?: 340.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator(color = PGreen) }

            candles.isEmpty() -> Text("داده نمودار در دسترس نیست", color = PRed, fontSize = 12.sp)

            else -> {
                val mod = if (height != null) Modifier.height(height) else Modifier.weight(1f)
                ChartCanvas(candles, closes, selectedModes, allSignals, consensus, mod)
            }
        }
    }
}

@Composable
private fun TfChips(tf: String, onTf: (String) -> Unit, tfs: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tfs.forEach { (key, label) ->
            FilterChip(
                selected = tf == key,
                onClick = { onTf(key) },
                label = { Text(label, fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun ModeChips(selected: Set<IndicatorMode>, onToggle: (IndicatorMode) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IndicatorMode.values().forEach { m ->
            if (m == IndicatorMode.NONE) {
                FilterChip(
                    selected = selected.isEmpty(),
                    onClick = { onToggle(m) },
                    label = { Text("${m.emoji} ${m.label}", fontSize = 11.sp) }
                )
            } else {
                val isSel = m in selected
                FilterChip(
                    selected = isSel,
                    onClick = { onToggle(m) },
                    label = { Text("${m.emoji} ${m.label}", fontSize = 11.sp) },
                    enabled = isSel || selected.size < 3
                )
            }
        }
    }
}

@Composable
fun ProChart(
    coinId: String,
    symbol: String = "",
    onTfChange: ((String) -> Unit)? = null,
    onModeChange: ((Set<IndicatorMode>) -> Unit)? = null
) {
    var tf by remember { mutableStateOf("1h") }
    var selectedModes by remember { mutableStateOf(setOf(IndicatorMode.EMA)) }
    var full by remember { mutableStateOf(false) }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coinId, tf) {
        scope.launch {
            loading = true
            try {
                candles = buildCandles(coinId, tf, symbol)
            } catch (_: Exception) {
                candles = emptyList()
            }
            loading = false
        }
    }

    fun toggleMode(m: IndicatorMode) {
        selectedModes = when {
            m == IndicatorMode.NONE -> emptySet()
            m in selectedModes -> selectedModes - m
            selectedModes.size < 3 -> selectedModes + m
            else -> selectedModes
        }
        onModeChange?.invoke(selectedModes)
    }

    fun setTf(t: String) {
        tf = t
        onTfChange?.invoke(t)
    }

    val tfs = listOf(
        "15m" to "۱۵د", "1h" to "۱س", "12h" to "۱۲س", "4h" to "۴س", "1d" to "روزانه", "1w" to "هفتگی"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📈 نمودار حرفه‌ای", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { full = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PGreen.copy(alpha = 0.2f))
                ) { Text("⛶", fontSize = 14.sp) }
            }

            Spacer(Modifier.height(6.dp))
            Text("⏱️ تایم‌فریم:", fontSize = 11.sp, color = PGray)
            TfChips(tf, ::setTf, tfs)

            Spacer(Modifier.height(6.dp))
            Text("🧠 اندیکاتورها (حداکثر ۳ تا — نقاط مشترک ★):", fontSize = 11.sp, color = PGray)
            ModeChips(selectedModes, ::toggleMode)

            Spacer(Modifier.height(8.dp))
            ChartBody(coinId, tf, symbol, candles, loading, selectedModes, height = 340.dp)
        }
    }

    if (full) {
        Dialog(
            onDismissRequest = { full = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PBg)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⛶ نمودار تمام‌صفحه", color = PGreen, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { full = false }) { Text("✕ بستن") }
                }
                Spacer(Modifier.height(6.dp))

                Text("⏱️ تایم‌فریم:", fontSize = 11.sp, color = PGray)
                TfChips(tf, ::setTf, tfs)

                Spacer(Modifier.height(6.dp))
                Text("🧠 اندیکاتورها (حداکثر ۳ تا — نقاط مشترک ★):", fontSize = 11.sp, color = PGray)
                ModeChips(selectedModes, ::toggleMode)

                Spacer(Modifier.height(8.dp))
                ChartBody(coinId, tf, symbol, candles, loading, selectedModes, height = null)
            }
        }
    }
}
