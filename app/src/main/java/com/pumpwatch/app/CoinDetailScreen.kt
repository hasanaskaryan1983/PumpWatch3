package com.pumpwatch.app.ui

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
import com.pumpwatch.app.formatMarketCap
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Yellow = Color(0xFFFFC107)
private val Blue = Color(0xFF40C4FF)
private val Gray = Color(0xFF9E9E9E)

// ---------- مدل تحلیل ----------

private data class TfRow(
    val name: String,
    val trend: String,
    val rsi: Double
)

private data class DetailAnalysis(
    val signal: String,
    val confidence: Int,
    val entry: Double,
    val stop: Double,
    val target1: Double,
    val target2: Double,
    val zoneLow: Double,
    val zoneHigh: Double,
    val tfs: List<TfRow>,
    val rsi1h: Double,
    val macdUp: Boolean,
    val ema20: Double,
    val ema50: Double,
    val atr: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val explanation: String
)

// ---------- توابع محاسباتی ----------

private fun emaLast(data: List<Double>, period: Int): Double {
    if (data.size < period) return data.lastOrNull() ?: 0.0
    val k = 2.0 / (period + 1)
    var ema = data.take(period).average()
    for (i in period until data.size) {
        ema = data[i] * k + ema * (1 - k)
    }
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
        ag = (ag * (period - 1) + maxOf(d, 0.0)) / period
        al = (al * (period - 1) + maxOf(-d, 0.0)) / period
    }
    if (al == 0.0) return 100.0
    return 100.0 - 100.0 / (1.0 + ag / al)
}

private fun macdUp(data: List<Double>): Boolean {
    if (data.size < 35) return false
    val ef = emaLast(data, 12)
    val es = emaLast(data, 26)
    val prev = data.dropLast(1)
    val pf = emaLast(prev, 12)
    val ps = emaLast(prev, 26)
    return (ef - es) > (pf - ps)
}

private fun atrOf(data: List<Double>, period: Int = 14): Double {
    if (data.size <= period) return 0.0
    var sum = 0.0
    for (i in 1..period) sum += abs(data[i] - data[i - 1])
    return sum / period
}

private fun bollOf(data: List<Double>, period: Int = 20): Pair<Double, Double> {
    if (data.size < period) return Pair(0.0, 0.0)
    val win = data.takeLast(period)
    val mid = win.average()
    val sd = sqrt(win.map { (it - mid) * (it - mid) }.average())
    return Pair(mid + 2 * sd, mid - 2 * sd)
}

private fun takeNth(data: List<Double>, n: Int): List<Double> =
    data.filterIndexed { i, _ -> (i + 1) % n == 0 }

private fun trendOf(closes: List<Double>): String {
    if (closes.size < 60) return "خنثی"
    val e20 = emaLast(closes, 20)
    val e50 = emaLast(closes, 50)
    val last = closes.last()
    return when {
        last > e20 && e20 > e50 -> "صعودی"
        last < e20 && e20 < e50 -> "نزولی"
        else -> "خنثی"
    }
}

// ---------- ساخت تحلیل کامل ----------

private fun buildAnalysis(
    p5: List<List<Double>>,
    p1: List<List<Double>>,
    price: Double
): DetailAnalysis {
    val c5 = p5.map { it[1] }
    val c1 = p1.map { it[1] }
    val c15 = takeNth(c5, 3)
    val c4 = takeNth(c1, 4)
    val c12 = takeNth(c1, 12)

    val tfs = listOf(
        TfRow("۵ دقیقه", trendOf(c5), rsiOf(c5)),
        TfRow("۱۵ دقیقه", trendOf(c15), rsiOf(c15)),
        TfRow("۱ ساعت", trendOf(c1), rsiOf(c1)),
        TfRow("۴ ساعت", trendOf(c4), rsiOf(c4)),
        TfRow("۱۲ ساعت", trendOf(c12), rsiOf(c12))
    )

    val ups = tfs.count { it.trend == "صعودی" }
    val dns = tfs.count { it.trend == "نزولی" }

    val rsi1 = rsiOf(c1)
    val mUp = macdUp(c1)
    val e20 = emaLast(c1, 20)
    val e50 = emaLast(c1, 50)
    val atr = atrOf(c1)
    val bb = bollOf(c1)

    val risk = atr * 1.5

    val signal: String = when {
        ups >= 4 && rsi1 > 75 -> "WAIT_BUY"
        ups >= 4 && rsi1 in 40.0..75.0 -> "BUY"
        dns >= 4 && rsi1 < 25 -> "WAIT_SELL"
        dns >= 4 && rsi1 in 25.0..60.0 -> "SELL"
        else -> "HOLD"
    }

    val confidence = (40 + maxOf(ups, dns) * 10 + if (mUp && ups > dns) 5 else 0)
        .coerceIn(10, 95)

    val entry = price
    val stop: Double
    val t1: Double
    val t2: Double
    if (signal == "SELL" || signal == "WAIT_SELL") {
        stop = price + risk
        t1 = price - risk * 1.5
        t2 = price - risk * 2.5
    } else {
        stop = price - risk
        t1 = price + risk * 1.5
        t2 = price + risk * 2.5
    }

    val zoneLow = e20
    val zoneHigh = price * 0.985

    val parts = mutableListOf<String>()
    parts.add("${ups} تایم‌فریم صعودی / ${dns} نزولی")
    parts.add("RSI یک‌ساعته: ${String.format(Locale.US, "%.1f", rsi1)}")
    if (rsi1 > 75) parts.add("⚠️ اشباع خرید — ورود الان دیره")
    if (rsi1 < 25) parts.add("⚠️ اشباع فروش — دنبال کردن نزول خطرناکه")
    parts.add(if (mUp) "MACD صعودی" else "MACD نزولی")
    if (price > bb.first) parts.add("قیمت بالای باند بولینگر")
    if (price < bb.second) parts.add("قیمت زیر باند بولینگر")

    return DetailAnalysis(
        signal, confidence, entry, stop, t1, t2, zoneLow, zoneHigh,
        tfs, rsi1, mUp, e20, e50, atr, bb.first, bb.second,
        parts.joinToString(" • ")
    )
}

