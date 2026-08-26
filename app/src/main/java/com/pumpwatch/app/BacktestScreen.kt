package com.pumpwatch.app

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.BacktestConfig
import com.pumpwatch.app.data.BacktestEngine
import com.pumpwatch.app.data.BacktestResult
import com.pumpwatch.app.data.ChartClient
import kotlinx.coroutines.launch
import java.util.Locale

private val BGreen = Color(0xFF00E676)
private val BRed = Color(0xFFFF5252)

// ---------- صفحه بک‌تست حرفه‌ای ----------

@Composable
fun BacktestScreen() {
    val scope = rememberCoroutineScope()

    var coinId by remember { mutableStateOf("bitcoin") }
    var days by remember { mutableStateOf(90) }

    var buyDrop by remember { mutableStateOf(5f) }
    var sellRise by remember { mutableStateOf(6f) }
    var stopLoss by remember { mutableStateOf(6f) }
    var rsiMax by remember { mutableStateOf(40f) }
    var stochMax by remember { mutableStateOf(25f) }

    var useTrend by remember { mutableStateOf(true) }
    var useMacd by remember { mutableStateOf(true) }
    var useBollinger by remember { mutableStateOf(true) }
    var useVolume by remember { mutableStateOf(true) }
    var useBreakEven by remember { mutableStateOf(true) }

    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BacktestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyBest() {
        buyDrop = 5f
        sellRise = 6f
        stopLoss = 6f
        rsiMax = 40f
        stochMax = 25f
        useTrend = true
        useMacd = true
        useBollinger = true
        useVolume = true
        useBreakEven = true
    }

    fun runBacktest() {
        scope.launch {
            loading = true
            error = null
            try {
                val chart = ChartClient.api.getMarketChart(coinId, days = days)
                val series = chart.prices.map { it[0].toLong() to it[1] }
                val vols = chart.totalVolumes?.map { it[1] } ?: emptyList()
                result = BacktestEngine.run(
                    series,
                    BacktestConfig(
                        buyDrop = buyDrop.toDouble(),
                        sellRise = sellRise.toDouble(),
                        stopLoss = stopLoss.toDouble(),
                        rsiMax = rsiMax.toDouble(),
                        stochMax = stochMax.toDouble(),
                        useTrend = useTrend,
                        useMacd = useMacd,
                        useBollinger = useBollinger,
                        useVolume = useVolume,
                        useBreakEven = useBreakEven
                    ),
                    vols
                )
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    val coins = listOf(
        "bitcoin" to "بیت‌کوین",
        "ethereum" to "اتریوم",
        "solana" to "سولانا",
        "dogecoin" to "دوج",
        "ripple" to "ریپل"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "بک‌تست استراتژی",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // ---------- ارز ----------
        Text("ارز:", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            coins.forEach { (id, title) ->
                FilterChip(
                    selected = coinId == id,
                    onClick = { coinId = id },
                    label = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        // ---------- بازه ----------
        Text("بازه زمانی:", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 180).forEach { d ->
                FilterChip(
                    selected = days == d,
                    onClick = { days = d },
                    label = { Text("$d روز", fontSize = 12.sp) }
                )
            }
        }

        // ---------- اسلایدرها ----------
        SliderRow("خرید در افت", buyDrop, 1f..15f) { buyDrop = it }
        SliderRow("فروش در رشد", sellRise, 2f..20f) { sellRise = it }
        SliderRow("استاپ ضرر", stopLoss, 3f..15f) { stopLoss = it }
        SliderRow("حداکثر RSI ورود", rsiMax, 30f..45f) { rsiMax = it }
        SliderRow("حداکثر Stochastic ورود", stochMax, 10f..40f) { stochMax = it }

        // ---------- فیلترها ----------
        Text("فیلترهای تأیید:", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("روند 📈", useTrend) { useTrend = !useTrend }
            ToggleChip("MACD ⚡", useMacd) { useMacd = !useMacd }
            ToggleChip("بولینگر 🎯", useBollinger) { useBollinger = !useBollinger }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("حجم 💧", useVolume) { useVolume = !useVolume }
            ToggleChip("بیک‌ایون 🛡️", useBreakEven) { useBreakEven = !useBreakEven }
        }

        // ---------- دکمه‌ها ----------
        Button(onClick = { applyBest() }, modifier = Modifier.fillMaxWidth()) {
            Text("بهترین تنظیمات 🎯")
        }

        Button(
            onClick = { runBacktest() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "در حال اجرا..." else "اجرای بک‌تست") }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = BGreen
            )
        }

        error?.let {
            Text("خطا: $it", color = BRed, fontSize = 12.sp)
        }

        // ---------- نتایج ----------
        result?.let { r ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("نتایج بک‌تست", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    ResultRow("تعداد معاملات:", "${r.totalTrades}")
                    ResultRow("سود:", "${r.winCount}")
                    ResultRow("ضرر:", "${r.lossCount}")
                    ResultRow(
                        "نرخ برد:",
                        String.format(Locale.US, "%.1f%%", r.winRatePercent)
                    )
                    ResultRow(
                        "سود خالص:",
                        String.format(Locale.US, "%.2f%%", r.netPnlPercent),
                        if (r.netPnlPercent >= 0) BGreen else BRed
                    )
                    ResultRow(
                        "حداکثر کشیدگی:",
                        String.format(Locale.US, "%.2f%%", r.maxDrawdownPercent)
                    )

                    Text(
                        "معاملات اخیر:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    r.trades.takeLast(5).reversed().forEachIndexed { idx, t ->
                        Text(
                            "#${idx + 1} ورود: ${String.format(Locale.US, "$%.2f", t.entryPrice)} | " +
                                    "خروج: ${String.format(Locale.US, "$%.2f", t.exitPrice)} | " +
                                    "سود/ضرر: ${String.format(Locale.US, "%.2f%%", t.pnlPercent)}",
                            fontSize = 11.sp,
                            color = if (t.pnlPercent >= 0) BGreen else BRed
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ---------- ردیف اسلایدر ----------

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${value.toInt()}%", fontSize = 13.sp)
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = 0)
    }
}

// ---------- چیپ فیلتر ----------

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) })
}

// ---------- ردیف نتیجه ----------

@Composable
private fun ResultRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}
