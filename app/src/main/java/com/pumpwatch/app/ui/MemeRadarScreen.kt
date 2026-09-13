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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    "ton" to "TON 🔵"
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

@Composable
private fun ContractRow(ctx: Context, contract: String?) {
    if (contract.isNullOrEmpty()) return
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

    fun scan() {
        scope.launch {
            loading = true
            error = null
            try {
                val signals = MemeRadar.scan { progress, msg ->
                    // می‌توانید progress bar اضافه کنید
                }

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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🐸 رادار میم‌کوین", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
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
                    Text("شناسایی قبل از پامپ • خروج قبل از دامپ", fontSize = 11.sp, color = MGray)
                    Text("🛡️ Rug Safety Check فعال — هر توکن ۱۲ چک امنیتی می‌شود", fontSize = 10.sp, color = MGreen)
                    Text("❓ اگر دادهٔ امنیتی موجود نباشد: برچسب UNKNOWN — هرگز safe", fontSize = 10.sp, color = MGray)
                    Text("📊 ضربه روی هر کارت = نمودار کامل استخر در GeckoTerminal", fontSize = 10.sp, color = MGray)
                    Text("شبکه‌ها: ${MEME_CHAINS.joinToString(" • ") { it.second }}", fontSize = 9.sp, color = MBlue)
                    Text(lastUpdate, fontSize = 9.sp, color = MGray)
                }
            }

            if (loading && items.isEmpty()) {
                item { Text("⏳ در حال اسکن ${MEME_CHAINS.size} شبکه + Rug Safety Check...", fontSize = 12.sp, color = MGray) }
            } else if (error != null && items.isEmpty()) {
                item { Text(error ?: "", fontSize = 12.sp, color = MGold, textAlign = TextAlign.Center) }
            } else {
                itemsIndexed(items) { i, m ->
                    val (verdict, vColor) = memeVerdict(m.changeH1, m.buyRatio)
                    val chainName = MEME_CHAINS.firstOrNull { it.first == m.chain }?.second ?: m.chain
                    val poolUrl = "https://www.geckoterminal.com/${m.chain}/pools/${m.name.split("/").lastOrNull()?.lowercase() ?: ""}"

                    // P0-1: val محلی برای smart cast روی Int?
                    val rug = m.rugScore

                    // رنگ کارت بر اساس Rug Score — UNKNOWN = خاکستری (هرگز سبز)
                    val cardColor = when {
                        rug == null -> MUnknown
                        rug >= 80 -> if (i % 2 == 0) MCardA else MCardB
                        rug >= 60 -> Color(0xFF2A2520)
                        else -> Color(0xFF3A2020)
                    }

                    Surface(
                        color = cardColor,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))
                            context.startActivity(intent)
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(chainName.take(2), fontSize = 18.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(m.symbol, fontWeight = FontWeight.Black, fontSize = 14.sp)
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

                            // 🛡️ نمایش Rug Safety Score — سه‌حالته (P0-1)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                                Text(
                                    if (rug == null) "$rugEmoji Rug Safety: UNKNOWN"
                                    else "$rugEmoji Rug Safety: $rug/100",
                                    fontSize = 11.sp, color = rugColor, fontWeight = FontWeight.Bold
                                )
                                Text("Score: ${m.score}/100", fontSize = 10.sp, color = MBlue, fontWeight = FontWeight.Bold)
                            }

                            // P0-1: پیام جدا برای EMPTY و FAILED — هیچ‌وقت safe نیست
                            if (rug == null) {
                                Text(
                                    when (m.securityStatus) {
                                        "EMPTY" -> "❓ GoPlus این توکن را نمی‌شناسد — به‌عنوان safe در نظر گرفته نشده"
                                        "FAILED" -> "❓ بررسی امنیتی انجام نشد (خطای اتصال) — به‌عنوان safe در نظر گرفته نشده"
                                        else -> "❓ وضعیت امنیتی نامشخص — به‌عنوان safe در نظر گرفته نشده"
                                    },
                                    fontSize = 9.sp, color = MGray, fontWeight = FontWeight.Bold
                                )
                            }

                            // 🆕 نمایش هشدارهای Rug Safety (اگر وجود دارد)
                            if (m.rugWarnings.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    m.rugWarnings.take(3).forEach { warning ->
                                        Text(warning, fontSize = 9.sp, color = if (rug == null) MGray else MRed)
                                    }
                                    if (m.rugWarnings.size > 3) {
                                        Text("... و ${m.rugWarnings.size - 3} هشدار دیگر", fontSize = 8.sp, color = MGray)
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🐳 فشار خرید: ${String.format(Locale.US, "%.0f", m.buyRatio * 100)}٪", fontSize = 10.sp, color = if (m.buyRatio >= 0.55) MGreen else MRed, fontWeight = FontWeight.Bold)
                                Text("حجم ۱س: ${compact(m.volumeH1)}", fontSize = 10.sp, color = MGray)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("💧 نقدینگی: ${compact(m.liquidity)}", fontSize = 9.sp, color = MBlue)
                                Text("FDV: ${compact(m.fdv)}", fontSize = 9.sp, color = MGray)
                                Text("سن: ${ageText(m.ageHours)}", fontSize = 9.sp, color = MGray)
                            }

                            Text(
                                "تغییر ۲۴س: ${String.format(Locale.US, "%+.1f%%", m.changeH24)} • حجم ۲۴س: ${compact(m.volumeH1 * 24)}",
                                fontSize = 9.sp, color = MGray
                            )

                            ContractRow(context, null)  // contract address در MemeSignal نیست
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
