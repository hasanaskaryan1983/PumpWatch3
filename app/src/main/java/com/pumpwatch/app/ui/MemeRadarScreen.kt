package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.data.DataEnvelope
import com.pumpwatch.app.data.DataStatus
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import com.pumpwatch.app.ui.components.GlassCard
import kotlinx.coroutines.launch
import java.util.Locale

private val MEME_CHAINS = listOf(
    "solana" to "Solana 🟣", "bsc" to "BSC 🟡", "base" to "Base 🔵", "ethereum" to "Ethereum ⚪",
    "ton" to "TON 🔵", "robinhood" to "Robinhood 🪽", "avalanche" to "Avalanche 🔺", "sei" to "SEI 🌊", "arc" to "Arc 🟣"
)

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun ageText(h: Double): String = when {
    h >= 9999 -> "—"; h < 1 -> "زیر ۱ ساعت"; h < 48 -> "${h.toInt()} ساعت"; else -> "${(h / 24).toInt()} روز"
}

// 🚀 P0 Tab 06: خنثی‌سازی ادبیات (حذف pump/dump/whale)
private fun memeVerdict(ch1: Double, r1: Double): Pair<String, androidx.compose.ui.graphics.Color> = when {
    ch1 <= -5 -> "⬇️ کاهش شدید قیمت در ۱ ساعت گذشته" to PumpWatchPalette.Negative
    ch1 >= 5 && r1 >= 0.6 -> "⬆️ افزایش شدید قیمت همراه با فشار خرید" to PumpWatchPalette.Positive
    ch1 >= 2 && r1 >= 0.55 -> "📈 رشد قیمت با نسبت خرید بالاتر" to PumpWatchPalette.Warning
    r1 >= 0.6 -> "👀 نسبت حجم خرید به فروش بالا" to PumpWatchPalette.Info
    else -> "➡️ نوسان محدود در بازه فعلی" to PumpWatchPalette.Muted
}

// 🚀 P0 Tab 06: توصیف عینی وضعیت نقدینگی به‌جای "تله"
private fun liquidityStatus(liq: Double): Pair<String, androidx.compose.ui.graphics.Color> = when {
    liq < 10_000 -> "نقدینگی بسیار پایین (ریسک اسلیپیج بالا در فروش)" to PumpWatchPalette.Negative
    liq < 50_000 -> "نقدینگی محدود (تأثیرگذار بر سفارش‌های متوسط)" to PumpWatchPalette.Warning
    else -> "نقدینگی نسبتاً مناسب" to PumpWatchPalette.Positive
}

private fun chainEmoji(chain: String): String {
    val label = MEME_CHAINS.firstOrNull { it.first == chain }?.second ?: return "⛓️"
    val emoji = label.substringAfterLast(' ', "").trim()
    return if (emoji.isEmpty()) "⛓️" else emoji
}

