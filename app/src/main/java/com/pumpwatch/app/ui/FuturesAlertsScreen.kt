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
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.engine.FuturesScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val AGold = Color(0xFFFFC107)
private val ABlue = Color(0xFF40C4FF)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A0E0E)

// 🚀 Sprint 12 (F5): مدل هشدار فیوچرز
private data class FutAlert(
    val base: String,
    val type: String,       // FUNDING / MOVE24 / OI_SPIKE / EMA_CROSS / BREAKOUT / SQUEEZE
    val severity: Int,      // 1..3
    val title: String,
    val detail: String
)

private fun typeEmoji(type: String): String = when (type) {
    "FUNDING" -> "💸"
    "MOVE24" -> "🌪️"
    "OI_SPIKE" -> "🏦"
    "EMA_CROSS" -> "➿"
    "BREAKOUT" -> "💥"
    "SQUEEZE" -> "🌀"
    else -> "🔔"
}

private fun severityColor(sev: Int): Color = when (sev) {
    3 -> ARed
    2 -> AGold
    else -> ABlue
}

private fun severityLabel(sev: Int): String = when (sev) {
    3 -> "🔥 شدید"
    2 -> "⚠️ متوسط"
    else -> "👀 خفیف"
}

@Composable
fun FuturesAlertsScreen() {
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<FutAlert>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun scan() {
        scope.launch {
            loading = true
            errorMsg = null
            alerts = emptyList()
            try {
                val result = withContext(Dispatchers.IO) {
                    val out = mutableListOf<FutAlert>()

                    progress = "دریافت فاندینگ و تیکرها..."
                    val prem = try { BinanceFutures.api.premiumIndexAll() } catch (_: Exception) { emptyList() }
                    val tick = try { BinanceFutures.api.ticker24h() } catch (_: Exception) { emptyList() }

                    // ---------- ۱) Funding extreme ----------
                    for (p in prem) {
                        val rate = p.lastFundingRate?.toDoubleOrNull() ?: continue
                        val pct = rate * 100.0
                        if (abs(pct) < 0.05) continue
                        val sev = if (abs(pct) >= 0.10) 3 else 2
                        out.add(
                            FutAlert(
                                base = p.symbol.removeSuffix("USDT"),
                                type = "FUNDING",
                                severity = sev,
                                title = if (pct > 0) "فاندینگ ekstrem مثبت" else "فاندینگ ekstrem منفی",
                                detail = if (pct > 0)
                                    String.format(Locale.US, "%+.4f%% — لانگ‌ها شلوغ شده‌اند و هزینه می‌دهند؛ سوختِ شورت‌اسکویز معکوس", pct)
                                else
                                    String.format(Locale.US, "%+.4f%% — شورت‌ها شلوغ شده‌اند؛ سوختِ اسکویز صعودی", pct)
                            )
                        )
                    }

                    // ---------- ۲) حرکت شدید ۲۴س ----------
                    for (t in tick) {
                        if (!t.symbol.endsWith("USDT")) continue
                        val chg = t.priceChangePercent?.toDoubleOrNull() ?: continue
                        if (abs(chg) < 10.0) continue
                        val sev = if (abs(chg) >= 20.0) 3 else 2
                        out.add(
                            FutAlert(
                                base = t.symbol.removeSuffix("USDT"),
                                type = "MOVE24",
                                severity = sev,
                                title = if (chg > 0) "پامپ شدید ۲۴ ساعته" else "دامپ شدید ۲۴ ساعته",
                                detail = String.format(Locale.US, "%+.1f%% در ۲۴ ساعت — به‌دنبال exagerated بودن حرکت باشید", chg)
                            )
                        )
                    }

                    // ---------- ۳) اسکن عمیق top 8 حجم ----------
                    val top8 = tick.filter { it.symbol.endsWith("USDT") }
                        .sortedByDescending { it.quoteVolume?.toDoubleOrNull() ?: 0.0 }
                        .take(8)

                    for ((i, t) in top8.withIndex()) {
                        val base = t.symbol.removeSuffix("USDT")
                        progress = "اسکن عمیق ${i + 1}/${top8.size}: $base"

                        // OI spike
                        try {
                            val oi = BinanceFutures.api.oiHist(t.symbol, "1h", 24)
                            if (oi.size >= 2) {
                                val old = oi.first().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                                val now = oi.last().sumOpenInterestValue?.toDoubleOrNull() ?: 0.0
                                if (old > 0) {
                                    val chgOi = (now - old) / old * 100.0
                                    if (chgOi >= 12.0) {
                                        out.add(
                                            FutAlert(
                                                base = base, type = "OI_SPIKE",
                                                severity = if (chgOi >= 25.0) 3 else 2,
                                                title = "جهش Open Interest",
                                                detail = String.format(Locale.US, "OI %+.1f%% در ۲۴ ساعت — پول جدید وارد پوزیشن‌ها شده", chgOi)
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) { }

                        // EMA cross + Squeeze روی 1h
                        try {
                            val k1 = BinanceFutures.api.klines(t.symbol, "1h", 200)
                                .mapNotNull { BinanceFutures.parseCandle(it) }
                            if (k1.size >= 60) {
                                val closes = k1.map { it.close }
                                val e20 = FuturesScannerEngine.ema(closes, 20)
                                val e50 = FuturesScannerEngine.ema(closes, 50)
                                for (j in (k1.size - 3) until k1.size) {
                                    if (j < 1) continue
                                    val prevDiff = e20[j - 1] - e50[j - 1]
                                    val curDiff = e20[j] - e50[j]
                                    if (prevDiff <= 0 && curDiff > 0) {
                                        out.add(FutAlert(base, "EMA_CROSS", 2, "کراس صعودی EMA20/50 (1h)", "EMA20 بالای EMA50 بست — مومنتوم کوتاه‌مدت مثبت"))
                                    } else if (prevDiff >= 0 && curDiff < 0) {
                                        out.add(FutAlert(base, "EMA_CROSS", 2, "کراس نزولی EMA20/50 (1h)", "EMA20 زیر EMA50 بست — مومنتوم کوتاه‌مدت منفی"))
                                    }
                                }

                                // Squeeze: فشردگی نوسان نسبت به بیشینهٔ اخیر
                                val trs = k1.zipWithNext { a, b ->
                                    maxOf(b.high - b.low, abs(b.high - a.close), abs(b.low - a.close))
                                }
                                if (trs.size >= 64) {
                                    val atrNow = trs.takeLast(14).average()
                                    val windows = trs.windowed(14).takeLast(50)
                                    val atrMax = windows.maxOfOrNull { it.average() } ?: atrNow
                                    if (atrMax > 0 && atrNow / atrMax < 0.55) {
                                        out.add(FutAlert(base, "SQUEEZE", 1, "فشردگی نوسان (Squeeze)", "ATR فعلی کمتر از ۵۵٪ بیشینهٔ اخیر — آمادهٔ حرکت انفجاری (جهت نامشخص)"))
                                    }
                                }
                            }
                        } catch (_: Exception) { }

                        // Breakout / Breakdown روی 4h
                        try {
                            val k4 = BinanceFutures.api.klines(t.symbol, "4h", 200)
                                .mapNotNull { BinanceFutures.parseCandle(it) }
                            if (k4.size >= 35) {
                                val lastClose = k4.last().close
                                val prev = k4.dropLast(1).takeLast(30)
                                val hi = prev.maxOf { it.high }
                                val lo = prev.minOf { it.low }
                                if (lastClose > hi) {
                                    out.add(FutAlert(base, "BREAKOUT", 3, "شکست سقف ۳۰ کندل 4h", "قیمت بالای سقف یک‌ماههٔ ۴ساعته بست — ادامهٔ روند محتمل"))
                                } else if (lastClose < lo) {
                                    out.add(FutAlert(base, "BREAKOUT", 3, "شکست کف ۳۰ کندل 4h", "قیمت زیر کف یک‌ماههٔ ۴ساعته بست — فشار فروش ساختاری"))
                                }
                            }
                        } catch (_: Exception) { }
                    }

                    out.sortedByDescending { it.severity }
                }
                alerts = result
                progress = ""
            } catch (e: Exception) {
                errorMsg = "⚠️ خطا در اسکن هشدارها: ${e.message?.take(80) ?: "نامشخص"}"
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
                "🔔 هشدارهای فیوچرز",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        Text(
            "۶ نوع هشدار: Funding extreme • حرکت ۲۴س • OI spike • EMA cross • Breakout • Squeeze\n" +
            "⚠️ صادقانه: لیکوئیدیشن‌ها و BOS/CHOCH در این نسخه نیستند (API عمومی لیکوئیدیشن قابل اتکا نیست؛ BOS به ساختار swing نیاز دارد)",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 9.sp, color = AGray, lineHeight = 15.sp
        )

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = ARed)
                if (progress.isNotEmpty()) {
                    Text(progress, fontSize = 11.sp, color = AGray, modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else if (errorMsg != null) {
            Text(errorMsg ?: "", color = ARed, modifier = Modifier.padding(24.dp))
        } else if (alerts.isEmpty()) {
            Text(
                "😴 هیچ هشدار فعالی نیست — بازار در حالت عادی است. آرامش هم یک وضعیت بازار است.",
                color = AGray, modifier = Modifier.padding(24.dp), fontSize = 12.sp
            )
        } else {
            Text(
                "${alerts.size} هشدار فعال",
                fontSize = 11.sp, color = AGold, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alerts) { a -> AlertCard(a) }
            }
        }
    }
}

@Composable
private fun AlertCard(a: FutAlert) {
    Surface(color = ACard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(typeEmoji(a.type), fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Text(a.base, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                Text(severityLabel(a.severity), fontSize = 10.sp, color = severityColor(a.severity), fontWeight = FontWeight.Bold)
            }
            Text(a.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = severityColor(a.severity))
            Text(a.detail, fontSize = 10.sp, color = AGray, lineHeight = 15.sp)
        }
    }
}
