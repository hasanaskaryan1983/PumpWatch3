package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.DataEnvelope
import com.pumpwatch.app.data.DataStatus
import com.pumpwatch.app.engine.FuturesScannerEngine
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@Composable
fun FuturesScannerScreen() {
    val scope = rememberCoroutineScope()
    
    // 🚀 P0 Tab 12: استفاده از DataEnvelope برای مدیریت دقیق وضعیت‌ها
    var envelope by remember { mutableStateOf<DataEnvelope<List<FuturesScannerEngine.ScanResult>>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var lastUpdate by remember { mutableStateOf("") }

    fun scan() {
        scope.launch {
            loading = true
            try {
                val coins = com.pumpwatch.app.data.ApiClient.getTop100Coins()
                val (tMap, pMap) = withContext(Dispatchers.IO) {
                    val t = try { com.pumpwatch.app.data.BinanceFutures.api.ticker24h().associateBy { it.symbol } } catch (_: Exception) { emptyMap() }
                    val p = try { com.pumpwatch.app.data.BinanceFutures.api.premiumIndexAll().associateBy { it.symbol } } catch (_: Exception) { emptyMap() }
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
                    envelope = DataEnvelope(
                        data = emptyList(),
                        source = "binance_futures",
                        observedAtMs = System.currentTimeMillis(),
                        status = DataStatus.EMPTY_VALID // 🚀 P0: بازار رنج، بدون کاندید معتبر
                    )
                    loading = false
                    return@launch
                }

                // ---------- فاز A: regime + setup ----------
                val phaseA = mutableListOf<FuturesScannerEngine.ScanResult>()
                var partialData = false
                
                for ((i, cand) in candidates.withIndex()) {
                    val c4 = withContext(Dispatchers.IO) { candles(cand.first, "4h", 200) }
                    val c1 = withContext(Dispatchers.IO) { candles(cand.first, "1h", 200) }
                    val c15 = withContext(Dispatchers.IO) { candles(cand.first, "15m", 100) }
                    
                    if (c4.size < 60 || c1.size < 60 || c15.size < 50) {
                        partialData = true // 🚀 P0: دادهٔ ناکافی برای تحلیل این ارز
                        continue
                    }
                    
                    val r = FuturesScannerEngine.analyze(
                        cand.first, cand.second, c4, c1, c15,
                        pMap[cand.first]?.lastFundingRate?.toDoubleOrNull()?.times(100.0)
                    )
                    if (r != null) phaseA.add(r)
                }

                // ---------- فاز B: trigger 5M فقط برای بازمانده‌ها ----------
                val final = phaseA.mapNotNull { r ->
                    val c5 = withContext(Dispatchers.IO) { candles(r.symbol, "5m", 100) }
                    if (c5.size < 50) return@mapNotNull null // 🚀 P0: InsufficientData برای trigger
                    FuturesScannerEngine.applyTrigger(r, c5)
                }

                val status = when {
                    final.isEmpty() && partialData -> DataStatus.PARTIAL
                    final.isEmpty() -> DataStatus.EMPTY_VALID
                    else -> DataStatus.SUCCESS
                }

                envelope = DataEnvelope(
                    data = final.sortedByDescending { it.score },
                    source = "binance_futures",
                    observedAtMs = System.currentTimeMillis(),
                    status = status
                )

                val now = System.currentTimeMillis()
                lastUpdate = String.format(Locale.US, "%02d:%02d:%02d", (now / 3600000) % 24, (now / 60000) % 60, (now / 1000) % 60)
            } catch (e: Exception) {
                envelope = DataEnvelope(
                    data = null,
                    source = "binance_futures",
                    observedAtMs = System.currentTimeMillis(),
                    status = DataStatus.FAILURE
                )
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 🚀 P0 Tab 12: صداقت محصول و انطباق
            EditorialPageHeader(
                eyebrow = "FUTURES · SCANNER",
                title = "اسکنر ستاپ‌های پژوهشی",
                subtitle = "شناسایی بر اساس Regime و Trigger. سطوح ورود/خروج مرجع هستند و وضعیت ریسک تا محاسبهٔ کارمزد/مارجین واقعی، NotComputed است.",
                accent = PumpWatchPalette.TabWhale
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scan() }, enabled = !loading) {
                    Text(if (loading) "در حال اسکن..." else "بروزرسانی 🔄")
                }
            }
        }

        when {
            loading && (envelope == null || envelope?.data?.isEmpty() == true) -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PumpWatchPalette.TabWhale)
                    Text("در حال ارزیابی Regime و Trigger...", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
            
            envelope?.status == DataStatus.FAILURE -> {
                GlassCard(accent = PumpWatchPalette.Negative, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️ خطا در دریافت داده", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                        Text("اتصال به منابع داده برقرار نشد. لطفاً اینترنت خود را بررسی کنید.", fontSize = 11.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                        Button(
                            onClick = { scan() },
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("تلاش مجدد", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            
            envelope?.status == DataStatus.EMPTY_VALID -> {
                GlassCard(accent = PumpWatchPalette.Muted, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "😴 هیچ ستاپ معتبری یافت نشد — بازار در حالت رنج یا بدون Trigger است. این خودش یک سیگنال است: نزدن هم یک پوزیشن است.",
                        color = PumpWatchPalette.Muted, modifier = Modifier.padding(16.dp), fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
            }
            
            envelope?.status == DataStatus.PARTIAL -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "⚠️ داده‌های ناقص: برخی ارزها کندل کافی برای تحلیل نداشتند (InsufficientData). نتایج زیر فقط بر اساس ارزهای دارای دادهٔ کامل است.",
                            color = PumpWatchPalette.Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp)
                        )
                    }
                    if (envelope?.data?.isEmpty() == true) {
                        Text("هیچ ستاپی با دادهٔ کامل یافت نشد.", color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                    } else {
                        ScannerList(envelope?.data ?: emptyList(), lastUpdate)
                    }
                }
            }
            
            envelope?.status == DataStatus.SUCCESS -> {
                ScannerList(envelope?.data ?: emptyList(), lastUpdate)
            }
        }
    }
}

