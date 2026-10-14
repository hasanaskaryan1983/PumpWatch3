package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.ContractRef
import com.pumpwatch.app.data.platformContractRef
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.RuleCondition
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.ui.components.AlertCard
import com.pumpwatch.app.ui.components.AlertView
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

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
    if (c1 >= 1.0) { pump += 25; pr.add("شتاب ۱ ساعته") }
    if (c1 >= 3.0) pump += 15
    if (c24 in 2.0..35.0) { pump += 20; pr.add("حرکت مثبت ۲۴ ساعته") }
    if (turnover >= 0.15) { pump += 20; pr.add("نسبت حجم به ارزش بازار بالا") }
    if (rangePos >= 0.85) { pump += 20; pr.add("نزدیک به سقف ۲۴ ساعته") }
    if (c7 > 10) pump += 5

    var dump = 0
    val dr = mutableListOf<String>()
    if (c1 <= -1.0) { dump += 25; dr.add("کاهش ۱ ساعته") }
    if (c1 <= -3.0) dump += 15
    if (c24 in -35.0..-2.0) { dump += 20; dr.add("حرکت منفی ۲۴ ساعته") }
    if (turnover >= 0.15) { dump += 20; dr.add("نسبت حجم به ارزش بازار بالا") }
    if (rangePos <= 0.15) { dump += 20; dr.add("نزدیک به کف ۲۴ ساعته") }
    if (c7 < -10) dump += 5

    val side = if (pump >= dump) "PUMP" else "DUMP"
    val score = max(pump, dump).coerceAtMost(100)
    if (score < 35) return null
    val early = abs(c1) >= 1.5 && abs(c24) < 10
    return AlertEval(c, side, score, early, if (pump >= dump) pr else dr)
}

private fun shortAddr(a: String): String =
    if (a.length > 16) "${a.take(6)}…${a.takeLast(4)}" else a

