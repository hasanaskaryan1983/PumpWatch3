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
    "WIN" -> "âœ…"
    "LOSS" -> "â‌Œ"
    "EXP" -> "âŒ›"
    else -> "âڈ³"
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
            // ط§ط±ط²غŒط§ط¨غŒ ط¯ظ‚غŒظ‚ ط¨ط§ ع©ظ†ط¯ظ„ ط³ط§ط¹طھغŒ (ط§ظˆظ„غŒظ† ط¨ط±ط®ظˆط±ط¯ ط¨ظ‡ ظ‡ط¯ظپ غŒط§ ط§ط³طھط§ظ¾)
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
            Text("ًں““ ظ„ط§ع¯ ط³غŒع¯ظ†ط§ظ„â€Œظ‡ط§غŒ ط²ظ†ط¯ظ‡", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = {
                    if (!isScanning) {
                        isScanning = true
                        scope.launch {
                            val mode = prefs.getString("mode", "SPOT") ?: "SPOT"
                            val report = withContext(Dispatchers.IO) {
                                QuickScanner.scan(ctx, QuickScanner.TOP_SYMBOLS.take(15), mode)
                            }
                            lastScores = "ط³غŒع¯ظ†ط§ظ„ ط¬ط¯غŒط¯: ${report.signalCount}\n" + report.lines.joinToString("\n")
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
                Text(if (isScanning) "ط¯ط± ط­ط§ظ„ ط§ط³ع©ظ†..." else "ًں”چ ط§ط³ع©ظ† ظپظˆط±غŒ", fontSize = 12.sp)
            }
        }

        stats?.let { st ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                Text("âœ… ط¨ط±ط¯: ${st.first}", color = LG, fontWeight = FontWeight.Bold)
                Text("â‌Œ ط¨ط§ط®طھ: ${st.second}", color = LR, fontWeight = FontWeight.Bold)
                Text("âŒ› ظ…ظ†ظ‚ط¶غŒ: ${st.third}", color = LY, fontWeight = FontWeight.Bold)
            }
            Text(
                "ط¨ط±ط¯ = ط±ط³غŒط¯ ط¨ظ‡ ظ‡ط¯ظپ | ط¨ط§ط®طھ = ط®ظˆط±ط¯ ط¨ظ‡ ط§ط³طھط§ظ¾ | ظ…ظ†ظ‚ط¶غŒ = ط¨ط¯ظˆظ† ظ†طھغŒط¬ظ‡ ط¨ط¹ط¯ ط§ط² غ²غ´ ط³ط§ط¹طھ",
                fontSize = 9.sp,
                color = LGr
            )
            Text(
                "ًںژ¯ ط§ط³طھط±ط§طھعکغŒ: ط®ط±غŒط¯ ط§ظپطھ ط´ط¯غŒط¯ (RSI2â‰¤غ±غµ) ط¯ط§ط®ظ„ ط±ظˆظ†ط¯ طµط¹ظˆط¯غŒ â€¢ ط§ط³طھط§ظ¾ ط¯ظˆط± غ².غ´أ—ATR + ظ‡ط¯ظپ ظ†ط²ط¯غŒع© غ°.غ¹أ—ATR â†’ ط§ط­طھظ…ط§ظ„ ط¨ط±ط¯ ط¨ط§ظ„ط§",
                fontSize = 9.sp,
                color = LBlue
            )
            val t = st.first + st.second
            if (t > 0) {
                val wr = st.first * 100.0 / t
                Text(
                    "ظˆغŒظ†â€Œط±غŒطھ ط²ظ†ط¯ظ‡: ${String.format(Locale.US, "%.1f%%", wr)}",
                    color = if (wr >= 55) LG else LR,
                    fontWeight = FontWeight.Bold
                )
            }
            // ظ…غŒط§ظ†ع¯غŒظ† ط³ظˆط¯/ط¶ط±ط± ظ…ط¹ط§ظ…ظ„ط§طھ طھط³ظˆغŒظ‡â€Œط´ط¯ظ‡
            val closed = logs.filter { (it.status == "WIN" || it.status == "LOSS") && it.exitPrice != null }
            if (closed.isNotEmpty()) {
                val avgPnl = closed.map { s ->
                    if (s.side == "BUY") (s.exitPrice!! - s.entry) / s.entry * 100
                    else (s.entry - s.exitPrice!!) / s.entry * 100
                }.average()
                Text(
                    "ظ…غŒط§ظ†ع¯غŒظ† ط³ظˆط¯/ط¶ط±ط± ظ‡ط± ظ…ط¹ط§ظ…ظ„ظ‡: ${String.format(Locale.US, "%+.2f%%", avgPnl)}",
                    fontSize = 11.sp,
                    color = if (avgPnl >= 0) LG else LR,
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
                label = { Text("ظ‡ظ…ظ‡ (${logs.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "SPOT",
                onClick = { selectedFilter = "SPOT" },
                label = { Text("ًںڈ¦ ط§ط³ظ¾ط§طھ (${logs.count { it.mode != "FUT" }})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "FUT",
                onClick = { selectedFilter = "FUT" },
                label = { Text("âڑ، ظپغŒظˆع†ط±ط² (${logs.count { it.mode == "FUT" }})", fontSize = 11.sp) }
            )
        }

        if (lastScores.isNotEmpty()) {
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(
                    if (showDetails) "ًں”¬ ط¬ط²ط¦غŒط§طھ ط§ط³ع©ظ† (ظ¾ظ†ظ‡ط§ظ† ع©ظ†)" else "ًں”¬ ط¬ط²ط¦غŒط§طھ ط§ط³ع©ظ† (ظ†ظ…ط§غŒط´)",
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
                if (logs.isEmpty()) "ظ‡ظ†ظˆط² ط³غŒع¯ظ†ط§ظ„غŒ ط«ط¨طھ ظ†ط´ط¯ظ‡ â€” ط¯ع©ظ…ظ‡ آ«ط§ط³ع©ظ† ظپظˆط±غŒآ» ط±ظˆ ط¨ط²ظ†"
                else "ط³غŒع¯ظ†ط§ظ„غŒ ط¨ط§ ط§غŒظ† ظپغŒظ„طھط± ظ¾غŒط¯ط§ ظ†ط´ط¯ â€” آ«ط§ط³ع©ظ† ظپظˆط±غŒآ» ط¨ط²ظ† طھط§ ط¯ط± ط§غŒظ† ط­ط§ظ„طھ ط§ط³ع©ظ† ط¨ط´ظ‡",
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
                                val modeEmoji = if (s.mode == "FUT") "âڑ،" else "ًںڈ¦"
                                val modeText = if (s.mode == "FUT") "ظپغŒظˆع†ط±ط²" else "ط§ط³ظ¾ط§طھ"
                                Text(
                                    "$modeEmoji ${s.symbol} â€¢ ${if(s.side=="BUY")"ًںں¢ ط®ط±غŒط¯" else "ًں”´ ظپط±ظˆط´"} â€¢ $modeText",
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
                                "ط§ظ…طھغŒط§ط²: ${s.score}/100 â€¢ ظˆط±ظˆط¯: $${fmtPrice(s.entry)}",
                                fontSize = 11.sp, color = LGr
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("ط§ط³طھط§ظ¾: $${fmtPrice(s.stop)}", fontSize = 10.sp, color = LR)
                                Text("ظ‡ط¯ظپ: $${fmtPrice(s.target)}", fontSize = 10.sp, color = LG)
                            }
                            s.exitPrice?.let { ep ->
                                val pnl = if (s.side == "BUY") (ep - s.entry) / s.entry * 100
                                else (s.entry - ep) / s.entry * 100
                                Text(
                                    "ط®ط±ظˆط¬: $${fmtPrice(ep)} â€¢ PnL: ${String.format(Locale.US, "%+.2f%%", pnl)}",
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
