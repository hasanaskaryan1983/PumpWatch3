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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.NewsClient
import com.pumpwatch.app.ui.TodayPicksCache
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private val CGreen = Color(0xFF00E676)
private val CRed = Color(0xFFFF5252)
private val CGray = Color(0xFF9E9E9E)
private val CGold = Color(0xFFFFC107)
private val CBubbleMe = Color(0xFF0E3B2E)
private val CBubbleAi = Color(0xFF1A2230)

private data class Msg(val me: Boolean, val text: String)
private data class CoinReply(val text: String, val price: Double)

// ---------- توابع تحلیل ----------

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
        ag = (ag * (period - 1) + maxOf(d, 0.0)) / period
        al = (al * (period - 1) + maxOf(-d, 0.0)) / period
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

private fun fmt(v: Double): String =
    if (v >= 1) String.format(Locale.US, "$%,.4f", v)
    else String.format(Locale.US, "$%.6f", v)

// ---------- شناسایی ارز ----------

private fun findCoin(input: String): Triple<String, String, String>? {
    val low = input.lowercase(Locale.US)
    val map = listOf(
        "بیت" to Triple("bitcoin", "BTC", "Bitcoin"), "bitcoin" to Triple("bitcoin", "BTC", "Bitcoin"), "btc" to Triple("bitcoin", "BTC", "Bitcoin"),
        "اتریوم" to Triple("ethereum", "ETH", "Ethereum"), "ethereum" to Triple("ethereum", "ETH", "Ethereum"), "eth" to Triple("ethereum", "ETH", "Ethereum"),
        "تتر" to Triple("tether", "USDT", "Tether"), "usdt" to Triple("tether", "USDT", "Tether"),
        "سولانا" to Triple("solana", "SOL", "Solana"), "solana" to Triple("solana", "SOL", "Solana"), "sol" to Triple("solana", "SOL", "Solana"),
        "ریپل" to Triple("ripple", "XRP", "XRP"), "xrp" to Triple("ripple", "XRP", "XRP"),
        "دوج" to Triple("dogecoin", "DOGE", "Dogecoin"), "doge" to Triple("dogecoin", "DOGE", "Dogecoin"),
        "شیبا" to Triple("shiba-inu", "SHIB", "Shiba"), "shib" to Triple("shiba-inu", "SHIB", "Shiba"),
        "کاردانو" to Triple("cardano", "ADA", "Cardano"), "ada" to Triple("cardano", "ADA", "Cardano"),
        "ترون" to Triple("tron", "TRX", "Tron"), "trx" to Triple("tron", "TRX", "Tron"),
        "لایت" to Triple("litecoin", "LTC", "Litecoin"), "ltc" to Triple("litecoin", "LTC", "Litecoin"),
        "bnb" to Triple("binancecoin", "BNB", "BNB"),
        "پپه" to Triple("pepe", "PEPE", "Pepe"), "pepe" to Triple("pepe", "PEPE", "Pepe"),
        "نات" to Triple("notcoin", "NOT", "Notcoin"), "not" to Triple("notcoin", "NOT", "Notcoin"),
        "تون" to Triple("the-open-network", "TON", "Toncoin"), "ton" to Triple("the-open-network", "TON", "Toncoin"),
        "آوالانچ" to Triple("avalanche-2", "AVAX", "Avalanche"), "avax" to Triple("avalanche-2", "AVAX", "Avalanche"),
        "پولکادات" to Triple("polkadot", "DOT", "Polkadot"), "dot" to Triple("polkadot", "DOT", "Polkadot"),
        "چین‌لینک" to Triple("chainlink", "LINK", "Chainlink"), "link" to Triple("chainlink", "LINK", "Chainlink"),
        "یونی‌سواپ" to Triple("uniswap", "UNI", "Uniswap"), "uni" to Triple("uniswap", "UNI", "Uniswap"),
        "آپتوس" to Triple("aptos", "APT", "Aptos"), "apt" to Triple("aptos", "APT", "Aptos"),
        "آربیتروم" to Triple("arbitrum", "ARB", "Arbitrum"), "arb" to Triple("arbitrum", "ARB", "Arbitrum"),
        "اپتیمیزم" to Triple("optimism", "OP", "Optimism"), "op" to Triple("optimism", "OP", "Optimism"),
        "نیر" to Triple("near", "NEAR", "Near"), "near" to Triple("near", "NEAR", "Near"),
        "سویی" to Triple("sui", "SUI", "Sui"), "sui" to Triple("sui", "SUI", "Sui"),
        "اینجکتیو" to Triple("injective-protocol", "INJ", "Injective"), "inj" to Triple("injective-protocol", "INJ", "Injective"),
        "فایل‌کوین" to Triple("filecoin", "FIL", "Filecoin"), "fil" to Triple("filecoin", "FIL", "Filecoin"),
        "استلار" to Triple("stellar", "XLM", "Stellar"), "xlm" to Triple("stellar", "XLM", "Stellar"),
        "مونرو" to Triple("monero", "XMR", "Monero"), "xmr" to Triple("monero", "XMR", "Monero"),
        "کازماس" to Triple("cosmos", "ATOM", "Cosmos"), "atom" to Triple("cosmos", "ATOM", "Cosmos"),
        "بونک" to Triple("bonk", "BONK", "Bonk"), "bonk" to Triple("bonk", "BONK", "Bonk"),
        "فلوکی" to Triple("floki", "FLOKI", "Floki"), "floki" to Triple("floki", "FLOKI", "Floki")
    )
    for ((key, triple) in map) {
        if (low.contains(key)) return triple
    }
    return null
}

