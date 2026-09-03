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
import com.pumpwatch.app.data.RadarBinance
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.QuickScanner
import com.pumpwatch.app.engine.SignalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LC = Color(0xFF1A2230)
private val LBlue = Color(0xFF40C4FF)

private fun statusEmoji(s: String) = when (s) {
    "WIN" -> "✅"
    "LOSS" -> "❌"
    "EXP" -> "⌛"
    else -> "⏳"
}

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> String.format(Locale.US, "%.2f", p)
    p >= 1 -> String.format(Locale.US, "%.4f", p)
    p >= 0.01 -> String.format(Locale.US, "%.5f", p)
    else -> String.format(Locale.US, "%.6f", p)
}

@Composable
fun SignalLogScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("pumpwatch_prefs", 0) }
    var logs by remember { mutableStateOf<List<LoggedSignal>>(emptyList()) }
    var stats by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedFilter by remember {
        mutableStateOf(if (prefs.getString("mode", "SPOT") == "FUTURES") "FUT" else "SPOT")
    }
    var lastScores by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }

    fun loadLogs() {
        scope.launch {
            val l = SignalLogger.load(ctx)
            val prices = try {
                RadarBinance.api.tickers().associate { t ->
                    val sym = t.symbol?.replace("USDT", "") ?: ""
                    sym to (t.lastPrice?.toDoubleOrNull() ?: 0.0)
                }
            } catch (_: Exception) {
                mapOf<String, Double>()
            }
            val ev = SignalLogger.evaluate(l, prices)
            SignalLogger.save(ctx, ev)
            logs = ev
            stats = Triple(
                ev.count { it.status == "WIN" },
                ev.count { it.status == "LOSS" },
                ev.count { it.status == "EXP" }
            )
        }
    }

    LaunchedEffect(Unit) {
        loadLogs()
        lastScores = prefs.getString("last_scores", "") ?: ""
    }

    val filteredLogs = when (selectedFilter) {
        "SPOT" -> logs.filter { it.mode != "FUT" }
        "FUT" -> logs.filter { it.mode == "FUT" }
        else -> logs
    }

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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isScanning) "در حال اسکن..." else "🔍 اسکن فوری", fontSize = 12.sp)
            }
        }

        stats?.let { st ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                Text("✅ برد: ${st.first}", color = LG, fontWeight = FontWeight.Bold)
                Text("❌ باخت: ${st.second}", color = LR, fontWeight = FontWeight.Bold)
                Text("⌛ منقضی: ${st.third}", color = LY, fontWeight = FontWeight.Bold)
            }
            Text(
                "برد = رسید به هدف | باخت = خورد به استاپ | منقضی = بدون نتیجه بعد از ۲۴ ساعت",
                fontSize = 9.sp,
                color = LGr
            )
            val t = st.first + st.second
            if (t > 0) {
                val wr = st.first * 100.0 / t
                Text(
                    "وین‌ریت زنده: ${String.format(Locale.US, "%.1f%%", wr)}",
                    color = if (wr >= 55) LG else LR,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("همه (${logs.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "SPOT",
                onClick = { selectedFilter = "SPOT" },
                label = { Text("🏦 اسپات (${logs.count { it.mode != "FUT" }})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "FUT",
                onClick = { selectedFilter = "FUT" },
                label = { Text("⚡ فیوچرز (${logs.count { it.mode == "FUT" }})", fontSize = 11.sp) }
            )
        }

        if (lastScores.isNotEmpty()) {
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(
                    if (showDetails) "🔬 جزئیات اسکن (پنهان کن)" else "🔬 جزئیات اسکن (نمایش)",
                    fontSize = 11.sp
                )
            }
            if (showDetails) {
                Surface(
                    color = LC,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    Column(
                        Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(lastScores, fontSize = 10.sp, color = LGr)
                    }
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            Text(
                if (logs.isEmpty()) "هنوز سیگنالی ثبت نشده — دکمه «اسکن فوری» رو بزن"
                else "سیگنالی با این فیلتر پیدا نشد — «اسکن فوری» بزن تا در این حالت اسکن بشه",
                color = LGr,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredLogs) { s ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(LC, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                val modeEmoji = if (s.mode == "FUT") "⚡" else "🏦"
                                val modeText = if (s.mode == "FUT") "فیوچرز" else "اسپات"
                                Text(
                                    "$modeEmoji ${s.symbol} • ${if(s.side=="BUY")"🟢 خرید" else "🔴 فروش"} • $modeText",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${statusEmoji(s.status)} ${s.status}",
                                    color = when(s.status) {
                                        "WIN" -> LG
                                        "LOSS" -> LR
                                        "EXP" -> LY
                                        else -> LGr
                                    }
                                )
                            }
                            Text(
                                "امتیاز: ${s.score}/100 • ورود: $${fmtPrice(s.entry)}",
                                fontSize = 11.sp, color = LGr
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("استاپ: $${fmtPrice(s.stop)}", fontSize = 10.sp, color = LR)
                                Text("هدف: $${fmtPrice(s.target)}", fontSize = 10.sp, color = LG)
                            }
                            s.exitPrice?.let { ep ->
                                val pnl = if (s.side == "BUY") (ep - s.entry) / s.entry * 100
                                else (s.entry - ep) / s.entry * 100
                                Text(
                                    "خروج: $${fmtPrice(ep)} • PnL: ${String.format(Locale.US, "%+.2f%%", pnl)}",
                                    fontSize = 10.sp,
                                    color = if (pnl >= 0) LG else LR
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