@Composable
fun SmartAlertsScreen(onCoinClick: (CoinMarket) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var coins by remember { mutableStateOf<List<CoinMarket>>(MarketHub.coins) }
    var loading by remember { mutableStateOf(MarketHub.coins.isEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("ALL") }
    var loadTimeMs by remember { mutableStateOf(MarketHub.lastUpdateMs) }
    var platformMap by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }

    // 🚀 فشرده‌سازی: فقط یکی از پنل‌ها آن هم با ضربه باز می‌شود
    var openPanel by remember { mutableStateOf<String?>(null) } // "ACCURACY" | "RULES" | null
    var accuracyResetKey by remember { mutableStateOf(0) }
    var resetMsg by remember { mutableStateOf<String?>(null) }

    var rules by remember { mutableStateOf<List<AlertRule>>(emptyList()) }
    var ruleSymbol by remember { mutableStateOf("") }
    var ruleCondition by remember { mutableStateOf(RuleCondition.PRICE_ABOVE) }
    var ruleThreshold by remember { mutableStateOf("") }
    var ruleMsg by remember { mutableStateOf("") }
    var conditionDialogOpen by remember { mutableStateOf(false) }

    fun copyText(label: String, value: String) {
        try {
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(label, value))
        } catch (_: Exception) { }
    }

    fun load(force: Boolean = false) {
        scope.launch {
            loading = true
            errorMsg = null
            loadTimeMs = System.currentTimeMillis()
            try {
                coins = MarketHub.ensure(force)
                loadTimeMs = MarketHub.lastUpdateMs
                platformMap = try { ApiClient.getPlatformMap() } catch (_: Exception) { emptyMap() }
            } catch (e: Exception) {
                errorMsg = "خطا در دریافت اطلاعات: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    fun reloadRules() { rules = AlertRulesStore.load(ctx) }

    LaunchedEffect(Unit) {
        load()
        reloadRules()
    }

    // 🚀 P0: Stale Gate
    val dataAgeSec = (System.currentTimeMillis() - loadTimeMs) / 1000L
    val isDataStale = dataAgeSec > 300

    val alerts = if (isDataStale) emptyList() else coins
        .mapNotNull { eval(it) }
        .filter { a ->
            val severity = when { a.score >= 70 -> 3; a.score >= 50 -> 2; else -> 1 }
            when (filter) {
                "HOT" -> severity == 3
                "MID" -> severity == 2
                "EARLY" -> a.early
                else -> true
            }
        }
        .sortedByDescending { it.score }

    Column(modifier = Modifier.fillMaxSize()) {
        // 🚀 هدر فشرده: بروزرسانی و سن داده داخل trailing هدر
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EditorialPageHeader(
                eyebrow = "RULES · EARLY WARNING",
                title = "هشدارهای بازار",
                subtitle = "تشخیص زودهنگام بدون AI • داده‌ها برچسب منبع/سن دارند",
                accent = PumpWatchPalette.TabAlerts,
                trailing = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { load(true) }, enabled = !loading, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(if (loading) "در حال دریافت" else "↻ بروزرسانی", fontSize = 10.sp, color = PumpWatchPalette.TabAlerts)
                        }
                        Text("آخرین دریافت: ${dataAgeSec / 60} دقیقه پیش", fontSize = 8.sp, color = PumpWatchPalette.Muted)
                    }
                }
            )

            if (resetMsg != null) {
                Text(resetMsg ?: "", fontSize = 9.sp, color = PumpWatchPalette.Positive, fontWeight = FontWeight.Bold)
            }

            // 🚀 نوار فشردهٔ کارنامه/قوانین (به‌جای دو کارت بزرگ)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = openPanel == "ACCURACY",
                    onClick = { openPanel = if (openPanel == "ACCURACY") null else "ACCURACY" },
                    label = { Text("📊 کارنامه", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabAlerts, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
                FilterChip(
                    selected = openPanel == "RULES",
                    onClick = { openPanel = if (openPanel == "RULES") null else "RULES" },
                    label = { Text("🔔 قوانین (${rules.size})", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PumpWatchPalette.TabAlerts, selectedLabelColor = PumpWatchPalette.Ink,
                        containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted
                    )
                )
            }

            // پنل کارنامه (فقط وقتی باز است)
            AnimatedVisibility(visible = openPanel == "ACCURACY") {
                GlassCard(accent = PumpWatchPalette.TabAlerts, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📊 کارنامهٔ انطباق قوانین", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text, modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            SignalLogger.clear(ctx)
                                            accuracyResetKey++
                                            resetMsg = "✅ آمار پاک شد"
                                            delay(2000); resetMsg = null
                                        } catch (_: Exception) { }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.3f), contentColor = PumpWatchPalette.Text),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("🔄 ریست", fontSize = 9.sp) }
                        }
                        Text("آمار انطباق بر اساس خروجی‌های واقعی محاسبه می‌شود؛ جزئیات در بخش ژورنال.", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                    }
                }
            }

            // پنل قوانین (فقط وقتی باز است)
            AnimatedVisibility(visible = openPanel == "RULES") {
                GlassCard(accent = PumpWatchPalette.TabAlerts, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (rules.isEmpty()) {
                            Text("هنوز قانونی تعریف نشده. مثال: BTC بالای ۷۰۰۰۰", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                        } else {
                            rules.take(4).forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${r.symbol} • ${r.condition.label} ${String.format(Locale.US, "%.4f", r.threshold)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (r.enabled) PumpWatchPalette.Positive else PumpWatchPalette.Muted)
                                        Text(if (r.enabled) "فعال ✅" else "غیرفعال ⚪", fontSize = 8.sp, color = PumpWatchPalette.Muted)
                                    }
                                    Switch(
                                        checked = r.enabled,
                                        onCheckedChange = { AlertRulesStore.toggle(ctx, r.id); reloadRules() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = PumpWatchPalette.Ink, checkedTrackColor = PumpWatchPalette.TabAlerts,
                                            uncheckedThumbColor = PumpWatchPalette.Muted, uncheckedTrackColor = PumpWatchPalette.Glass
                                        )
                                    )
                                    TextButton(onClick = { AlertRulesStore.delete(ctx, r.id); reloadRules() }) { Text("🗑", fontSize = 10.sp) }
                                }
                            }
                            if (rules.size > 4) Text("… و ${rules.size - 4} قانون دیگر", fontSize = 8.sp, color = PumpWatchPalette.Muted)
                            HorizontalDivider(color = PumpWatchPalette.Border.copy(alpha = 0.3f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = ruleSymbol, onValueChange = { ruleSymbol = it },
                                placeholder = { Text("نماد یا *", fontSize = 9.sp) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = PumpWatchPalette.GlassDeep, unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                                    focusedTextColor = PumpWatchPalette.Text, unfocusedTextColor = PumpWatchPalette.Text,
                                    cursorColor = PumpWatchPalette.TabAlerts, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Button(onClick = { conditionDialogOpen = true }, colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text), shape = RoundedCornerShape(8.dp)) {
                                Text(ruleCondition.label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = ruleThreshold, onValueChange = { ruleThreshold = it },
                                placeholder = { Text("آستانه (عدد)", fontSize = 9.sp) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = PumpWatchPalette.GlassDeep, unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                                    focusedTextColor = PumpWatchPalette.Text, unfocusedTextColor = PumpWatchPalette.Text,
                                    cursorColor = PumpWatchPalette.TabAlerts, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Button(
                                onClick = {
                                    val thr = ruleThreshold.trim().toDoubleOrNull()
                                    if (thr == null) ruleMsg = "❌ آستانه باید عدد باشد"
                                    else {
                                        AlertRulesStore.add(ctx, ruleSymbol.trim().uppercase(Locale.US).ifEmpty { "*" }, ruleCondition, thr)
                                        reloadRules(); ruleSymbol = ""; ruleThreshold = ""; ruleMsg = "✅ اضافه شد"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Warning, contentColor = PumpWatchPalette.Ink),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("➕", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                        if (ruleMsg.isNotEmpty()) Text(ruleMsg, fontSize = 8.sp, color = if (ruleMsg.startsWith("✅")) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    }
                }
            }

            // فیلترها (همان نام‌های آشنا)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("همه ${alerts.size}", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PumpWatchPalette.TabAlerts, selectedLabelColor = PumpWatchPalette.Ink, containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted))
                FilterChip(selected = filter == "HOT", onClick = { filter = "HOT" }, label = { Text("🔥 شدید", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PumpWatchPalette.TabAlerts, selectedLabelColor = PumpWatchPalette.Ink, containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted))
                FilterChip(selected = filter == "MID", onClick = { filter = "MID" }, label = { Text("⚠️ متوسط", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PumpWatchPalette.TabAlerts, selectedLabelColor = PumpWatchPalette.Ink, containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted))
                FilterChip(selected = filter == "EARLY", onClick = { filter = "EARLY" }, label = { Text("👀 زودهنگام", fontSize = 10.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PumpWatchPalette.TabAlerts, selectedLabelColor = PumpWatchPalette.Ink, containerColor = PumpWatchPalette.Glass, labelColor = PumpWatchPalette.Muted))
            }
        }

        // 🚀 P0: Stale Gate
        when {
            isDataStale -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⏰ داده‌های بازار کهنه هستند (>۵ دقیقه)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                        Text("برای جلوگیری از هشدار گمراه‌کننده، ارزیابی متوقف شد.", fontSize = 10.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                        Button(onClick = { load(true) }, colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Warning.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text), shape = RoundedCornerShape(8.dp)) {
                            Text("تازه‌سازی فوری", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            loading && coins.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PumpWatchPalette.TabAlerts) }
            errorMsg != null && coins.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(errorMsg ?: "", color = PumpWatchPalette.Negative, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
            }
            alerts.isEmpty() && !isDataStale -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("😴 در بازهٔ فعلی دارایی منطبق با فیلتر یافت نشد؛ بازار آرام است.", color = PumpWatchPalette.Muted, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center, fontSize = 11.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(alerts, key = { it.coin.id }) { a ->
                    val isPump = a.side == "PUMP"
                    val severity = when { a.score >= 70 -> 3; a.score >= 50 -> 2; else -> 1 }
                    val title = if (isPump) {
                        if (a.early) "رشد قیمت در بازه کوتاه" else "افزایش قابل توجه قیمت"
                    } else {
                        if (a.early) "کاهش قیمت در بازه کوتاه" else "کاهش قابل توجه قیمت"
                    }
                    val contractRef = platformContractRef(platformMap, a.coin.id)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AlertCard(
                            a = AlertView
