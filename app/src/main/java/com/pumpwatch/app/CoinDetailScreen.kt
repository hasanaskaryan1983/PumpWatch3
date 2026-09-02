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
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.data.CoinInfo
import com.pumpwatch.app.data.CoinInfoClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.Derivative
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.NewsClient
import com.pumpwatch.app.data.NewsItem
import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.ui.IndicatorMode
import com.pumpwatch.app.ui.ProChart
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Yellow = Color(0xFFFFC107)
private val Gray = Color(0xFF8B949E)
private val Blue = Color(0xFF40C4FF)

private data class TfInfo(val name: String, val trend: String, val rsi: Double)

private data class Layer(
    val ema: Int, val rsi: Int, val macd: Int, val vol: Int, val boll: Int, val score: Int
)

private data class DetailAnalysis(
    val signal: String, val confidence: Int, val entry: Double, val stop: Double,
    val target1: Double, val target2: Double, val zoneLow: Double, val zoneHigh: Double,
    val rsi1h: Double, val macdUp: Boolean, val ema20: Double, val ema50: Double,
    val atr: Double, val bbUpper: Double, val bbLower: Double,
    val tfs: List<TfInfo>, val explanation: String
)

private data class Verdict(val text: String, val color: Color)

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

private suspend fun binanceData(symbol: String, interval: String): Pair<List<Double>, List<Double>>? {
    return try {
        val kl = BinanceClient.api.klines(symbol + "USDT", interval, 100)
        Pair(kl.map { it[4].asDouble }, kl.map { it[5].asDouble })
    } catch (_: Exception) {
        null
    }
}

private fun computeLayer(closes: List<Double>, volumes: List<Double>?): Layer {
    if (closes.size < 40) return Layer(0, 0, 0, 0, 0, 0)
    val price = closes.last()
    val e20 = emaLast(closes, 20)
    val e50 = emaLast(closes, 50)
    val ema = when {
        price > e20 && e20 > e50 -> 1
        price < e20 && e20 < e50 -> -1
        else -> 0
    }
    val r = rsiOf(closes)
    val rsi = when {
        r <= 35 -> 1
        r >= 65 -> -1
        else -> 0
    }
    val macd = if (macdUp(closes)) 1 else -1

    val (bu, bl) = bollinger(closes)
    val prev = closes.dropLast(1)
    val (pbu, pbl) = bollinger(prev)
    val c = price
    val pc = prev.lastOrNull() ?: c
    val boll = when {
        pc <= pbl && c > bl -> 1
        pc >= pbu && c < bu -> -1
        c <= bl * 1.01 -> 1
        c >= bu * 0.99 -> -1
        c > (bu + bl) / 2 && macd == 1 -> 1
        c < (bu + bl) / 2 && macd == -1 -> -1
        else -> 0
    }

    val vol = if (volumes != null && volumes.size > 15) {
        val lv = volumes.last()
        val av = volumes.dropLast(1).takeLast(14).average()
        val bd = if (c >= pc) 1 else -1
        if (av > 0 && lv >= 1.5 * av) bd else 0
    } else 0

    val score = ema * 25 + macd * 25 + rsi * 20 + vol * 15 + boll * 15
    return Layer(ema, rsi, macd, vol, boll, score)
}

