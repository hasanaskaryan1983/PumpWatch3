package com.pumpwatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val WGreen = Color(0xFF00E676)
private val WRed = Color(0xFFFF5252)
private val WBlue = Color(0xFF40C4FF)
private val WGold = Color(0xFFFFC107)
private val WGray = Color(0xFF8B949E)
private val CardA = Color(0xFF1A2230)
private val CardB = Color(0xFF141B25)

private val ALL_CHAINS = listOf(
    "solana" to "Solana 🟣",
    "bsc" to "BSC 🟡",
    "base" to "Base 🔵",
    "ethereum" to "Ethereum ⚪",
    "arbitrum" to "Arbitrum 🔷",
    "optimism" to "Optimism 🔴",
    "polygon" to "Polygon ",
    "avalanche" to "Avalanche 🔺",
    "ton" to "TON 🔵",
    "cronos" to "Cronos 🔷",
    "fantom" to "Fantom 👻",
    "gnosis" to "Gnosis 🦉",
    "celo" to "Celo 🟢",
    "aurora" to "Aurora ",
    "harmony" to "Harmony 🎵",
    "moonbeam" to "Moonbeam 🌕",
    "moonriver" to "Moonriver 🌊",
    "kava" to "Kava ☕",
    "metis" to "Metis 🏛️",
    "boba" to "Boba 🧋",
    "fuse" to "Fuse 🔥",
    "evmos" to "Evmos ",
    "milkomeda" to "Milkomeda 🥛",
    "syscoin" to "Syscoin 🪙",
    "oasis" to "Oasis 🏝️",
    "telos" to "Telos ",
    "wanchain" to "Wanchain 🔗",
    "iotex" to "IoTeX 📡",
    "theta" to "Theta 🎥",
    "klaytn" to "Klaytn 🇰",
    "velas" to "Velas ⚡",
    "elastos" to "Elastos 🐉",
    "heco" to "HECO 🔥",
    "okexchain" to "OKExChain 🔷",
    "smartbch" to "SmartBCH ",
    "rsk" to "RSK 🔴",
    "xdai" to "xDai 🦴",
    "poa" to "POA 📜",
    "artis" to "ARTIS 🎨",
    "callisto" to "Callisto 🌑",
    "tombchain" to "Tombchain ️",
    "dogechain" to "Dogechain 🐕",
    "step" to "Step 👣",
    "godwoken" to "Godwoken 🐉",
    "rei" to "REI ⚔️",
    "astar" to "Astar ",
    "shiden" to "Shiden 🌑",
    "shibuya" to "Shibuya 🌃",
    "clover" to "Clover 🍀",
    "parallel" to "Parallel ⫽",
    "centrifuge" to "Centrifuge 🌀",
    "altair" to "Altair ️",
    "kintsugi" to "Kintsugi 🏺",
    "robonomics" to "Robonomics 🤖",
    "sakura" to "Sakura ",
    "shadow" to "Shadow 👤",
    "crust" to "Crust ",
    "equilibrium" to "Equilibrium ⚖️",
    "genshiro" to "Genshiro 🎯",
    "calamari" to "Calamari 🦑",
    "manta" to "Manta "
)

private data class ChartCandle(val o: Double, val h: Double, val l: Double, val c: Double, val marker: Int)
private data class FlowRow(val label: String, val buy: Double, val sell: Double)

private data class AnalysisData(
    val candles: List<ChartCandle>,
    val zone: Double?,
    val flows: List<FlowRow>,
    val changePct: Double,
    val poolName: String?
)

private data class WhalePick(
    val symbol: String,
    val name: String,
    val chain: String,
    val chainName: String,
    val price: Double,
    val volH1: Double,
    val buysH1: Double,
    val sellsH1: Double,
    val buysH6: Double,
    val sellsH6: Double,
    val buysH24: Double,
    val sellsH24: Double,
    val volH6: Double,
    val volH24: Double,
    val liquidity: Double,
    val changeH1: Double,
    val ageHours: Double,
    val fdv: Double,
    val credScore: Int,
    val rank: Int?,
    val marketCap: Double?,
    val poolUrl: String
)

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun chainEmoji(chain: String): String = ALL_CHAINS.firstOrNull { it.first == chain }?.second?.take(2) ?: "⛓️"

private fun ageText(h: Double): String = when {
    h >= 9999 -> "—"
    h < 1 -> "زیر ۱ ساعت"
    h < 48 -> "${h.toInt()} ساعت"
    else -> "${(h / 24).toInt()} روز"
}

