package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.BinanceClient
import com.pumpwatch.app.data.BinanceFutures
import com.pumpwatch.app.data.RadarBinance
import com.pumpwatch.app.engine.LoggedSignal
import com.pumpwatch.app.engine.SignalLogger
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

private val LG = Color(0xFF00E676)
private val LR = Color(0xFFFF5252)
private val LY = Color(0xFFFFC107)
private val LGr = Color(0xFF8B949E)
private val LB = Color(0xFF40C4FF)
private val LC = Color(0xFF1A2230)
private val STABLES = listOf("USDT", "USDC", "BUSD", "DAI", "FDUSD", "TUSD", "USDP")

private fun ema(d: List<Double>, p: Int): Double {
    if (d.size < p) return d.lastOrNull() ?: 0.0
    val k = 2.0 / (p + 1)
    var e = d.take(p).average()
    for (i in p until d.size) e = d[i] * k + e * (1 - k)
    return e
}

private fun rsi(d: List<Double>): Double {
    if (d.size < 15) return 50.0
    var g = 0.0; var l = 0.0
    for (i in 1..14) { val x = d[i] - d[i-1]; if (x > 0) g += x else l -= x }
    var ag = g/14; var al = l/14
    for (i in 15 until d.size) {
        val x = d[i] - d[i-1]
        ag = (ag*13 + maxOf(x,0.0))/14
        al = (al*13 + maxOf(-x,0.0))/14
    }
    return if (al == 0.0) 100.0 else 100.0 - 100.0/(1.0 + ag/al)
}

private fun macdUp(d: List<Double>): Boolean {
    if (d.size < 35) return false
    val p = d.dropLast(1)
    return (ema(d,12)-ema(d,26)) > (ema(p,12)-ema(p,26))
}

private fun boll(d: List<Double>): Pair<Double,Double> {
    if (d.size < 20) return 0.0 to 0.0
    val w = d.takeLast(20); val m = w.average()
    val sd = sqrt(w.map{(it-m)*(it-m)}.average())
    return (m+2*sd) to (m-2*sd)
}

private fun layer(c: List<Double>, v: List<Double>): Int {
    if (c.size < 40) return 0
    val p = c.last(); val e20 = ema(c,20); val e50 = ema(c,50)
    val em = if (p>e20 && e20>e50) 25 else if (p<e20 && e20<e50) -25 else 0
    val r = rsi(c); val rs = if (r<=35) 20 else if (r>=65) -20 else 0
    val mc = if (macdUp(c)) 25 else -25
    val (bu,bl) = boll(c); val vl = if (v.size>15) {
        val lv = v.last(); val av = v.dropLast(1).takeLast(14).average()
        val bd = if (c.last()>=c.dropLast(1).last()) 15 else -15
        if (av>0 && lv>=1.5*av) bd else 0
    } else 0
    return (em+rs+mc+vl).coerceIn(-100,100)
}

private fun statusEmoji(s: String) = when(s) {
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
    var stats by remember { mutableStateOf<Triple<Int,Int,Int>?>(null) }
    
    LaunchedEffect(Unit) {
        scope.launch {
            val l = SignalLogger.load(ctx)
            val prices = try {
                RadarBinance.api.tickers().associate { t ->
                    val sym = t.symbol?.replace("USDT","") ?: ""
                    val price = t.lastPrice?.toDoubleOrNull() ?: 0.0
                    sym to price
                }
            } catch (_: Exception) { mapOf<String,Double>() }
            val ev = SignalLogger.evaluate(l, prices)
            SignalLogger.save(ctx, ev)
            logs = ev
            val w = ev.count{it.status=="WIN"}
            val l2 = ev.count{it.status=="LOSS"}
            val e = ev.count{it.status=="EXP"}
            stats = Triple(w,l2,e)
        }
    }
    
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("📓 لاگ سیگنال‌های زنده", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        stats?.let { (w,l,e) ->
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                Text("✅ برد: $w", color = LG, fontWeight = FontWeight.Bold)
                Text("❌ باخت: $l", color = LR, fontWeight = FontWeight.Bold)
                Text("⌛ منقضی: $e", color = LY, fontWeight = FontWeight.Bold)
            }
            val t = w+l
            if (t > 0) {
                val wr = w*100.0/t
                Text(" وین‌ریت: ${String.format(Locale.US,"%.1f%%",wr)}", 
                     color = if (wr>=55) LG else LR, fontWeight = FontWeight.Bold)
            }
        }
        if (logs.isEmpty()) {
            Text("هنوز سیگنالی ثبت نشده — صبر کن تا اپ سیگنال‌های جدید بسازه", 
                 color = LGr, modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs) { s ->
                    Surface(LC, RoundedCornerShape(12.dp), Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("${s.symbol} • ${if(s.side=="BUY")"🟢 خرید" else "🔴 فروش"}", 
                                     fontWeight = FontWeight.Bold)
                                Text("${statusEmoji(s.status)} ${s.status}", 
                                     color = when(s.status) {
                                         "WIN" -> LG; "LOSS" -> LR; "EXP" -> LY; else -> LGr
                                     })
                            }
                            Text("امتیاز: ${s.score}/100 • ورود: $${s.entry}", fontSize = 11.sp, color = LGr)
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("استاپ: $${s.stop}", fontSize = 10.sp, color = LR)
                                Text("هدف: $${s.target}", fontSize = 10.sp, color = LG)
                            }
                            s.exitPrice?.let { ep ->
                                Text("خروج: $${ep} • PnL: ${String.format(Locale.US,"%.2f%%", 
                                     if(s.side=="BUY") (ep-s.entry)/s.entry*100 
                                     else (s.entry-ep)/s.entry*100)}%", 
                                     fontSize = 10.sp, color = if(s.status=="WIN") LG else LR)
                            }
                        }
                    }
                }
            }
        }
    }
}
