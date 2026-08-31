package com.pumpwatch.app

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinInfo
import com.pumpwatch.app.data.CoinInfoClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.Derivative
import com.pumpwatch.app.data.NewsClient
import com.pumpwatch.app.data.NewsItem
import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.ui.IndicatorMode
import com.pumpwatch.app.ui.ProChart
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Yellow = Color(0xFFFFC107)
private val Gray = Color(0xFF8B949E)
private val Blue = Color(0xFF40C4FF)

private data class TfInfo(val name: String, val trend: String, val rsi: Double)

private data class Vote(val name: String, val emoji: String, val dir: Int)

private data class DetailAnalysis(
    val signal: String,
    val confidence: Int,
    val entry: Double,
    val stop: Double,
    val target1: Double,
    val target2: Double,
    val zoneLow: Double,
    val zoneHigh: Double,
    val rsi1h: Double,
    val macdUp: Boolean,
    val ema20: Double,
    val ema50: Double,
    val atr: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val tfs: List<TfInfo>,
    val explanation: String
)

// ---------- توابع محاسباتی ----------

private fun chunkEvery(v: List<Double>, n: Int): List<Double> =
    if (n <= 1) v else v.filterIndexed { i, _ -> (i + 1) % n == 0 }

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

private fun atrOf(data: List<Double>, period: Int = 14): Double {
    if (data.size <= period) return 0.0
    var s = 0.0
    for (i in 1..period) s += abs(data[i] - data[i - 1])
    return s / period
}

private fun bollinger(data: List<Double>, period: Int = 20): Pair<Double, Double> {
    if (data.size < period) return Pair(0.0, 0.0)
    val win = data.takeLast(period)
    val m = win.average()
    val sd = kotlin.math.sqrt(win.map { (it - m) * (it - m) }.average())
    return Pair(m + 2 * sd, m - 2 * sd)
}

private fun trendOf(closes: List<Double>): String {
    if (closes.size < 50) return "خنثی"
    val e20 = emaLast(closes, 20)
    val e50 = emaLast(closes, 50)
    return if (e20 > e50) "صعودی" else if (e20 < e50) "نزولی" else "خنثی"
}

private fun tfLabel(tf: String): String = when (tf) {
    "15m" -> "۱۵ دقیقه"
    "1h" -> "۱ ساعته"
    "4h" -> "۴ ساعته"
    "1d" -> "روزانه"
    else -> "هفتگی"
}

private fun tfParams(tf: String): Pair<Int, Int> = when (tf) {
    "15m" -> 1 to 3
    "1h" -> 2 to 1
    "4h" -> 8 to 4
    "1d" -> 200 to 1
    else -> 365 to 7
}

private suspend fun closesFor(coinId: String, tf: String): List<Double> {
    val (days, chunk) = tfParams(tf)
    val chart = ApiClient.getCoinChart(coinId, days = days)
    return chart.prices.map { it[1] }.chunked(chunk).map { it.last() }
}

// ---------- رأی هر اندیکاتور (هم‌قانون با نمودار) ----------

private fun computeVotes(closes: List<Double>, price: Double): List<Vote> {
    if (closes.size < 40) return emptyList()

    val rsi = rsiOf(closes)
    val e20 = emaLast(closes, 20)
    val e50 = emaLast(closes, 50)
    val mUp = macdUp(closes)

    val emaDir = when {
        price > e20 && e20 > e50 -> 1
        price < e20 && e20 < e50 -> -1
        else -> 0
    }

    val rsiDir = when {
        rsi <= 35 -> 1
        rsi >= 65 -> -1
        else -> 0
    }

    val macdDir = if (mUp) 1 else -1

    val (bbU, bbL) = bollinger(closes)
    val prev = closes.dropLast(1)
    val (pU, pL) = bollinger(prev)
    val c = closes.last()
    val pc = if (prev.isNotEmpty()) prev.last() else c
    val bollDir = when {
        pc <= pL && c > bbL -> 1
        pc >= pU && c < bbU -> -1
        c <= bbL * 1.01 -> 1
        c >= bbU * 0.99 -> -1
        c > (bbU + bbL) / 2 && mUp -> 1
        c < (bbU + bbL) / 2 && !mUp -> -1
        else -> 0
    }

    return listOf(
        Vote("EMA", "📊", emaDir),
        Vote("RSI", "📈", rsiDir),
        Vote("MACD", "📉", macdDir),
        Vote("بولینگر", "🎯", bollDir)
    )
}

