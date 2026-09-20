package com.pumpwatch.app.ui

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
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private val FuturesAccent = Color(0xFFFF5252)
private val FuturesGreen = Color(0xFF00E676)
private val FuturesGray = Color(0xFF8B949E)
private val FuturesBlue = Color(0xFF40C4FF)
private val FuturesGold = Color(0xFFFFC107)
private val FuturesCard = Color(0xFF1A0E0E)

// 🚀 Sprint 15 (فاز ۳ / Commit 18): مدل سیگنال فیوچرز
private data class FutSignal(
    val rank: Int,
    val symbol: String,
    val base: String,
    val direction: String,     // "LONG" | "SHORT"
    val score: Int,            // 0..100
    val entry: Double,
    val stopLoss: Double,
    val target: Double,
    val change24h: Double,
    val fundingPct: Double?,
    val volumeUsd: Double,
    val reasons: List<String>
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

/**
 * 🚀 Commit 18: منطق سیگنال ساده (بدون FuturesScannerEngine)
 * - شتاب ۲۴ ساعته > 5% → LONG
 * - شتاب ۲۴ ساعته < -5% → SHORT
 * - امتیاز = |شتاب ۲۴س| × 3 + فاندینگ extreme (۲۰) + نقدشوندگی (۱۵)
 */
private fun buildSignal(
    rank: Int,
    symbol: String,
    base: String,
    price: Double,
    change24h: Double,
    fundingPct: Double?,
    volumeUsd: Double
): FutSignal? {
    val absChange = abs(change24h)
    if (absChange < 5.0) return null // آستانه: حداقل ۵٪ حرکت

    val direction = if (change24h > 0) "LONG" else "SHORT"
    val reasons = mutableListOf<String>()

    // امتیاز شتاب (حداکثر ۶۰)
    var score = (absChange * 3.0).toInt().coerceAtMost(60)
    reasons.add("شتاب ۲۴س: ${String.format(Locale.US, "%+.1f%%", change24h)}")

    // امتیاز فاندینگ (حداکثر ۲۰)
    if (fundingPct != null && abs(fundingPct) >= 0.03) {
        score += 20
        reasons.add("فاندینگ شدید: ${String.format(Locale.US, "%+.4f%%", fundingPct)}")
    }

    // امتیاز نقدشوندگی (حداکثر ۱۵)
    if (volumeUsd >= 50_000_000) {
        score += 15
        reasons.add("نقدشوندگی بالا: ${fmtVol(volumeUsd)}")
    }

    // محاسبه SL و TP (ریسک/پاداش ۲:۱)
    val slDistance = price * 0.02  // ۲٪
    val tpDistance = price * 0.04 // ۴٪
    val stopLoss = if (direction == "LONG") price - slDistance else price + slDistance
    val target = if (direction == "LONG") price + tpDistance else price - tpDistance

    return FutSignal(
        rank = rank,
        symbol = symbol,
        base = base,
        direction = direction,
        score = score.coerceAtMost(100),
        entry = price,
        stopLoss = stopLoss,
        target = target,
        change24h = change24h,
        fundingPct = fundingPct,
        volumeUsd = volumeUsd,
        reasons = reasons
    )
}

@Composable
fun FuturesDashboardScreen() {
    val scope = rememberCoroutineScope()
    var signals by remember { mutableStateOf<List<FutSignal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf("") }
    var statsText by remember { mutableStateOf("") }

    fun scan() {
        scope.launch {
            loading = true
            errorMsg = null
            signals = emptyList()
            try {
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

                val allSignals = mutableListOf<FutSignal>()
                var perpCount = 0

                for ((idx, c) in coins.withIndex()) {
                    val fsym = c.symbol.uppercase(Locale.US) + "USDT"
                    val t = tickerMap[fsym]
                    if (t == null) continue // جفت perpetual ندارد
                    perpCount++

                    val prem = premMap[fsym]
                    val price = t.lastPrice?.toDoubleOrNull() ?: c.current_price
                    val change24h = t.priceChangePercent?.toDoubleOrNull()
                        ?: (c.price_change_percentage_24h ?: 0.0)
                    val fundingPct = prem?.lastFundingRate?.toDoubleOrNull()?.times(100.0)
                    val volumeUsd = t.quoteVolume?.toDoubleOrNull() ?: c.total_volume

                    val sig = buildSignal(
                        rank = c.market_cap_rank ?: (idx + 1),
                        symbol = fsym,
                        base = c.symbol.uppercase(Locale.US),
                        price = price,
                        change24h = change24h,
                        fundingPct = fundingPct,
                        volumeUsd = volumeUsd
                    )
                    if (sig != null) allSignals.add(sig)
                }

                signals = allSignals.sortedByDescending { it.score }.take(20)
                statsText = "${signals.size} سیگنال از $perpCount ارز perp در ۱۰۰ برتر"

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
                "🎯 تابلوی سیگنال فیوچرز",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        Text(
            "🎯 سیگنال = شتاب ۲۴س ≥ ۵٪ + فاندینگ + نقدشوندگی • ورود/SL/TP با ریسک/پاداش ۲:۱\n" +
            "⚠️ این سیگنال‌های ساده هستند — سیگنال‌های پیشرفته با موتور اسکنر در تب بعدی",
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
        } else if (signals.isEmpty()) {
            Text(
                "😴 هیچ سیگنالی فعال نیست — بازار در حالت عادی است. آرامش هم یک وضعیت بازار است.",
                color = FuturesGray, modifier = Modifier.padding(24.dp), fontSize = 12.sp
            )
        } else {
            Text(
                statsText,
                fontSize = 11.sp, color = FuturesGold, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            Text(
                "بروزرسانی: $lastUpdate",
                fontSize = 9.sp, color = FuturesGray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(signals) { sig -> SignalCard(sig) }
            }
        }
    }
}

@Composable
private fun SignalCard(sig: FutSignal) {
    val dirColor = if (sig.direction == "LONG") FuturesGreen else FuturesAccent
    val dirEmoji = if (sig.direction == "LONG") "🚀" else "🩸"

    Surface(color = FuturesCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Row 1: نماد + جهت + امتیاز
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dirEmoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(sig.base, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                Text(" #${sig.rank}", fontSize = 10.sp, color = FuturesGray)
                Spacer(Modifier.weight(1f))
                Text(
                    "${sig.score}/100",
                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                    color = if (sig.score >= 70) FuturesGreen else FuturesGold
                )
            }

            // Row 2: جهت + شتاب
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = dirColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${sig.direction} ${sig.direction}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dirColor
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "شتاب ۲۴س: ${String.format(Locale.US, "%+.1f%%", sig.change24h)}",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dirColor
                )
                Spacer(Modifier.weight(1f))
                if (sig.fundingPct != null) {
                    Text(
                        "فاندینگ: ${String.format(Locale.US, "%+.4f%%", sig.fundingPct)}",
                        fontSize = 9.sp, color = FuturesGray
                    )
                }
            }

            // Row 3: ورود / SL / TP
            Surface(color = FuturesBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ورود", fontSize = 9.sp, color = FuturesGray)
                        Text(fmtPrice(sig.entry), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("استاپ", fontSize = 9.sp, color = FuturesGray)
                        Text(fmtPrice(sig.stopLoss), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FuturesAccent)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("تارگت", fontSize = 9.sp, color = FuturesGray)
                        Text(fmtPrice(sig.target), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FuturesGreen)
                    }
                }
            }

            // Row 4: دلایل
            Text(sig.reasons.joinToString(" • "), fontSize = 9.sp, color = FuturesGray, lineHeight = 14.sp)

            // Row 5: حجم
            Text("💧 حجم ۲۴س: ${fmtVol(sig.volumeUsd)}", fontSize = 9.sp, color = FuturesGray)
        }
    }
}
