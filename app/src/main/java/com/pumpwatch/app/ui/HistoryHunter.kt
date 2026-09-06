package com.pumpwatch.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.pumpwatch.app.data.GeckoOhlcv
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.SolanaRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HGreen = Color(0xFF00E676)
private val HRed = Color(0xFFFF5252)
private val HBlue = Color(0xFF40C4FF)
private val HGold = Color(0xFFFFC107)
private val HGray = Color(0xFF8B949E)
private val HTeal = Color(0xFF26C6DA)
private val HCard = Color(0xFF1A2230)

private data class HolderSus(
    val owner: String, val amount: Double, val valueUsd: Double,
    val firstBuyText: String, val entryPrice: Double, val mult: Double,
    val txCount: Int, val verdict: String
)

private data class HistoryReport(
    val symbol: String, val chainName: String, val currentPrice: Double,
    val bottomText: String, val holders: List<HolderSus>
)

@Composable
fun HistoryHunterSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sym by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var rep by remember { mutableStateOf<HistoryReport?>(null) }

    fun hunt() {
        val s = sym.trim()
        if (s.isEmpty()) { err = "❌ نماد ارز رو وارد کن"; return }
        scope.launch {
            loading = true; err = null; rep = null
            try {
                val r = withContext(Dispatchers.IO) {
                    val pools = GeckoTerminal.api.searchPools(s).data?.filter { it.attributes != null } ?: emptyList()
                    val pool = pools.maxByOrNull { it.attributes?.volume?.h24 ?: 0.0 } ?: throw Exception("استخری پیدا نشد")
                    val net = pool.relationships?.network?.data?.id ?: ""
                    if (net != "solana") throw Exception("حالت تاریخی فعلاً فقط Solana 🟣")
                    val mint = pool.relationships?.base_token?.data?.id?.substringAfter('_') ?: throw Exception("آدرس توکن پیدا نشد")
                    val current = pool.attributes?.priceUsd?.toDoubleOrNull() ?: 0.0
                    val poolAddr = pool.id?.substringAfter('_') ?: ""

                    val rows = try { GeckoOhlcv.api.poolOhlcvHour(net, poolAddr).data?.attributes?.ohlcv_list ?: emptyList() } catch (_: Exception) { emptyList<List<Double>>() }
                    var bottomTs = 0L
                    if (rows.size >= 10) {
                        val cut = rows.size * 2 / 3
                        val br = rows.subList(0, cut).minByOrNull { it[4] }
                        bottomTs = (br?.get(0)?.toLong() ?: 0L) * 1000
                    }

                    val largest = SolanaRpc.api.rpcRaw(mapOf(
                        "jsonrpc" to "2.0", "id" to 1,
                        "method" to "getTokenLargestAccounts",
                        "params" to listOf(mint)
                    ))
                    val arr = largest.result?.asJsonObject?.get("value")?.asJsonArray ?: throw Exception("هلدرها پیدا نشدن")

                    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
                    val coins = try { ApiClient.getTop1000Coins() } catch (_: Exception) { emptyList() }
                    val coin = coins.firstOrNull { it.symbol.equals(s, true) }
                    val byDay = if (coin != null) try {
                        ApiClient.getCoinChart(coin.id, days = 365).prices.associate { p -> sdf.format(Date(p[0].toLong())) to p[1] }
                    } catch (_: Exception) { emptyMap() } else emptyMap()

                    val list = mutableListOf<HolderSus>()
                    var i = 0
                    for (el in arr) {
                        if (i >= 8) break
                        i++
                        val obj = el.asJsonObject
                        val tokenAcc = obj.get("address")?.asString ?: continue
                        val amt = obj.get("uiAmountString")?.asString?.toDoubleOrNull() ?: 0.0
                        try {
                            val info = SolanaRpc.api.rpcRaw(mapOf(
                                "jsonrpc" to "2.0", "id" to 1,
                                "method" to "getAccountInfo",
                                "params" to listOf(tokenAcc, mapOf("encoding" to "jsonParsed"))
                            ))
                            val owner = info.result?.asJsonObject?.get("value")?.asJsonObject
                                ?.get("data")?.asJsonObject?.get("parsed")?.asJsonObject
                                ?.get("info")?.asJsonObject?.get("owner")?.asString ?: continue

                            val sigs = SolanaRpc.api.rpcRaw(mapOf(
                                "jsonrpc" to "2.0", "id" to 1,
                                "method" to "getSignaturesForAddress",
                                "params" to listOf(tokenAcc, mapOf("limit" to 1000))
                            ))
                            val sigArr = sigs.result?.asJsonArray ?: continue
                            val oldest = sigArr.lastOrNull()?.asJsonObject
                            val firstTs = (oldest?.get("blockTime")?.asLong ?: 0L) * 1000
                            val firstDay = if (firstTs > 0) sdf.format(Date(firstTs)) else "—"
                            val entry = byDay[firstDay] ?: 0.0
                            val mult = if (entry > 0 && current > 0) current / entry else 0.0
                            val nearBottom = firstTs in (bottomTs - 3 * 86400000)..(bottomTs + 3 * 86400000)
                            val verdict = when {
                                nearBottom -> "🎯 کف‌خر تاریخی (حوالی کف)"
                                firstTs > 0 && firstTs < bottomTs -> "⏳ قبل از کف وارد شده"
                                else -> "🕐 ورود بعد از کف"
                            }
                            list.add(HolderSus(owner, amt, amt * current, firstDay, entry, mult, sigArr.size(), verdict))
                        } catch (_: Exception) { }
                    }
                    HistoryReport(s, "Solana 🟣", current, if (bottomTs > 0) sdf.format(Date(bottomTs)) else "—", list.sortedByDescending { it.valueUsd })
                }
                rep = r
                if (r.holders.isEmpty()) err = "😴 هلدری پیدا نشد"
            } catch (t: Throwable) { err = "⚠️ ${t.message}" }
            loading = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = HCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🏛️ موتور ۴: شکارچی تاریخی (هلدرهای کف‌خر — حتی پامپ‌های قدیمی)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = HTeal)
                Text("۸ هلدر برتر + تاریخ اولین خرید + قیمت ورود از نمودار ۱ ساله (Solana)", fontSize = 9.sp, color = HGray)
                TextField(value = sym, onValueChange = { sym = it },
                    placeholder = { Text("نماد ارز... (ANSEM, ZCAT...)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                Button(onClick = { hunt() }, enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = HTeal),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text(" 🏛️ پیدا کن کف‌خرهای تاریخی", fontSize = 12.sp)
                }
                if (err != null) Text(err ?: "", fontSize = 10.sp, color = HGold)
            }
        }

        rep?.let { r ->
            Card(colors = CardDefaults.cardColors(containerColor = HCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🎯 ${r.symbol} • ${r.chainName}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = HBlue)
                    Text("الان: ${String.format(Locale.US, "$%.8f", r.currentPrice)} • تاریخ کف: ${r.bottomText}", fontSize = 10.sp, color = HGray)
                }
            }
            r.holders.forEach { h ->
                Card(colors = CardDefaults.cardColors(containerColor = HCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🐋 ${if (h.owner.length > 12) h.owner.take(6) + "..." + h.owner.takeLast(4) else h.owner}", fontWeight = FontWeight.Black, fontSize = 13.sp, color = HGold)
                            Spacer(Modifier.weight(1f))
                            Text(if (h.mult > 0) "${String.format(Locale.US, "%.1f", h.mult)}x" else "", fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (h.mult >= 2) HGreen else HGray)
                        }
                        Text("💵 ارزش فعلی: ${String.format(Locale.US, "$%,.0f", h.valueUsd)} • مقدار: ${String.format(Locale.US, "%.2f", h.amount)}", fontSize = 10.sp, color = HGray)
                        Text("🕐 اولین خرید: ${h.firstBuyText} • قیمت ورود: ${String.format(Locale.US, "$%.6f", h.entryPrice)} • ${h.txCount} تراکنش", fontSize = 9.sp, color = HGray)
                        Text(h.verdict, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = if (h.verdict.contains("🎯")) HGreen else if (h.verdict.contains("⏳")) HTeal else HGray)
                        Button(onClick = {
                            try {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("addr", h.owner))
                            } catch (_: Exception) { }
                        }, colors = ButtonDefaults.buttonColors(containerColor = HCard), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("📋 کپی آدرس هلدر", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}
