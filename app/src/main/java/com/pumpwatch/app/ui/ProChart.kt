package com.pumpwatch.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.launch
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

private data class Candle(val o: Double, val h: Double, val l: Double, val c: Double)

// ---------- ساخت کندل ----------

private fun chunkEvery(v: List<Double>, n: Int): List<Double> =
    if (n <= 1) v else v.filterIndexed { i, _ -> (i + 1) % n == 0 }

private fun candlesFrom(prices: List<Double>): List<Candle> {
    if (prices.size < 2) return emptyList()
    val out = ArrayList<Candle>(prices.size - 1)
    for (i in 1 until prices.size) {
        val o = prices[i - 1]
        val c = prices[i]
        out.add(
            Candle(
                o = o,
                c = c,
                h = max(o, c) * 1.0015,
                l = min(o, c) * 0.9985
            )
        )
    }
    return out
}

private suspend fun buildCandles(coinId: String, tf: String): List<Candle> {
    return when (tf) {
        "15m" -> {
            val p = ApiClient.getCoinChart(coinId, 1).prices.map { it[1] }
            candlesFrom(chunkEvery(p, 3))
        }
        "1h" -> {
            val p = ApiClient.getCoinChart(coinId, 30).prices.map { it[1] }
            candlesFrom(p.takeLast(200))
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

// ---------- سری‌های اندیکاتور ----------

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

// ---------- نقاط خرید/فروش (کراس EMA) ----------

private fun bsPoints(closes: List<Double>): List<Pair<Int, Boolean>> {
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

// ---------- بوم نمودار ----------

@Composable
private fun ChartCanvas(
    candles: List<Candle>,
    closes: List<Double>,
    signals: List<Pair<Int, Boolean>>,
    modifier: Modifier
) {
    val e7 = remember(closes) { emaSeries(closes, 7) }
    val e30 = remember(closes) { emaSeries(closes, 30) }
    val e200 = remember(closes) { emaSeries(closes, 200) }
    val rsiS = remember(closes) { rsiSeries(closes) }
    val macd = remember(closes) { macdSeries(closes) }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height
        if (candles.size < 5) return@Canvas

        val n = min(70, candles.size)
        val start = candles.size - n
        val vis = candles.takeLast(n)
        val cw = w / n
        val bodyW = cw * 0.55f

        val mainH = h * 0.52f
        val rsiTop = h * 0.56f
        val rsiH = h * 0.16f
        val macdTop = h * 0.76f
        val macdH = h * 0.22f

        var min = vis.minOf { it.l }
        var max = vis.maxOf { it.h }
        val range = if (max > min) max - min else 1.0

        fun yMain(v: Double): Float {
            return (mainH - ((v - min) / range * (mainH * 0.9f) + mainH * 0.05f)).toFloat()
        }

        // ---------- کندل‌ها ----------
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

        // ---------- خطوط MA ----------
        fun drawMA(series: List<Double>, color: Color) {
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
        drawMA(e7, POrange)
        drawMA(e30, PCyan)
        drawMA(e200, PPurple)

        // ---------- نقاط B / S ----------
        val paint = android.graphics.Paint().apply {
            textSize = 30f
            isFakeBoldText = true
        }
        signals.forEach { (idx, isBuy) ->
            val r = closes.size - 1 - idx
            if (r >= n) return@forEach
            val i = n - 1 - r
            val c = vis[i]
            val x = i * cw + cw / 2
            if (isBuy) {
                val y = yMain(c.l) + 34f
                drawCircle(PGreen, radius = 13f, center = Offset(x, y))
                paint.color = android.graphics.Color.BLACK
                drawContext.canvas.nativeCanvas.drawText("B", x - 9f, y + 10f, paint)
            } else {
                val y = yMain(c.h) - 34f
                drawCircle(PRed, radius = 13f, center = Offset(x, y))
                paint.color = android.graphics.Color.WHITE
                drawContext.canvas.nativeCanvas.drawText("S", x - 8f, y + 10f, paint)
            }
        }

        // ---------- پنل RSI ----------
        drawLine(PGray, Offset(0f, rsiTop), Offset(w, rsiTop), strokeWidth = 1f)
        if (rsiS.isNotEmpty()) {
            fun yRsi(v: Double): Float = (rsiTop + rsiH - (v / 100.0 * rsiH)).toFloat()
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
        }

        // ---------- پنل MACD ----------
        drawLine(PGray, Offset(0f, macdTop), Offset(w, macdTop), strokeWidth = 1f)
        if (macd.hist.isNotEmpty()) {
            val all = macd.hist + macd.dif + macd.dea
            val mMax = all.maxOrNull() ?: 1.0
            val mMin = all.minOrNull() ?: -1.0
            val mRange = if (mMax > mMin) mMax - mMin else 1.0
            fun yM(v: Double): Float = (macdTop + macdH - ((v - mMin) / mRange * macdH)).toFloat()
            val zero = yM(0.0)
            for (i in 0 until n) {
                val r = n - 1 - i
                if (r >= macd.hist.size) continue
                val v = macd.hist[macd.hist.size - 1 - r]
                val x = i * cw + cw / 2
                val y = yM(v)
                drawLine(
                    if (v >= 0) PGreen else PRed,
                    Offset(x, zero),
                    Offset(x, y),
                    strokeWidth = bodyW
                )
            }
            fun drawLine2(series: List<Double>, color: Color) {
                var prev: Offset? = null
                for (i in 0 until n) {
                    val r = n - 1 - i
                    if (r >= series.size) continue
                    val cur = Offset(i * cw + cw / 2, yM(series[series.size - 1 - r]))
                    if (prev != null) drawLine(color, prev!!, cur, strokeWidth = 2f)
                    prev = cur
                }
            }
            drawLine2(macd.dif, POrange)
            drawLine2(macd.dea, PPurple)
        }
    }
}

// ---------- بدنه نمودار ----------

@Composable
private fun ChartBody(coinId: String, tf: String, candles: List<Candle>, loading: Boolean, height: androidx.compose.ui.unit.Dp?) {
    val closes = remember(candles) { candles.map { it.c } }
    val signals = remember(closes) { bsPoints(closes) }
    val e7 = remember(closes) { emaSeries(closes, 7).lastOrNull() ?: 0.0 }
    val e30 = remember(closes) { emaSeries(closes, 30).lastOrNull() ?: 0.0 }
    val rsiLast = remember(closes) { rsiSeries(closes).lastOrNull() ?: 50.0 }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("MA(7): ${String.format(java.util.Locale.US, "%.4f", e7)}", fontSize = 10.sp, color = POrange)
            Text("MA(30): ${String.format(java.util.Locale.US, "%.4f", e30)}", fontSize = 10.sp, color = PCyan)
            Text("RSI: ${String.format(java.util.Locale.US, "%.0f", rsiLast)}", fontSize = 10.sp, color = POrange)
        }
        Spacer(Modifier.height(4.dp))
        when {
            loading -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height ?: 300.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator(color = PGreen) }

            candles.isEmpty() -> Text("داده نمودار در دسترس نیست", color = PRed, fontSize = 12.sp)

            else -> {
                val mod = if (height != null)
                    Modifier.height(height)
                else
                    Modifier.weight(1f)
                ChartCanvas(candles, closes, signals, mod)
            }
        }
    }
}

// ---------- کامپوننت اصلی ----------

@Composable
fun ProChart(coinId: String) {
    var tf by remember { mutableStateOf("1h") }
    var full by remember { mutableStateOf(false) }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coinId, tf) {
        scope.launch {
            loading = true
            try {
                candles = buildCandles(coinId, tf)
            } catch (_: Exception) {
                candles = emptyList()
            }
            loading = false
        }
    }

    val tfs = listOf("15m" to "۱۵د", "1h" to "۱س", "4h" to "۴س", "1d" to "روزانه", "1w" to "هفتگی")

    Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tfs.forEach { (key, label) ->
                    Surface(
                        onClick = { tf = key },
                        color = if (tf == key) PGreen.copy(alpha = 0.25f) else Color(0xFF1A2230),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            fontSize = 11.sp,
                            color = if (tf == key) PGreen else PGray
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ChartBody(coinId, tf, candles, loading, height = 300.dp)
        }
    }

    // ---------- تمام‌صفحه ----------
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tfs.forEach { (key, label) ->
                        Surface(
                            onClick = { tf = key },
                            color = if (tf == key) PGreen.copy(alpha = 0.25f) else Color(0xFF1A2230),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 11.sp,
                                color = if (tf == key) PGreen else PGray
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ChartBody(coinId, tf, candles, loading, height = null)
            }
        }
    }
}