private fun ageHours(createdAt: String?): Double {
    if (createdAt == null) return 9999.0
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val t = sdf.parse(createdAt) ?: return 9999.0
        (System.currentTimeMillis() - t.time) / 3_600_000.0
    } catch (_: Exception) {
        9999.0
    }
}

private fun ratio(b: Double, s: Double): Double {
    val t = b + s
    return if (t > 0) b / t else 0.5
}

private fun verdictText(r1: Double): String = when {
    r1 >= 0.6 -> "🐳 نهنگ‌ها در حال جمع‌کردن این ارز هستن 🚀"
    r1 <= 0.4 -> " فشار فروش نهنگی — احتیاط"
    else -> "⚖️ خرید معمولی"
}

private fun verdictColor(r1: Double): Color = when {
    r1 >= 0.6 -> WGreen
    r1 <= 0.4 -> WRed
    else -> WGray
}

private fun marketPosText(rank: Int?, cap: Double?): String {
    return if (rank != null) {
        val capText = if (cap != null) " • کپ ${compact(cap)}" else ""
        "🏦 جایگاه در بازار: رتبه #${rank}$capText"
    } else {
        "🏦 جایگاه در بازار: بدون رتبه — فقط در DEX"
    }
}

private fun tfNameOf(k: String): String = when (k) {
    "1h" -> "۱ ساعته"
    "6h" -> "۶ ساعته"
    "24h" -> "روزانه"
    "4h" -> " ساعته"
    "12h" -> "۱۲ ساعته"
    "3d" -> "۳ روزه"
    else -> "هفتگی"
}

private fun trustChecks(l: WhalePick): List<Pair<String, Boolean>> {
    val r1 = ratio(l.buysH1, l.sellsH1)
    return listOf(
        "نقدینگی ≥ ۱۰۰K" to (l.liquidity >= 100_000),
        "حجم واقعی ۱س ≥ ۵۰K" to (l.volH1 >= 50_000),
        "معامله دوطرفه (ضد هانی‌پات)" to (l.buysH1 > 0 && l.sellsH1 > 0),
        "فشار خرید مثبت ≥ ۵٪" to (r1 >= 0.55),
        "سن استخر ≥ ۲۴ ساعت" to (l.ageHours >= 24),
        "FDV سالم (۰۰K تا ۲۰M)" to (l.fdv in 100_000.0..20_000_000.0),
        "لیست‌شده در CoinGecko" to (l.rank != null)
    )
}

@Composable
private fun TrustRows(checks: List<Pair<String, Boolean>>) {
    checks.forEach { (label, ok) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (ok) "✅" else "⚠️", fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 10.sp, color = if (ok) WGreen else WGold)
        }
    }
}

private fun poolStats(p: GeckoPool): WhalePick? {
    val a = p.attributes ?: return null
    val price = a.priceUsd?.toDoubleOrNull() ?: return null
    if (price <= 0) return null
    val name = a.name ?: "?"
    val symbol = name.split("/").firstOrNull()?.trim() ?: "?"

    val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
    val b1 = a.transactions?.h1?.buys ?: 0.0
    val s1 = a.transactions?.h1?.sells ?: 0.0
    val b6 = a.transactions?.h6?.buys ?: 0.0
    val s6 = a.transactions?.h6?.sells ?: 0.0
    val b24 = a.transactions?.h24?.buys ?: 0.0
    val s24 = a.transactions?.h24?.sells ?: 0.0
    val fdv = a.fdvUsd ?: 0.0
    val age = ageHours(a.createdAt)

    var score = 0
    if (liq >= 100_000) score += 25 else if (liq >= 50_000) score += 10
    if (b1 > 0 && s1 > 0) score += 15
    val r1 = ratio(b1, s1)
    if (r1 >= 0.6) score += 25 else if (r1 >= 0.5) score += 10
    if (age >= 24) score += 15 else if (age >= 6) score += 10 else score += 5
    if (fdv in 100_000.0..10_000_000.0) score += 20

    val network = p.relationships?.network?.data?.id ?: "solana"
    val addr = p.id?.substringAfter('_') ?: ""
    val chainName = ALL_CHAINS.firstOrNull { it.first == network }?.second ?: network

    return WhalePick(
        symbol = symbol, name = name, chain = network, chainName = chainName, price = price,
        volH1 = a.volume?.h1 ?: 0.0,
        buysH1 = b1, sellsH1 = s1, buysH6 = b6, sellsH6 = s6,
        buysH24 = b24, sellsH24 = s24,
        volH6 = a.volume?.h6 ?: 0.0, volH24 = a.volume?.h24 ?: 0.0,
        liquidity = liq, changeH1 = a.priceChange?.h1 ?: 0.0,
        ageHours = age, fdv = fdv, credScore = score.coerceAtMost(100),
        rank = null, marketCap = null,
        poolUrl = "https://www.geckoterminal.com/$network/pools/$addr"
    )
}

