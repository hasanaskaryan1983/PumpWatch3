package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.BinanceFutures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val FuturesAccent = Color(0xFFFF5252)
private val FuturesGreen = Color(0xFF00E676)
private val FuturesGray = Color(0xFF8B949E)
private val FuturesBlue = Color(0xFF40C4FF)
private val FuturesCardA = Color(0xFF1A0E0E)
private val FuturesCardB = Color(0xFF140B0B)

// 🚀 Sprint 12 (F2): ردیف داشبورد — پایه = ۱۰۰ ارز برتر مارکت‌کپ
private data class PerpRow(
    val rank: Int,
    val symbol: String,        // "BTCUSDT"
    val base: String,          // "BTC"
    val lastPrice: Double,
    val changePct: Double,
    val volumeUsd: Double,
    val fundingPct: Double?,   // null = جفت perpetual ندارد
    val hasPerp: Boolean,
    val longPct: Double?       // فقط برای top 5
)

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}

private fun fmtVol(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

@Composable
fun FuturesDashboardScreen() {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PerpRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf("") }
    var perpCount by remember { mutableStateOf(0) }

    fun scan() {
        scope.launch {
            loading = true
            errorMsg = null
            try {
                // 🚀 Sprint 12 (F2): پایه = ۱۰۰ ارز برتر مارکت‌کپ CoinGecko
                val coins = ApiClient.getTop100Coins()

                val (tickerMap, premMap) = withContext(Dispatchers.IO) {
                    val t = try {
                        BinanceFutures.api.ticker24h().associateBy { it.symbol }
                    } catch (_: Exception) { emptyMap() }
                    val p = try {
                        BinanceFutures.api.premiumIndexAll().associateBy { it.symbol }
                    } catch (_: Exception) { emptyMap() }
                    t to p
                }

                val built = coins.mapIndexed { idx, c ->
                    val fsym = c.symbol.uppercase(Locale.US) + "USDT"
                    val t = tickerMap[fsym]
                    val prem = premMap[fsym]
                    PerpRow(
                        rank = c.market_cap_rank ?: (idx + 1),
                        symbol = fsym,
                        base = c.symbol.uppercase(Locale.US),
                        // اول Binance؛ اگر جفت نبود fallback به CoinGecko
                        lastPrice = t?.lastPrice?.toDoubleOrNull() ?: c.current_price,
                        changePct = t?.priceChangePercent?.toDoubleOrNull()
                            ?: (c.price_change_percentage_24h ?: 0.0),
                        volumeUsd = t?.quoteVolume?.toDoubleOrNull() ?: c.total_volume,
                        fundingPct = prem?.lastFundingRate?.toDoubleOrNull()?.times(100.0),
                        hasPerp = t != null,
                        longPct = null
                    )
                }

                perpCount = built.count { it.hasPerp }

                // Long/Short فقط برای top 5 دارای perp (محدودیت API — صادقانه در هدر)
                val top5 = built.filter { it.hasPerp }.take(5)
                val lsUpdates = coroutineScope {
                    top5.map { r ->
                        async(Dispatchers.IO) {
                            try {
                                val list = BinanceFutures.api.lsRatio(r.symbol, "1h", 1)
                                r.symbol to (list.firstOrNull()?.longAccount?.toDoubleOrNull()?.times(100.0))
                            } catch (_: Exception) {
                                r.symbol to null
                            }
                        }
                    }.awaitAll()
                }
                val lsMap = lsUpdates.toMap()
                rows = built.map { r ->
                    if (lsMap.containsKey(r.symbol)) r.copy(longPct = lsMap[r.symbol]) else r
                }

                val now = System.currentTimeMillis()
                lastUpdate = String.format(
                    Locale.US, "%02d:%02d:%02d",
                    (now / 3600000) % 24, (now / 60000) % 60, (now / 1000) % 60
                )
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در دریافت داده: ${e.message?.take(80) ?: "نامشخص"}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🎛️ داشبورد فیوچرز (۱۰۰ ارز برتر مارکت)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        Text(
            "پایه: ۱۰۰ ارز برتر مارکت‌کپ CoinGecko • دادهٔ perp از Binance USDⓈ-M\n" +
            "⏱ فاندینگ هر ۸ ساعت تسویه می‌شود • Long/Short فقط ۵ برتر (محدودیت API)\n" +
            "🟢 فاندینگ منفی = شورت‌ها هزینه می‌دهند • 🔴 مثبت = لانگ‌ها هزینه می‌دهند • «spot» = جفت perpetual ندارد",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = FuturesGray, lineHeight = 15.sp
        )

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator(color = FuturesAccent) }
        } else if (errorMsg != null) {
            Text(errorMsg ?: "", color = FuturesAccent, modifier = Modifier.padding(24.dp))
        } else if (rows.isEmpty()) {
            Text("😴 داده‌ای نرسید — بعداً سر بزن", color = FuturesGray, modifier = Modifier.padding(24.dp))
        } else {
            // ---------- خلاصهٔ بازار ----------
            val posF = rows.count { (it.fundingPct ?: 0.0) >= 0.01 }
            val negF = rows.count { (it.fundingPct ?: 0.0) <= -0.01 }
            val avgChg = rows.map { it.changePct }.average()

            Surface(
                color = FuturesCardA,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("📊 خلاصه", fontSize = 9.sp, color = FuturesGray)
                        Text("${rows.size} ارز", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("$perpCount با perp", fontSize = 8.sp, color = FuturesBlue)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🟢 فاندینگ منفی", fontSize = 8.sp, color = FuturesGray)
                        Text("$negF", fontSize = 12.sp, fontWeight = FontWeight.Black, color = FuturesGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔴 فاندینگ مثبت", fontSize = 8.sp, color = FuturesGray)
                        Text("$posF", fontSize = 12.sp, fontWeight = FontWeight.Black, color = FuturesAccent)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("میانگین ۲۴س", fontSize = 8.sp, color = FuturesGray)
                        Text(
                            String.format(Locale.US, "%+.2f%%", avgChg),
                            fontSize = 12.sp, fontWeight = FontWeight.Black,
                            color = if (avgChg >= 0) FuturesGreen else FuturesAccent
                        )
                    }
                }
            }

            Text(
                "بروزرسانی: $lastUpdate",
                fontSize = 9.sp, color = FuturesGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // ---------- هدر جدول ----------
            Surface(color = FuturesCardB, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", fontSize = 9.sp, color = FuturesGray, modifier = Modifier.width(26.dp))
                    Text("نماد", fontSize = 9.sp, color = FuturesGray, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                    Text("قیمت", fontSize = 9.sp, color = FuturesGray, modifier = Modifier.weight(1f))
                    Text("۲۴س", fontSize = 9.sp, color = FuturesGray, modifier = Modifier.width(56.dp))
                    Text("فاندینگ", fontSize = 9.sp, color = FuturesGray, modifier = Modifier.width(64.dp))
                    Text("حجم", fontSize = 9.sp, color = FuturesGray, modifier = Modifier.width(56.dp))
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(rows) { idx, r -> PerpRowCard(r, idx % 2 == 0) }
            }
        }
    }
}

@Composable
private fun PerpRowCard(r: PerpRow, isA: Boolean) {
    val fundingColor = when {
        r.fundingPct == null -> FuturesGray
        r.fundingPct >= 0.01 -> FuturesAccent
        r.fundingPct <= -0.01 -> FuturesGreen
        else -> FuturesGray
    }

    Surface(
        color = if (isA) FuturesCardA else FuturesCardB,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${r.rank}", fontSize = 10.sp, color = FuturesGray, modifier = Modifier.width(26.dp))
            Column(modifier = Modifier.width(64.dp)) {
                Text(r.base, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    if (r.hasPerp) "PERP" else "spot",
                    fontSize = 8.sp,
                    color = if (r.hasPerp) FuturesBlue else FuturesGray
                )
            }
            Text(fmtPrice(r.lastPrice), fontSize = 10.sp, color = Color.White, modifier = Modifier.weight(1f))
            Text(
                String.format(Locale.US, "%+.2f%%", r.changePct),
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = if (r.changePct >= 0) FuturesGreen else FuturesAccent,
                modifier = Modifier.width(56.dp)
            )
            Text(
                if (r.fundingPct != null) String.format(Locale.US, "%+.4f%%", r.fundingPct) else "—",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = fundingColor,
                modifier = Modifier.width(64.dp)
            )
            Text(fmtVol(r.volumeUsd), fontSize = 10.sp, color = FuturesGray, modifier = Modifier.width(56.dp))
        }
    }
}