// ---------- تحلیل کامل + اخبار ----------

private suspend fun analyzeCoin(id: String, symbol: String, name: String): CoinReply {
    val c30 = ApiClient.getCoinChart(id, days = 30)
    val closes = c30.prices.map { it[1] }
    if (closes.size < 60) return CoinReply("😅 داده کافی برای تحلیل $symbol ندارم.", 0.0)

    val price = closes.last()
    val rsi = rsiOf(closes)
    val e20 = emaLast(closes, 20)
    val e50 = emaLast(closes, 50)
    val mUp = macdUp(closes)
    val atr = atrOf(closes)
    val up = price > e20 && e20 > e50
    val dn = price < e20 && e20 < e50

    val signal = when {
        up && mUp && rsi in 40.0..75.0 -> "BUY"
        up && mUp && rsi > 75 -> "WAIT"
        dn && !mUp && rsi in 25.0..60.0 -> "SELL"
        dn && rsi < 25 -> "WAIT_SELL"
        else -> "HOLD"
    }

    val conf = (40 +
            (if (up || dn) 20 else 0) +
            (if (mUp == up) 15 else 0) +
            (if (rsi in 40.0..60.0) 10 else 5)).coerceIn(10, 95)

    val risk = atr * 1.5
    val (stop, t1, t2) = if (signal == "SELL" || signal == "WAIT_SELL")
        Triple(price + risk, price - risk * 1.5, price - risk * 2.5)
    else
        Triple(price - risk, price + risk * 1.5, price + risk * 2.5)

    val sigText = when (signal) {
        "BUY" -> "✅ خرید"
        "SELL" -> "❌ فروش/شورت"
        "WAIT" -> "⏳ صبر کن (اشباع خرید)"
        "WAIT_SELL" -> "⏳ صبر کن (اشباع فروش)"
        else -> "⏸️ فعلاً بدون معامله"
    }

    val newsLines = try {
        NewsClient.api.news(symbol).data?.take(2)?.mapNotNull { n ->
            if (n.title.isNullOrBlank()) null else "📰 ${n.title}"
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    val sb = StringBuilder()
    sb.append("📊 تحلیل $name ($symbol):\n\n")
    sb.append("سیگنال: $sigText (اطمینان $conf٪)\n")
    sb.append("💵 قیمت: ${fmt(price)}\n")
    sb.append("🎯 ورود: ${fmt(price)}\n")
    sb.append("🛑 استاپ: ${fmt(stop)}\n")
    sb.append("🎯 هدف۱: ${fmt(t1)} | هدف۲: ${fmt(t2)}\n")
    sb.append("📌 RSI: ${String.format(Locale.US, "%.0f", rsi)} | روند: ${if (up) "صعودی 🟢" else if (dn) "نزولی 🔴" else "خنثی ⚪"}\n")
    sb.append("💡 ${if (rsi > 75) "قیمت داغه! دنبال کردن پامپ خطرناکه." else if (rsi < 25) "اشباع فروش — منتظر برگشت باش." else "شرایط متعادله، با مدیریت ریسک وارد شو."}\n")
    if (newsLines.isNotEmpty()) {
        sb.append("\n📰 اخبار اخیر:\n")
        newsLines.forEach { sb.append(it).append("\n") }
    }
    sb.append("\n👇 برای نمودار کندلی + تایم‌فریم‌ها + نقاط B/S دکمه زیر رو بزن!")

    return CoinReply(sb.toString(), price)
}

// ---------- بهترین امروز (از کش سریع TopPicks) ----------

private fun bestPick(context: android.content.Context): String {
    val all = TodayPicksCache.spot + TodayPicksCache.fut
    val top = all.maxByOrNull { it.score }
        ?: return "🤷 هنوز اسکن امروز انجام نشده!\n\nبرای پاسخ سریع:\n۱. برو تب «🏆 برترین‌ها»\n۲. دکمه «اسکن مجدد» رو بزن\n۳. برگرد اینجا و بپرس: بهترین ارز امروز"
    return "🏆 بهترین امروز: ${top.symbol}\n\n" +
            "${if (top.side == "PUMP") "🚀 پامپ" else "🩸 دامپ"} | امتیاز ${top.score}/100 ${if (top.golden) "| 🏅 طلایی" else ""}\n" +
            "💵 ورود: ${fmt(top.entry)}\n" +
            "🛑 استاپ: ${fmt(top.stopLoss)}\n" +
            "🎯 هدف: ${fmt(top.target1)}\n" +
            "📌 ${top.reasons.take(2).joinToString(" • ")}"
}

// ---------- آموزش ----------

private fun teach(low: String): String = when {
    "rsi" in low -> "📚 RSI یعنی قدرت خریدار/فروشنده (۰ تا ۱۰۰):\n• زیر ۳۰ = اشباع فروش (فرصت خرید پله‌ای)\n• بالای ۷۰ = اشباع خرید (احتیاط!)\n• ۴۵ تا ۶۵ = منطقه قدرت برای ادامه روند"
    "macd" in low -> "📚 MACD تقاطع دو میانگین رو نشون می‌ده:\n• خط MACD بالای سیگنال = مومنتوم صعودی 🟢\n• پایین = نزولی 🔴\n• بهترین سیگنال: تقاطع + تأیید حجم"
    "استاپ" in low || "stop" in low -> "📚 استاپ‌لاس = کمربند ایمنی! 🛑\nهمیشه ۱.۵ تا ۲ برابر ATR پایین‌تر از ورود (برای خرید). بدون استاپ = قمار، نه معامله!"
    "اهرم" in low || "لوریج" in low -> "📚 اهرم = تیغ دو لبه! ⚔️\nاهرم ۱۰ یعنی سود و ضرر ۱۰ برابر. تازه‌کارها حداکثر ۳-۵. حرفه‌ای‌ها اول ریسک، بعد سود."
    "فاندینگ" in low || "funding" in low -> "📚 فاندینگ ریت = هزینه نگه‌داشتن پوزیشن:\n• منفی شدید = شورت‌ها شلوغن → پتانسیل اسکوییز صعودی 🚀\n• مثبت شدید = لانگ‌ها شلوغن → احتمال اصلاح 🩸"
    "بولینگر" in low -> "📚 بولینگر = نوار نوسان قیمت:\n• قیمت زیر باند پایین + RSI پایین = فرصت\n• بیرون زد از باند = حرکت شدید، منتظر برگشت باش"
    else -> "📚 چی یاد بگیرم؟ بپرس: RSI / MACD / استاپ / اهرم / فاندینگ / بولینگر\n\nیا بنویس: تحلیل بیت‌کوین 📊"
}

// ---------- صفحه دستیار ----------

@Composable
fun AssistantScreen(onOpenCoin: (CoinMarket) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var messages by remember {
        mutableStateOf(
            listOf(
                Msg(
                    false,
                    "سلام! 🤖 من دستیار هوشمند توأم.\n\nبنویس:\n• «تحلیل بیت‌کوین» 📊\n• «بهترین ارز امروز» 🏆\n• «RSI چیه؟» 📚"
                )
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var lastCoin by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var lastPrice by remember { mutableStateOf(0.0) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, thinking) {
        listState.animateScrollToItem(messages.size + 1)
    }

    fun send(raw: String) {
        val t = raw.trim()
        if (t.isEmpty() || thinking) return
        input = ""
        messages = messages + Msg(true, t)
        thinking = true
        scope.launch {
            val reply = try {
                val low = t.lowercase(Locale.US)
                when {
                    "بهترین" in low || "پیشنهاد" in low -> bestPick(context)
                    ("چی" in low || "چیه" in low || "آموزش" in low || "یاد" in low) -> teach(low)
                    else -> {
                        val coin = findCoin(low)
                        if (coin != null) {
                            val r = analyzeCoin(coin.first, coin.second, coin.third)
                            if (r.price > 0) {
                                lastCoin = coin
                                lastPrice = r.price
                            }
                            r.text
                        } else {
                            "🤔 ارز یا دستور رو نشناختم. نمونه‌ها:\n• تحلیل بیت‌کوین / اتریوم / سولانا / دوج / پپه...\n• بهترین ارز امروز\n• RSI چیه؟"
                        }
                    }
                }
            } catch (e: Exception) {
                "😅 خطا: ${e.message}\nاینترنت رو چک کن و دوباره بپرس."
            }
            messages = messages + Msg(false, reply)
            thinking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // ---------- سربرگ ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", fontSize = 26.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("دستیار هوشمند", fontWeight = FontWeight.Black, fontSize = 17.sp, color = CGreen)
                Text("آنلاین • آماده چت", fontSize = 10.sp, color = CGreen)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- پیام‌ها ----------
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { m -> Bubble(m) }
            if (thinking) {
                item {
                    Row {
                        Surface(color = CBubbleAi, shape = RoundedCornerShape(16.dp)) {
                            Text(
                                "🤖 در حال تحلیل...",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 12.sp,
                                color = CGreen
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---------- دکمه نمودار کامل ----------
        if (lastCoin != null) {
            Button(
                onClick = {
                    val c = lastCoin!!
                    onOpenCoin(
                        CoinMarket(
                            id = c.first, symbol = c.second, name = c.third, image = "",
                            current_price = lastPrice, market_cap = 0.0, total_volume = 0.0,
                            price_change_percentage_24h = null, market_cap_rank = null,
                            change1h = null, change7d = null, high24h = null, low24h = null
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CGreen.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("📈 نمودار کامل ${lastCoin!!.second} + تایم‌فریم‌ها + نقاط B/S + اخبار", fontSize = 12.sp, color = CGreen)
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---------- میان‌برها ----------
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("تحلیل بیت‌کوین", "بهترین امروز", "RSI چیه؟").forEach { q ->
                Surface(
                    onClick = { send(q) },
                    color = CBubbleAi,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(q, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 10.sp, color = CGreen)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ---------- ورودی ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیامت رو بنویس...", fontSize = 12.sp, color = CGray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CBubbleAi,
                    unfocusedContainerColor = CBubbleAi,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(14.dp),
                maxLines = 2
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { send(input) },
                colors = ButtonDefaults.buttonColors(containerColor = CGreen),
                shape = RoundedCornerShape(14.dp)
            ) { Text("➤") }
        }
    }
}

// ---------- حباب پیام ----------

@Composable
private fun Bubble(m: Msg) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (m.me) CBubbleMe else CBubbleAi,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (m.me) 18.dp else 4.dp,
                bottomEnd = if (m.me) 4.dp else 18.dp
            ),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Text(
                m.text,
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}
