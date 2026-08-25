package com.pumpwatch.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.IndicatorEngine
import com.pumpwatch.app.data.IndicatorResult
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CoinDetailScreen(coin: CoinMarket, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ohlc by remember { mutableStateOf<List<List<Double>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var indicator by remember { mutableStateOf<IndicatorResult?>(null) }

    LaunchedEffect(coin.id) {
        scope.launch {
            loading = true
            error = null
            try {
                val data = ApiClient.api.getOhlc(coin.id, days = "30")
                ohlc = data
                indicator = IndicatorEngine.calculate(data, coin.current_price)
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
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
                    TextButton(onClick = onBack) {
                        Text("← بازگشت", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${coin.name} (${coin.symbol.uppercase(Locale.US)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )

                    error != null -> Text(
                        "خطا: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        textAlign = TextAlign.Center
                    )

                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    formatPrice(coin.current_price),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black
                                )
                                val change = coin.price_change_percentage_24h ?: 0.0
                                Text(
                                    String.format(Locale.US, "%+.2f%%", change),
                                    color = if (change >= 0) Color(0xFF00E676) else Color(0xFFFF5252),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (ohlc.isNotEmpty()) {
                            SimplePriceChart(ohlc = ohlc)
                        }

                        indicator?.let { ind ->
                            IndicatorCard(ind)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimplePriceChart(ohlc: List<List<Double>>) {
    val closes = ohlc.map { it[4] }
    val minPrice = closes.minOrNull() ?: 0.0
    val maxPrice = closes.maxOrNull() ?: 0.0
    val range = kotlin.math.max(maxPrice - minPrice, 0.0001)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)) {
            val width = size.width
            val height = size.height
            val stepX = width / (closes.size - 1).coerceAtLeast(1)

            val path = Path()
            closes.forEachIndexed { index, price ->
                val x = index * stepX
                val y = height - ((price - minPrice) / range * height).toFloat()
                if (index == 0) path.moveTo(x, y)
                else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = Color(0xFF00E676),
                style = Stroke(width = 3f)
            )
        }
    }
}

@Composable
private fun IndicatorCard(ind: IndicatorResult) {
    val signalColor = when (ind.signal) {
        "STRONG_BUY", "BUY" -> Color(0xFF00E676)
        "STRONG_SELL", "SELL" -> Color(0xFFFF5252)
        else -> Color(0xFFFFC107)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "تحلیل تکنیکال",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("سیگنال:")
                Text(
                    when (ind.signal) {
                        "STRONG_BUY" -> "خرید قوی"
                        "BUY" -> "خرید"
                        "STRONG_SELL" -> "فروش قوی"
                        "SELL" -> "فروش"
                        else -> "نگهداری"
                    },
                    color = signalColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("اعتماد: ${ind.confidence}%")
            Text("RSI: ${String.format(Locale.US, "%.1f", ind.rsi)}")
            Text("MACD: ${String.format(Locale.US, "%.2f", ind.macd)}")
            Text(
                ind.explanation,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
