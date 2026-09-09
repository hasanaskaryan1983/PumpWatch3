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
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.QuickScanner
import com.pumpwatch.app.engine.SignalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            val ev = SignalLogger.evaluate(ctx, l)
            SignalLogger.save(ctx, ev)
            logs = ev
            stats = Triple(
                ev.count { it.status == "WIN" },
                ev.count { it.status == "LOSS" },
                ev.count { it.status == "EXP" }
            )
        }
    }

    // 🔄 آپدیت خودکار قیمت‌ها هر ۴ ثانیه برای سیگنال‌های باز
    LaunchedEffect(Unit) {
        loadLogs()
        lastScores = prefs.getString("last_scores", "") ?: ""
        
        while (true) {
            delay(45_000L) // ۴۵ ثانیه
            val currentLogs = SignalLogger.load(ctx)
            val hasOpen = currentLogs.any { it.status == "OPEN" || it.status == "EXP" }
            if (hasOpen) {
                val updated = SignalLogger.updateOpenSignals(ctx, currentLogs)
                logs = updated
                SignalLogger.save(ctx, updated)
                // آپدیت آمار
                stats = Triple(
                    updated.count { it.status == "WIN" },
                    updated.count { it.status == "LOSS" },
                    updated.count { it.status == "EXP" }
                )
            }
        }
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
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
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
            Text("🎯 استراتژی: استاپ دنباله‌رو (Trailing) + هدف شناور. تا وقتی استاپ نخوره، پوزیشن باز می‌مونه!", fontSize = 9.sp, color = LBlue, fontWeight = FontWeight.Bold)
            
            val t = st.first + st.second
            if (t > 0) {
                val wr = st.first * 100.0 / t
                Text("وین‌ریت زنده: ${String.format(Locale.US, "%.1f%%", wr)}", color = if (wr >= 55) LG else LR, fontWeight = FontWeight.Bold)
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                Text("$modeEmoji ${s.symbol} • ${if(isLong)"🟢 Long" else "🔴 Short"}", fontWeight = FontWeight.Bold)
                                Text("${statusEmoji(s.status)} ${s.status}", color = when(s.status) {
                                    "WIN" -> LG; "LOSS" -> LR; "EXP" -> LY; "OPEN" -> LBlue; else -> LGr
                                })
                            }
                            
                            // نمایش قیمت زنده
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("ورود: $${fmtPrice(s.entry)}", fontSize = 11.sp, color = LGr)
                                if (s.currentPrice != null && s.status == "OPEN") {
                                    Text("الان: $${fmtPrice(s.currentPrice)}", fontSize = 12.sp, color = LBlue, fontWeight = FontWeight.Bold)
                                }
                            }

                            // نمایش استاپ و هدف شناور
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