private fun lightEmoji(score: Int): String = when {
    score >= 40 -> ""
    score <= -40 -> ""
    else -> ""
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
        append("🧠 معماری امتیازدهی وزنی (Confluence):\n")
        append("EMA 25% + MACD 25% + RSI 20% + حجم 15% + بولینگر 15% = امتیاز پایه (-100 تا +100)\n")
        append("🚦 هم‌راستایی ۳ تایم‌فریم (۱۵د/۱س/۴س) = +10 پاداش\n")
        append(" فاندینگ منفی شدید = +10 | فاندینگ مثبت شدید = -10\n")
        append("📈 OI صعودی همراه قیمت = +10 | پامپ بدون OI = -10 (پامپ مصنوعی)\n")
        append("⛔ وتوی نهنگی: فشار فروش آن‌چین ≥ 65% = مسدود شدن سیگنال خرید\n")
        append("🎯 آستانه‌ها: ≥75 خرید قوی | ≥40 خرید | ±40 خنثی | ≤-40 فروش\n")
        append("💡 سیگنال کمتر ولی باکیفیت‌تر = اعتماد بیشتر = سود پایدار")
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

    var tf by remember { mutableStateOf("1h") }
    var mode by remember { mutableStateOf(IndicatorMode.EMA) }

    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var confScore by remember { mutableStateOf(0) }
    var layer by remember { mutableStateOf<Layer?>(null) }
    var lights by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var aligned by remember { mutableStateOf(false) }
    var fundingRate by remember { mutableStateOf<Double?>(null) }
    var oiUp by remember { mutableStateOf<Boolean?>(null) }
    var veto by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(coin.id, tf, deriv) {
        scope.launch {
            try {
                val sym = coin.symbol.uppercase(Locale.US)

                val bd = binanceData(sym, tf)
                val closes = bd?.first ?: closesFor(coin.id, tf)
                val vols = bd?.second
                val ly = computeLayer(closes, vols)
                layer = ly

                val lightList = mutableListOf<Pair<String, Int>>()
                val dirs = mutableListOf<Int>()
                listOf("15m", "1h", "4h").forEach { t ->
                    val l = if (t == tf) ly else run {
                        val b2 = binanceData(sym, t)
                        computeLayer(b2?.first ?: closesFor(coin.id, t), b2?.second)
                    }
                    lightList.add(tfLabel(t) to l.score)
                    dirs.add(if (l.score >= 40) 1 else if (l.score <= -40) -1 else 0)
                }
                lights = lightList
                aligned = dirs.size == 3 && dirs[0] != 0 && dirs.all { it == dirs[0] }

                val price = coin.current_price
                val atrPct = (atrOf(closes) / price).coerceAtLeast(0.0005)
                val prevCloses = closes.dropLast(1)
                val prevC = prevCloses.lastOrNull() ?: closes.last()
                val body = if (prevC > 0) (closes.last() - prevC) / prevC else 0.0
                val prevHigh = prevCloses.takeLast(12).maxOrNull() ?: closes.last()
                val brk = body > 1.5 * atrPct && closes.last() > prevHigh
                val e7 = emaLast(closes, 7)
                val ext = if (e7 > 0) (price - e7) / e7 else 0.0
                val chs = abs(ext) > 3.0 * atrPct

                val fr = try {
                    BinanceFutures.api.premiumIndex(sym + "USDT").lastFundingRate?.toDoubleOrNull()
                } catch (_: Exception) { null }
                fundingRate = fr
                val oi = try {
                    val h = BinanceFutures.api.oiHist(sym + "USDT", "1h", 24)
                    if (h.size >= 2) {
                        val first = h.first().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                        val lastV = h.last().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                        lastV > first
                    } else null
                } catch (_: Exception) { null }
                oiUp = oi

                val vt = try {
                    val pool = GeckoTerminal.api.searchPools(sym)
                        .data?.firstOrNull { it.attributes != null }
                    val at = pool?.attributes
                    val b = at?.transactions?.h1?.buys ?: 0.0
                    val s = at?.transactions?.h1?.sells ?: 0.0
                    if (s > 0 && b / (b + s) <= 0.35) "فشار فروش سنگین نهنگ‌ها در آن‌چین 🐳" else null
                } catch (_: Exception) { null }
                veto = vt

                var sc = ly.score
                if (aligned) sc += if (dirs[0] > 0) 10 else -10
                val priceUp = closes.last() >= prevC
                fr?.let { if (it <= -0.0003) sc += 10 else if (it >= 0.0005) sc -= 10 }
                oi?.let { if (priceUp) sc += if (it) 10 else -10 }
                sc = sc.coerceIn(-100, 100)
                confScore = sc

                // 📝 ثبت خودکار سیگنال
                if (confScore >= 40 || confScore <= -40) {
                    val side = if (confScore > 0) "BUY" else "SELL"
                    val tgt = if (side == "BUY") a?.target1 else a?.target2
                    if (a != null) {
                        SignalLogger.log(
                            context,
                            LoggedSignal(
                                symbol = sym,
                                side = side,
                                score = confScore,
                                entry = price,
                                stop = a.stop,
                                target = tgt ?: price,
                                time = System.currentTimeMillis()
                            )
                        )
                    }
                }

                val base = when {
                    sc >= 75 -> Verdict("خرید قوی 🟢🟢", Green)
                    sc >= 40 -> Verdict("خرید ✅", Green)
                    sc > -40 -> Verdict("خنثی ⚪ — منتظر بمون", Gray)
                    sc > -75 -> Verdict("فروش / شورت ❌", Red)
                    else -> Verdict("فروش قوی 🔴🔴", Red)
                }
                verdict = when {
                    vt != null && sc > 0 -> Verdict(" خرید مسدود: $vt", Red)
                    chs && sc >= 40 -> Verdict("⏳ روند صعودیه ولی قیمت بعد از پامپ فاصله گرفته — منتظر پولبک 🛑", Yellow)
                    chs && sc <= -40 -> Verdict("⏳ روند نزولیه ولی بعد از ریزش شدید — تعقیب نکن ", Yellow)
                    brk && sc in -20..39 -> Verdict("🚀 شروع حرکت — ورود پله‌ای با استاپ تنگ", Green)
                    else -> base
                }
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
                    "${String.format(Locale.US, "$%,.6f", coin.current_price)}  •  ${if (isFutures) "⚡ فیوچرز" else " اسپات"}",
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
                val v = verdict

                Surface(
                    color = (v?.color ?: Gray).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            v?.text ?: "⏳ در حال محاسبه...",
                            fontWeight = FontWeight.Black, fontSize = 17.sp,
                            color = v?.color ?: Gray
                        )
                        Text(
                            "امتیاز هم‌گرایی: ${confScore}/100",
                            fontSize = 13.sp, color = Gray, fontWeight = FontWeight.Bold
                        )

                        layer?.let { l ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IndChip("EMA 25%", l.ema)
                                IndChip("MACD 25%", l.macd)
                                IndChip("RSI 20%", l.rsi)
                                IndChip("حجم 15%", l.vol)
                                IndChip("بولینگر 15%", l.boll)
                            }
                        }

                        if (lights.isNotEmpty()) {
                            Text(
                                "🚦 " + lights.joinToString(" • ") { (label, s) -> "$label ${lightEmoji(s)}" },
                                fontSize = 11.sp, color = Gray
                            )
                            if (aligned) {
                                Text(
                                    "✅ هم‌راستایی کامل ۳ تایم‌فریم (+۰ امتیاز)",
                                    fontSize = 10.sp, color = Green, fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        fundingRate?.let { fr ->
                            Text(
                                "⚡ فاندینگ: ${String.format(Locale.US, "%.4f%%", fr * 100)}" +
                                        (if (fr <= -0.0003) " — پتانسیل اسکوییز 🚀" else if (fr >= 0.0005) " — لانگ‌ها شلوغن 🩸" else ""),
                                fontSize = 10.sp,
                                color = if (fr <= -0.0003) Green else if (fr >= 0.0005) Red else Gray
                            )
                        }
                        oiUp?.let { up ->
                            Text(
                                "📈 OI (24h): ${if (up) "صعودی — پول واقعی وارد شده ✅" else "نزولی — احتیاط"}",
                                fontSize = 10.sp,
                                color = if (up) Green else Yellow
                            )
                        }
                        veto?.let { vt ->
                            Text(
                                "⛔ وتوی نهنگی: $vt",
                                fontSize = 10.sp, color = Red, fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "🎯 بر اساس: ${tfLabel(tf)} • معماری وزنی Confluence • ${if (isFutures) "فیوچرز ⚡" else "اسپات 🏦"}",
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
                                "صعودی" -> "" to Green
                                "نزولی" -> "" to Red
                                else -> "" to Gray
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
                        Text(" اندیکاتورهای ۱ ساعته:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IndRow("RSI", String.format(Locale.US, "%.1f", an.rsi1h),
                            if (an.rsi1h > 75 || an.rsi1h < 25) Yellow else Green)
                        IndRow("MACD", if (an.macdUp) "صعودی " else "نزولی ",
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
private fun IndChip(label: String, dir: Int) {
    Text(
        "$label ${if (dir > 0) "" else if (dir < 0) "" else ""}",
        fontSize = 10.sp
    )
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