@Composable
private fun WhaleFlowChart(candles: List<ChartCandle>, zone: Double?) {
    Canvas(modifier = Modifier.fillMaxWidth().height(190.dp)) {
        if (candles.size < 2) return@Canvas
        val vals = candles.flatMap { listOf(it.h, it.l) }
        val minValue = vals.min()
        val maxValue = vals.max()
        val range = if (maxValue > minValue) maxValue - minValue else 1.0
        val w = size.width
        val h = size.height
        val n = candles.size
        val cw = w / n
        val bodyW = cw * 0.55f

        fun y(v: Double) = (h - ((v - minValue) / range * h * 0.86 + h * 0.07)).toFloat()

        candles.forEachIndexed { i, c ->
            val x = i * cw + cw / 2
            val col = if (c.c >= c.o) WGreen else WRed
            drawLine(col, Offset(x, y(c.h)), Offset(x, y(c.l)), strokeWidth = 2f)
            val yO = y(c.o)
            val yC = y(c.c)
            val top = min(yO, yC)
            val bh = max(3f, abs(yO - yC))
            drawRect(col, topLeft = Offset(x - bodyW / 2, top), size = Size(bodyW, bh))
            if (c.marker == 1) drawCircle(WGreen, radius = 7f, center = Offset(x, y(c.l) + 18f))
            else if (c.marker == -1) drawCircle(WRed, radius = 6f, center = Offset(x, y(c.h) - 18f))
        }

        if (zone != null && zone in minValue..maxValue) {
            drawLine(
                WGold, Offset(0f, y(zone)), Offset(w, y(zone)),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            )
        }

        val paint = android.graphics.Paint().apply {
            textSize = 26f
            color = android.graphics.Color.GRAY
        }
        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "$%,.4f", maxValue), 4f, 32f, paint)
        drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "$%,.4f", minValue), 4f, h - 6f, paint)
    }
}

@Composable
private fun FlowLine(label: String, b: Double, s: Double, vol: Double) {
    val r = ratio(b, s)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = WGray, modifier = Modifier.width(56.dp))
        Text(" ${compact(vol * r)}", fontSize = 10.sp, color = WGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("🔴 ${compact(vol * (1 - r))}", fontSize = 10.sp, color = WRed)
        Spacer(Modifier.width(8.dp))
        Text(
            "${String.format(Locale.US, "%.0f", r * 100)}٪",
            fontSize = 10.sp, fontWeight = FontWeight.Black,
            color = if (r >= 0.5) WGreen else WRed
        )
    }
}