@Composable
private fun MemeContractRow(ctx: Context, contract: String?) {
    if (contract.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("⛓️ ارز بومی شبکه / بدون آدرس قرارداد", fontSize = 9.sp, color = PumpWatchPalette.Muted)
        }
        return
    }
    val copied = remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("📋 قرارداد: ", fontSize = 9.sp, color = PumpWatchPalette.Muted)
        Text(
            if (contract.length > 22) "${contract.take(10)}...${contract.takeLast(8)}" else contract,
            fontSize = 9.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { 
                try { 
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("contract", contract))
                    copied.value = true 
                } catch (_: Exception) { } 
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (copied.value) PumpWatchPalette.Positive else PumpWatchPalette.Warning, contentColor = PumpWatchPalette.Ink),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) { Text(if (copied.value) "✅" else "📋 کپی", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun MemeRadarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 🚀 P0 Tab 06: استفاده از DataEnvelope برای مدیریت دقیق وضعیت‌ها
    var envelope by remember { mutableStateOf<DataEnvelope<List<MemeSignal>>?>(null) }
    var loading by remember { mutableStateOf(MemeHub.loading && MemeHub.signals.isEmpty()) }
    var sniperMode by remember { mutableStateOf(false) }

    fun scan(force: Boolean) {
        scope.launch {
            loading = true
            try {
                val signals = MemeHub.scan(force = force, sniper = sniperMode)
                val lastUpdateMs = MemeHub.lastUpdateMs
                
                // 🚀 P0: تعیین وضعیت دقیق بر اساس نتیجه اسکن
                val status = when {
                    signals.isEmpty() && MemeHub.lastFailed -> DataStatus.PARTIAL // برخی شبکه‌ها شکست خوردند
                    signals.isEmpty() -> DataStatus.EMPTY_VALID // اسکن موفق بود، اما موردی یافت نشد
                    else -> DataStatus.SUCCESS
                }

                envelope = DataEnvelope(
                    data = signals,
                    source = "geckoterminal+goplus",
                    observedAtMs = lastUpdateMs,
                    status = status
                )
            } catch (e: Exception) {
                envelope = DataEnvelope(
                    data = null,
                    source = "geckoterminal+goplus",
                    observedAtMs = System.currentTimeMillis(),
                    status = DataStatus.FAILURE
                )
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { 
        if (MemeHub.signals.isEmpty()) scan(false) 
    }
    
    val displayItems = envelope?.data ?: emptyList()
    val lastUpdate = envelope?.observedAtMs ?: 0L

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 🚀 P0 Tab 06: صداقت محصول و سلب مسئولیت شفاف
            EditorialPageHeader(
                eyebrow = "DEX · HIGH VOLATILITY",
                title = "رادار میم‌کوین",
                subtitle = "تحلیل حجم و نقدینگی توکن‌های جدید. این داده‌ها پژوهشی است و به معنای تایید امنیت یا توصیه خرید نیست.",
                accent = PumpWatchPalette.TabMeme
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎯 فیلتر توکن‌های تازه", fontSize = 11.sp, color = if (sniperMode) PumpWatchPalette.Positive else PumpWatchPalette.Muted, fontWeight = FontWeight.Bold)
                Switch(
                    checked = sniperMode,
                    onCheckedChange = { sniperMode = it; scan(true) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PumpWatchPalette.Ink,
                        checkedTrackColor = PumpWatchPalette.TabMeme,
                        uncheckedThumbColor = PumpWatchPalette.Muted,
                        uncheckedTrackColor = PumpWatchPalette.Glass
                    )
                )
                Spacer(Modifier.weight(1f))
                HubRefreshRow(lastUpdate, loading, PumpWatchPalette.TabMeme) { scan(true) }
            }
        }

        // 🚀 P0 Tab 06: نمایش وضعیت‌های Sealed Outcomes
        when {
            loading && displayItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PumpWatchPalette.TabMeme)
                        Text("در حال اسکن شبکه‌ها و بررسی امنیتی...", fontSize = 11.sp, color = PumpWatchPalette.Muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            
            envelope?.status == DataStatus.FAILURE -> {
                GlassCard(accent = PumpWatchPalette.Negative, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⚠️ خطا در دریافت داده", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PumpWatchPalette.Negative)
                        Text("اتصال به منابع داده برقرار نشد. لطفاً اینترنت خود را بررسی کنید.", fontSize = 11.sp, color = PumpWatchPalette.Muted, textAlign = TextAlign.Center)
                        Button(
                            onClick = { scan(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = PumpWatchPalette.Negative.copy(alpha = 0.2f), contentColor = PumpWatchPalette.Text),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("تلاش مجدد", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            
            envelope?.status == DataStatus.EMPTY_VALID -> {
                GlassCard(accent = PumpWatchPalette.Muted, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "😴 در بازه زمانی فعلی، توکنی با معیارهای انتخاب‌شده یافت نشد. بازار در حالت آرام است.",
                        color = PumpWatchPalette.Muted, modifier = Modifier.padding(16.dp), fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                }
            }
            
            envelope?.status == DataStatus.PARTIAL -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    GlassCard(accent = PumpWatchPalette.Warning, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "⚠️ داده‌های ناقص (Partial): برخی شبکه‌ها یا منابع امنیتی در دسترس نبودند. نتایج زیر فقط بر اساس داده‌های دریافت‌شده است.",
                            color = PumpWatchPalette.Warning, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp)
                        )
                    }
                    if (displayItems.isEmpty()) {
                        Text("هیچ توکنی با دادهٔ کامل یافت نشد.", color = PumpWatchPalette.Muted, modifier = Modifier.padding(24.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
                    } else {
                        MemeList(displayItems, lastUpdate, context, sniperMode)
                    }
                }
            }
            
            envelope?.status == DataStatus.SUCCESS -> {
                MemeList(displayItems, lastUpdate, context, sniperMode)
            }
        }
    }
}

@Composable
private fun MemeList(items: List<MemeSignal>, lastUpdate: Long, context: Context, sniperMode: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sniperMode) {
                    val policy = MemeRadar.lastPolicy
                    GlassCard(accent = PumpWatchPalette.TabMeme, modifier = Modifier.fillMaxWidth()) {
                        Text("🎯 فقط توکن‌های زیر ${policy.maxAgeHours.toInt()} ساعت با Rug Score ≥ ${policy.minRugScore} و نقدینگی ≥ ${compact(policy.minLiquidityUsd)} نمایش داده می‌شوند.", fontSize = 10.sp, color = PumpWatchPalette.Positive, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("شناسایی بر اساس تغییرات حجم و قیمت در بازه‌های کوتاه", fontSize = 11.sp, color = PumpWatchPalette.Muted)
                }
                Text("🛡️ بررسی امنیتی GoPlus فعال است. تعداد چک‌ها در شبکه‌های مختلف متفاوت است.", fontSize = 10.sp, color = PumpWatchPalette.Positive)
                Text("❓ اگر وضعیت امنیتی UNKNOWN باشد، به معنای عدم تایید ایمنی است.", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                Text("📊 ضربه روی هر کارت = مشاهده نمودار استخر در GeckoTerminal", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                if (lastUpdate > 0) Text("🕒 آخرین دریافت: ${hubAgeText(lastUpdate)}", fontSize = 9.sp, color = PumpWatchPalette.MutedDim)
            }
        }

        itemsIndexed(items) { _, m ->
            val (verdict, vColor) = memeVerdict(m.changeH1, m.buyRatio)
            val poolUrl = if (m.poolAddress.isNullOrEmpty()) null else "https://www.geckoterminal.com/${m.chain}/pools/${m.poolAddress}"

            GlassCard(
                accent = PumpWatchPalette.TabMeme,
                modifier = Modifier.fillMaxWidth().then(
                    if (poolUrl != null) Modifier.clickable { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))) } catch (_: Exception) { } } else Modifier
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chainEmoji(m.chain), fontSize = 18.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(m.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp, color = PumpWatchPalette.Text)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.US, "$%.8f", m.price), fontSize = 10.sp, color = PumpWatchPalette.Muted)
                        Spacer(Modifier.width(6.dp))
                        Text(String.format(Locale.US, "%+.1f%%", m.changeH1), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (m.changeH1 >= 0) PumpWatchPalette.Positive else PumpWatchPalette.Negative)
                    }

                    Text(verdict, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = vColor)

                    val securityText = when (m.securityStatus) { 
                        "READY" -> "GoPlus: تکمیل ✅"
                        "EMPTY" -> "GoPlus: داده‌ای یافت نشد ❓"
                        "FAILED" -> "GoPlus: خطا در بررسی ⚠️"
                        else -> "وضعیت نامشخص ⚪" 
                    }
                    Text("📡 منبع: GeckoTerminal • 🛡️ امنیت: $securityText", fontSize = 8.sp, color = PumpWatchPalette.Muted)

                    val rug = m.rugScore
                    val rugColor = when { rug == null -> PumpWatchPalette.Muted; rug >= 80 -> PumpWatchPalette.Positive; rug >= 60 -> PumpWatchPalette.Warning; else -> PumpWatchPalette.Negative }
                    val rugEmoji = when { rug == null -> "❓"; rug >= 80 -> "✅"; rug >= 60 -> "⚠️"; else -> "🚨" }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val rugLabel = if (rug == null) "$rugEmoji Rug Safety: UNKNOWN" else "$rugEmoji Rug Safety: $rug/100"
                        Text(rugLabel, fontSize = 11.sp, color = rugColor, fontWeight = FontWeight.Bold)
                        Text("Score: ${m.score}/100", fontSize = 10.sp, color = PumpWatchPalette.Info, fontWeight = FontWeight.Bold)
                    }

                    if (rug == null) {
                        val warningText = when (m.securityStatus) {
                            "EMPTY" -> "❓ GoPlus این توکن را شناسایی نکرد — ایمن فرض نمی‌شود."
                            "FAILED" -> "❓ بررسی امنیتی انجام نشد — ایمن فرض نمی‌شود."
                            else -> "❓ وضعیت امنیتی نامشخص — ایمن فرض نمی‌شود."
                        }
                        Text(warningText, fontSize = 9.sp, color = PumpWatchPalette.Muted, fontWeight = FontWeight.Bold)
                    }

                    if (m.rugWarnings.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            m.rugWarnings.take(3).forEach { warning -> Text(warning, fontSize = 9.sp, color = if (rug == null) PumpWatchPalette.Muted else PumpWatchPalette.Negative) }
                            if (m.rugWarnings.size > 3) Text("... و ${m.rugWarnings.size - 3} هشدار دیگر", fontSize = 8.sp, color = PumpWatchPalette.Muted)
                        }
                    }

                    val buyRatioPct = String.format(Locale.US, "%.0f", m.buyRatio * 100)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📊 نسبت خرید: $buyRatioPct٪", fontSize = 10.sp, color = if (m.buyRatio >= 0.55) PumpWatchPalette.Positive else PumpWatchPalette.Negative, fontWeight = FontWeight.Bold)
                        Text("حجم ۱س: ${compact(m.volumeH1)}", fontSize = 10.sp, color = PumpWatchPalette.Muted)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("💧 نقدینگی: ${compact(m.liquidity)}", fontSize = 9.sp, color = PumpWatchPalette.Info)
                        Text("FDV: ${compact(m.fdv)}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                        Text("سن: ${ageText(m.ageHours)}", fontSize = 9.sp, color = PumpWatchPalette.Muted)
                    }

                    val (exitText, exitColor) = liquidityStatus(m.liquidity)
                    Text(exitText, fontSize = 9.sp, color = exitColor, fontWeight = FontWeight.Bold)

                    val change24 = String.format(Locale.US, "%+.1f%%", m.changeH24)
                    val vol24 = compact(m.volumeH24)
                    Text("تغییر ۲۴س: $change24 • حجم ۲۴س: $vol24", fontSize = 9.sp, color = PumpWatchPalette.Muted)

                    MemeContractRow(context, m.contract)
                }
            }
        }
        item { 
            Surface(color = PumpWatchPalette.Negative.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠️ هشدار ریسک: میم‌کوین‌ها دارای نوسان بسیار بالا و ریسک از دست رفتن کامل سرمایه هستند. این خروجی صرفاً تحلیل داده است و توصیه مالی نیست.", 
                    fontSize = 10.sp, color = PumpWatchPalette.Negative, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center
                )
            }
        }
    }
}