private fun scoreOf(votes: List<Vote>): Int = votes.sumOf { it.dir }

private data class Verdict(val text: String, val color: Color)

private fun verdictOf(score: Int): Verdict = when {
    score >= 3 -> Verdict("خرید قوی 🟢", Green)
    score >= 1 -> Verdict("خرید ✅", Green)
    score == 0 -> Verdict("خنثی ⚪ — منتظر بمون", Gray)
    score >= -2 -> Verdict("فروش / شورت ❌", Red)
    else -> Verdict("فروش قوی 🔴🔴", Red)
}

private fun tfEmoji(score: Int): String = when {
    score >= 1 -> "🟢"
    score <= -1 -> "🔴"
    else -> "⚪"
}

// ---------- تحلیل پایه ----------

private fun buildAnalysis(
    prices1d: List<List<Double>>,
    prices30: List<List<Double>>,
    price: Double
): DetailAnalysis {
    val closes1h = prices30.map { it[1] }
    val c4h = chunkEvery(closes1h, 4)
    val cD = chunkEvery(closes1h, 24)

    val rsi1h = rsiOf(closes1h)
    val e20 = emaLast(closes1h, 20)
    val e50 = emaLast(closes1h, 50)
    val mUp = macdUp(closes1h)
    val atr = atrOf(closes1h)
    val (bbU, bbL) = bollinger(closes1h)

    val up = price > e20 && e20 > e50
    val dn = price < e20 && e20 < e50

    val signal = when {
        up && mUp && rsi1h in 40.0..75.0 -> "BUY"
        up && mUp && rsi1h > 75.0 -> "WAIT_BUY"
        dn && !mUp && rsi1h in 25.0..60.0 -> "SELL"
        dn && rsi1h < 25.0 -> "WAIT_SELL"
        else -> "HOLD"
    }

    val confidence = (40 +
            (if (up || dn) 20 else 0) +
            (if (mUp == up) 15 else 0) +
            (if (rsi1h in 40.0..60.0) 10 else 5)).coerceIn(10, 95)

    val risk = if (atr > 0) atr * 1.5 else price * 0.03
    val isSell = signal == "SELL" || signal == "WAIT_SELL"
    val stop = if (isSell) price + risk else price - risk
    val t1 = if (isSell) price - risk * 1.5 else price + risk * 1.5
    val t2 = if (isSell) price - risk * 2.5 else price + risk * 2.5
    val zoneLow = if (isSell) price + risk * 0.4 else price - risk * 1.2
    val zoneHigh = if (isSell) price + risk * 1.2 else price - risk * 0.4

    val tfs = listOf(
        TfInfo("۱ ساعته", trendOf(closes1h), rsiOf(closes1h)),
        TfInfo("۴ ساعته", trendOf(c4h), rsiOf(c4h)),
        TfInfo("روزانه", trendOf(cD), rsiOf(cD))
    )

    val explanation = buildString {
        append("حکم بالا برآیند ۴ اندیکاتور + کارآگاه شروع‌حرکت + ضدتعقیبه.\n")
        append("🚀 شروع‌حرکت: کندلی که بدنه‌اش بزرگ‌تر از ۱٫۵×ATR باشه و سقف ۱۲ کندل قبل رو بشکنه = اولین هشدار.\n")
        append("🛑 ضدتعقیب: اگه قیمت بیشتر از ۳×ATR از EMA7 فاصله گرفته باشه، حتی در روند صعودی می‌گیم «پولبک رو صبر کن» — چون تعقیبِ سقف = ضرر.\n")
        append("روند ۱ ساعته: ${trendOf(closes1h)} | RSI: ${String.format(Locale.US, "%.0f", rsi1h)}\n")
        append(if (mUp) "MACD صعودیه.\n" else "MACD نزولیه.\n")
        append("💡 B/S های روی نمودار = نقاط «تأیید روند» (دیر اما مطمئن)؛ ورود بهینه = ترکیب حکم بالا + پولبک.")
    }

    return DetailAnalysis(
        signal = signal, confidence = confidence, entry = price, stop = stop,
        target1 = t1, target2 = t2, zoneLow = zoneLow, zoneHigh = zoneHigh,
        rsi1h = rsi1h, macdUp = mUp, ema20 = e20, ema50 = e50, atr = atr,
        bbUpper = bbU, bbLower = bbL, tfs = tfs, explanation = explanation
    )
}

