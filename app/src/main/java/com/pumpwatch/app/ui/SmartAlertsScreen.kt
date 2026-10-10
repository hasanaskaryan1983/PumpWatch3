package com.pumpwatch.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.RuleCondition
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.ui.components.AlertCard
import com.pumpwatch.app.ui.components.AlertView
import com.pumpwatch.app.ui.design.TabPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private val AGreen = Color(0xFF00E676)
private val ARed = Color(0xFFFF5252)
private val AGold = Color(0xFFFFC107)
private val AGray = Color(0xFF8B949E)
private val ACard = Color(0xFF1A2230)

private data class AlertEval(
    val coin: CoinMarket,
    val side: String,
    val score: Int,
    val early: Boolean,
    val reasons: List<String>
)

private fun eval(c: CoinMarket): AlertEval? {
    val c1 = c.change1h ?: 0.0
    val c24 = c.price_change_percentage_24h ?: 0.0
    val c7 = c.change7d ?: 0.0
    val cap = c.market_cap
    val turnover = if (cap > 0) c.total_volume / cap else 0.0
    val high = c.high24h ?: 0.0
    val low = c.low24h ?: 0.0
    val rangePos = if (high > low) (c.current_price - low) / (high - low) else 0.5

    var pump = 0
    val pr = mutableListOf<String>()
    if (c1 >= 1.0) { pump += 25; pr.add("شتاب ۱ ساعته 🚀") }
    if (c1 >= 3.0) pump += 15
    if (c24 in 2.0..35.0) { pump += 20; pr.add("حرکت مثبت ۲۴ ساعته") }
    if (turnover >= 0.15) { pump += 20; pr.add("حجم غیرعادی 💥") }
    if (rangePos >= 0.85) { pump += 20; pr.add("شکست سقف ۲۴ ساعته 📈") }
    if (c7 > 10) pump += 5

    var dump = 0
    val dr = mutableListOf<String>()
    if (c1 <= -1.0) { dump += 25; dr.add("ریزش ۱ ساعته 🩸") }
    if (c1 <= -3.0) dump += 15
    if (c24 in -35.0..-2.0) { dump += 20; dr.add("حرکت منفی ۲۴ ساعته") }
    if (turnover >= 0.15) { dump += 20; dr.add("حجم غیرعادی 💥") }
    if (rangePos <= 0.15) { dump += 20; dr.add("شکست کف ۲۴ ساعته 📉") }
    if (c7 < -10) dump += 5

    val side = if (pump >= dump) "PUMP" else "DUMP"
    val score = max(pump, dump).coerceAtMost(100)
    if (score < 35) return null

    val early = abs(c1) >= 1.5 && abs(c24) < 10

    return AlertEval(c, side, score, early, if (pump >= dump) pr else dr)
}

private fun fmtThreshold(v: Double): String = when {
    v >= 1000 -> String.format(Locale.US, "%.2f", v)
    v >= 1 -> String.format(Locale.US, "%.4f", v)
    else -> String.format(Locale.US, "%.6f", v)
}

