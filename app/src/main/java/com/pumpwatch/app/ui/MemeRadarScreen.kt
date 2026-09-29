package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.pumpwatch.app.engine.MemeRadar
import com.pumpwatch.app.engine.MemeSignal
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MGreen = Color(0xFF00E676)
private val MRed = Color(0xFFFF5252)
private val MBlue = Color(0xFF40C4FF)
private val MGold = Color(0xFFFFC107)
private val MGray = Color(0xFF8B949E)
private val MCardA = Color(0xFF1A2230)
private val MCardB = Color(0xFF141B25)
private val MUnknown = Color(0xFF23272E)

private val MEME_CHAINS = listOf(
    "solana" to "Solana 🟣",
    "bsc" to "BSC 🟡",
    "base" to "Base 🔵",
    "ethereum" to "Ethereum ⚪",
    "ton" to "TON 🔵",
    "robinhood" to "Robinhood 🪽",
    "avalanche" to "Avalanche 🔺",
    "sei" to "SEI 🌊",
    "arc" to "Arc 🟣"
)

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun ageText(h: Double): String = when {
    h >= 9999 -> "—"
    h < 1 -> "زیر ۱ ساعت"
    h < 48 -> "${h.toInt()} ساعت"
    else -> "${(h / 24).toInt()} روز"
}

private fun memeVerdict(ch1: Double, r1: Double): Pair<String, Color> = when {
    ch1 <= -5 -> "🩸 دامپ شده — خروج قبل از بدتر شدن" to MRed
    ch1 >= 5 && r1 >= 0.6 -> "🚀 پامپ شروع شده — نهنگ‌ها می‌خرن" to MGreen
    ch1 >= 2 && r1 >= 0.55 -> "⏳ قبل از پامپ — در حال جمع‌کردن" to MGold
    r1 >= 0.6 -> "👀 فشار خرید بالا — زیر نظر بگیر" to MBlue
    else -> "😴 فعلاً حرکت خاصی نداره" to MGray
}

private fun exitFeasibility(liq: Double): Pair<String, Color> = when {
    liq < 10_000 -> "🔴 تله نقدینگی (فروش > ۵۰$ = اسلیپیج شدید)" to MRed
    liq < 50_000 -> "🟡 خروج با احتیاط (فروش > ۱K$ تاثیرگذار است)" to MGold
    else -> "🟢 خروج آسان (نقدینگی کافی برای فروش متوسط)" to MGreen
}

private fun chainEmoji(chain: String): String {
    val label = MEME_CHAINS.firstOrNull { it.first == chain }?.second ?: return "⛓️"
    val emoji = label.substringAfterLast(' ', "").trim()
    return if (emoji.isEmpty()) "⛓️" else emoji
}

@Composable
private fun ContractRow(ctx: Context, contract: String?) {
    if (contract.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("⛓️ بومی / بدون کانترکت", fontSize = 9.sp, color = MGray)
        }
        return
    }
    val copied = remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("📋 کانترکت: ", fontSize = 9.sp, color = MGray)
        Text(
            if (contract.length > 22) "${contract.take(10)}...${contract.takeLast(8)}" else contract,
            fontSize = 9.sp, color = MBlue, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                try {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("contract", contract))
                    copied.value = true
                } catch (_: Exception) { }
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (copied.value) MGreen else MGold),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) { Text(if (copied.value) "✅" else "📋 کپی", fontSize = 9.sp, color = Color.Black) }
    }
}

