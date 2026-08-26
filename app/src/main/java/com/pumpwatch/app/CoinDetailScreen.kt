package com.pumpwatch.app.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.IndicatorEngine
import com.pumpwatch.app.IndicatorResult
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import kotlinx.coroutines.launch
import java.util.Locale

private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Gold = Color(0xFFFFC107)
private val Blue = Color(0xFF4FC3F7)

@Composable
fun CoinDetailScreen(coin: CoinMarket, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<IndicatorResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(coin.id) {
        scope.launch {
            try {
                val chart = ApiClient.getCoinChart(coin.id, days = 90)
                val prices = chart.prices
                val volumes = chart.totalVolumes?.map { it[1] } ?: emptyList()

                if (prices.size >= 90) {
                    val ohlc = prices.map { p ->
                        listOf(
                            p[0],           // time
                            p[1],           // open (using close as proxy)
                            p[1] * 1.005,   // high (estimate)
                            p[1] * 0.995,   // low (estimate)
                            p[1]            // close
                        )
                    }
                    result = IndicatorEngine.calculate(ohlc, volumes, coin.current_price)
                } else {
                    error = "داده کافی نیست (نیاز به ۹۰ کندل)"
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- سربرگ ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("← بازگشت") }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${coin.symbol.uppercase(Locale.US)} - ${coin.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    String.format(Locale.US, "$%,.4f", coin.current_price),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        when {
            loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Green
                )
            }

            error != null -> Text("خطا: $error", color = Red)

            result != null -> {
                val r = result!!

                // ---------- سیگنال اصلی ----------
                val signalColor = when (r.signal) {
                    "STRONG_BUY" -> Green
                    "BUY" -> Green.copy(alpha = 0.7f)
                    "STRONG_SELL" -> Red
                    "SELL" -> Red.copy(alpha = 0.7f)
                    else -> Color.Gray
                }

                Surface(
                    color = signalColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            when (r.signal) {
                                "STRONG_BUY" -> "💪 خرید قوی"
                                "BUY" -> "✅ خرید"
                                "STRONG_SELL" -> "💪 فروش قوی"
                                "SELL" -> "❌ فروش"
                                else -> "⏳ نگه دار"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = signalColor
                        )
                        Text(
                            "اطمینان: ${r.confidence}%",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // ---------- اندیکاتورها ----------
                IndicatorRow("RSI", String.format(Locale.US, "%.1f", r.rsi), r.rsi < 30 || r.rsi > 70)
                IndicatorRow("MFI", String.format(Locale.US, "%.1f", r.mfi), r.mfi < 25 || r.mfi > 75)
                IndicatorRow("ADX", String.format(Locale.US, "%.1f", r.adx), r.adx >= 25)
                IndicatorRow("MACD", String.format(Locale.US, "%.4f", r.macd), r.macd > r.macdSignal)

                // ---------- EMA ----------
                Text("میانگین‌های متحرک:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                EmaRow("EMA 20", r.ema20, coin.current_price)
                EmaRow("EMA 50", r.ema50, coin.current_price)
                EmaRow("EMA 200", r.ema200, coin.current_price)

                // ---------- VWAP ----------
                val vwapColor = if (coin.current_price > r.vwap) Green else Red
                IndicatorRow(
                    "VWAP",
                    String.format(Locale.US, "%.4f (%.1f%%)", r.vwap, r.vwapDeviation),
                    coin.current_price > r.vwap,
                    vwapColor
                )

                // ---------- OBV Divergence ----------
                if (r.obvDivergence != "NONE") {
                    val obvColor = if (r.obvDivergence == "BULLISH") Green else Red
                    IndicatorRow(
                        "OBV Divergence",
                        r.obvDivergence,
                        true,
                        obvColor
                    )
                }

                // ---------- ATR & Trailing Stop ----------
                IndicatorRow("ATR", String.format(Locale.US, "%.4f", r.atr), false)
                IndicatorRow(
                    "Trailing Stop",
                    String.format(Locale.US, "%.4f", r.trailingStop),
                    true,
                    Blue
                )

                // ---------- بولینگر ----------
                Text("بولینگر:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                BollingerRow(r.bbUpper, r.bbLower, coin.current_price)

                // ---------- حمایت/مقاومت ----------
                if (r.supports.isNotEmpty() || r.resistances.isNotEmpty()) {
                    Text("حمایت / مقاومت:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    r.supports.forEach { s ->
                        Text("  🟢 حمایت: ${String.format(Locale.US, "%.4f", s)}", fontSize = 12.sp, color = Green)
                    }
                    r.resistances.forEach { r ->
                        Text("  🔴 مقاومت: ${String.format(Locale.US, "%.4f", r)}", fontSize = 12.sp, color = Red)
                    }
                }

                // ---------- توضیح ----------
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        r.explanation,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IndicatorRow(
    label: String,
    value: String,
    highlight: Boolean,
    color: Color = if (highlight) Green else MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun EmaRow(label: String, ema: Double, price: Double) {
    val color = if (price > ema) Green else Red
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(
            String.format(Locale.US, "%.4f", ema),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun BollingerRow(upper: Double, lower: Double, price: Double) {
    val progress = if (upper > lower) ((price - lower) / (upper - lower)).toFloat().coerceIn(0f, 1f) else 0.5f
    Column {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (price > (upper + lower) / 2) Green else Red,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(String.format(Locale.US, "پایین: %.4f", lower), fontSize = 11.sp, color = Green)
            Text(String.format(Locale.US, "بالا: %.4f", upper), fontSize = 11.sp, color = Red)
        }
    }
}
