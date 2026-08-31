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

// ---------- سیگنال پویا بر اساس تایم‌فریم + اندیکاتور + فیوچرز ----------

private fun computeDynSignal(
    closes: List<Double>,
    price: Double,
    mode: IndicatorMode,
    isFutures: Boolean,
    funding: Double?
): Pair<String, Int> {
    if (closes.size < 40) return "HOLD" to 50
    val rsi = rsiOf(closes)
    val e7 = emaLast(closes, 7)
    val e30 = emaLast(closes, 30)
    val mUp = macdUp(closes)
    val (bbU, bbL) = bollinger(closes)

    var sig: String
    var conf: Int

    when (mode) {
        IndicatorMode.EMA -> {
            if (price > e7 && e7 > e30) { sig = "BUY"; conf = if (mUp) 82 else 66 }
            else if (price < e7 && e7 < e30) { sig = "SELL"; conf = if (!mUp) 82 else 66 }
            else { sig = "HOLD"; conf = 50 }
        }
        IndicatorMode.RSI -> {
            if (rsi <= 30) { sig = "BUY"; conf = 85 }
            else if (rsi <= 40 && mUp) { sig = "BUY"; conf = 66 }
            else if (rsi >= 70) { sig = "SELL"; conf = 85 }
            else if (rsi >= 60 && !mUp) { sig = "SELL"; conf = 66 }
            else { sig = "HOLD"; conf = 50 }
        }
        IndicatorMode.MACD -> {
            if (mUp && price > e30) { sig = "BUY"; conf = 78 }
            else if (mUp) { sig = "WAIT_BUY"; conf = 60 }
            else if (!mUp && price < e30) { sig = "SELL"; conf = 78 }
            else if (!mUp) { sig = "WAIT_SELL"; conf = 60 }
            else { sig = "HOLD"; conf = 50 }
        }
        IndicatorMode.BOLL -> {
            if (price <= bbL * 1.01) { sig = "BUY"; conf = 82 }
            else if (price >= bbU * 0.99) { sig = "SELL"; conf = 82 }
            else if (price > (bbU + bbL) / 2 && mUp) { sig = "BUY"; conf = 62 }
            else if (price < (bbU + bbL) / 2 && !mUp) { sig = "SELL"; conf = 62 }
            else { sig = "HOLD"; conf = 50 }
        }
        IndicatorMode.NONE -> {
            if (price > e30 && mUp) { sig = "BUY"; conf = 60 }
            else if (price < e30 && !mUp) { sig = "SELL"; conf = 60 }
            else { sig = "HOLD"; conf = 50 }
        }
    }

    if (isFutures && funding != null) {
        if (funding <= -0.0003 && sig == "BUY") conf = (conf + 10).coerceAtMost(95)
        if (funding >= 0.0005 && sig == "BUY") { sig = "WAIT_BUY"; conf = (conf - 10).coerceAtLeast(40) }
        if (funding >= 0.0005 && sig == "SELL") conf = (conf + 10).coerceAtMost(95)
        if (funding <= -0.0003 && sig == "SELL") { sig = "WAIT_SELL"; conf = (conf - 10).coerceAtLeast(40) }
    }
    return sig to conf
}

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
        append("روند ۱ ساعته: ${trendOf(closes1h)} | RSI: ${String.format(Locale.US, "%.0f", rsi1h)}\n")
        append(if (mUp) "MACD صعودیه و مومنتوم مثبته.\n" else "MACD نزولیه و مومنتوم منفیه.\n")
        when (signal) {
            "BUY" -> append("✅ شرایط خرید مهیاست: روند + مومنتوم + RSI سالم. با مدیریت ریسک وارد شو.")
            "SELL" -> append("✅ شرایط فروش/شورت مهیاست: روند نزولی + مومنتوم منفی.")
            "WAIT_BUY" -> append("🟡 قیمت داغه (RSI بالا). منتظر پولبک به منطقه زرد بمون بعد وارد شو.")
            "WAIT_SELL" -> append("🟡 اشباع فروش. عجولانه شورت نزن — منتظر برگشت باش.")
            else -> append("⏸️ سیگنال واضحی نیست. بهتره فعلاً فقط تماشا کنی.")
        }
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

    // ---------- حالت پویای سیگنال ----------
    var tf by remember { mutableStateOf("1h") }
    var mode by remember { mutableStateOf(IndicatorMode.EMA) }
    var dynSig by remember { mutableStateOf<String?>(null) }
    var dynConf by remember { mutableStateOf<Int?>(null) }

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

    // ---------- سیگنال پویا: با تغییر tf/mode/deriv دوباره حساب می‌شه ----------
    LaunchedEffect(coin.id, tf, mode, deriv) {
        scope.launch {
            try {
                val (days, chunk) = when (tf) {
                    "15m" -> 1 to 3
                    "1h" -> 2 to 1
                    "4h" -> 8 to 4
                    "1d" -> 200 to 1
                    else -> 365 to 7
                }
                val chart = ApiClient.getCoinChart(coin.id, days = days)
                val closes = chart.prices.map { it[1] }.chunked(chunk).map { it.last() }
                val (s, c) = computeDynSignal(
                    closes, coin.current_price, mode, isFutures, deriv?.fundingRate
                )
                dynSig = s
                dynConf = c
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
                val effSig = dynSig ?: an.signal
                val effConf = dynConf ?: an.confidence

                val (sigText, sigColor) = when (effSig) {
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(sigText, fontWeight = FontWeight.Black, fontSize = 22.sp, color = sigColor)
                        Text("اطمینان: ${effConf}٪", fontSize = 14.sp, color = Gray)
                        Text(
                            "🎯 بر اساس: ${tfLabel(tf)} • ${mode.label} • ${if (isFutures) "فیوچرز ⚡" else "اسپات 🏦"}",
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
                        if (effSig == "WAIT_BUY") {
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