private fun fmt(v: Double): String =
    if (v >= 1) String.format(Locale.US, "$%,.4f", v)
    else String.format(Locale.US, "$%.6f", v)

// ---------- صفحه جزئیات ----------

@Composable
fun CoinDetailScreen(coin: CoinMarket, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var a by remember { mutableStateOf<DetailAnalysis?>(null) }
    var info by remember { mutableStateOf<CoinInfo?>(null) }
    var deriv by remember { mutableStateOf<Derivative?>(null) }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }

    var tf by remember { mutableStateOf("1h") }
    var mode by remember { mutableStateOf(IndicatorMode.EMA) }

    var votes by remember { mutableStateOf<List<Vote>>(emptyList()) }
    var score by remember { mutableStateOf(0) }
    var tfScores by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var breakout by remember { mutableStateOf(false) }
    var chase by remember { mutableStateOf(false) }

    val isFutures = remember {
        context.getSharedPreferences("pumpwatch_prefs", 0)
            .getString("mode", "SPOT") == "FUTURES"
    }

    LaunchedEffect(coin.id) {
        scope.launch {
            loading = true
            error = null
            try {
                val c1d = ApiClient.getCoinChart(coin.id, days = 1)
                val c30 = ApiClient.getCoinChart(coin.id, days = 30)
                if (c1d.prices.size < 10 || c30.prices.size < 20) {
                    error = "داده کافی برای تحلیل نیست (کندل‌ها: ${c1d.prices.size}/${c30.prices.size})"
                } else {
                    a = buildAnalysis(c1d.prices, c30.prices, coin.current_price)
                    loading = false
                    scope.launch {
                        try { info = CoinInfoClient.api.info(coin.id) } catch (_: Exception) { }
                    }
                    scope.launch {
                        if (isFutures) {
                            try {
                                val ders = ScanClient.api.derivatives()
                                deriv = ders.find {
                                    it.base.equals(coin.symbol, true) ||
                                            it.symbol.equals(coin.symbol, true)
                                }
                            } catch (_: Exception) { }
                        }
                    }
                    scope.launch {
                        try {
                            val sym = coin.symbol.uppercase(Locale.US)
                            val specific = NewsClient.api.news(sym).data ?: emptyList()
                            val general = if (specific.isEmpty()) {
                                NewsClient.api.news(null).data ?: emptyList()
                            } else specific
                            news = general.take(3)
                        } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    // ---------- حکم تجمیعی + شروع‌حرکت + ضدتعقیب ----------
    LaunchedEffect(coin.id, tf, deriv) {
        scope.launch {
            try {
                val closes = closesFor(coin.id, tf)
                val v = computeVotes(closes, coin.current_price)
                var s = scoreOf(v)
                if (isFutures) {
                    val fr = deriv?.fundingRate
                    if (fr != null) {
                        if (fr <= -0.0003) s += 1
                        if (fr >= 0.0005) s -= 1
                    }
                }

                // 🚀 کارآگاه شروع حرکت (کندل شکعت بزرگ)
                val price = coin.current_price
                val atrPct = (atrOf(closes) / price).coerceAtLeast(0.0005)
                val lastC = closes.last()
                val prevCloses = closes.dropLast(1)
                val prevC = prevCloses.lastOrNull() ?: lastC
                val body = if (prevC > 0) (lastC - prevC) / prevC else 0.0
                val prevHigh = prevCloses.takeLast(12).maxOrNull() ?: lastC
                breakout = body > 1.5 * atrPct && lastC > prevHigh

                // 🛑 ضدتعقیب: فاصله زیاد از EMA7 بعد از پامپ
                val e7 = emaLast(closes, 7)
                val ext = if (e7 > 0) (price - e7) / e7 else 0.0
                chase = abs(ext) > 3.0 * atrPct

                votes = v + Vote("شروع‌حرکت", "🚀", if (breakout) (if (body > 0) 1 else -1) else 0)
                score = s
            } catch (_: Exception) { }
        }
    }

    // ---------- قطب‌نمای تایم‌فریم‌ها ----------
    LaunchedEffect(coin.id) {
        scope.launch {
            try {
                val list = mutableListOf<Pair<String, Int>>()
                listOf("15m", "1h", "4h", "1d").forEach { t ->
                    val closes = closesFor(coin.id, t)
                    list.add(t to scoreOf(computeVotes(closes, coin.current_price)))
                }
                tfScores = list
            } catch (_: Exception) { }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("← بازگشت") }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${coin.symbol.uppercase(Locale.US)} - ${coin.name}",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    "${String.format(Locale.US, "$%,.6f", coin.current_price)}  •  ${if (isFutures) "⚡ فیوچرز" else "🏦 اسپات"}",
                    fontSize = 13.sp, color = Gray
                )
            }
        }

        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally), color = Green
            )

            error != null -> Text("خطا: $error", color = Red)

            a != null -> {
                val an = a!!

                // ---------- حکم نهایی هوشمند ----------
                val verdict = when {
                    chase && score >= 1 -> Verdict(
                        "⏳ روند صعودیه ولی قیمت بعد از پامپ فاصله گرفته — منتظر پولبک باش، تعقیب نکن 🛑",
                        Yellow
                    )
                    chase && score <= -1 -> Verdict(
                        "⏳ روند نزولیه ولی بعد از ریزش شدید — تعقیب فروش نکن 🛑",
                        Yellow
                    )
                    breakout && score >= -1 -> Verdict(
                        "🚀 شروع حرکت صعودی — ورود پله‌ای با استاپ تنگ",
                        Green
                    )
                    breakout && score <= -2 -> Verdict(
                        " شروع حرکت نزولی — شورت پله‌ای با استاپ تنگ",
                        Red
                    )
                    else -> verdictOf(score)
                }
                val conf = (50 + abs(score) * 11 + (if (breakout) 8 else 0)).coerceIn(40, 95)

                Surface(
                    color = verdict.color.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(verdict.text, fontWeight = FontWeight.Black, fontSize = 18.sp, color = verdict.color)
                        Text("اطمینان: ${conf}٪", fontSize = 14.sp, color = Gray)

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            votes.forEach { v ->
                                Text(
                                    "${v.emoji} ${if (v.dir > 0) "🟢" else if (v.dir < 0) "🔴" else "⚪"}",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (tfScores.isNotEmpty()) {
                            Text(
                                "🧭 " + tfScores.joinToString(" • ") { (t, s) -> "${tfLabel(t)} ${tfEmoji(s)}" },
                                fontSize = 10.sp, color = Gray
                            )
                        }

                        Text(
                            "🎯 بر اساس: ${tfLabel(tf)} • برآیند ۴ اندیکاتور + شروع‌حرکت + ضدتعقیب • ${if (isFutures) "فیوچرز ⚡" else "اسپات 🏦"}",
                            fontSize = 10.sp, color = Gray
                        )
                    }
                }

                ProChart(
                    coin.id,
                    coin.symbol,
                    onTfChange = { tf = it },
                    onModeChange = { mode = it }
                )

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🎯 نقاط دقیق معامله (پایه ۱ ساعته):", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("ورود: ${fmt(an.entry)}", fontSize = 12.sp)
                            Text("استاپ: ${fmt(an.stop)}", fontSize = 12.sp, color = Red)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("هدف۱: ${fmt(an.target1)}", fontSize = 12.sp, color = Green)
                            Text("هدف۲: ${fmt(an.target2)}", fontSize = 12.sp, color = Green)
                        }
                        if (an.signal == "WAIT_BUY") {
                            Text(
                                "🟡 منطقه خرید ایده‌آل: ${fmt(an.zoneLow)} تا ${fmt(an.zoneHigh)}",
                                fontSize = 12.sp, color = Yellow, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⏱️ تایم‌فریم‌ها:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        an.tfs.forEach { t ->
                            val (emoji, tColor) = when (t.trend) {
                                "صعودی" -> "🟢" to Green
                                "نزولی" -> "🔴" to Red
                                else -> "⚪" to Gray
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(t.name, fontSize = 12.sp)
                                Text("${t.trend} $emoji", fontSize = 12.sp, color = tColor)
                                Text("RSI: ${String.format(Locale.US, "%.0f", t.rsi)}", fontSize = 12.sp, color = Gray)
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📊 اندیکاتورهای ۱ ساعته:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IndRow("RSI", String.format(Locale.US, "%.1f", an.rsi1h),
                            if (an.rsi1h > 75 || an.rsi1h < 25) Yellow else Green)
                        IndRow("MACD", if (an.macdUp) "صعودی 🟢" else "نزولی 🔴",
                            if (an.macdUp) Green else Red)
                        IndRow("EMA 20", fmt(an.ema20), if (coin.current_price > an.ema20) Green else Red)
                        IndRow("EMA 50", fmt(an.ema50), if (coin.current_price > an.ema50) Green else Red)
                        IndRow("ATR", fmt(an.atr), Gray)
                        IndRow("بولینگر بالا", fmt(an.bbUpper), Red)
                        IndRow("بولینگر پایین", fmt(an.bbLower), Green)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🏛️ فاندامنتال:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IndRow("رتبه بازار", "#${info?.rank ?: coin.market_cap_rank ?: "-"}", Gray)
                        IndRow("مارکت کپ", formatMarketCap(info?.marketData?.marketCap?.get("usd") ?: coin.market_cap), Gray)
                        val cap = coin.market_cap
                        val turnover = if (cap > 0) coin.total_volume / cap * 100 else 0.0
                        IndRow("فعالیت (حجم/کپ)", String.format(Locale.US, "%.1f%%", turnover),
                            if (turnover > 15) Green else Gray)
                        val ath = info?.marketData?.athChange?.get("usd")
                        if (ath != null) {
                            IndRow("فاصله از سقف تاریخی", String.format(Locale.US, "%.1f%%", ath),
                                if (ath < -50) Green else Yellow)
                        }
                        if (isFutures && deriv != null) {
                            val d = deriv!!
                            val fr = d.fundingRate ?: 0.0
                            IndRow("فاندینگ ریت", String.format(Locale.US, "%.4f%%", fr * 100),
                                if (fr <= -0.0003) Green else if (fr >= 0.0005) Red else Gray)
                            IndRow("Open Interest", formatMarketCap(d.openInterestUsd), Blue)
                            IndRow("حجم ۲۴س فیوچرز", formatMarketCap(d.volume24h), Gray)
                            if (d.market?.name != null) {
                                IndRow("صرافی اصلی", d.market!!.name!!, Gray)
                            }
                        }
                    }
                }

                if (isFutures && deriv?.fundingRate != null) {
                    val fr = deriv!!.fundingRate!!
                    if (fr <= -0.0003) {
                        Text("💡 فاندینگ منفی: شورت‌ها شلوغن — پتانسیل اسکوییز صعودی 🚀", fontSize = 12.sp, color = Green)
                    } else if (fr >= 0.0005) {
                        Text("💡 فاندینگ مثبت شدید: لانگ‌ها شلوغن — احتیاط، احتمال اصلاح 🩸", fontSize = 12.sp, color = Red)
                    }
                }

                if (news.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("📰 اخبار فاندامنتال:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            news.forEach { n ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(n.url ?: ""))
                                                context.startActivity(intent)
                                            } catch (_: Exception) { }
                                        }
                                ) {
                                    Text(n.title ?: "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue)
                                    val t = n.publishedOn ?: 0L
                                    if (t > 0) {
                                        Text(
                                            "${n.source ?: ""} • ${DateUtils.getRelativeTimeSpanString(t * 1000)}",
                                            fontSize = 10.sp, color = Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        an.explanation,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IndRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Gray)
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
