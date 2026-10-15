package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.OpenPosition
import com.pumpwatch.app.data.TraderProfile
import com.pumpwatch.app.data.TraderStore
import com.pumpwatch.app.engine.Validators
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private fun fmtAmt(d: Double): String = String.format(Locale.US, "%.4f", d)
private fun fmtUsd(d: Double): String = String.format(Locale.US, "$%,.2f", d)

internal sealed class LeaderboardLoadState {
    object Loading : LeaderboardLoadState()
    data class Loaded(val traders: List<TraderProfile>) : LeaderboardLoadState()
    object Unreadable : LeaderboardLoadState()
}

@Composable
fun TraderLeaderboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadState by remember { mutableStateOf<LeaderboardLoadState>(LeaderboardLoadState.Loading) }
    var addr by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("") }
    var mint by remember { mutableStateOf("") }
    var entryText by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    var positions by remember { mutableStateOf<Map<String, OpenPosition?>>(emptyMap()) }
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)

    fun refresh() {
        loadState = LeaderboardLoadState.Loading
        try {
            TraderStore.load(context)
            val traders = TraderStore.ranked(context)
            loadState = LeaderboardLoadState.Loaded(traders)
        } catch (e: Exception) {
            loadState = LeaderboardLoadState.Unreadable
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorialPageHeader(
            eyebrow = "TRACKER · LOCAL RANKING",
            title = "رتبه‌بندی تریدرهای ردیابی‌شده",
            subtitle = "داده‌های محلی شما (نه ۵۰ برتر جهانی). عملکرد گذشته تضمین‌کنندهٔ آینده نیست. کپی‌تریدینگ ریسک خاص خود را دارد.",
            accent = PumpWatchPalette.TabLeaderboard
        )

        GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("⚠️ سلب مسئولیت ریسک کپی‌تریدینگ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PumpWatchPalette.Warning)
                Text(
                    "• این لیست «۵۰ برتر جهانی» نیست؛ فقط تریدرهایی که شما در این اپ ردیابی کرده‌اید.\n" +
                    "• PnL نمایش‌داده‌شده تقریبی است و ورود واقعی آن‌چین را تأیید نمی‌کند.\n" +
                    "• رتبه‌های کوتاه‌مدت می‌توانند ناشی از شانس باشند؛ بازهٔ زمانی طولانی‌تر قابل اعتمادتر است.\n" +
                    "• کپی‌تریدینگ ریسک‌های خاص (اسلیپیج، تأخیر، تفاوت حجم) را دارد.\n" +
                    "• عملکرد گذشته هرگز تضمین‌کنندهٔ عملکرد آینده نیست.",
                    fontSize = 9.sp,
                    color = PumpWatchPalette.Muted,
                    lineHeight = 14.sp
                )
            }
        }

        GlassCard(accent = PumpWatchPalette.TabLeaderboard, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("➕ افزودن تریدر به لیست ردیابی", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PumpWatchPalette.Text)
                val tfColors = TextFieldDefaults.colors(
                    focusedContainerColor = PumpWatchPalette.GlassDeep,
                    unfocusedContainerColor = PumpWatchPalette.GlassDeep,
                    focusedTextColor = PumpWatchPalette.Text,
                    unfocusedTextColor = PumpWatchPalette.Text,
                    cursorColor = PumpWatchPalette.TabLeaderboard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
                TextField(
                    value = addr, onValueChange = { addr = it },
                    placeholder = { Text("آدرس ولت تریدر...", fontSize = 11.sp, color = PumpWatchPalette.MutedDim) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true, colors = tfColors
                )
                TextField(
                    value = symbol, onValueChange = { symbol = it },
                    placeholder = { Text("نماد... (CATE)", fontSize = 11.sp, color = PumpWatchPalette.MutedDim) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true, colors = tfColors
                )
                TextField(
                    value = mint, onValueChange = { mint = it },
                    placeholder = { Text("کانترکت توکن (mint) — اختیاری", fontSize = 10.sp, color = PumpWatchPalette.MutedDim) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true, colors = tfColors
                )
                TextField(
                    value = entryText, onValueChange = { entryText = it },
                    placeholder = { Text("قیمت ورود تقریبی ($) — اختیاری", fontSize = 10.sp, color = PumpWatchPalette.MutedDim) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true, colors = tfColors
                )
                Button(
                    onClick = {
                        val a = addr.trim(); val s = symbol.trim().uppercase(Locale.US)
                        val addrErr = Validators.validateWalletAddress(a)
                        if (addrErr != null) { info = "❌ $addrErr"; return@Button }
                        val symErr = Validators.validateSymbol(s)
                        if (symErr != null) { info = "❌ $symErr"; return@Button }
                        val mintTrim = mint.trim()
                        if (mintTrim.isNotEmpty()) {
                            val mErr = Validators.validateContractMint(mintTrim, Validators.detectChainFamily(a))
                            if (mErr != null) { info = "❌ $mErr"; return@Button }
                        }
                        val entryTrim = entryText.trim()
                        if (entryTrim.isNotEmpty()) {
                            val eErr = Validators.validatePositivePrice(entryTrim)
                            if (eErr != null) { info = "❌ $eErr"; return@Button }
                        }
                        try {
                            TraderStore.upsert(
                                context, TraderProfile(
                                    addr = a, chain = TraderStore.detectChain(a), symbol = s, note = "ردیابی دستی",
                                    addedAtMs = System.currentTimeMillis(), lastSeenTs = 0L, lastTxId = "",
                                    boughtUsd = 0.0, maxSingleUsd = 0.0, soldUsd = 0.0, txCount = 0,
                                    multiplier = 0.0, pumpsCount = 0, alertOn = true,
                                    mint = mintTrim.takeIf { it.isNotEmpty() },
                                    entryPrice = entryTrim.toDoubleOrNull()
                                )
                            )
                            addr = ""; symbol = ""; mint = ""; entryText = ""
                            info = "✅ تریدر به لیست ردیابی اضافه شد"
                            refresh()
                        } catch (e: Exception) {
                            info = "⚠️ خطا در افزودن: ${e.message}"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.TabLeaderboard, contentColor = PumpWatchPalette.Ink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("➕ افزودن به ردیابی", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                if (info.isNotEmpty()) Text(info, fontSize = 10.sp, color = if (info.startsWith("✅")) PumpWatchPalette.Positive else if (info.startsWith("❌") || info.startsWith("⚠️")) PumpWatchPalette.Negative else PumpWatchPalette.Muted)
            }
        }

        when (val state = loadState) {
            is LeaderboardLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PumpWatchPalette.TabLeaderboard)
                }
            }
            is LeaderboardLoadState.Unreadable -> {
                GlassCard(accent = PumpWatchPalette.Negative, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️ خطا در بارگذاری داده‌ها", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                        Text("داده‌های محلی شما قابل خواندن نیستند. لطفاً اپ را مجدداً باز کنید.", fontSize = 10.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                        Button(
                            onClick = { refresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("تلاش مجدد", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            is LeaderboardLoadState.Loaded -> {
                val traders = state.traders
                if (traders.isEmpty()) {
                    GlassCard(accent = PumpWatchPalette.Muted, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📭", fontSize = 32.sp)
                            Text("هنوز تریدری در لیست ردیابی شما نیست", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Text)
                            Text("از بخش ردیابی نهنگ‌ها، یک فعالیت را به لیست اضافه کنید یا از فرم بالا استفاده کنید.", fontSize = 9.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    Surface(color = PumpWatchPalette.Info.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "📊 ${traders.size} تریدر در لیست شما • رتبه‌بندی بر اساس امتیاز محلی (حجم خرید/فروش، تعداد پامپ، ضریب)\n" +
                            "⏱️ بازهٔ زمانی: از زمان افزودن به لیست تا الان • PnL: تقریبی و بدون تأیید آن‌چین",
                            fontSize = 9.sp,
                            color = PumpWatchPalette.Info,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(traders) { idx, t ->
                            val pos = positions[t.addr]
                            GlassCard(accent = PumpWatchPalette.TabLeaderboard, modifier = Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${idx + 1}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = PumpWatchPalette.Warning)
                                        Spacer(Modifier.width(6.dp))
                                        Text(TraderStore.short(t.addr), fontWeight = FontWeight.Black, fontSize = 13.sp, color = PumpWatchPalette.Info)
                                        Spacer(Modifier.weight(1f))
                                        Text("امتیاز: ${TraderStore.scoreOf(t)}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = PumpWatchPalette.Info)
                                    }
                                    Text("${t.symbol} • ${t.note} • زنجیره: ${t.chain}", fontSize = 9.sp, color = PumpWatchPalette.Muted)

                                    Text("حجم خرید: ${fmtUsd(t.boughtUsd)} • بزرگ‌ترین تراکنش: ${fmtUsd(t.maxSingleUsd)} • حجم فروش: ${fmtUsd(t.soldUsd)} • ${t.txCount} تراکنش", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                    Text("تعداد پامپ: ${t.pumpsCount} • ضریب: ${String.format(Locale.US, "%.1f", t.multiplier)}x • آخرین فعالیت: ${if (t.lastSeenTs > 0) sdf.format(Date(t.lastSeenTs)) else "بررسی نشده"}", fontSize = 9.sp, color = PumpWatchPalette.Muted)

                                    when {
                                        t.mint.isNullOrBlank() -> Text("📊 موقعیت: بررسی نمی‌شود (کانترکت توکن ثبت نشده)", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                        pos == null -> Text("📊 موقعیت: بررسی نشده", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                        pos.amount > 0.0 -> Text("🟢 موقعیت باز: ${fmtAmt(pos.amount)} ${t.symbol} ≈ ${fmtUsd(pos.valueUsd)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Positive)
                                        else -> Text("🔴 بدون موقعیت باز", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                                    }

                                    val entry = t.entryPrice
                                    if (pos != null && pos.amount > 0.0 && pos.priceUsd != null && entry != null && entry > 0.0) {
                                        val pnl = (pos.priceUsd - entry) / entry * 100.0
                                        Text(
                                            "تغییر قیمت تقریبی: ${String.format(Locale.US, "%+.1f", pnl)}% (⚠️ تقریبی — ورود آن‌چین تأیید نشده)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pnl >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative
                                        )
                                    } else if (pos != null && pos.amount > 0.0) {
                                        Text("تغییر قیمت: نامعلوم (قیمت ورود ثبت نشده)", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                                    }

                                    // 🚀 P1-3: دکمه‌های اکشن با contentDescription دقیق و اندازهٔ لمسی استاندارد ۴۸dp
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilterChip(
                                            selected = t.alertOn,
                                            onClick = {
                                                try {
                                                    TraderStore.setAlert(context, t.addr, !t.alertOn)
                                                    refresh()
                                                } catch (_: Exception) { }
                                            },
                                            label = { Text("هشدار", fontSize = 10.sp) },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PumpWatchPalette.TabLeaderboard,
                                                selectedLabelColor = PumpWatchPalette.Ink,
                                                containerColor = PumpWatchPalette.Glass,
                                                labelColor = PumpWatchPalette.Muted
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val p = TraderStore.checkOpenPosition(context, t)
                                                        positions = positions + (t.addr to p)
                                                        info = when {
                                                            p == null -> "⚠️ بررسی موقعیت فقط برای Solana + با کانترکت است"
                                                            p.amount > 0.0 -> "🟢 موقعیت باز پیدا شد"
                                                            else -> "🔴 موقعیتی ندارد"
                                                        }
                                                    } catch (e: Exception) {
                                                        info = "⚠️ خطا: ${e.message}"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) { Text("📊 موقعیت", fontSize = 10.sp, fontWeight = FontWeight.Medium) }
                                        
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        val act = TraderStore.checkNewActivity(context, t)
                                                        if (act != null) {
                                                            TraderStore.markSeen(context, t.addr, act.ts, act.txId)
                                                            info = "🔔 فعالیت جدید برای ${TraderStore.short(t.addr)} ثبت شد"
                                                        } else {
                                                            if (t.chain == "solana") {
                                                                TraderStore.markSeen(context, t.addr, max(t.lastSeenTs, System.currentTimeMillis()), t.lastTxId)
                                                                info = "✅ بررسی شد؛ فعالیت جدیدی نبود"
                                                            } else {
                                                                info = "⚠️ بررسی خودکار فعلاً فقط برای Solana است"
                                                            }
                                                        }
                                                        refresh()
                                                    } catch (e: Exception) {
                                                        info = "⚠️ خطا: ${e.message}"
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Glass, contentColor = PumpWatchPalette.Text),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) { Text("🔍 فعالیت", fontSize = 10.sp, fontWeight = FontWeight.Medium) }
                                        
                                        IconButton(
                                            onClick = {
                                                try {
                                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                        .setPrimaryClip(ClipData.newPlainText("addr", t.addr))
                                                    info = "📋 آدرس کپی شد"
                                                } catch (_: Exception) { info = "⚠️ کپی ناموفق" }
                                            },
                                            modifier = Modifier.size(48.dp).semantics { contentDescription = "کپی آدرس کیف پول ${t.symbol}" }
                                        ) {
                                            Text("📋", fontSize = 18.sp)
                                        }
                                        
                                        IconButton(
                                            onClick = {
                                                try {
                                                    TraderStore.remove(context, t.addr)
                                                    refresh()
                                                    info = "🗑 تریدر حذف شد"
                                                } catch (e: Exception) {
                                                    info = "⚠️ خطا در حذف: ${e.message}"
                                                }
                                            },
                                            modifier = Modifier.size(48.dp).semantics { contentDescription = "حذف تریدر ${t.symbol} از لیست ردیابی" }
                                        ) {
                                            Text("🗑", fontSize = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