@Composable
fun SmartAlertsScreen(onCoinClick: (CoinMarket) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var coins by remember { mutableStateOf<List<CoinMarket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("ALL") }
    
    // 🚀 Commit 163: زمان بارگذاری برای محاسبه ageSec واقعی در SourceAgeLine
    var loadTimeMs by remember { mutableStateOf(0L) }

    var accuracyExpanded by remember { mutableStateOf(false) }
    var accuracyResetKey by remember { mutableStateOf(0) }
    var resetMsg by remember { mutableStateOf<String?>(null) }

    var rules by remember { mutableStateOf<List<AlertRule>>(emptyList()) }
    var rulesExpanded by remember { mutableStateOf(false) }
    var ruleSymbol by remember { mutableStateOf("") }
    var ruleCondition by remember { mutableStateOf(RuleCondition.PRICE_ABOVE) }
    var ruleThreshold by remember { mutableStateOf("") }
    var ruleMsg by remember { mutableStateOf("") }
    var conditionDialogOpen by remember { mutableStateOf(false) }

    fun load(force: Boolean = false) {
        scope.launch {
            loading = true
            errorMsg = null
            loadTimeMs = System.currentTimeMillis()
            try {
                coins = ApiClient.getTop1000Coins(forceRefresh = force)
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun forceRefresh() {
        scope.launch {
            loading = true
            errorMsg = null
            loadTimeMs = System.currentTimeMillis()
            try {
                ApiClient.clearMemoryCache()
                coins = ApiClient.getTop1000Coins(forceRefresh = true)
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun resetAccuracy() {
        scope.launch {
            try {
                SignalLogger.clear(ctx)
                accuracyResetKey++
                resetMsg = "✅ آمار سیگنال‌ها پاک شد"
                delay(2500)
                resetMsg = null
            } catch (e: Exception) {
                resetMsg = "⚠️ خطا در پاک‌سازی: ${e.message}"
            }
        }
    }

    fun reloadRules() {
        rules = AlertRulesStore.load(ctx)
    }

    LaunchedEffect(Unit) {
        load()
        reloadRules()
    }

    val alerts = coins
        .mapNotNull { eval(it) }
        .filter { a ->
            when (filter) {
                "HOT" -> a.score >= 70
                "MID" -> a.score in 50..69
                "EARLY" -> a.early
                else -> true
            }
        }
        .sortedByDescending { it.score }

    val ageSec = if (loadTimeMs > 0L) (System.currentTimeMillis() - loadTimeMs) / 1000L else 0L

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🔔 هشدارهای بازار",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE6EDF3)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { forceRefresh() }, enabled = !loading) {
                Text(if (loading) "..." else "بروزرسانی", color = TabPalette.Alerts)
            }
        }

        Text(
            "تشخیص زودهنگام با شتاب ۱ ساعته + حجم + شکست سقف/کف (اسکنر قاعده‌محور، بدون AI)",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        if (resetMsg != null) {
            Text(
                resetMsg ?: "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 10.sp,
                color = AGreen,
                fontWeight = FontWeight.Bold
            )
        }

        // کارت کارنامه دقت (بدون تغییر در منطق، فقط ظاهر)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ACard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { accuracyExpanded = !accuracyExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (accuracyExpanded) "▼" else "▶", fontSize = 12.sp, color = AGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "📊 کارنامهٔ دقت سیگنال‌ها",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { resetAccuracy() },
                        colors = ButtonDefaults.buttonColors(containerColor = ARed.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🔄 ریست", fontSize = 10.sp, color = Color.White)
                    }
                }
                AnimatedVisibility(visible = accuracyExpanded) {
                    SignalAccuracyCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        accent = AGreen,
                        key = accuracyResetKey
                    )
                }
            }
        }

        // کارت قوانین سفارشی (بدون تغییر در منطق)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ACard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { rulesExpanded = !rulesExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (rulesExpanded) "▼" else "▶", fontSize = 12.sp, color = AGold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🔔 قانون‌های من (${rules.size})",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text("ارزیابی هر ۳۰ دقیقه توسط Worker", fontSize = 9.sp, color = AGray)
                }

                AnimatedVisibility(visible = rulesExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (rules.isEmpty()) {
                            Text("هنوز قانونی نساختی. مثال: BTC قیمت بالای ۷۰۰۰۰", fontSize = 10.sp, color = AGray)
                        } else {
                            rules.take(5).forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${r.symbol} • ${r.condition.label} ${fmtThreshold(r.threshold)}",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (r.enabled) AGreen else AGray
                                        )
                                        Text(
                                            when {
                                                !r.enabled -> "خاموش ⚪"
                                                AlertRulesStore.inCooldown(r, System.currentTimeMillis()) -> "فعال ولی در سکوت"
                                                else -> "فعال ✅"
                                            },
                                            fontSize = 9.sp, color = AGray
                                        )
                                    }
                                    Switch(
                                        checked = r.enabled,
                                        onCheckedChange = { AlertRulesStore.toggle(ctx, r.id); reloadRules() }
                                    )
                                    TextButton(onClick = { AlertRulesStore.delete(ctx, r.id); reloadRules() }) {
                                        Text("🗑", fontSize = 11.sp)
                                    }
                                }
                            }
                            if (rules.size > 5) {
                                Text("... و ${rules.size - 5} قانون دیگر", fontSize = 9.sp, color = AGray, modifier = Modifier.padding(top = 4.dp))
                            }
                            HorizontalDivider(color = AGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = ruleSymbol, onValueChange = { ruleSymbol = it },
                                placeholder = { Text("نماد یا *", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true
                            )
                            Button(
                                onClick = { conditionDialogOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ACard),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(ruleCondition.label, fontSize = 9.sp) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = ruleThreshold, onValueChange = { ruleThreshold = it },
                                placeholder = { Text("آستانه (عدد)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true
                            )
                            Button(
                                onClick = {
                                    val thr = ruleThreshold.trim().toDoubleOrNull()
                                    if (thr == null) {
                                        ruleMsg = "❌ آستانه باید عدد باشد"
                                    } else {
                                        val sym = ruleSymbol.trim().uppercase(Locale.US).ifEmpty { "*" }
                                        AlertRulesStore.add(ctx, sym, ruleCondition, thr)
                                        reloadRules()
                                        ruleSymbol = ""
                                        ruleThreshold = ""
                                        ruleMsg = "✅ قانون اضافه شد"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AGold),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("➕", fontSize = 12.sp) }
                        }
                        if (ruleMsg.isNotEmpty()) {
                            Text(
                                ruleMsg, fontSize = 9.sp,
                                color = if (ruleMsg.startsWith("✅")) AGreen else ARed,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // فیلترها
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("همه ${alerts.size}", fontSize = 11.sp) })
            FilterChip(selected = filter == "HOT", onClick = { filter = "HOT" }, label = { Text("🔥 شدید", fontSize = 11.sp) })
            FilterChip(selected = filter == "MID", onClick = { filter = "MID" }, label = { Text("⚠️ متوسط", fontSize = 11.sp) })
            FilterChip(selected = filter == "EARLY", onClick = { filter = "EARLY" }, label = { Text("👀 زودهنگام", fontSize = 11.sp) })
        }

        // لیست
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TabPalette.Alerts)
            }
            errorMsg != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg ?: "", color = ARed, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
            }
            alerts.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "😴 بازار آرومه — هنوز سیگنالی نیست",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alerts, key = { it.coin.id }) { a ->
                    // 🚀 Commit 163: مهاجرت به AlertCard شیشه‌ای
                    val isPump = a.side == "PUMP"
                    val severity = when {
                        a.score >= 70 -> 3
                        a.score >= 50 -> 2
                        else -> 1
                    }
                    val title = if (isPump) {
                        if (a.early) "شتاب مثبت زودهنگام" else "پامپ قدرتمند"
                    } else {
                        if (a.early) "ریزش زودهنگام" else "دامپ شدید"
                    }
                    
                    AlertCard(
                        a = AlertView(
                            symbol = a.coin.symbol.uppercase(Locale.US),
                            type = if (isPump) "PUMP" else "DUMP",
                            severity = severity,
                            title = title,
                            detail = a.reasons.joinToString(" • "),
                            rank = a.coin.market_cap_rank,
                            emoji = if (isPump) "🚀" else "🩸",
                            source = "CoinGecko",
                            ageSec = ageSec
                        ),
                        tabKey = "alerts" // رنگ امضایی #FB4D6D
                    )
                }
            }
        }
    }

    // دیالوگ انتخاب شرط
    if (conditionDialogOpen) {
        AlertDialog(
            onDismissRequest = { conditionDialogOpen = false },
            title = { Text("شرط قانون را انتخاب کنید", fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RuleCondition.values().forEach { c ->
                        Button(
                            onClick = { ruleCondition = c; conditionDialogOpen = false },
                            colors = ButtonDefaults.buttonColors(containerColor = ACard),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(c.label, fontSize = 11.sp) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { conditionDialogOpen = false }) { Text("بستن") }
            }
        )
    }
}