@Composable
private fun MethodCard() {
    Surface(color = CardB, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🛡️ معیارهای اعتماد PumpDump", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WBlue)
            Text(
                "تحلیل دلخواه: فقط ۱۰۰ ارز برتر CoinGecko • نهنگ‌ها چی می‌خرن: رتبه ۱-۱۰۰۰ + DEX • شکار میم‌کوین‌ها: تمام شبکه‌های DEX",
                fontSize = 10.sp, color = WGray, lineHeight = 16.sp
            )
            Text(
                "⚠️ شفافیت: داده‌ها لحظه‌ای از GeckoTerminal و CoinGecko هستن. این اپ مشاوره مالی نیست.",
                fontSize = 10.sp, color = WGold, lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LeaderCard(l: WhalePick, index: Int, leaderTf: String, bFlows: Map<String, Pair<Double, Double>>) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val r1 = ratio(l.buysH1, l.sellsH1)
    val checks = trustChecks(l)
    val passed = checks.count { it.second }

    val (buyV, sellV, tfName) = when (leaderTf) {
        "1h" -> Triple(l.volH1 * r1, l.volH1 * (1 - r1), tfNameOf("1h"))
        "6h" -> {
            val r6 = ratio(l.buysH6, l.sellsH6)
            Triple(l.volH6 * r6, l.volH6 * (1 - r6), tfNameOf("6h"))
        }
        "24h" -> {
            val r24 = ratio(l.buysH24, l.sellsH24)
            Triple(l.volH24 * r24, l.volH24 * (1 - r24), tfNameOf("24h"))
        }
        else -> {
            val f = bFlows[l.symbol]
            if (f != null) Triple(f.first, f.second, tfNameOf(leaderTf))
            else Triple(l.volH1 * r1, l.volH1 * (1 - r1), tfNameOf("1h"))
        }
    }
    val rSel = ratio(buyV, sellV)

    val poolUrl = l.poolUrl

    Surface(
        color = if (index % 2 == 0) CardA else CardB,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))
            context.startActivity(intent)
        }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chainEmoji(l.chain), fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(l.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("(${l.chainName})", fontSize = 10.sp, color = WGray)
                Spacer(Modifier.weight(1f))
                Text(String.format(Locale.US, "$%.6f", l.price), fontSize = 10.sp, color = WGray)
                Spacer(Modifier.width(6.dp))
                Text(
                    String.format(Locale.US, "%+.1f%%", l.changeH1),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (l.changeH1 >= 0) WGreen else WRed
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🐳 $tfName: خرید ${compact(buyV)} / فروش ${compact(sellV)}",
                    fontSize = 10.sp, color = WGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲ بستن" else "▼ جزئیات", fontSize = 10.sp)
                }
            }

            Text(verdictText(rSel), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = verdictColor(rSel))
            Text("🛡️ بررسی اعتماد: $passed از ${checks.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WBlue)
            Text(marketPosText(l.rank, l.marketCap), fontSize = 10.sp, color = WGray)

            if (expanded) {
                FlowLine("۱ ساعته", l.buysH1, l.sellsH1, l.volH1)
                FlowLine("۶ ساعته", l.buysH6, l.sellsH6, l.volH6)
                FlowLine("روزانه", l.buysH24, l.sellsH24, l.volH24)
                if (leaderTf !in listOf("1h", "6h", "24h") && bFlows[l.symbol] != null) {
                    val f = bFlows[l.symbol]!!
                    FlowLine(tfNameOf(leaderTf), f.first, f.second, f.first + f.second)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("💧 موجودی: ${compact(l.liquidity)}", fontSize = 10.sp, color = WBlue)
                    Text("FDV: ${compact(l.fdv)}", fontSize = 10.sp, color = WGray)
                    Text("سن: ${ageText(l.ageHours)}", fontSize = 10.sp, color = WGray)
                }
                TrustRows(checks)
            }
        }
    }
}

@Composable
private fun FreshCard(f: WhalePick, index: Int) {
    val context = LocalContext.current
    val r1 = ratio(f.buysH1, f.sellsH1)
    val checks = trustChecks(f)
    val passed = checks.count { it.second }
    val poolUrl = f.poolUrl

    Surface(
        color = if (index % 2 == 0) CardB else CardA,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))
            context.startActivity(intent)
        }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chainEmoji(f.chain), fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(f.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("(${f.chainName})", fontSize = 10.sp, color = WGray)
                Spacer(Modifier.weight(1f))
                Text("🛡️ $passed/${checks.size}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (passed >= 6) WGreen else WGold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("قیمت: ${String.format(Locale.US, "$%.8f", f.price)}", fontSize = 10.sp, color = WGray)
                Text("💧 ${compact(f.liquidity)}", fontSize = 10.sp, color = WBlue)
                Text(String.format(Locale.US, "%+.1f%%", f.changeH1), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (f.changeH1 >= 0) WGreen else WRed)
            }
            Text("🐳 فشار خرید: ${String.format(Locale.US, "%.0f", r1 * 100)}٪", fontSize = 10.sp, color = WGreen, fontWeight = FontWeight.Bold)
            Text(verdictText(r1), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = verdictColor(r1))
            Text(marketPosText(f.rank, f.marketCap), fontSize = 10.sp, color = WGray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (f.liquidity >= 100_000) Text("✅ نقدینگی قوی", fontSize = 9.sp, color = WGreen)
                if (f.buysH1 > 0 && f.sellsH1 > 0) Text("✅ دوطرفه", fontSize = 9.sp, color = WGreen)
            }
        }
    }
}

