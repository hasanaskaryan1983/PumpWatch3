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
import com.pumpwatch.app.data.GeckoPool
import com.pumpwatch.app.data.GeckoTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val MGreen = Color(0xFF00E676)
private val MRed = Color(0xFFFF5252)
private val MBlue = Color(0xFF40C4FF)
private val MGold = Color(0xFFFFC107)
private val MGray = Color(0xFF8B949E)
private val MCardA = Color(0xFF1A2230)
private val MCardB = Color(0xFF141B25)

private val MEME_CHAINS = listOf(
    "solana" to "Solana 🟣",
    "bsc" to "BSC 🟡",
    "base" to "Base 🔵",
    "eth" to "Ethereum ⚪",
    "ton" to "TON 🔵"
)

private data class MemePick(
    val symbol: String,
    val name: String,
    val chain: String,
    val chainName: String,
    val price: Double,
    val changeH1: Double,
    val changeH24: Double,
    val volH1: Double,
    val volH24: Double,
    val buysH1: Double,
    val sellsH1: Double,
    val liquidity: Double,
    val fdv: Double,
    val ageHours: Double,
    val credScore: Int,
    val poolUrl: String,
    val contract: String? = null
)

private fun compact(v: Double): String = when {
    v >= 1_000_000_000 -> String.format(Locale.US, "$%.2fB", v / 1_000_000_000)
    v >= 1_000_000 -> String.format(Locale.US, "$%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "$%.0fK", v / 1_000)
    else -> String.format(Locale.US, "$%.0f", v)
}

private fun ratio(b: Double, s: Double): Double {
    val t = b + s
    return if (t > 0) b / t else 0.5
}

private fun ageHours(createdAt: String?): Double {
    if (createdAt == null) return 9999.0
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val t = sdf.parse(createdAt) ?: return 9999.0
        (System.currentTimeMillis() - t.time) / 3_600_000.0
    } catch (_: Exception) {
        9999.0
    }
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

private fun toMemePick(p: GeckoPool): MemePick? {
    val a = p.attributes ?: return null
    val price = a.priceUsd?.toDoubleOrNull() ?: return null
    if (price <= 0) return null
    val name = a.name ?: "?"
    val symbol = name.split("/").firstOrNull()?.trim() ?: "?"
    val liq = a.reserveUsd?.toDoubleOrNull() ?: 0.0
    val b1 = a.transactions?.h1?.buys ?: 0.0
    val s1 = a.transactions?.h1?.sells ?: 0.0
    val v1 = a.volume?.h1 ?: 0.0
    val v24 = a.volume?.h24 ?: 0.0
    val fdv = a.fdvUsd ?: 0.0
    val age = ageHours(a.createdAt)
    val ch1 = a.priceChange?.h1 ?: 0.0
    val ch24 = a.priceChange?.h24 ?: 0.0

    var score = 0
    if (liq >= 100_000) score++ else if (liq >= 25_000) score++
    if (v1 >= 50_000) score++
    if (b1 > 0 && s1 > 0) score++
    if (ratio(b1, s1) >= 0.55) score++
    if (age >= 24) score++
    if (fdv in 100_000.0..20_000_000.0) score++

    val network = p.relationships?.network?.data?.id ?: "solana"
    val addr = p.id?.substringAfter('_') ?: ""
    val chainName = MEME_CHAINS.firstOrNull { it.first == network }?.second ?: network
    val contract = p.relationships?.base_token?.data?.id?.substringAfter('_')

    return MemePick(
        symbol = symbol, name = name, chain = network, chainName = chainName,
        price = price, changeH1 = ch1, changeH24 = ch24,
        volH1 = v1, volH24 = v24, buysH1 = b1, sellsH1 = s1,
        liquidity = liq, fdv = fdv, ageHours = age, credScore = score,
        poolUrl = "https://www.geckoterminal.com/$network/pools/$addr",
        contract = contract
    )
}

@Composable
fun MemeRadarScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MemePick>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdate by remember { mutableStateOf("") }

    fun scan() {
        scope.launch {
            loading = true
            error = null
            try {
                val pools = coroutineScope {
                    MEME_CHAINS.map { (chain, _) ->
                        async(Dispatchers.IO) {
                            try { GeckoTerminal.api.trendingPools(chain).data ?: emptyList() }
                            catch (_: Exception) { emptyList<GeckoPool>() }
                        }
                    }.map { it.await() }.flatten()
                }

                val found = pools
                    .mapNotNull { toMemePick(it) }
                    .filter { m ->
                        m.liquidity >= 20_000 &&
                        m.volH1 >= 20_000 &&
                        m.buysH1 > 0 &&
                        m.buysH1 >= m.sellsH1 &&
                        m.fdv in 50_000.0..100_000_000.0 &&
                        m.ageHours >= 1
                    }
                    .distinctBy { it.symbol + it.chain }
                    .sortedByDescending { it.volH1 * ratio(it.buysH1, it.sellsH1) }
                    .take(20)

                items = found
                if (found.isEmpty()) {
                    error = "😴 فعلاً میم‌کوین مستعدی پیدا نشد — بعداً سر بزن"
                }
                lastUpdate = "بروزرسانی: " + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            } catch (_: Exception) {
                error = "⚠️ اتصال به سرورهای رادار برقرار نشد\nاینترنت/فیلترشکن رو چک کن و دوباره اسکن کن"
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
                    Text("📊 ضربه روی هر کارت = نمودار کامل استخر در GeckoTerminal", fontSize = 10.sp, color = MGray)
                    Text("شبکه‌ها: ${MEME_CHAINS.joinToString(" • ") { it.second }}", fontSize = 9.sp, color = MBlue)
                    Text(lastUpdate, fontSize = 9.sp, color = MGray)
                }
            }

            if (loading && items.isEmpty()) {
                item { Text("⏳ در حال اسکن ${MEME_CHAINS.size} شبکه...", fontSize = 12.sp, color = MGray) }
            } else if (error != null && items.isEmpty()) {
                item { Text(error ?: "", fontSize = 12.sp, color = MGold, textAlign = TextAlign.Center) }
            } else {
                itemsIndexed(items) { i, m ->
                    val r1 = ratio(m.buysH1, m.sellsH1)
                    val (verdict, vColor) = memeVerdict(m.changeH1, r1)
                    val poolUrl = m.poolUrl

                    Surface(
                        color = if (i % 2 == 0) MCardA else MCardB,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(poolUrl))
                            context.startActivity(intent)
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.chainName.take(2), fontSize = 18.sp)
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

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🐳 فشار خرید: ${String.format(Locale.US, "%.0f", r1 * 100)}٪", fontSize = 10.sp, color = if (r1 >= 0.55) MGreen else MRed, fontWeight = FontWeight.Bold)
                                Text("حجم ۱س: ${compact(m.volH1)}", fontSize = 10.sp, color = MGray)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("💧 نقدینگی: ${compact(m.liquidity)}", fontSize = 9.sp, color = MBlue)
                                Text("FDV: ${compact(m.fdv)}", fontSize = 9.sp, color = MGray)
                                Text("سن: ${ageText(m.ageHours)}", fontSize = 9.sp, color = MGray)
                                Text("🛡️ ${m.credScore}/7", fontSize = 9.sp, color = if (m.credScore >= 5) MGreen else MGold, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                "تغییر ۲۴س: ${String.format(Locale.US, "%+.1f%%", m.changeH24)} • حجم ۲۴س: ${compact(m.volH24)}",
                                fontSize = 9.sp, color = MGray
                            )

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