// ---------- صفحه جزئیات حرفه‌ای ----------

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
                    try {
                        info = CoinInfoClient.api.info(coin.id)
                    } catch (_: Exception) { }
                    if (isFutures) {
                        try {
                            val ders = ScanClient.api.derivatives()
                            deriv = ders.find {
                                it.base.equals(coin.symbol, true) ||
                                        it.symbol.equals(coin.symbol, true)
                            }
                        } catch (_: Exception) { }
                    }
                    try {
                        val sym = coin.symbol.uppercase(Locale.US)
                        val specific = NewsClient.api.news(sym).data ?: emptyList()
                        val general = if (specific.isEmpty()) {
                            NewsClient.api.news(null).data ?: emptyList()
                        } else specific
                        news = general.take(3)
                    } catch (_: Exception) { }
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
                    "${String.format(Locale.US, "$%,.6f", coin.current_price)}  •  ${if (isFutures) "⚡ فیوچرز" else "🏦 اسپات"}",
                    fontSize = 13.sp,
                    color = Gray
                )
            }
        }

        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Green
            )

            error != null -> Text("خطا: $error", color = Red)

            a != null -> {
                val an = a!!

                // ---------- سیگنال ----------
                val (sigText, sigColor) = when (an.signal) {
                    "BUY" -> "✅ خرید" to Green
                    "SELL" -> "❌ فروش / شورت" to Red
                    "WAIT_BUY" -> "⏳ صبر کن برای پولبک" to Yellow
                    "WAIT_SELL" -> "⏳ اشباع فروش — صبر کن" to Yellow
                    else -> "⏸️ بدون معامله" to Gray
                }
                Surface(
                    color = sigColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(sigText, fontWeight = FontWeight.Black, fontSize = 22.sp, color = sigColor)
                        Text("اطمینان: ${an.confidence}٪", fontSize = 14.sp, color = Gray)
                    }
                }

                // ---------- نمودار حرفه‌ای ----------
                ProChart(coin.id, coin.symbol)

                // ---------- نقاط دقیق ----------
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🎯 نقاط دقیق معامله:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                                fontSize = 12.sp,
                                color = Yellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ---------- جدول تایم‌فریم‌ها ----------
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
                                Text(
                                    "RSI: ${String.format(Locale.US, "%.0f", t.rsi)}",
                                    fontSize = 12.sp,
                                    color = Gray
                                )
                            }
                        }
                    }
                }

                // ---------- اندیکاتورهای ۱ ساعته ----------
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

                // ---------- فاندامنتال ----------
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
                            IndRow(
                                "فاندینگ ریت",
                                String.format(Locale.US, "%.4f%%", fr * 100),
                                if (fr <= -0.0003) Green else if (fr >= 0.0005) Red else Gray
                            )
                            IndRow("Open Interest", formatMarketCap(d.openInterestUsd), Blue)
                            IndRow("حجم ۲۴س فیوچرز", formatMarketCap(d.volume24h), Gray)
                            if (d.market?.name != null) {
                                IndRow("صرافی اصلی", d.market!!.name!!, Gray)
                            }
                        }
                    }
                }

                // ---------- نکته فاندینگ ----------
                if (isFutures && deriv?.fundingRate != null) {
                    val fr = deriv!!.fundingRate!!
                    if (fr <= -0.0003) {
                        Text(
                            "💡 فاندینگ منفی: شورت‌ها شلوغن — پتانسیل اسکوییز صعودی 🚀",
                            fontSize = 12.sp,
                            color = Green
                        )
                    } else if (fr >= 0.0005) {
                        Text(
                            "💡 فاندینگ مثبت شدید: لانگ‌ها شلوغن — احتیاط، احتمال اصلاح 🩸",
                            fontSize = 12.sp,
                            color = Red
                        )
                    }
                }

                // ---------- اخبار فاندامنتال ----------
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
                                    Text(
                                        n.title ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Blue
                                    )
                                    val t = n.publishedOn ?: 0L
                                    if (t > 0) {
                                        Text(
                                            "${n.source ?: ""} • ${DateUtils.getRelativeTimeSpanString(t * 1000)}",
                                            fontSize = 10.sp,
                                            color = Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- توضیحات ----------
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        an.explanation,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ---------- ردیف اندیکاتور ----------

@Composable
private fun IndRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ---------- فرمت قیمت ----------

private fun fmt(v: Double): String =
    if (v >= 1) String.format(Locale.US, "$%,.4f", v)
    else String.format(Locale.US, "$%.6f", v)
