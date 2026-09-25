package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.OpenPosition
import com.pumpwatch.app.data.TraderProfile
import com.pumpwatch.app.data.TraderStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TGreen = Color(0xFF00E676)
private val TRed = Color(0xFFFF5252)
private val TBlue = Color(0xFF40C4FF)
private val TGold = Color(0xFFFFC107)
private val TGray = Color(0xFF8B949E)
private val TCard = Color(0xFF1A2230)

private fun fmtAmt(d: Double): String = String.format(Locale.US, "%.4f", d)
private fun fmtUsd(d: Double): String = String.format(Locale.US, "$%,.2f", d)

@Composable
fun TraderLeaderboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var traders by remember { mutableStateOf<List<TraderProfile>>(emptyList()) }
    var addr by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var mint by remember { mutableStateOf("") }
    var entryText by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var positions by remember { mutableStateOf<Map<String, OpenPosition?>>(emptyMap()) }
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)

    fun refresh() { traders = TraderStore.ranked(context) }

    LaunchedEffect(Unit) {
        TraderStore.load(context)
        refresh()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🏆 تاپ تریدرها", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TGreen)
                Text(
                    "برترین‌های کشف‌شده توسط PumpWatch (موتور ۶ + ردیابی دستی). این لیست «۵۰ برتر جهانی» نیست؛ فقط تریدرهایی که خود اپ دیده و ثبت کرده است. PnL نمایش‌داده‌شده تقریبی است و ورود آن‌چین را تأیید نمی‌کند.",
                    fontSize = 9.sp, color = TGold, lineHeight = 14.sp
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField(value = addr, onValueChange = { addr = it },
                    placeholder = { Text("آدرس ولت تریدر...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                TextField(value = symbol, onValueChange = { symbol = it },
                    placeholder = { Text("نماد... (CATE)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                TextField(value = mint, onValueChange = { mint = it },
                    placeholder = { Text("کانترکت توکن (mint) — اختیاری، برای بررسی موقعیت", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                TextField(value = entryText, onValueChange = { entryText = it },
                    placeholder = { Text("قیمت ورود تقریبی ($) — اختیاری، برای PnL", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Button(onClick = {
                    val a = addr.trim()
                    val s = symbol.trim().uppercase(Locale.US)
                    if (a.length < 20) { info = "❌ آدرس معتبر نیست"; return@Button }
                    if (s.isEmpty()) { info = "❌ نماد را وارد کن"; return@Button }
                    TraderStore.upsert(context, TraderProfile(
                        addr = a,
                        chain = TraderStore.detectChain(a),
                        symbol = s,
                        note = "ردیابی دستی",
                        addedAtMs = System.currentTimeMillis(),
                        lastSeenTs = 0L,
                        lastTxId = "",
                        boughtUsd = 0.0, maxSingleUsd = 0.0, soldUsd = 0.0,
                        txCount = 0, multiplier = 0.0, pumpsCount = 0,
                        alertOn = true,
                        mint = mint.trim().takeIf { it.isNotEmpty() },
                        entryPrice = entryText.trim().toDoubleOrNull()
                    ))
                    addr = ""; symbol = ""; mint = ""; entryText = ""
                    info = "✅ تریدر به لیست ردیابی اضافه شد"
                    refresh()
                }, colors = ButtonDefaults.buttonColors(containerColor = TBlue),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("➕ افزودن به ردیابی", fontSize = 12.sp)
                }
                if (info.isNotEmpty()) Text(info, fontSize = 10.sp, color = TGreen)
            }
        }

        if (traders.isEmpty()) {
            Text("😴 هنوز تریدری ردیابی نمی‌شود. از موتور ۶ یک نهنگ را ❤️ بزن یا دستی اضافه کن.", fontSize = 11.sp, color = TGray)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(traders) { idx, t ->
                val pos = positions[t.addr]
                Card(colors = CardDefaults.cardColors(containerColor = TCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${idx + 1}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TGold)
                            Spacer(Modifier.width(6.dp))
                            Text("🐋 ${TraderStore.short(t.addr)}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = TGreen)
                            Spacer(Modifier.weight(1f))
                            Text("${TraderStore.scoreOf(t)} امتیاز", fontSize = 12.sp, fontWeight = FontWeight.Black, color = TBlue)
                        }
                        Text("${t.symbol} • ${t.note} • زنجیره: ${t.chain}", fontSize = 9.sp, color = TGray)
                        Text(
                            "💵 خرید: ${fmtUsd(t.boughtUsd)} • بزرگ‌ترین: ${fmtUsd(t.maxSingleUsd)} • فروش: ${fmtUsd(t.soldUsd)} • ${t.txCount} tx",
                            fontSize = 9.sp, color = TGray
                        )
                        Text(
                            "🔁 ${t.pumpsCount} پامپ • ضریب ${String.format(Locale.US, "%.1f", t.multiplier)}x • آخرین فعالیت: ${if (t.lastSeenTs > 0) sdf.format(Date(t.lastSeenTs)) else "بررسی نشده"}",
                            fontSize = 9.sp, color = TGray
                        )

                        //  v0.1: وضعیت موقعیت
                        when {
                            t.mint.isNullOrBlank() ->
                                Text("📊 موقعیت: بررسی نمی‌شود (کانترکت توکن ثبت نشده)", fontSize = 9.sp, color = TGray)
                            pos == null ->
                                Text("📊 موقعیت: بررسی نشده", fontSize = 9.sp, color = TGray)
                            pos.amount > 0.0 ->
                                Text("🟢 موقعیت باز: ${fmtAmt(pos.amount)} ${t.symbol} ≈ ${fmtUsd(pos.valueUsd)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TGreen)
                            else ->
                                Text("🔴 خارج شده / بدون موقعیت", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TRed)
                        }

                        // 🚀 v0.1: PnL تقریبی با disclaimer
                        val entry = t.entryPrice
                        if (pos != null && pos.amount > 0.0 && pos.priceUsd != null && entry != null && entry > 0.0) {
                            val pnl = (pos.priceUsd - entry) / entry * 100.0
                            Text(
                                "سود/زیان تقریبی: ${String.format(Locale.US, "%+.1f", pnl)}% (تقریبی — ورود آن‌چین تأیید نشده)",
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (pnl >= 0) TGreen else TRed
                            )
                        } else if (pos != null && pos.amount > 0.0) {
                            Text("سود/زیان: نامعلوم (قیمت ورود ثبت نشده)", fontSize = 9.sp, color = TGray)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = t.alertOn, onClick = {
                                TraderStore.setAlert(context, t.addr, !t.alertOn)
                                refresh()
                            }, label = { Text("هشدار", fontSize = 9.sp) })
                            Button(onClick = {
                                scope.launch {
                                    val p = TraderStore.checkOpenPosition(context, t)
                                    positions = positions + (t.addr to p)
                                    info = when {
                                        p == null -> "⚠️ بررسی موقعیت فقط برای Solana + با کانترکت است"
                                        p.amount > 0.0 -> "🟢 موقعیت باز پیدا شد"
                                        else -> "🔴 موقعیتی ندارد"
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = TCard), shape = RoundedCornerShape(6.dp)) {
                                Text("📊 موقعیت", fontSize = 9.sp)
                            }
                            Button(onClick = {
                                scope.launch {
                                    val act = TraderStore.checkNewActivity(context, t)
                                    if (act != null) {
                                        TraderStore.markSeen(context, t.addr, act.ts, act.txId)
                                        info = "🔔 فعالیت جدید برای ${TraderStore.short(t.addr)} ثبت شد"
                                    } else {
                                        if (t.chain == "solana") {
                                            TraderStore.markSeen(context, t.addr, maxOf(t.lastSeenTs, System.currentTimeMillis()), t.lastTxId)
                                            info = "✅ بررسی شد؛ فعالیت جدیدی نبود"
                                        } else {
                                            info = "⚠️ بررسی خودکار فعلاً فقط برای Solana است"
                                        }
                                    }
                                    refresh()
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = TCard), shape = RoundedCornerShape(6.dp)) {
                                Text("🔍 فعالیت", fontSize = 9.sp)
                            }
                            Button(onClick = {
                                try {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("addr", t.addr))
                                    info = "📋 آدرس کپی شد"
                                } catch (_: Exception) { }
                            }, colors = ButtonDefaults.buttonColors(containerColor = TCard), shape = RoundedCornerShape(6.dp)) {
                                Text("📋", fontSize = 9.sp)
                            }
                            Button(onClick = {
                                TraderStore.remove(context, t.addr)
                                refresh()
                            }, colors = ButtonDefaults.buttonColors(containerColor = TCard), shape = RoundedCornerShape(6.dp)) {
                                Text("🗑", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