@Composable
private fun ScannerList(results: List<FuturesScannerEngine.ScanResult>, lastUpdate: String) {
    Column {
        Text(
            "${results.size} ستاپ شناسایی‌شده",
            fontSize = 11.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Text(
            "بروزرسانی: $lastUpdate",
            fontSize = 9.sp, color = PumpWatchPalette.Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(results) { r -> ScannerCard(r) }
            item {
                GlassCard(accent = PumpWatchPalette.Muted, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "⚠️ فیوچرز = اهرم + ریسک لیکوئیدیشن. این خروجی تحلیل پژوهشی است نه توصیه. فقط با سرمایه‌ای که توان از دست دادنش را داری وارد شو.",
                        fontSize = 10.sp, color = PumpWatchPalette.Warning, modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerCard(r: FuturesScannerEngine.ScanResult) {
    val isLong = r.direction == "LONG"
    val dirColor = if (isLong) PumpWatchPalette.Positive else PumpWatchPalette.Negative

    GlassCard(accent = PumpWatchPalette.TabWhale, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.base, fontWeight = FontWeight.Black, fontSize = 16.sp, color = PumpWatchPalette.Text)
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Surface(color = dirColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
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
                    color = if (r.score >= 70) PumpWatchPalette.Positive else PumpWatchPalette.Warning
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Regime 4H: ${if (r.regime4h == FuturesScannerEngine.Regime.BULL) "🟢" else "🔴"}",
                    fontSize = 10.sp, color = PumpWatchPalette.Muted
                )
                Text(
                    "Regime 1H: ${if (r.regime1h == r.regime4h) "✅" else "⚠️"}",
                    fontSize = 10.sp, color = PumpWatchPalette.Muted
                )
                Text(
                    "Trigger: ${if (r.trigger5m) "✅" else "⏳"}",
                    fontSize = 10.sp, color = if (r.trigger5m) PumpWatchPalette.Positive else PumpWatchPalette.Muted
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ورود: ${fmtP(r.entry)}", fontSize = 11.sp, color = PumpWatchPalette.Text, fontWeight = FontWeight.Bold)
                Text("SL: ${fmtP(r.stop)}", fontSize = 11.sp, color = PumpWatchPalette.Negative, fontWeight = FontWeight.Bold)
                Text("TP: ${fmtP(r.target)}", fontSize = 11.sp, color = PumpWatchPalette.Positive, fontWeight = FontWeight.Bold)
            }

            if (r.fundingPct != null) {
                Text(
                    "فاندینگ: ${String.format(Locale.US, "%+.4f%%", r.fundingPct)}",
                    fontSize = 9.sp, color = PumpWatchPalette.Info
                )
            }

            r.reasons.forEach { reason ->
                Text("• $reason", fontSize = 10.sp, color = PumpWatchPalette.Muted)
            }
        }
    }
}

private suspend fun candles(symbol: String, interval: String, limit: Int): List<com.pumpwatch.app.data.FuturesCandle> =
    try {
        com.pumpwatch.app.data.BinanceFutures.api.klines(symbol, interval, limit)
            .mapNotNull { com.pumpwatch.app.data.BinanceFutures.parseCandle(it) }
    } catch (_: Exception) { emptyList() }

private fun fmtP(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "$%,.2f", p)
    p >= 1 -> String.format(Locale.US, "$%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "$%.5f", p)
    else -> String.format(Locale.US, "$%.6f", p)
}
