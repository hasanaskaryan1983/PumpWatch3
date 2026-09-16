package com.pumpwatch.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.data.FuturesCandle
import com.pumpwatch.app.engine.FuturesScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val CGreen = Color(0xFF00E676)
private val CRed = Color(0xFFFF5252)
private val CBlue = Color(0xFF40C4FF)
private val CGold = Color(0xFFFFC107)
private val CGray = Color(0xFF8B949E)
private val CCard = Color(0xFF1A0E0E)

private val QUICK_SYMBOLS = listOf(
    "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
    "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "LINKUSDT", "SUIUSDT"
)

private val TIMEFRAMES = listOf("5m", "15m", "1h", "4h", "1d")
private val MTF_LIST = listOf("1d", "4h", "1h", "15m")

private fun regimeEmoji(r: FuturesScannerEngine.Regime?): String = when (r) {
    FuturesScannerEngine.Regime.BULL -> "🟢"
    FuturesScannerEngine.Regime.BEAR -> "🔴"
    FuturesScannerEngine.Regime.RANGE -> "⚪"
    null -> "❓"
}

private fun fmtP(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}

@Composable
fun FuturesChartScreen() {
    val scope = rememberCoroutineScope()
    var symbol by remember { mutableStateOf("BTCUSDT") }
    var input by remember { mutableStateOf("BTCUSDT") }
    var tf by remember { mutableStateOf("4h") }
    var candles by remember { mutableStateOf<List<FuturesCandle>>(emptyList()) }
    var regimes by remember { mutableStateOf<Map<String, FuturesScannerEngine.Regime>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun load(sym: String, timeframe: String) {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val (kl, regMap) = withContext(Dispatchers.IO) {
                    val main = BinanceFutures.api.klines(sym, timeframe, 200)
                        .mapNotNull { BinanceFutures.parseCandle(it) }
                    if (main.isEmpty()) throw Exception("دادهٔ کندل خالی است")

                    // 🚀 Sprint 12 (F4): خلاصهٔ MTF — بازاستفاده از regimeOf موتور اسکنر
                    val map = mutableMapOf<String, FuturesScannerEngine.Regime>()
                    for (t in MTF_LIST) {
                        val k = if (t == timeframe) main
                        else BinanceFutures.api.klines(sym, t, 200)
                            .mapNotNull { BinanceFutures.parseCandle(it) }
                        map[t] = FuturesScannerEngine.regimeOf(k)
                    }
                    main to map
                }
                candles = kl
                regimes = regMap
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در دریافت نمودار ${sym}: ${e.message?.take(60) ?: "نامشخص"}"
                candles = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load(symbol, tf) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📊 نمودار فیوچرز (چندتایم‌فریمی)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { load(symbol, tf) }, enabled = !loading) { Text("🔄") }
        }

        // ---------- انتخاب نماد ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("نماد... مثلاً SOL یا SOLUSDT", fontSize = 11.sp) },
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    val s = input.trim().uppercase(Locale.US).replace("-", "")
                    val sym = if (s.endsWith("USDT")) s else "${s}USDT"
                    symbol = sym
                    load(sym, tf)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CRed),
                shape = RoundedCornerShape(10.dp)
            ) { Text("نمایش", fontSize = 11.sp) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QUICK_SYMBOLS.forEach { s ->
                FilterChip(selected = symbol == s, onClick = { symbol = s; input = s; load(s, tf) },
                    label = { Text(s.removeSuffix("USDT"), fontSize = 10.sp) })
            }
        }

        // ---------- تایم‌فریم ----------
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TIMEFRAMES.forEach { k ->
                FilterChip(selected = tf == k, onClick = { tf = k; load(symbol, k) },
                    label = { Text(k, fontSize = 10.sp) })
            }
        }

        // ---------- خلاصهٔ MTF ----------
        Surface(
            color = CCard,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MTF_LIST.forEach { t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t, fontSize = 9.sp, color = CGray)
                        Text(regimeEmoji(regimes[t]), fontSize = 14.sp)
                    }
                }
            }
        }

        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator(color = CRed) }

            errorMsg != null -> Text(errorMsg ?: "", color = CRed, modifier = Modifier.padding(24.dp))

            candles.isEmpty() -> Text("😴 داده‌ای نیست", color = CGray, modifier = Modifier.padding(24.dp))

            else -> {
                val shown = candles.takeLast(60)
                val closes = shown.map { it.close }
                val e20 = FuturesScannerEngine.ema(closes, 20)
                val e50 = FuturesScannerEngine.ema(closes, 50)
                val last = shown.lastOrNull()?.close ?: 0.0
                val first = shown.firstOrNull()?.close ?: last
                val chg = if (first > 0) (last - first) / first * 100 else 0.0

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$symbol • $tf", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(
                        "${fmtP(last)}  ${String.format(Locale.US, "%+.2f%%", chg)}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (chg >= 0) CGreen else CRed
                    )
                }

                FuturesCandleChart(shown, e20, e50)

                Text(
                    "— EMA20 آبی • — EMA50 طلایی • خط‌چین = قیمت آخر\n" +
                    "⚠️ نمودار تحلیلی است نه سیگنال؛ ورود/خروج با ستاپ و تریگر اسکنر (تب 🔍)",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 9.sp, color = CGray, lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun FuturesCandleChart(
    candles: List<FuturesCandle>,
    e20: List<Double>,
    e50: List<Double>
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 8.dp)) {
        if (candles.size < 2) return@Canvas

        val minV = candles.minOf { it.low }
        val maxV = candles.maxOf { it.high }
        val range = if (maxV > minV) maxV - minV else 1.0
        val w = size.width
        val h = size.height
        val n = candles.size
        val cw = w / n
        val bodyW = cw * 0.6f

        fun y(v: Double) = (h - ((v - minV) / range * h * 0.86 + h * 0.07)).toFloat()

        // ---------- کندل‌ها ----------
        candles.forEachIndexed { i, c ->
            val x = i * cw + cw / 2
            val col = if (c.close >= c.open) CGreen else CRed
            drawLine(col, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 2f)
            val yO = y(c.open)
            val yC = y(c.close)
            val top = min(yO, yC)
            val bh = max(3f, abs(yO - yC))
            drawRect(col, topLeft = Offset(x - bodyW / 2, top), size = Size(bodyW, bh))
        }

        // ---------- EMA ها ----------
        fun drawEma(values: List<Double>, color: Color) {
            if (values.size < 2) return
            for (i in 1 until values.size) {
                drawLine(
                    color,
                    Offset((i - 1) * cw + cw / 2, y(values[i - 1])),
                    Offset(i * cw + cw / 2, y(values[i])),
                    strokeWidth = 2f
                )
            }
        }
        drawEma(e20, CBlue)
        drawEma(e50, CGold)

        // ---------- خط‌چین قیمت آخر ----------
        val lastP = candles.last().close
        drawLine(
            CGray, Offset(0f, y(lastP)), Offset(w, y(lastP)),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )

        // ---------- برچسب‌ها ----------
        val paint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.GRAY
        }
        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "$%,.6f", maxV), 4f, 30f, paint)
        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "$%,.6f", minV), 4f, h - 6f, paint)
        drawContext.canvas.nativeCanvas.drawText(
            String.format(Locale.US, "$%,.6f", lastP), w - 220f, y(lastP) - 8f, paint
        )
    }
}
