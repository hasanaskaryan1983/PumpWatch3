package com.pumpwatch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.RadarBinance
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.SignalLogger
import kotlinx.coroutines.launch
import java.util.Locale

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LC = Color(0xFF1A2230)

private fun statusEmoji(s: String) = when (s) {
    "WIN" -> "✅"
    "LOSS" -> "❌"
    "EXP" -> "⌛"
    else -> "⏳"
}

@Composable
fun SignalLogScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<LoggedSignal>>(emptyList()) }
    var stats by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }

    LaunchedEffect(Unit) {
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

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("📓 لاگ سیگنال‌های زنده", fontWeight = FontWeight.Bold, fontSize = 18.sp)

        stats?.let { st ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                Text("✅ برد: ${st.first}", color = LG, fontWeight = FontWeight.Bold)
                Text("❌ باخت: ${st.second}", color = LR, fontWeight = FontWeight.Bold)
                Text("⌛ منقضی: ${st.third}", color = LY, fontWeight = FontWeight.Bold)
            }
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

        if (logs.isEmpty()) {
            Text(
                "هنوز سیگنالی ثبت نشده — با اسکن‌های بعدی، سیگنال‌ها اینجا ثبت و ارزیابی می‌شن",
                color = LGr,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs) { s ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(LC, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(
                                    "${s.symbol} • ${if (s.side == "BUY") "🟢 خرید" else "🔴 فروش"}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${statusEmoji(s.status)} ${s.status}",
                                    color = when (s.status) {
                                        "WIN" -> LG
                                        "LOSS" -> LR
                                        "EXP" -> LY
                                        else -> LGr
                                    }
                                )
                            }
                            Text(
                                "امتیاز: ${s.score}/100 • ورود: $${s.entry}",
                                fontSize = 11.sp, color = LGr
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("استاپ: $${s.stop}", fontSize = 10.sp, color = LR)
                                Text("هدف: $${s.target}", fontSize = 10.sp, color = LG)
                            }
                            s.exitPrice?.let { ep ->
                                val pnl = if (s.side == "BUY") (ep - s.entry) / s.entry * 100
                                else (s.entry - ep) / s.entry * 100
                                Text(
                                    "خروج: $${ep} • PnL: ${String.format(Locale.US, "%+.2f%%", pnl)}",
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