@Composable
fun WhaleRadarScreen() {
    val scope = rememberCoroutineScope()

    var searchInput by remember { mutableStateOf("") }
    var analysisSymbol by remember { mutableStateOf("BTC") }
    var window by remember { mutableStateOf("1d") }
    var analysis by remember { mutableStateOf<AnalysisData?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    var leaders by remember { mutableStateOf<List<WhalePick>>(emptyList()) }
    var fresh by remember { mutableStateOf<List<WhalePick>>(emptyList()) }
    var memeTrends by remember { mutableStateOf<List<WhalePick>>(emptyList()) }
    var threshold by remember { mutableStateOf(100_000.0) }
    var leaderTf by remember { mutableStateOf("1h") }
    var bFlows by remember { mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap()) }
    var loadingList by remember { mutableStateOf(true) }
    var lastUpdate by remember { mutableStateOf("") }

    fun analyze(symbol: String, tf: String) {
        scope.launch {
            analyzing = true
            analysisError = null
            try {
                val coins = ApiClient.getTop1000Coins()
                val coin = coins.firstOrNull {
                    it.symbol.equals(symbol, true) && it.market_cap_rank != null && it.market_cap_rank <= 100
                } ?: throw Exception("not in top 100")

                val days: Int; val chunk: Int; val take: Int; val th: Double
                when (tf) {
                    "1h" -> { days = 1; chunk = 1; take = 70; th = 0.0015 }
                    "4h" -> { days = 1; chunk = 3; take = 70; th = 0.003 }
                    "12h" -> { days = 1; chunk = 6; take = 70; th = 0.005 }
                    "1d" -> { days = 2; chunk = 2; take = 24; th = 0.008 }
                    "3d" -> { days = 6; chunk = 8; take = 18; th = 0.015 }
                    else -> { days = 89; chunk = 24; take = 7; th = 0.03 }
                }

                val chart = ApiClient.getCoinChart(coin.id, days = days)
                val prices = chart.prices.map { it[1] }
                val chunked = prices.chunked(chunk).filter { it.size == chunk }
                val candles = mutableListOf<ChartCandle>()
                val buyZones = mutableListOf<Double>()
                for (c in chunked) {
                    val o = c.first()
                    val cl = c.last()
                    val hh = c.max()
                    val ll = c.min()
                    val body = if (o > 0) (cl - o) / o else 0.0
                    val marker = if (body > th) 1 else if (body < -th) -1 else 0
                    if (marker == 1) buyZones.add(cl)
                    candles.add(ChartCandle(o, hh, ll, cl, marker))
                }
                val shown = candles.takeLast(take)
                val zone = if (buyZones.isNotEmpty()) buyZones.average() else null
                val first = prices.first()
                val last = prices.last()
                val chg = if (first > 0) (last - first) / first * 100 else 0.0

                var poolName: String? = null
                val flows = mutableListOf<FlowRow>()
                try {
                    val pool = GeckoTerminal.api.searchPools(coin.symbol).data?.firstOrNull { it.attributes != null }
                    if (pool != null) {
                        poolName = pool.attributes?.name
                        val a = pool.attributes!!
                        fun split(vol: Double?, b: Double?, s: Double?): Pair<Double, Double> {
                            val v = vol ?: 0.0; val bb = b ?: 0.0; val ss = s ?: 0.0; val t = bb + ss
                            if (t <= 0) return Pair(v / 2, v / 2)
                            return Pair(v * bb / t, v * ss / t)
                        }
                        val f1 = split(a.volume?.h1, a.transactions?.h1?.buys, a.transactions?.h1?.sells)
                        flows.add(FlowRow("۱ ساعته", f1.first, f1.second))
                        val f6 = split(a.volume?.h6, a.transactions?.h6?.buys, a.transactions?.h6?.sells)
                        flows.add(FlowRow(" ساعته", f6.first, f6.second))
                        val f24 = split(a.volume?.h24, a.transactions?.h24?.buys, a.transactions?.h24?.sells)
                        flows.add(FlowRow("۲۴ ساعته", f24.first, f24.second))
                    }
                } catch (_: Exception) { }

                analysisSymbol = coin.symbol.uppercase(Locale.US)
                analysis = AnalysisData(shown, zone, flows, chg, poolName)
            } catch (e: Exception) {
                analysis = null
                analysisError = "ارز در ۰۰ ارز برتر CoinGecko پیدا نشد 🤔 (فقط ۱۰ تای برتر مجاز است)"
            }
            analyzing = false
        }
    }

    LaunchedEffect(leaderTf, leaders) {
        if (leaderTf in listOf("1h", "6h", "24h")) return@LaunchedEffect
        val (interval, limit) = when (leaderTf) {
            "4h" -> "5m" to 48
            "12h" -> "15m" to 48
            "3d" -> "1h" to 72
            else -> "4h" to 42
        }
        val map = mutableMapOf<String, Pair<Double, Double>>()
        try {
            coroutineScope {
                leaders.take(12).map { l ->
                    async(Dispatchers.IO) {
                        try {
                            val kl = BinanceClient.api.klines(l.symbol + "USDT", interval, limit)
                            var bq = 0.0; var sq = 0.0
                            for (k in kl) {
                                val qv = k[7].asDouble; val tb = k[10].asDouble
                                bq += tb; sq += (qv - tb)
                            }
                            l.symbol to (bq to sq)
                        } catch (_: Exception) { null }
                    }
                }.awaitAll().filterNotNull().forEach { (s, p) -> map[s] = p }
            }
        } catch (_: Exception) { }
        bFlows = map
    }

    fun fetchLists() {
        scope.launch {
            loadingList = true
            try {
                val allChains = ALL_CHAINS.map { it.first }
                
                val trendDeferred = async(Dispatchers.IO) {
                    allChains.map { chain ->
                        try { GeckoTerminal.api.trendingPools(chain).data ?: emptyList() } 
                        catch (_: Exception) { emptyList<GeckoPool>() }
                    }.flatten()
                }
                
                val newsDeferred = async(Dispatchers.IO) {
                    allChains.map { chain ->
                        try { GeckoTerminal.api.newPools(chain).data ?: emptyList() } 
                        catch (_: Exception) { emptyList<GeckoPool>() }
                    }.flatten()
                }
                
                val marketsDeferred = async(Dispatchers.IO) {
                    try { ApiClient.getTop1000Coins() } 
                    catch (_: Exception) { emptyList<CoinMarket>() }
                }
                
                val allTrendingDeferred = async(Dispatchers.IO) {
                    allChains.map { chain ->
                        try { GeckoTerminal.api.trendingPools(chain).data ?: emptyList() } 
                        catch (_: Exception) { emptyList<GeckoPool>() }
                    }.flatten()
                }
                
                val trend = trendDeferred.await()
                val news = newsDeferred.await()
                val markets = marketsDeferred.await()
                val allTrending = allTrendingDeferred.await()

                fun marketOf(sym: String): CoinMarket? = markets.firstOrNull { it.symbol.equals(sym, true) }

                leaders = trend
                    .mapNotNull { poolStats(it) }
                    .map {
                        val mk = marketOf(it.symbol)
                        it.copy(rank = mk?.market_cap_rank, marketCap = mk?.market_cap)
                    }
                    .filter {
                        (it.rank == null || it.rank <= 1000) &&
                        it.volH1 >= threshold && it.buysH1 > it.sellsH1 && it.sellsH1 > 0
                    }
                    .sortedByDescending { it.volH1 }
                    .take(15)

                memeTrends = allTrending
                    .mapNotNull { poolStats(it) }
                    .filter { p ->
                        p.liquidity >= 50_000 &&
                        p.fdv in 100_000.0..50_000_000.0 &&
                        p.ageHours >= 1 &&
                        p.sellsH1 > 0 &&
                        p.buysH1 > p.sellsH1 &&
                        ratio(p.buysH1, p.sellsH1) >= 0.55 &&
                        p.credScore >= 45
                    }
                    .map {
                        val mk = marketOf(it.symbol)
                        it.copy(rank = mk?.market_cap_rank, marketCap = mk?.market_cap)
                    }
                    .sortedByDescending { it.volH1 * ratio(it.buysH1, it.sellsH1) }
                    .take(20)

                fresh = news
                    .mapNotNull { poolStats(it) }
                    .filter { p ->
                        p.liquidity >= 100_000 &&
                        p.fdv in 100_000.0..20_000_000.0 &&
                        p.ageHours >= 6 &&
                        p.sellsH1 > 0 &&
                        p.buysH1 > p.sellsH1 &&
                        p.credScore >= 55
                    }
                    .map {
                        val mk = marketOf(it.symbol)
                        it.copy(rank = mk?.market_cap_rank, marketCap = mk?.market_cap)
                    }
                    .sortedByDescending { it.credScore * 1_000_000 + it.volH1 }
                    .take(10)

                lastUpdate = "بروزرسانی: " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            } catch (_: Exception) { }
            loadingList = false
        }
    }

    LaunchedEffect(Unit) {
        analyze("BTC", "1d")
        fetchLists()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            fetchLists()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🐳 رادار نهنگ‌ها (تمام شبکه‌ها)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { fetchLists() }, enabled = !loadingList) {
                Text(if (loadingList) "در حال اسکن ${ALL_CHAINS.size} شبکه..." else "اسکن 🔄")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { MethodCard() }

            // ================= ۱) تحلیل ارز دلخواه (رتبه ۱-۱۰) =================
            item {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍 تحلیل نهنگی ارز دلخواه (فقط ۱۰۰ ارز برتر CoinGecko)", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = searchInput,
                                onValueChange = { searchInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("نماد ارز... مثلاً SOL", fontSize = 12.sp, color = WGray) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = { val s = searchInput.trim(); if (s.isNotEmpty()) analyze(s, window) },
                                colors = ButtonDefaults.buttonColors(containerColor = WGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("تحلیل", fontSize = 12.sp) }
                        }

                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("BTC", "ETH", "SOL", "XRP", "DOGE", "PEPE", "SHIB", "TON").forEach { s ->
                                FilterChip(selected = analysisSymbol == s, onClick = { analyze(s, window) }, label = { Text(s, fontSize = 10.sp) })
                            }
                        }

                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1h" to "۱ ساعته", "4h" to "۴ ساعته", "12h" to "۱۲ ساعته", "1d" to "روزانه", "3d" to "۳ روزه", "1w" to "هفتگی").forEach { (k, label) ->
                                FilterChip(
                                    selected = window == k,
                                    onClick = { window = k; analyze(analysisSymbol, k) },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }

                        when {
                            analyzing -> Text("⏳ در حال تحلیل...", color = WGray, fontSize = 12.sp)
                            analysisError != null -> Text(analysisError ?: "", color = WRed, fontSize = 12.sp)
                            analysis != null -> {
                                val an = analysis!!
                                Text("📈 نمودار کندلی $analysisSymbol", fontSize = 10.sp, color = WGray)
                                WhaleFlowChart(an.candles, an.zone)
                                if (an.zone != null) {
                                    Text(" نهنگ‌ها حوالی ${String.format(Locale.US, "$%,.6f", an.zone)} شروع به خرید کردن", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WGold)
                                }
                                Text("تغییر بازه: ${String.format(Locale.US, "%+.2f%%", an.changePct)}", fontSize = 11.sp, color = if (an.changePct >= 0) WGreen else WRed)

                                if (an.flows.isNotEmpty()) {
                                    Text("💰 جریان پول در استخر ${an.poolName ?: ""}:", fontSize = 11.sp, color = WGray)
                                    an.flows.forEach { f ->
                                        val tot = f.buy + f.sell
                                        val r = if (tot > 0) f.buy / tot else 0.5
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text(f.label, fontSize = 11.sp, color = WGray, modifier = Modifier.width(64.dp))
                                            Text("🟢 ${compact(f.buy)}", fontSize = 11.sp, color = WGreen, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text(" ${compact(f.sell)}", fontSize = 11.sp, color = WRed, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(8.dp))
                                            Text("${String.format(Locale.US, "%.0f", r * 100)}٪", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (r >= 0.5) WGreen else WRed)
                                        }
                                    }
                                    val f24 = an.flows.last()
                                    Text(
                                        when {
                                            f24.buy > f24.sell * 1.5 -> "💡 نهنگ‌ها دارن این ارز رو جمع می‌کنن — پتانسیل پامپ "
                                            f24.sell > f24.buy * 1.5 -> "💡 نهنگ‌ها دارن خالی می‌کنن — احتیاط 🩸"
                                            else -> " تعادل خرید/فروش — منتظر شکست بمون ⚖️"
                                        },
                                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        color = if (f24.buy > f24.sell * 1.5) WGreen else if (f24.sell > f24.buy * 1.5) WRed else WGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= ۲) مهمترین نهنگ‌ها (رتبه ۱ تا ۱۰۰۰ + DEX) =================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("👑 مهمترین نهنگ‌ها (رتبه ۱ تا ۱۰۰۰ CoinGecko + DEX‌ها) — الان دارن چی می‌خرن؟", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = threshold == 50_000.0, onClick = { threshold = 50_000.0 }, label = { Text("۵۰ هزار", fontSize = 10.sp) })
                        FilterChip(selected = threshold == 100_000.0, onClick = { threshold = 100_000.0 }, label = { Text("۱۰۰ هزار", fontSize = 10.sp) })
                        FilterChip(selected = threshold == 500_000.0, onClick = { threshold = 500_000.0 }, label = { Text("۵۰۰ هزار", fontSize = 10.sp) })
                        FilterChip(selected = threshold == 1_000_000.0, onClick = { threshold = 1_000_000.0 }, label = { Text("۱ میلیون", fontSize = 10.sp) })
                    }

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1h" to "۱ ساعته", "4h" to "۴ ساعته", "6h" to "۶ ساعته", "12h" to "۱۲ ساعته", "24h" to "روزانه", "3d" to "۳ روزه", "1w" to "هفتگی").forEach { (k, label) ->
                            FilterChip(selected = leaderTf == k, onClick = { leaderTf = k }, label = { Text("⏱ $label", fontSize = 10.sp) })
                        }
                    }
                    Text("ضربه روی کارت = نمودار کامل در GeckoTerminal 📊 • $lastUpdate", fontSize = 9.sp, color = WGray)
                }
            }

            if (loadingList && leaders.isEmpty()) {
                item { Text(" در حال دریافت...", fontSize = 11.sp, color = WGray) }
            } else if (leaders.isEmpty()) {
                item { Text(" فعلاً خرید نهنگی سنگینی در ۰۰۰ ارز برتر + DEX‌ها ثبت نشده", fontSize = 11.sp, color = WGray) }
            } else {
                itemsIndexed(leaders) { i, l -> LeaderCard(l, i, leaderTf, bFlows) }
            }

            // ================= ) شکار میم‌کوین‌های ترند DEX =================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🚀 شکار میم‌کوین‌های ترند DEX (تمام شبکه‌ها — بدون محدودیت رتبه)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Solana • BSC • Base • Ethereum • TON + ${ALL_CHAINS.size - 5} شبکه دیگر", fontSize = 9.sp, color = WBlue)
                    Text("فیلترهای امنیتی: نقدینگی ≥ ۵۰K • فشار خرید ≥ ۵۵٪ • سن ≥ ۱ ساعت • FDV سالم", fontSize = 9.sp, color = WGray)
                }
            }

            if (loadingList && memeTrends.isEmpty()) {
                item { Text(" در حال اسکن تمام شبکه‌ها...", fontSize = 11.sp, color = WGray) }
            } else if (memeTrends.isEmpty()) {
                item { Text("😴 فعلاً میم‌کوین ترند مورد تأییدی پیدا نشد — بعداً سر بزن", fontSize = 11.sp, color = WGray) }
            } else {
                itemsIndexed(memeTrends) { i, m ->
                    val poolUrl = m.poolUrl
                    Surface(
                        color = if (i % 2 == 0) CardA else CardB,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))
                            LocalContext.current.startActivity(intent)
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chainEmoji(m.chain), fontSize = 18.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(m.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                Text("(${m.chainName})", fontSize = 10.sp, color = WGray)
                                Spacer(Modifier.weight(1f))
                                Text("🔥 ترند #${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WGold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("قیمت: ${String.format(Locale.US, "$%.8f", m.price)}", fontSize = 10.sp, color = WGray)
                                Text("💧 ${compact(m.liquidity)}", fontSize = 10.sp, color = WBlue)
                                Text(String.format(Locale.US, "%+.1f%%", m.changeH1), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (m.changeH1 >= 0) WGreen else WRed)
                            }
                            Text("🐳 حجم س: ${compact(m.volH1)} • فشار خرید: ${String.format(Locale.US, "%.0f", ratio(m.buysH1, m.sellsH1) * 100)}٪", fontSize = 10.sp, color = WGreen, fontWeight = FontWeight.Bold)
                            Text(verdictText(ratio(m.buysH1, m.sellsH1)), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = verdictColor(ratio(m.buysH1, m.sellsH1)))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("سن: ${ageText(m.ageHours)}", fontSize = 9.sp, color = WGray)
                                Text("FDV: ${compact(m.fdv)}", fontSize = 9.sp, color = WGray)
                                Text("امتیاز: ${m.credScore}/100", fontSize = 9.sp, color = WBlue)
                            }
                        }
                    }
                }
                item {
                    Text("⚠️ میم‌کوین‌ها = ریسک بسیار بالا! فقط با پولی که توان از دست دادنش رو داری وارد شو.", fontSize = 10.sp, color = WGold)
                }
            }

            // ================= ۴) تازه‌واردها =================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🌱 تازه‌واردهای تأییدشده (فیلترهای سخت‌گیرانه)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("فقط ارزهایی با حداقل  از ۷ بررسی اعتماد 🛡️", fontSize = 9.sp, color = WGray)
                }
            }

            if (loadingList && fresh.isEmpty()) {
                item { Text(" در حال دریافت...", fontSize = 11.sp, color = WGray) }
            } else if (fresh.isEmpty()) {
                item { Text("😴 فعلاً تازه‌وارد مورد تأییدی پیدا نشد — بعداً سر بزن", fontSize = 11.sp, color = WGray) }
            } else {
                itemsIndexed(fresh) { i, f -> FreshCard(f, i) }
                item {
                    Text("⚠️ امتیاز اعتماد فقط معیارهای آن‌چین رو می‌سنجه — میم‌کوین = ریسک بالا!", fontSize = 10.sp, color = WGold)
                }
            }
        }
    }
}
