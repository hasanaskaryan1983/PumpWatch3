package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.AssetIdentity
import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.data.DataStatus
import com.pumpwatch.app.data.ServedFrom
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
    val reasons: List<String>,
    val matchStatus: String // 🚀 P0 Tab 02: MATCH / NO_MATCH / UNKNOWN
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
    val matchStatus = if (score >= 70) "MATCH" else if (score >= 50) "PARTIAL_MATCH" else "NO_MATCH"

    return AlertEval(c, side, score, early, if (pump >= dump) pr else dr, matchStatus)
}

@Composable
fun SmartAlertsScreen(onCoinClick: (CoinMarket) -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var coins by remember { mutableStateOf<List<CoinMarket>>(MarketHub.coins) }
    var loading by remember { mutableStateOf(MarketHub.coins.isEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("ALL") }
    var loadTimeMs by remember { mutableStateOf(MarketHub.lastUpdateMs) }

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
                coins = MarketHub.ensure(force)
                loadTimeMs = MarketHub.lastUpdateMs
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
                coins = MarketHub.ensure(force = true)
                loadTimeMs = MarketHub.lastUpdateMs
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
                resetMsg = "✅ آمار انطباق قوانین پاک شد"
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

    // 🚀 P0 Tab 02: Stale Gate - بررسی کهنه بودن داده قبل از ارزیابی
    val dataAgeSec = (System.currentTimeMillis() - loadTimeMs) / 1000L
    val isDataStale = dataAgeSec > 300 // بیشتر از ۵ دقیقه

    val alerts = if (isDataStale) {
        emptyList()
    } else {
        coins
            .mapNotNull { eval(it) }
            .filter { a ->
                when (filter) {
                    "HOT" -> a.score >= 70 && a.matchStatus == "MATCH"
                    "MID" -> a.score in 50..69
                    "EARLY" -> a.early
                    else -> true
                }
            }
            .sortedByDescending { it.score }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 🚀 P0 Tab 02: صداقت محصول و خنثی‌سازی ادبیات
            EditorialPageHeader(
                eyebrow = "SPOT · RULE ENGINE",
                title = "بررسی انطباق قوانین",
                subtitle = "ارزیابی دارایی‌ها بر اساس قوانین تعریف‌شده. این خروجی پژوهشی است و به معنای توصیه خرید یا فروش نیست.",
                accent = PumpWatchPalette.TabAlerts
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubRefreshRow(loadTimeMs, loading, PumpWatchPalette.TabAlerts) { forceRefresh() }
                Spacer(Modifier.weight(1f))
                if (isDataStale) {
                    Surface(color = PumpWatchPalette.Warning.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                        Text("⚠️ داده کهنه (>۵ دقیقه)", fontSize = 9.sp, color = PumpWatchPalette.Warning, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }

        if (resetMsg != null) {
            Text(
                resetMsg ?: "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 10.sp,
                color = PumpWatchPalette.Positive,
                fontWeight = FontWeight.Bold
            )
        }

        // کارت کارنامه دقت
        GlassCard(accent = PumpWatchPalette.TabAlerts, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { accuracyExpanded = !accuracyExpanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (accuracyExpanded) "▼" else "▶", fontSize = 12.sp, color = PumpWatchPalette.Positive)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "📊 کارنامهٔ انطباق قوانین",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { resetAccuracy() },
                        colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.3f), contentColor = PumpWatchPalette.Text),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🔄 بازنشانی", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                AnimatedVisibility(visible = accuracyExpanded) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Text("📊 آمار انطباق بر اساس خروجی‌های واقعی محاسبه می‌شود.", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                        Text("برای مشاهدهٔ جزئیات کامل، به بخش «ژورنال» مراجعه کنید.", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                    }
                }
            }
        }

        // کارت قوانین سفارشی
        GlassCard(accent = PumpWatchPalette.TabAlerts, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { rulesExpanded = !rulesExpanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (rulesExpanded) "▼" else "▶", fontSize = 12.sp, color = PumpWatchPalette.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🔔 قوانین تعریف‌شده (${rules.size})",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text,
                        modifier = Modifier.weight(1f)
                    )
                    Text("ارزیابی دوره‌ای در پس‌زمینه", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                }

                AnimatedVisibility(visible = rulesExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        if (rules.isEmpty()) {
                            Text("هنوز قانونی تعریف نشده است. مثال: قیمت BTC بالای ۷۰۰۰۰", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                        } else {
                            rules.take(5).forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "${r.symbol} • ${r.condition.label} ${fmtThreshold(r.threshold)}",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (r.enabled) PumpWatchPalette.Positive else PumpWatchPalette.Muted
                                        )
                                        Text(
                                            when {
                                                !r.enabled -> "غیرفعال ⚪"
                                                AlertRulesStore.inCooldown(r, System.currentTimeMillis()) -> "فعال ولی در دوره سکوت"
                                                else -> "فعال ✅"
                                            },
                                            fontSize = 9.sp, color = PumpWatchPalette.Muted
                                        )
                                    }
                                    androidx.compose.material3.Switch(
                                        checked = r.enabled,
                                        onCheckedChange = { AlertRulesStore.toggle(ctx, r.id); reloadRules() },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(
                                            checkedThumbColor = PumpWatchPalette.Ink,
                                            checkedTrackColor = PumpWatchPalette.TabAlerts,
                                            uncheckedThumbColor = PumpWatchPalette.Muted,
                                            uncheckedTrackColor = PumpWatchPalette.Glass
                                        )
                                    )
                                    TextButton(onClick = { AlertRulesStore.delete(ctx, r.id); reloadRules() }) {
                                        Text("🗑", fontSize = 11.sp, color = PumpWatchPalette.Text)
                                    }
                                }
                            }
                            if (rules.size > 5) {
                                Text("... و ${rules.size - 5} قانون دیگر", fontSize = 9.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(top = 4.dp))
                            }
                            HorizontalDivider(color = PumpWatchPalette.Border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextField(
                                value = ruleSymbol, onValueChange = { ruleSymbol = it },
                                placeholder = { Text("نماد یا *", fontSize = 10.sp, color = PumpWatchPalette.MutedDim) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true,
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = PumpWatchPalette.GlassDeep,
                                    unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                                    focusedTextColor = PumpWatchPalette.Text,
                                    unfocusedTextColor = PumpWatchPalette.Text,
                                    cursorColor = PumpWatchPalette.TabAlerts,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Button(
                                onClick = { conditionDialogOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(ruleCondition.label, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextField(
                                value = ruleThreshold, onValueChange = { ruleThreshold = it },
                                placeholder = { Text("آستانه (عدد)", fontSize = 10.sp, color = PumpWatchPalette.MutedDim) },
                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true,
                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                    focusedContainerColor = PumpWatchPalette.GlassDeep,
                                    unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                                    focusedTextColor = PumpWatchPalette.Text,
                                    unfocusedTextColor = PumpWatchPalette.Text,
                                    cursorColor = PumpWatchPalette.TabAlerts,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
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
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Warning, contentColor = PumpWatchPalette.Ink),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("➕", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        if (ruleMsg.isNotEmpty()) {
                            Text(
                                ruleMsg, fontSize = 9.sp,
                                color = if (ruleMsg.startsWith("✅")) PumpWatchPalette.Positive else PumpWatchPalette.Negative,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // فیلترها
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == "ALL", onClick = { filter = "ALL" },
                label = { Text("همه ${alerts.size}", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PumpWatchPalette.TabAlerts,
                    selectedLabelColor = PumpWatchPalette.Ink,
                    containerColor = PumpWatchPalette.Glass,
                    labelColor = PumpWatchPalette.Muted
                )
            )
            FilterChip(
                selected = filter == "HOT", onClick = { filter = "HOT" },
                label = { Text("🔥 انطباق کامل", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PumpWatchPalette.TabAlerts,
                    selectedLabelColor = PumpWatchPalette.Ink,
                    containerColor = PumpWatchPalette.Glass,
                    labelColor = PumpWatchPalette.Muted
                )
            )
            FilterChip(
                selected = filter == "MID", onClick = { filter = "MID" },
                label = { Text("⚠️ انطباق جزئی", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PumpWatchPalette.TabAlerts,
                    selectedLabelColor = PumpWatchPalette.Ink,
                    containerColor = PumpWatchPalette.Glass,
                    labelColor = PumpWatchPalette.Muted
                )
            )
            FilterChip(
                selected = filter == "EARLY", onClick = { filter = "EARLY" },
                label = { Text("👀 حرکت اولیه", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PumpWatchPalette.TabAlerts,
                    selectedLabelColor = PumpWatchPalette.Ink,
                    containerColor = PumpWatchPalette.Glass,
                    labelColor = PumpWatchPalette.Muted
                )
            )
        }

        // 🚀 P0 Tab 02: نمایش وضعیت‌های Sealed Outcomes
        when {
            isDataStale -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("⏰ داده‌های بازار کهنه هستند (>۵ دقیقه)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Warning)
                            Text("برای جلوگیری از هشدارهای گمراه‌کننده، ارزیابی متوقف شد. لطفاً داده‌ها را تازه‌سازی کنید.", fontSize = 11.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                            Button(
                                onClick = { forceRefresh() },
                                colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Warning.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text("تازه‌سازی فوری", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            loading && coins.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PumpWatchPalette.TabAlerts)
                }
            }
            errorMsg != null && coins.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMsg ?: "", color = PumpWatchPalette.Negative, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                }
            }
            alerts.isEmpty() && !isDataStale -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "😴 در بازه زمانی فعلی، دارایی‌ای با معیارهای انتخاب‌شده یافت نشد. بازار در حالت آرام است.",
                        color = PumpWatchPalette.Muted,
                        modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(alerts, key = { it.coin.id }) { a ->
                        val isPump = a.side == "PUMP"
                        val severity = when {
                            a.score >= 70 -> 3
                            a.score >= 50 -> 2
                            else -> 1
                        }
                        val title = if (isPump) {
                            if (a.early) "رشد قیمت در بازه کوتاه" else "افزایش قابل توجه قیمت"
                        } else {
                            if (a.early) "کاهش قیمت در بازه کوتاه" else "کاهش قابل توجه قیمت"
                        }

                        AlertCard(
                            a = AlertView(
                                symbol = a.coin.symbol.uppercase(Locale.US),
                                type = if (isPump) "PUMP" else "DUMP",
                                severity = severity,
                                title = title,
                                detail = a.reasons.joinToString(" • "),
                                rank = a.coin.market_cap_rank,
                                emoji = if (isPump) "📈" else "📉",
                                source = "Rule Engine",
                                ageSec = dataAgeSec
                            ),
                            tabKey = "alerts"
                        )
                    }
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
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
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

private fun fmtThreshold(v: Double): String = when {
    v >= 1000 -> String.format(Locale.US, "%.2f", v)
    v >= 1 -> String.format(Locale.US, "%.4f", v)
    else -> String.format(Locale.US, "%.6f", v)
}
