package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.Blockscout
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.solanaRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

private val XGreen = Color(0xFF00E676)
private val XRed = Color(0xFFFF5252)
private val XBlue = Color(0xFF40C4FF)
private val XGold = Color(0xFFFFC107)
private val XGray = Color(0xFF8B949E)
private val XCard = Color(0xFF1A2230)

private data class ChainLite(val label: String, val host: String)

private val EVM_HOSTS = listOf(
    ChainLite("Ethereum ⚪", "https://eth.blockscout.com/"),
    ChainLite("Base 🔵", "https://base.blockscout.com/"),
    ChainLite("Arbitrum 🔷", "https://arbitrum.blockscout.com/"),
    ChainLite("Optimism 🔴", "https://optimism.blockscout.com/"),
    ChainLite("Polygon 🟣", "https://polygon.blockscout.com/"),
    ChainLite("Gnosis 🦉", "https://gnosis.blockscout.com/"),
    ChainLite("Robinhood 🪽", "https://robinhoodchain.blockscout.com/")
)

private data class HistTx(
    val ts: Long,
    val dateText: String,
    val chain: String,
    var symbol: String,
    val amount: Double,
    val incoming: Boolean,
    val other: String,
    var priceUsd: Double? = null
)

private fun kindOf(a: String): String = when {
    a.startsWith("0x") && a.length == 42 -> "evm"
    a.startsWith("0x") && a.length >= 64 -> "sui"
    a.startsWith("EQ") || a.startsWith("UQ") || a.startsWith("kQ") || a.startsWith("0:") -> "ton"
    else -> "solana"
}

