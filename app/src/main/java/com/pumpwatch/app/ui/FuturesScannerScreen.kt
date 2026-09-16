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
import com.pumpwatch.app.data.FuturesCandle
import com.pumpwatch.app.engine.FuturesScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private val SGreen = Color(0xFF00E676)
private val SRed = Color(0xFFFF5252)
private val SGold = Color(0xFFFFC107)
private val SGray = Color(0xFF8B949E)
private val SBlue = Color(0xFF40C4FF)
private val SCardA = Color(0xFF1A0E0E)
private val SCardB = Color(0xFF140B0B)

private fun fmtP(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}

@Composable
fun FuturesScannerScreen() {
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<FuturesScannerEngine.ScanResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    suspend fun candles(symbol: String, interval: String, limit: Int): List<FuturesCandle> =
        try {
            BinanceFutures.api.klines(symbol, interval, limit)
                .mapNotNull { BinanceFutures.parseCandle(it) }
        } catch (_: Exception) { emptyList() }

    fun scan() {
        scope.launch {
            loading = true
            errorMsg = null
            results = emptyList()
            try {
                progress = "دریافت ۱۰۰ ارز برتر + تیکرها..."
                val coins = ApiClient.getTop100Coins()
                val (tMap, pMap) = withContext(Dispatchers.IO) {
                    val t = try { BinanceFutures.api.ticker24h().associateBy { it.symbol } }
                    catch (_: Exception) { emptyMap() }
                    val p = try { BinanceFutures.api.premiumIndexAll().associateBy { it.symbol } }
                    catch (_: Exception) { emptyMap() }
                    t to p
                }

                // کاندیدها: perp دارد + (|تغییر| >= 2% یا حجم >= 50M) -> top 10 با حجم
                val candidates = coins.mapNotNull { c ->
                    val fsym = c.symbol.uppercase(Locale.US) + "USDT"
                    val t = tMap[fsym] ?: return@mapNotNull null
                    val chg = t.priceChangePercent?.toDoubleOrNull() ?: 0.0
                    val vol = t.quoteVolume?.toDoubleOrNull() ?: 0.0
                    if (abs(chg) < 2.0 && vol < 50_000_000) return@mapNotNull null
                    Triple(fsym, c.symbol.uppercase(Locale.US), vol)
                }.sortedByDescending { it.third }.take(10)

                if (candidates.isEmpty()) {
                    progress = ""
                    loading = false
                    return@launch
                }

                // ---------- فاز A: regime + setup ----------
                val phaseA = mutableListOf<FuturesScannerEngine.ScanResult>()
                for ((i, cand) in candidates.withIndex()) {
                    progress = "فاز A — اسکن ${i + 1}/${candidates.size}: ${cand.second}"
                    val c4 = withContext(Dispatchers.IO) { candles(cand.first, "4h", 200) }
                    val c1 = withContext(Dispatchers.IO) { candles(cand.first, "1h", 200) }
                    val c15 = withContext(Dispatchers.IO) { candles(cand.first, "15m", 100) }
                    val r = FuturesScannerEngine.analyze(
                        cand.first, cand.second, c4, c1, c15,
                        pMap[cand.first]?.lastFundingRate?.toDoubleOrNull()?.times(100.0)
                    )
                    if (r != null) phaseA.add(r)
                }

                // ---------- فاز B: trigger 5M فقط برای بازمانده‌ها ----------
                val final = phaseA.map { r ->
                    progress = "فاز B — تریگر ${r.base}..."
                    val c5 = withContext(Dispatchers.IO) { candles(r.symbol, "5m", 100) }
                    FuturesScannerEngine.applyTrigger(r, c5)
                }

                results = final.sortedByDescending { it.score }
                progress = ""
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در اسکن: ${e.message?.take(80) ?: "نامشخص"}"
                progress = ""
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
                "🔍 اسکنر فیوچرز (Regime/Setup/Trigger)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        Text(
            "کاندیدها: ۱۰۰ ارز برتر مارکت که perp دارند و حرکت/حجم کافی • حداکثر ۱۰ کاندید\n" +
            "Regime 4H+1H • Setup 15M (پولبک/شکست) • Trigger 5M • استاپ = ۲×ATR • تارگت = ۳×ATR\n" +
            "⚠️ بدون Setup = بدون سیگنال. Trigger نیامده = «در انتظار»، نه «فعال»",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = SGray, lineHeight = 15.sp
        )

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = SRed)
                if (progress.isNotEmpty()) {
                    Text(progress, fontSize = 11.sp, color = SGray, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else if (errorMsg != null) {
            Text(errorMsg ?: "", color = SRed, modifier = Modifier.padding(24.dp))
        } else if (results.isEmpty()) {
            Text(
                "😴 هیچ کاندیدی Setup معتبر ندارد — بازار در انتظار است. این خودش یک سیگنال است: نزدن هم یک پوزیشن است.",
                color = SGray, modifier = Modifier.padding(24.dp), fontSize = 12.sp
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(results) { r -> ScannerCard(r) }
                item {
                    Text(
                        "⚠️ فیوچرز = اهرم + ریسک لیکوئیدیشن. این خروجی تحلیل است نه توصیه. فقط با سرمایه‌ای که توان از دست دادنش را داری.",
                        fontSize = 10.sp, color = SGold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerCard(r: FuturesScannerEngine.ScanResult) {
    val isLong = r.direction == "LONG"
    val dirColor = if (isLong) SGreen else SRed

    Surface(color = SCardA, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.base, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Surface(color = dirColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        if (isLong) "🚀 LONG" else "🩸 SHORT",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.Black, color = dirColor
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${r.score}/100",
                    fontSize = 14.sp, fontWeight = FontWeight.Black,
                    color = if (r.score >= 70) SGreen else SGold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Regime 4H: ${if (r.regime4h == FuturesScannerEngine.Regime.BULL) "🟢 صعودی" else "🔴 نزولی"}",
                    fontSize = 10.sp, color = SGray
                )
                Text(
                    "Regime 1H: ${if (r.regime1h == r.regime4h) "✅ هم‌راستا" else "⚠️ مخالف"}",
                    fontSize = 10.sp, color = SGray
                )
                Text(
                    "Trigger: ${if (r.trigger5m) "✅ فعال" else "⏳ انتظار"}",
                    fontSize = 10.sp, color = if (r.trigger5m) SGreen else SGray
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ورود: ${fmtP(r.entry)}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text("استاپ: ${fmtP(r.stop)}", fontSize = 11.sp, color = SRed, fontWeight = FontWeight.Bold)
                Text("تارگت: ${fmtP(r.target)}", fontSize = 11.sp, color = SGreen, fontWeight = FontWeight.Bold)
            }

            if (r.fundingPct != null) {
                Text(
                    "فاندینگ فعلی: ${String.format(Locale.US, "%+.4f%%", r.fundingPct)}" +
                        if (isLong && r.fundingPct >= 0.05) " ⚠️ هزینهٔ نگهداری لانگ بالا"
                        else if (!isLong && r.fundingPct <= -0.05) " ⚠️ هزینهٔ نگهداری شورت بالا"
                        else "",
                    fontSize = 9.sp, color = SBlue
                )
            }

            r.reasons.forEach { reason ->
                Text("• $reason", fontSize = 10.sp, color = SGray)
            }
        }
    }
}
