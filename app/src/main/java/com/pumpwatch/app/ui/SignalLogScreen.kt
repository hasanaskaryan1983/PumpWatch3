package com.pumpwatch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.AlertRule
import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.OptReport
import com.pumpwatch.app.engine.QuickScanner
import com.pumpwatch.app.engine.RuleCondition
import com.pumpwatch.app.engine.SignalLogger
import com.pumpwatch.app.engine.WalkForwardOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LC = Color(0xFF1A2230)
private val LBlue = Color(0xFF40C4FF)
private val LPurple = Color(0xFFCE93D8)

private val candleTimeFmt = SimpleDateFormat("HH:mm", Locale.US)
private val reportDateFmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)

private fun statusEmoji(s: String) = when (s) {
    "WIN" -> "✅"
    "LOSS" -> ""
    "EXP" -> "⌛"
    "OPEN" -> "🔓"
    else -> "⏳"
}

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "%.2f", p)
    p >= 1 -> String.format(Locale.US, "%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "%.5f", p)
    else -> String.format(Locale.US, "%.6f", p)
}

private fun fmtCandleTime(ts: Long): String = if (ts > 0) candleTimeFmt.format(Date(ts)) else "—"

@Composable
fun SignalLogScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("pumpwatch_prefs", 0) }
    var logs by remember { mutableStateOf<List<LoggedSignal>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedFilter by remember {
        mutableStateOf(if (prefs.getString("mode", "SPOT") == "FUTURES") "FUT" else "SPOT")
    }
    var lastScores by remember { mutableStateOf("") }
    var optReport by remember { mutableStateOf<OptReport?>(null) }

    // 🚀 Sprint 5: state قوانین هشدار سفارشی
    var rules by remember { mutableStateOf<List<AlertRule>>(emptyList()) }
    var rulesOpen by remember { mutableStateOf(false) }
    var ruleSymbol by remember { mutableStateOf("") }
    var ruleCondition by remember { mutableStateOf(RuleCondition.PRICE_ABOVE) }
    var ruleThreshold by remember { mutableStateOf("") }
    var ruleMenuOpen by remember { mutableStateOf(false) }
    var ruleMsg by remember { mutableStateOf("") }

    fun loadLogs() {
        scope.launch {
            val l = SignalLogger.load(ctx)
            val ev = SignalLogger.evaluate(ctx, l)
            SignalLogger.save(ctx, ev)
            logs = ev
        }
    }

    fun reloadRules() {
        rules = AlertRulesStore.load(ctx)
    }

    LaunchedEffect(Unit) {
        loadLogs()
        reloadRules()
        lastScores = prefs.getString("last_scores", "") ?: ""

        // 🚀 Sprint 4: تریگر بهینه‌سازی Walk-Forward
        try {
            optReport = WalkForwardOptimizer.loadReport(ctx)
            val current = SignalLogger.load(ctx)
            val closedCount = current.count { it.status in listOf("WIN", "LOSS", "EXP") }
            val now = System.currentTimeMillis()
            val stale = optReport == null || now - (optReport?.ts ?: 0L) > 24 * 3_600_000L
            if (closedCount >= 50 && stale) {
                val rep = WalkForwardOptimizer.runIfNeeded(ctx)
                if (rep != null) optReport = rep
            }
        } catch (_: Exception) { }

        while (true) {
            delay(45_000L)
            val currentLogs = SignalLogger.load(ctx)
            val hasOpen = currentLogs.any { it.status == "OPEN" || it.status == "EXP" }
            if (hasOpen) {
                val updated = SignalLogger.updateOpenSignals(ctx, currentLogs)
                logs = updated
                SignalLogger.save(ctx, updated)
            }
        }
    }

    val filteredLogs = when (selectedFilter) {
        "SPOT" -> logs.filter { it.mode != "FUT" }
        "FUT" -> logs.filter { it.mode == "FUT" }
        else -> logs
    }

    val closedSignals = logs.filter { it.status in listOf("WIN", "LOSS", "EXP") }
    val wfTotal = closedSignals.size
    val wfWins = closedSignals.count { it.status == "WIN" }
    val wfLosses = closedSignals.count { it.status == "LOSS" }
    val wfExpired = closedSignals.count { it.status == "EXP" }
    val wfDecided = wfWins + wfLosses
    val wfWinRate = if (wfDecided > 0) wfWins * 100.0 / wfDecided else 0.0
    val wfExpectancy = closedSignals.mapNotNull { s ->
        s.exitPrice?.let { ep ->
            if (s.side == "BUY") (ep - s.entry) / s.entry * 100
            else (s.entry - ep) / s.entry * 100
        }
    }.let { pnls -> if (pnls.isNotEmpty()) pnls.average() else 0.0 }
    val wfProgressPct = (wfTotal.coerceAtMost(50) * 100.0 / 50.0)
    val wfReady = wfTotal >= 50

    val openCount = logs.count { it.status == "OPEN" }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📓 لاگ سیگنال‌های زنده", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = {
                    if (!isScanning) {
                        isScanning = true
                        scope.launch {
                            val mode = prefs.getString("mode", "SPOT") ?: "SPOT"
                            val report = withContext(Dispatchers.IO) {
                                QuickScanner.scan(ctx, QuickScanner.TOP_SYMBOLS.take(15), mode)
                            }
                            lastScores = "سیگنال جدید: ${report.signalCount}\n" + report.lines.joinToString("\n")
                            prefs.edit().putString("last_scores", lastScores).apply()
                            loadLogs()
                            isScanning = false
                        }
                    }
                },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = LBlue)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isScanning) "در حال اسکن..." else "🔍 اسکن فوری", fontSize = 12.sp)
            }
        }

        // داشبورد واحد عملکرد استراتژی
        Card(
            colors = CardDefaults.cardColors(containerColor = LC),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚀 عملکرد استراتژی", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LPurple)
                    Spacer(Modifier.weight(1f))
                    Text("$wfTotal / 50", fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (wfReady) LG else LY)
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(LGr.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((wfProgressPct / 100.0).toFloat().coerceIn(0f, 1f))
                            .height(10.dp)
                            .background(if (wfReady) LG else LBlue, RoundedCornerShape(5.dp))
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("✅ برد: $wfWins", color = LG, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("❌ باخت: $wfLosses", color = LR, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("⌛ منقضی: $wfExpired", color = LY, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("🔓 باز: $openCount", color = LBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "وین‌ریت: ${String.format(Locale.US, "%.1f%%", wfWinRate)}",
                        color = if (wfWinRate >= 55) LG else LR,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        "Expectancy: ${String.format(Locale.US, "%+.2f%%", wfExpectancy)}",
                        color = if (wfExpectancy >= 0) LG else LR,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )

                Text(
                    "🎯 استراتژی: استاپ دنباله‌رو (Trailing) + هدف شناور. تا وقتی استاپ نخوره، پوزیشن باز می‌مونه.",
                    fontSize = 9.sp,
                    color = LBlue,
                    lineHeight = 14.sp
                )

                Text(
                    when {
                        wfTotal == 0 -> "💡 هنوز داده‌ای جمع نشده — از اپ استفاده کنید، سیگنال‌ها خودکار ثبت و ارزیابی می‌شن"
                        wfReady -> "✅ به ۵۰ سیگنال رسیدیم! آماده برای فعال‌سازی Walk-Forward Optimization"
                        else -> "📊 به ${50 - wfTotal} سیگنال دیگه نیاز داریم تا دادهٔ کافی برای بهینه‌سازی خودکار وزن‌ها جمع بشه"
                    },
                    fontSize = 10.sp,
                    color = if (wfReady) LG else LGr,
                    lineHeight = 14.sp
                )
            }
        }

        // کارت گزارش بهینه‌سازی Walk-Forward
        optReport?.let { r ->
            Card(
                colors = CardDefaults.cardColors(containerColor = LC),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🧠 بهینه‌سازی Walk-Forward", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LPurple)
                        Spacer(Modifier.weight(1f))
                        Text(reportDateFmt.format(Date(r.ts)), fontSize = 9.sp, color = LGr)
                    }
                    Text(
                        if (r.applied)
                            "✅ آستانهٔ فعال: ${r.chosenMinScore} (قبلی: ${r.previousMinScore}) — بر اساس دادهٔ واقعی اعمال شد"
                        else
                            "📊 بررسی شد: آستانهٔ فعلی (${r.previousMinScore}) از کاندیداها بهتر بود — تغییری لازم نبود",
                        fontSize = 11.sp,
                        color = if (r.applied) LG else LGr,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "IN (${r.inSampleSize} سیگنال): وین‌ریت ${String.format(Locale.US, "%.1f%%", r.inWinRate)} | Expectancy ${String.format(Locale.US, "%+.2f%%", r.inExpectancy)}",
                        fontSize = 10.sp, color = LGr
                    )
                    Text(
                        "OUT (${r.outSampleSize} سیگنال): وین‌ریت ${String.format(Locale.US, "%.1f%%", r.outWinRate)} | Expectancy ${String.format(Locale.US, "%+.2f%%", r.outExpectancy)}",
                        fontSize = 10.sp, color = LGr
                    )
                }
            }
        }

        // 🚀 Sprint 5: کارت قوانین هشدار سفارشی (جمع‌شونده)
        Card(
            colors = CardDefaults.cardColors(containerColor = LC),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔔 قوانین هشدار سفارشی (${rules.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LY)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { rulesOpen = !rulesOpen },
                        colors = ButtonDefaults.buttonColors(containerColor = LC),
                        shape = RoundedCornerShape(6.dp)
                    ) { Text(if (rulesOpen) "▾ بستن" else "▸ باز کردن", fontSize = 10.sp) }
                }

                if (rulesOpen) {
                    // لیست قوانین موجود
                    if (rules.isEmpty()) {
                        Text("هنوز قانونی نساختی — مثلاً: BTC قیمت بالای X، یا * فاندینگ زیر -0.0003", fontSize = 9.sp, color = LGr)
                    }
                    rules.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${r.symbol} • ${r.condition.label} ${fmtPrice(r.threshold)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (r.enabled) LG else LGr
                                )
                                Text(
                                    when {
                                        !r.enabled -> "خاموش ⚪"
                                        AlertRulesStore.inCooldown(r, System.currentTimeMillis()) -> "فعال ✅ ولی در دورهٔ سکوت ⏳"
                                        else -> "فعال ✅"
                                    },
                                    fontSize = 9.sp,
                                    color = LGr
                                )
                            }
                            Switch(checked = r.enabled, onCheckedChange = {
                                AlertRulesStore.toggle(ctx, r.id)
                                reloadRules()
                            })
                            Button(
                                onClick = {
                                    AlertRulesStore.delete(ctx, r.id)
                                    reloadRules()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LC),
                                shape = RoundedCornerShape(6.dp)
                            ) { Text("🗑", fontSize = 10.sp) }
                        }
                    }

                    HorizontalDivider(color = LGr.copy(alpha = 0.3f))

                    // فرم افزودن قانون جدید
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = ruleSymbol,
                            onValueChange = { ruleSymbol = it },
                            placeholder = { Text("نماد یا * (همه)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        Box {
                            Button(
                                onClick = { ruleMenuOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LC),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(ruleCondition.label, fontSize = 10.sp) }
                            DropdownMenu(expanded = ruleMenuOpen, onDismissRequest = { ruleMenuOpen = false }) {
                                RuleCondition.values().forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c.label, fontSize = 11.sp) },
                                        onClick = { ruleCondition = c; ruleMenuOpen = false }
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = ruleThreshold,
                            onValueChange = { ruleThreshold = it },
                            placeholder = { Text("آستانه (عدد)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
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
                            colors = ButtonDefaults.buttonColors(containerColor = LY),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("➕ افزودن", fontSize = 10.sp) }
                    }
                    if (ruleMsg.isNotEmpty()) Text(ruleMsg, fontSize = 9.sp, color = if (ruleMsg.startsWith("✅")) LG else LR)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedFilter == "ALL", onClick = { selectedFilter = "ALL" }, label = { Text("همه (${logs.size})", fontSize = 11.sp) })
            FilterChip(selected = selectedFilter == "SPOT", onClick = { selectedFilter = "SPOT" }, label = { Text("🏦 اسپات", fontSize = 11.sp) })
            FilterChip(selected = selectedFilter == "FUT", onClick = { selectedFilter = "FUT" }, label = { Text("⚡ فیوچرز", fontSize = 11.sp) })
        }

        if (filteredLogs.isEmpty()) {
            Text("هنوز سیگنالی ثبت نشده — دکمه «اسکن فوری» رو بزن", color = LGr, modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs) { s ->
                    val isLong = s.side == "BUY"
                    val pnl = s.exitPrice?.let { ep ->
                        if (isLong) (ep - s.entry) / s.entry * 100 else (s.entry - ep) / s.entry * 100
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (s.status == "OPEN") Color(0xFF1A2A3A) else LC,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                val modeEmoji = if (s.mode == "FUT") "⚡" else ""
                                Text("$modeEmoji ${s.symbol} • ${if (isLong) "🟢 Long" else "🔴 Short"}", fontWeight = FontWeight.Bold)
                                Text("${statusEmoji(s.status)} ${s.status}", color = when (s.status) {
                                    "WIN" -> LG; "LOSS" -> LR; "EXP" -> LY; "OPEN" -> LBlue; else -> LGr
                                })
                            }

                            Text(
                                "🕐 کندل: ${fmtCandleTime(s.time)}",
                                fontSize = 10.sp,
                                color = LGr
                            )

                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("ورود: $${fmtPrice(s.entry)}", fontSize = 11.sp, color = LGr)
                                if (s.currentPrice != null && s.status == "OPEN") {
                                    Text("الان: $${fmtPrice(s.currentPrice)}", fontSize = 12.sp, color = LBlue, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                if (s.status == "OPEN" && s.trailingStop > 0) {
                                    Text("🛑 استاپ دنباله‌رو: $${fmtPrice(s.trailingStop)}", fontSize = 10.sp, color = LR, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("استاپ اولیه: $${fmtPrice(s.stop)}", fontSize = 10.sp, color = LR)
                                }

                                if (s.status == "OPEN" && s.currentTarget > s.target) {
                                    Text("🎯 هدف شناور: $${fmtPrice(s.currentTarget)}", fontSize = 10.sp, color = LG, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("هدف: $${fmtPrice(s.target)}", fontSize = 10.sp, color = LG)
                                }
                            }

                            if (s.status == "OPEN" && s.highestPrice > s.entry) {
                                Text("📈 سقف ثبت‌شده: $${fmtPrice(s.highestPrice)} (سود قفل‌شده تا $${fmtPrice(s.trailingStop)})", fontSize = 9.sp, color = LY)
                            }

                            s.exitPrice?.let { ep ->
                                Text(
                                    "خروج: $${fmtPrice(ep)} • PnL: ${String.format(Locale.US, "%+.2f%%", pnl ?: 0.0)}",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if ((pnl ?: 0.0) >= 0) LG else LR
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