@Composable
fun MemeRadarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MemeSignal>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf("") }
    var sniperMode by remember { mutableStateOf(false) }

    fun scan() {
        scope.launch {
            loading = true
            error = null
            try {
                val signals = MemeRadar.scan { _, _ -> }
                items = signals
                if (signals.isEmpty()) {
                    error = if (MemeRadar.lastScanFailed) {
                        "⚠️ اتصال به سرورهای رادار برقرار نشد\nاینترنت/فیلترشکن رو چک کن و دوباره اسکن کن"
                    } else {
                        "😴 فعلاً میم‌کوین مستعدی پیدا نشد — بعداً سر بزن"
                    }
                }
                lastUpdate = "بروزرسانی: " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            } catch (e: Exception) {
                error = "⚠️ خطا در اسکن: ${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { scan() }

    val displayItems = if (sniperMode) {
        items.filter { 
            it.ageHours < 24.0 && 
            it.rugScore != null && it.rugScore >= 70 && 
            it.liquidity >= 10_000.0 
        }
    } else {
        items
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("🐸 رادار میم‌کوین", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯 حالت اسنایپر", fontSize = 11.sp, color = if (sniperMode) MGreen else MGray, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = sniperMode,
                        onCheckedChange = { sniperMode = it },
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            TextButton(onClick = { scan() }, enabled = !loading) {
                Text(if (loading) "در حال اسکن..." else "اسکن 🔄")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sniperMode) {
                        Surface(color = MGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("🎯 فقط توکن‌های زیر ۲۴ ساعت با Rug Score ≥ ۷۰ و نقدینگی ≥ ۱۰K$ نمایش داده می‌شوند.", 
                                fontSize = 10.sp, color = MGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                        }
                    } else {
                        Text("شناسایی قبل از پامپ • خروج قبل از دامپ", fontSize = 11.sp, color = MGray)
                    }
                    Text("🛡️ Rug Safety Check فعال — هر توکن ۱۲ چک امنیتی می‌شود", fontSize = 10.sp, color = MGreen)
                    Text("❓ اگر دادهٔ امنیتی موجود نباشد: برچسب UNKNOWN — هرگز safe", fontSize = 10.sp, color = MGray)
                    Text("📊 ضربه روی هر کارت = نمودار کامل استخر در GeckoTerminal", fontSize = 10.sp, color = MGray)
                    Text(lastUpdate, fontSize = 9.sp, color = MGray)
                }
            }

            if (loading && displayItems.isEmpty()) {
                item { Text("⏳ در حال اسکن شبکه‌ها + Rug Safety Check...", fontSize = 12.sp, color = MGray) }
            } else if (error != null && displayItems.isEmpty()) {
                item { Text(error ?: "", fontSize = 12.sp, color = MGold, textAlign = TextAlign.Center) }
            } else if (sniperMode && displayItems.isEmpty() && items.isNotEmpty()) {
                item { 
                    Text("😴 در حالت اسنایپر، توکن امن و تازه‌ای یافت نشد. فیلترها را غیرفعال کنید یا بعداً سر بزنید.", 
                        fontSize = 12.sp, color = MGold, textAlign = TextAlign.Center) 
                }
            } else {
                itemsIndexed(displayItems) { i, m ->
                    val (verdict, vColor) = memeVerdict(m.changeH1, m.buyRatio)
                    val poolUrl = if (m.poolAddress.isNullOrEmpty()) null
                    else "https://www.geckoterminal.com/${m.chain}/pools/${m.poolAddress}"

                    val rug = m.rugScore
                    val cardColor = when {
                        rug == null -> MUnknown
                        rug >= 80 -> if (i % 2 == 0) MCardA else MCardB
                        rug >= 60 -> Color(0xFF2A2520)
                        else -> Color(0xFF3A2020)
                    }

                    Surface(
                        color = cardColor,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().then(
                            if (poolUrl != null) Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            } else Modifier
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chainEmoji(m.chain), fontSize = 18.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(m.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                Text(String.format(Locale.US, "$%.8f", m.price), fontSize = 10.sp, color = MGray)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    String.format(Locale.US, "%+.1f%%", m.changeH1),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (m.changeH1 >= 0) MGreen else MRed
                                )
                            }

                            Text(verdict, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = vColor)

                            val securityText = when (m.securityStatus) {
                                "READY" -> "GoPlus ✅"
                                "EMPTY" -> "GoPlus: ناشناخته ❓"
                                "FAILED" -> "GoPlus: خطا ⚠️"
                                else -> "نامشخص ⚪"
                            }
                            Text("📡 منبع: GeckoTerminal • 🛡️ امنیت: $securityText", fontSize = 8.sp, color = MGray)

                            val rugColor = when {
                                rug == null -> MGray
                                rug >= 80 -> MGreen
                                rug >= 60 -> MGold
                                else -> MRed
                            }
                            val rugEmoji = when {
                                rug == null -> "❓"
                                rug >= 80 -> "✅"
                                rug >= 60 -> "⚠️"
                                else -> "🚨"
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val rugLabel = if (rug == null) "$rugEmoji Rug Safety: UNKNOWN" else "$rugEmoji Rug Safety: $rug/100"
                                Text(rugLabel, fontSize = 11.sp, color = rugColor, fontWeight = FontWeight.Bold)
                                Text("Score: ${m.score}/100", fontSize = 10.sp, color = MBlue, fontWeight = FontWeight.Bold)
                            }

                            if (rug == null) {
                                val warningText = when (m.securityStatus) {
                                    "EMPTY" -> "❓ GoPlus این توکن را نمی‌شناسد — به‌عنوان safe در نظر گرفته نشده"
                                    "FAILED" -> "❓ بررسی امنیتی انجام نشد — به‌عنوان safe در نظر گرفته نشده"
                                    else -> "❓ وضعیت امنیتی نامشخص — به‌عنوان safe در نظر گرفته نشده"
                                }
                                Text(warningText, fontSize = 9.sp, color = MGray, fontWeight = FontWeight.Bold)
                            }

                            if (m.rugWarnings.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    m.rugWarnings.take(3).forEach { warning ->
                                        Text(warning, fontSize = 9.sp, color = if (rug == null) MGray else MRed)
                                    }
                                    if (m.rugWarnings.size > 3) {
                                        val remaining = m.rugWarnings.size - 3
                                        Text("... و $remaining هشدار دیگر", fontSize = 8.sp, color = MGray)
                                    }
                                }
                            }

                            val buyRatioPct = String.format(Locale.US, "%.0f", m.buyRatio * 100)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🐳 فشار خرید: $buyRatioPct٪", fontSize = 10.sp, color = if (m.buyRatio >= 0.55) MGreen else MRed, fontWeight = FontWeight.Bold)
                                Text("حجم ۱س: ${compact(m.volumeH1)}", fontSize = 10.sp, color = MGray)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("💧 نقدینگی: ${compact(m.liquidity)}", fontSize = 9.sp, color = MBlue)
                                Text("FDV: ${compact(m.fdv)}", fontSize = 9.sp, color = MGray)
                                Text("سن: ${ageText(m.ageHours)}", fontSize = 9.sp, color = MGray)
                            }

                            val (exitText, exitColor) = exitFeasibility(m.liquidity)
                            Text(exitText, fontSize = 9.sp, color = exitColor, fontWeight = FontWeight.Bold)

                            val change24 = String.format(Locale.US, "%+.1f%%", m.changeH24)
                            val vol24 = compact(m.volumeH1 * 24)
                            Text("تغییر ۲۴س: $change24 • حجم ۲۴س: $vol24", fontSize = 9.sp, color = MGray)

                            ContractRow(context, m.contract)
                        }
                    }
                }
                item {
                    Text(
                        "⚠️ میم‌کوین‌ها = ریسک بسیار بالا! فقط با پولی که توان از دست دادنش رو داری وارد شو. این توصیه مالی نیست.",
                        fontSize = 10.sp, color = MGold
                    )
                }
            }
        }
    }
}