@Composable
fun WalletHistorySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addrIn by remember { mutableStateOf("") }
    var filterSym by remember { mutableStateOf("") }
    var depth by remember { mutableStateOf(75) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<HistTx>>(emptyList()) }
    var summary by remember { mutableStateOf("") }

    fun load() {
        val addr = addrIn.trim()
        if (addr.isEmpty()) { err = "❌ آدرس رو وارد کن"; return }
        scope.launch {
            loading = true; err = null; list = emptyList()
            summary = "🔍 در حال خواندن $depth تراکنش آخر از همه شبکه‌ها..."
            try {
                val out = withContext(Dispatchers.IO) {
                    val res = mutableListOf<HistTx>()
                    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
                    val mintByShort = mutableMapOf<String, String>()
                    val fs = filterSym.trim()

                    when (kindOf(addr)) {
                        "ton" -> summary = "⚠️ تاریخچه TON به‌زودی اضافه می‌شه"
                        "sui" -> summary = "⚠️ تاریخچه SUI به‌زودی اضافه می‌شه"
                        "evm" -> {
                            for (h in EVM_HOSTS) {
                                try {
                                    val txs = Blockscout.api(h.host).tokenTx("account", "tokentx", addr, "desc").result ?: continue
                                    txs.take(20).forEach { t ->
                                        val sym = t.tokenSymbol ?: "?"
                                        if (fs.isNotEmpty() && !sym.equals(fs, true)) return@forEach
                                        val ts = (t.timeStamp?.toLongOrNull() ?: return@forEach) * 1000
                                        val dec = t.tokenDecimal?.toDoubleOrNull() ?: 18.0
                                        val amt = (t.value?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                                        val inc = (t.to ?: "").equals(addr, true)
                                        res.add(HistTx(ts, sdf.format(Date(ts)), h.label, sym, amt, inc, if (inc) t.from ?: "" else t.to ?: ""))
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        else -> {
                            if (addr.length !in 32..44) {
                                summary = "❌ طول آدرس سولانا باید ۳۲ تا ۴۴ کاراکتر باشه — دوباره کامل کپی کن"
                                return@withContext res
                            }
                            var filterMint: String? = null
                            if (fs.isNotEmpty()) {
                                try {
                                    val fp = GeckoTerminal.api.searchPools(fs).data
                                        ?.filter { it.attributes != null && it.relationships?.network?.data?.id == "solana" }
                                        ?.maxByOrNull { it.attributes?.volume?.h24 ?: 0.0 }
                                    filterMint = fp?.relationships?.base_token?.data?.id?.substringAfter('_')
                                } catch (_: Exception) { }
                                if (filterMint == null) { summary = "❌ استخر Solana برای «$fs» پیدا نشد"; return@withContext res }
                            }

                            val sigs = solanaRaw(mapOf(
                                "jsonrpc" to "2.0", "id" to 1,
                                "method" to "getSignaturesForAddress",
                                "params" to listOf(addr, mapOf("limit" to depth))
                            ))
                            if (sigs == null) { summary = "⚠️ اتصال به هر دو سرور Solana ناموفق بود — دوباره تلاش کن"; return@withContext res }
                            val sigArr = sigs.result?.asJsonArray
                            if (sigArr == null) {
                                summary = "⚠️ سرور Solana خطا داد: ${sigs.error?.message ?: "نامشخص"} — آدرس رو چک کن"
                                return@withContext res
                            }
                            if (sigArr.size() == 0) summary = "😴 این کیف هیچ تراکنشی نداره"
                            else for ((si, el) in sigArr.withIndex()) {
                                val obj = el.asJsonObject
                                val sig = obj.get("signature")?.asString ?: continue
                                val bt = obj.get("blockTime")?.asLong ?: 0L
                                try {
                                    val txr = solanaRaw(mapOf(
                                        "jsonrpc" to "2.0", "id" to 1,
                                        "method" to "getTransaction",
                                        "params" to listOf(sig, mapOf("encoding" to "jsonParsed", "maxSupportedTransactionVersion" to 0))
                                    ), preferAlt = si % 2 == 1) ?: continue
                                    val r = txr.result?.asJsonObject ?: continue
                                    val meta = r.getAsJsonObject("meta") ?: continue

                                    val preMap = mutableMapOf<String, MutableMap<String, Double>>()
                                    val postMap = mutableMapOf<String, MutableMap<String, Double>>()
                                    val pre = meta.getAsJsonArray("preTokenBalances")
                                    val post = meta.getAsJsonArray("postTokenBalances")
                                    if (pre != null) for (p in pre) {
                                        val o = p.asJsonObject
                                        val ow = o.get("owner")?.asString ?: continue
                                        val m = o.get("mint")?.asString ?: continue
                                        preMap.getOrPut(ow) { mutableMapOf() }[m] = o.getAsJsonObject("uiTokenAmount")?.get("uiAmountString")?.asString?.toDoubleOrNull() ?: 0.0
                                    }
                                    if (post != null) for (p in post) {
                                        val o = p.asJsonObject
                                        val ow = o.get("owner")?.asString ?: continue
                                        val m = o.get("mint")?.asString ?: continue
                                        postMap.getOrPut(ow) { mutableMapOf() }[m] = o.getAsJsonObject("uiTokenAmount")?.get("uiAmountString")?.asString?.toDoubleOrNull() ?: 0.0
                                    }

                                    val deltas = mutableListOf<Triple<String, String, Double>>()
                                    for (ow in (preMap.keys + postMap.keys).distinct()) {
                                        for (m in ((preMap[ow]?.keys ?: emptySet()) + (postMap[ow]?.keys ?: emptySet())).distinct()) {
                                            val d = (postMap[ow]?.get(m) ?: 0.0) - (preMap[ow]?.get(m) ?: 0.0)
                                            if (abs(d) > 1e-9) deltas.add(Triple(ow, m, d))
                                        }
                                    }

                                    for (d in deltas) {
                                        if (d.first != addr) continue
                                        if (filterMint != null && d.second != filterMint) continue
                                        val cp = deltas.filter { it.second == d.second && it.first != addr && it.third * d.third < 0 }
                                            .maxByOrNull { abs(it.third) }
                                        val short = d.second.take(8)
                                        if (filterMint == null) mintByShort[short] = d.second
                                        res.add(HistTx(bt * 1000, sdf.format(Date(bt * 1000)), "Solana 🟣",
                                            if (filterMint != null) fs.uppercase(Locale.US) else short,
                                            d.third, d.third > 0, cp?.first ?: ""))
                                    }

                                    if (filterMint == null) {
                                        val keysArr = r.getAsJsonObject("transaction")?.getAsJsonObject("message")?.getAsJsonArray("accountKeys")
                                        val preB = meta.getAsJsonArray("preBalances")
                                        val postB = meta.getAsJsonArray("postBalances")
                                        if (keysArr != null && preB != null && postB != null) {
                                            val sols = mutableListOf<Triple<String, Int, Double>>()
                                            for ((i, k) in keysArr.withIndex()) {
                                                if (i >= preB.size() || i >= postB.size()) break
                                                val pk = if (k.isJsonObject) k.asJsonObject.get("pubkey")?.asString else k.asString
                                                sols.add(Triple(pk ?: "", i, (postB.get(i).asLong - preB.get(i).asLong) / 1e9))
                                            }
                                            val mine = sols.firstOrNull { it.first == addr }
                                            if (mine != null && abs(mine.third) > 1e-9) {
                                                val cp = sols.filter { it.first != addr && it.third * mine.third < 0 }.maxByOrNull { abs(it.third) }
                                                res.add(HistTx(bt * 1000, sdf.format(Date(bt * 1000)), "Solana 🟣", "SOL", mine.third, mine.third > 0, cp?.first ?: ""))
                                            }
                                        }
                                    }
                                } catch (_: Exception) { }
                            }

                            for ((short, mint) in mintByShort.entries.take(6)) {
                                try {
                                    val at = GeckoPrice.api.tokenInfo("solana", mint).data?.attributes
                                    val s2 = at?.symbol
                                    val px = at?.price_usd?.toDoubleOrNull() ?: 0.0
                                    res.forEach {
                                        if (it.chain == "Solana 🟣" && it.symbol == short) {
                                            if (!s2.isNullOrEmpty()) it.symbol = s2
                                            if (it.priceUsd == null && px > 0) it.priceUsd = px
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }

                    try {
                        val coins = ApiClient.getTop1000Coins()
                        val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        for (sym in res.map { it.symbol }.distinct().take(6)) {
                            val coin = coins.firstOrNull { it.symbol.equals(sym, true) } ?: continue
                            try {
                                val chart = ApiClient.getCoinChart(coin.id, days = 365)
                                val byDay = chart.prices.associate { p -> sdfDay.format(Date(p[0].toLong())) to p[1] }
                                res.forEach { t ->
                                    if (t.symbol.equals(sym, true)) t.priceUsd = byDay[sdfDay.format(Date(t.ts))]
                                }
                            } catch (_: Exception) { }
                        }
                        val solPx = coins.firstOrNull { it.symbol.equals("SOL", true) }?.current_price ?: 0.0
                        if (solPx > 0) res.forEach { if (it.symbol == "SOL" && it.priceUsd == null) it.priceUsd = solPx }
                    } catch (_: Exception) { }

                    val totalRead = res.size
                    val filtered = res.filter { abs(it.amount) * (it.priceUsd ?: 0.0) >= 10.0 }
                    if (totalRead > 0) summary = "✅ ${filtered.size} تراکنش بالای ۱۰$ (از $totalRead تراکنش خونده‌شده)"
                    filtered.sortedByDescending { it.ts }.take(60)
                }
                list = out
            } catch (t: Throwable) {
                err = "⚠️ خطا: ${t.message}"
            }
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = XCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📜 موتور ۵: تاریخچه تراکنش‌های کیف (همه شبکه‌ها خودکار)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = XBlue)
                Text("فقط تراکنش‌های بالای ۱۰ دلار • طرف مقابل کامل با دکمه کپی • دو سرور RPC یکی‌درمیان", fontSize = 9.sp, color = XGray)
                TextField(value = addrIn, onValueChange = { addrIn = it },
                    placeholder = { Text("آدرس کیف... (Solana یا 0x)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                TextField(value = filterSym, onValueChange = { filterSym = it },
                    placeholder = { Text("فیلتر توکن (اختیاری)... مثلاً USELESS", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(25, 75, 150).forEach { d ->
                        FilterChip(selected = depth == d, onClick = { depth = d }, label = { Text("عمق: $d تراکنش", fontSize = 10.sp) })
                    }
                }
                Button(onClick = { load() }, enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = XBlue),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" 📜 بخون تاریخچه رو", fontSize = 12.sp)
                }
                if (summary.isNotEmpty()) Text(summary, fontSize = 10.sp, color = XGreen)
                if (err != null) Text(err ?: "", fontSize = 10.sp, color = XRed)
            }
        }

        list.forEach { t ->
            Card(colors = CardDefaults.cardColors(containerColor = XCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (t.incoming) "🟢" else "🔴", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${if (t.incoming) "دریافت" else "ارسال"} ${t.symbol}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${t.dateText} • ${t.chain}", fontSize = 9.sp, color = XGray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(String.format(Locale.US, "%s%.4f", if (t.incoming) "+" else "-", t.amount), fontSize = 12.sp, fontWeight = FontWeight.Black,
                                color = if (t.incoming) XGreen else XRed)
                            if (t.priceUsd != null) Text("ارزش اون روز: ${String.format(Locale.US, "$%,.2f", t.amount * (t.priceUsd ?: 0.0))}", fontSize = 9.sp, color = XGold)
                        }
                    }
                    if (t.other.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("طرف مقابل: ${t.other}", fontSize = 8.sp, color = XGold, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                try {
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("addr", t.other))
                                    summary = "📋 آدرس طرف مقابل کپی شد"
                                } catch (_: Exception) { }
                            }, colors = ButtonDefaults.buttonColors(containerColor = XCard), shape = RoundedCornerShape(6.dp)) {
                                Text("📋 کپی", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
