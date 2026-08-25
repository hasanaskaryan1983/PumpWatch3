package com.pumpwatch.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.BacktestEngine
import com.pumpwatch.app.data.BacktestResult
import com.pumpwatch.app.data.ChartClient
import kotlinx.coroutines.launch
import java.util.Locale

private val backtestCoins = listOf(
    "bitcoin" to "بیت‌کوین",
    "ethereum" to "اتریوم",
    "solana" to "سولانا",
    "dogecoin" to "دوج",
    "ripple" to "ریپل",
    "tron" to "ترون"
)

@Composable
fun BacktestScreen() {
    val scope = rememberCoroutineScope()
    var selectedCoin by remember { mutableStateOf(backtestCoins.first()) }
    var buyDrop by remember { mutableStateOf(5.0) }
    var sellRise by remember { mutableStateOf(10.0) }
    var result by remember { mutableStateOf<BacktestResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "بک‌تست استراتژی",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Text("ارز:", fontSize = 14.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                backtestCoins.forEach { coin ->
                    FilterChip(
                        selected = selectedCoin == coin,
                        onClick = { selectedCoin = coin },
                        label = { Text(coin.second) }
                    )
                }
            }

            Text("خرید در افت: ${buyDrop.toInt()}%", fontSize = 14.sp)
            Slider(
                value = buyDrop.toFloat(),
                onValueChange = { buyDrop = it.toDouble() },
                valueRange = 1f..20f,
                steps = 18
            )

            Text("فروش در رشد: ${sellRise.toInt()}%", fontSize = 14.sp)
            Slider(
                value = sellRise.toFloat(),
                onValueChange = { sellRise = it.toDouble() },
                valueRange = 1f..50f,
                steps = 48
            )

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val chart = ChartClient.api.getMarketChart(
                                id = selectedCoin.first,
                                days = 365
                            )
                            val prices = chart.prices.map { (it[0].toLong() to it[1]) }
                            result = BacktestEngine.run(prices, buyDrop, sellRise)
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("اجرای بک‌تست")
                }
            }

            if (error != null) {
                Text(
                    "خطا: $error",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            result?.let { res ->
                BacktestResultCard(res)
            }
        }
    }
}

@Composable
private fun BacktestResultCard(res: BacktestResult) {
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
                "نتایج بک‌تست",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            ResultRow("تعداد معاملات:", res.totalTrades.toString())
            ResultRow("سود:", res.winCount.toString())
            ResultRow("ضرر:", res.lossCount.toString())
            ResultRow(
                "نرخ برد:",
                String.format(Locale.US, "%.1f%%", res.winRatePercent)
            )
            ResultRow(
                "سود خالص:",
                String.format(Locale.US, "%.2f%%", res.netPnlPercent),
                if (res.netPnlPercent >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
            )
            ResultRow(
                "حداکثر کشیدگی:",
                String.format(Locale.US, "%.2f%%", res.maxDrawdownPercent)
            )

            if (res.trades.isNotEmpty()) {
                Text(
                    "معاملات اخیر:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                res.trades.takeLast(5).forEachIndexed { index, trade ->
                    val color = if (trade.pnlPercent >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                    Text(
                        "#${index + 1} ورود: ${formatPrice(trade.entryPrice)} | خروج: ${
                            formatPrice(
                                trade.exitPrice
                            )
                        } | سود/ضرر: ${String.format(Locale.US, "%.2f%%", trade.pnlPercent)}",
                        fontSize = 12.sp,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}
