// [PIECE B1 START]
package com.pumpwatch.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import com.pumpwatch.app.data.GeckoOhlcv
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.GeckoTerminal
import com.pumpwatch.app.data.GtTrade
import com.pumpwatch.app.data.RpcKeyStore
import com.pumpwatch.app.data.SecureStorage
import com.pumpwatch.app.data.solanaRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val VGreen = Color(0xFF00E676)
private val VRed = Color(0xFFFF5252)
private val VBlue = Color(0xFF40C4FF)
private val VGold = Color(0xFFFFC107)
private val VGray = Color(0xFF8B949E)
private val VCard = Color(0xFF1A2230)

private val forensicsSigsCache = mutableMapOf<String, List<Pair<String, Long>>>()
private val forensicsDepthCache = mutableMapOf<String, Long>()
private val forensicsParsedCache = mutableMapOf<String, List<Triple<String, Double, Long>>>()

private data class SusWallet(
    val addr: String, val boughtUsd: Double, val avgEntry: Double,
    val firstBuyText: String, val txCount: Int, val soldUsd: Double,
    val statusText: String, val multiplier: Double,
    val bottomTag: Boolean = false,
    val maxSingleUsd: Double = 0.0
)

private fun num(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private fun shortAddrB(a: String): String = if (a.length > 12) "${a.take(6)}...${a.takeLast(4)}" else a

private suspend fun <T> rpcBackoff(block: suspend () -> T): T {
    var wait = 2000L
    for (attempt in 0 until 4) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val m = e.message ?: ""
            if (m.contains("429") && attempt < 3) { delay(wait); wait *= 2 } else throw e
        }
    }
    throw Exception("RPC بی‌پاسخ ماند")
}

@Composable
internal fun ChainForensicsSection(
    onCopy: (String) -> Unit,
    onInspect: (String) -> Unit,
    onStar: (String, String, Int, String, Double, Double, Double, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var symbol by remember { mutableStateOf("") }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf(10_000.0) }
    var rpcKey by remember { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var wallets by remember { mutableStateOf<List<SusWallet>>(emptyList()) }
    var coverage by remember { mutableStateOf("") }
    var flowLine by remember { mutableStateOf("") }
    var poolLabel by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        when (val r = RpcKeyStore.migrateFromLegacy(context)) {
            is RpcKeyStore.MigrationResult.Migrated -> savedMsg = "✅ کلید قدیمی به Keystore امن منتقل شد"
            is RpcKeyStore.MigrationResult.KeystoreUnavailable -> savedMsg = "⚠️ کلید قدیمی در SharedPreferences است ولی Keystore خراب"
            else -> {}
        }
        rpcKey = ""
    }

    fun parseTs(v: Any?): Long {
        val n = num(v)
        if (n != null && n > 0) return if (n < 1.0e12) (n * 1000).toLong() else n.toLong()
        val s = (v as? String)?.trim() ?: return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(s)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val fmt2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                fmt2.timeZone = TimeZone.getTimeZone("UTC")
                fmt2.parse(s)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }
    }

    fun flowText(b: Double, s: Double): String {
        val bias = when {
            b > s * 1.3 -> "تجمع (خرید سنگین‌تر)"
            s > b * 1.3 -> "توزیع (فروش سنگین‌تر)"
            else -> "متعادل"
        }
        val bTxt = String.format(Locale.US, "$%,.0f", b)
        val sTxt = String.format(Locale.US, "$%,.0f", s)
        return "💹 جریان پنجره: خرید " + bTxt + " در برابر فروش " + sTxt + " → " + bias
    }

    fun run() {
        val sym = symbol.trim()
        if (sym.isEmpty()) { err = "❌ نماد ارز رو وارد کن"; return }
        val thr = threshold
        val appCtx = context
        job?.cancel()
        job = scope.launch {
            loading = true; err = null; wallets = emptyList(); coverage = ""; flowLine = ""
            try {
                withContext(Dispatchers.IO) {
                    val sdfIn = SimpleDateFormat("yyyy/MM/dd", Locale.US)
                    val now = System.currentTimeMillis()
                    var fromTs = now - 14L * 86400000L
                    var toTs = now
                    try { if (fromText.trim().isNotEmpty()) fromTs = sdfIn.parse(fromText.trim())?.time ?: fromTs } catch (_: Exception) { }
                    try { if (toText.trim().isNotEmpty()) toTs = (sdfIn.parse(toText.trim())?.time ?: toTs) + 86400000L } catch (_: Exception) { }
                    val poolsResp = try {
                        GeckoTerminal.api.searchPools(sym)
                    } catch (_: Exception) {
                        delay(1500)
                        try { GeckoTerminal.api.searchPools(sym) } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            throw Exception("اتصال به GeckoTerminal برقرار نشد — یک دقیقه صبر کن. علت: " + t.message)
                        }
                    }
                    val poolsAll = poolsResp.data ?: throw Exception("پاسخ GeckoTerminal برای «" + sym + "» نامعتبر بود")
                    val withAttrs = poolsAll.filter { it.attributes != null }
                    var solanaPools = withAttrs.filter { it.relationships?.network?.data?.id == "solana" }
                    if (solanaPools.isEmpty()) {
                        solanaPools = withAttrs.filter {
                            val n = it.attributes?.name ?: ""
                            n.contains("/ SOL", true) || n.contains("/SOL", true)
                        }
                    }
                    if (solanaPools.isEmpty()) {
                        val nets = withAttrs.mapNotNull { it.relationships?.network?.data?.id }.distinct().take(6).joinToString(", ")
                        throw Exception("استخر Solana پیدا نشد. GeckoTerminal " + poolsAll.size + " استخر برگرداند روی شبکه‌ها: [" + nets + "]")
                    }
                    val candidates = solanaPools.sortedByDescending { it.attributes?.volume?.h24 ?: 0.0 }.take(5)
                    var poolAddr = candidates[0].id?.substringAfter('_') ?: ""
                    var mint = candidates[0].relationships?.base_token?.data?.id?.substringAfter('_') ?: ""
                    var currentPrice = candidates[0].attributes?.priceUsd?.toDoubleOrNull() ?: 0.0
                    poolLabel = candidates[0].attributes?.name ?: sym
                    class Agg { var usd = 0.0; var tok = 0.0; var first = Long.MAX_VALUE; var n = 0; var maxSingle = 0.0 }
                    val sigs = mutableListOf<Pair<String, Long>>()
                    var rpcBlocked = mint.isEmpty()
                    var activeFound = false
                    val windowNotes = mutableListOf<String>()
                    var before: String? = null
                    var rpcDepthFrom = Long.MAX_VALUE
                    var newestSeen = 0L
                    var pagesUsed = 0
                    var stopReason = ""
                    if (!rpcBlocked) {
                        for (cand in candidates) {
                            val addr = cand.id?.substringAfter('_') ?: continue
                            val probe = try {
                                rpcBackoff {
                                    solanaRaw(mapOf(
                                        "jsonrpc" to "2.0", "id" to 1,
                                        "method" to "getSignaturesForAddress",
                                        "params" to listOf(addr, mapOf("limit" to 1))
                                    ), ctx = appCtx)
                                }
                            } catch (_: Exception) { null }
                            if (probe == null || probe.result == null) { rpcBlocked = true; stopReason = "پروب RPC ناموفق"; break }
                            val a0 = probe.result.asJsonArray
                            val newest = if (a0.size() > 0) (a0.get(0).asJsonObject.get("blockTime")?.asLong ?: 0L) * 1000L else 0L
                            windowNotes.add("«" + (cand.attributes?.name ?: addr.take(6)) + "»: آخرین فعالیت " + (if (newest > 0) sdfIn.format(Date(newest)) else "بدون تراکنش"))
                            if (newest >= fromTs) {
                                poolAddr = addr
                                mint = cand.relationships?.base_token?.data?.id?.substringAfter('_') ?: ""
                                currentPrice = cand.attributes?.priceUsd?.toDoubleOrNull() ?: 0.0
                                poolLabel = cand.attributes?.name ?: sym
                                activeFound = true
                                break
                            }
                        }
                    }
                    if (activeFound && !rpcBlocked) {
                        val rows = try { GeckoOhlcv.api.poolOhlcvHour("solana", poolAddr).data?.attributes?.ohlcv_list ?: emptyList() } catch (_: Exception) { emptyList<List<Double>>() }
                        fun priceAt(ts: Long): Double {
                            if (rows.isEmpty()) return currentPrice
                            val hourTs = (ts / 3600000L) * 3600000L
                            val row = rows.minByOrNull { kotlin.math.abs(it[0].toLong() * 1000L - hourTs) } ?: return currentPrice
                            return row[4]
                        }
                        val rowsIn = rows.filter { (it[0].toLong()) * 1000 in fromTs..toTs }
                        val periodLow = (if (rowsIn.size >= 3) rowsIn else rows).minOfOrNull { it[3] } ?: 0.0
                        val cacheKey = poolAddr + "|" + fromTs + "|" + toTs
                        val cachedSigs = forensicsSigsCache[cacheKey]
                        if (cachedSigs != null) {
                            sigs.addAll(cachedSigs)
                            rpcDepthFrom = forensicsDepthCache[cacheKey] ?: Long.MAX_VALUE
                            stopReason = "کش پنجره"
                            progress = "⚡ از کش executions قبلی استفاده شد"
                        } else {
                            val t0 = System.currentTimeMillis()
                            val daysSpan = (toTs - fromTs) / 86400000.0
                            var pageCap = when {
                                daysSpan < 0.25 -> 15
                                daysSpan < 0.5 -> 20
                                daysSpan < 1 -> 30
                                else -> 50
                            }
                            var page = 0
                            try {
                                while (page < pageCap) {
                                    pagesUsed = page + 1
                                    val perMs = if (page > 0) (System.currentTimeMillis() - t0) / page else 0L
                                    val etaMin = (perMs * (pageCap - page)) / 60000L
                                    progress = "📜 عقب‌رفتن: صفحه " + (page + 1) + "/" + pageCap + " • ≈" + etaMin + " دقیقه..."
                                    val opt = mutableMapOf<String, Any>("limit" to 1000)
                                    if (before != null) opt["before"] = before!!
                                    val resp = rpcBackoff {
                                        solanaRaw(mapOf(
                                            "jsonrpc" to "2.0", "id" to 1,
                                            "method" to "getSignaturesForAddress",
                                            "params" to listOf(poolAddr, opt)
                                        ), ctx = appCtx)
                                    }
                                    if (resp == null || resp.result == null) { rpcBlocked = true; stopReason = "پاسخ نامعتبر"; break }
                                    val arr = resp.result.asJsonArray ?: break
                                    if (arr.size() == 0) { stopReason = "به شروع تاریخچه رسیدم"; break }
                                    val pageNewest = (arr.get(0).asJsonObject.get("blockTime")?.asLong ?: 0L) * 1000L
                                    if (page == 0) newestSeen = pageNewest
                                    var oldest = Long.MAX_VALUE
                                    for (el in arr) {
                                        val o = el.asJsonObject
                                        val ts = (o.get("blockTime")?.asLong ?: continue) * 1000L
                                        val sg = o.get("signature")?.asString ?: continue
                                        if (ts < oldest) oldest = ts
                                        if (ts in fromTs..toTs) sigs.add(sg to ts)
                                    }
                                    if (page == 0 && oldest < Long.MAX_VALUE && pageNewest > oldest) {
                                        val spanMs = pageNewest - oldest
                                        if (spanMs > 60_000L) {
                                            val need = ((pageNewest - fromTs) / spanMs).toInt() + 3
                                            pageCap = minOf(pageCap, maxOf(15, need))
                                        }
                                    }
                                    if (oldest < rpcDepthFrom) rpcDepthFrom = oldest
                                    if (oldest == Long.MAX_VALUE || oldest < fromTs) { stopReason = "به شروع بازه رسیدم"; break }
                                    before = arr.get(arr.size() - 1).asJsonObject.get("signature")?.asString ?: break
                                    page++
                                    delay(100)
                                }
                                if (stopReason.isEmpty()) stopReason = "سقف صفحات تمام شد"
                            } catch (t: Throwable) {
                                if (t is CancellationException) throw t
                                val m = t.message ?: ""
                                if (m.contains("closed", true) || m.contains("refused", true) || m.contains("timeout", true) || m.contains("connect", true) || m.contains("429")) {
                                    rpcBlocked = true
                                    stopReason = "خطای اتصال: " + m
                                } else throw t
                            }
                            forensicsSigsCache[cacheKey] = sigs.toList()
                            forensicsDepthCache[cacheKey] = rpcDepthFrom
                        }
                        if (sigs.isNotEmpty()) {
                            val ordered = sigs.sortedBy { it.second }
                            val sample = if (ordered.size <= 1200) ordered else ordered.take(1200)
                            val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)
                            val deltas: List<Triple<String, Double, Long>> = forensicsParsedCache[cacheKey] ?: run {
                                val outList = mutableListOf<Triple<String, Double, Long>>()
                                var parsed = 0
                                sample.chunked(4).forEach { chunk ->
                                    progress = "🔬 تحلیل: " + parsed + "/" + sample.size + "..."
                                    val parts = chunk.map { (sg, ts) ->
                                        async(Dispatchers.IO) {
                                            try {
                                                val tx = rpcBackoff {
                                                    solanaRaw(mapOf(
                                                        "jsonrpc" to "2.0", "id" to 1,
                                                        "method" to "getParsedTransaction",
                                                        "params" to listOf(sg, mapOf("encoding" to "jsonParsed", "maxSupportedTransactionVersion" to 0))
                                                    ), ctx = appCtx)
                                                }
                                                val resultObj = tx?.result?.asJsonObject ?: return@async null
                                                val meta = resultObj.getAsJsonObject("meta") ?: return@async null
                                                fun bal(key: String): Map<String, Pair<String, Double>> {
                                                    val out = mutableMapOf<String, Pair<String, Double>>()
                                                    val arrB = meta.getAsJsonArray(key) ?: return out
                                                    for (b in arrB) {
                                                        val o = b.asJsonObject
                                                        if (o.get("mint")?.asString != mint) continue
                                                        val owner = o.get("owner")?.asString ?: continue
                                                        val idx = o.get("accountIndex")?.asInt ?: -1
                                                        val amt = o.getAsJsonObject("uiTokenAmount")?.get("uiAmount")?.asDouble ?: 0.0
                                                        out[idx.toString() + "|" + owner] = owner to amt
                                                    }
                                                    return out
                                                }
                                                val pre = bal("preTokenBalances")
                                                val post = bal("postTokenBalances")
                                                val px = priceAt(ts)
                                                val out = mutableListOf<Triple<String, Double, Long>>()
                                                for ((k, pv) in post) {
                                                    val delta = pv.second - (pre[k]?.second ?: 0.0)
                                                    out.add(Triple(pv.first, delta * px, ts))
                                                }
                                                for ((k, pv) in pre) {
                                                    if (k !in post) out.add(Triple(pv.first, -pv.second * px, ts))
                                                }
                                                out
                                            } catch (_: Exception) { null }
                                        }
                                    }.awaitAll().filterNotNull()
                                    for (lst in parts) outList.addAll(lst)
                                    parsed += chunk.size
                                    delay(300)
                                }
                                forensicsParsedCache[cacheKey] = outList.toList()
                                outList
                            }
                            val buys = mutableMapOf<String, Agg>()
                            val sells = mutableMapOf<String, Double>()
                            for ((owner, usd, ts) in deltas) {
                                if (usd >= thr) {
                                    val a = buys.getOrPut(owner) { Agg() }
                                    a.usd += usd; a.n++
                                    if (usd > a.maxSingle) a.maxSingle = usd
                                    if (ts < a.first) a.first = ts
                                } else if (usd <= -thr) {
                                    sells[owner] = (sells[owner] ?: 0.0) + -usd
                                }
                            }
                            val list = buys.map { (w, a) ->
                                val avg = a.usd / a.n
                                val sold = sells[w] ?: 0.0
                                SusWallet(w, a.usd, avg, if (a.first < Long.MAX_VALUE) sdf.format(Date(a.first)) else "—", a.n, sold,
                                    when { sold >= a.usd * 0.5 -> "✅ سود رو گرفته"; sold > 0 -> "⚠️ بخشی رو فروخته"; else -> "💎 هنوز هودل می‌کنه" },
                                    if (avg > 0 && currentPrice > 0) currentPrice / avg else 0.0,
                                    bottomTag = periodLow > 0 && avg > 0 && avg <= periodLow * 1.3,
                                    maxSingleUsd = a.maxSingle)
                            }.sortedByDescending { it.boughtUsd }.take(10)
                            wallets = list
                            flowLine = flowText(buys.values.sumOf { it.usd }, sells.values.sum())
                            val thrTxt = String.format(Locale.US, "$%,.0f", thr)
                            coverage = "⛓️ منبع: RPC مستقیم • استخر: " + poolLabel + " • " + deltas.size + " delta از " + sigs.size + " تراکنش • صفحات: " + pagesUsed + " • آستانه: ≥" + thrTxt +
                                (if (rpcDepthFrom > fromTs) " • ⚠️ پوشش جزئی (تا " + sdfIn.format(Date(rpcDepthFrom)) + ")" else "")
                            if (wallets.isEmpty()) err = "😴 در این بازه کیفی با خرید تکی ≥" + thrTxt + " نبود"
                        }
                    }
                    val coverageGap = rpcDepthFrom > fromTs
                    if (wallets.isEmpty() && sigs.isEmpty() && (rpcBlocked || coverageGap)) {
                        progress = "🌍 تغییر به منبع تریدهای GeckoTerminal..."
                        val allTrades = mutableListOf<GtTrade>()
                        var cursor: Long? = null
                        var gtDepthFrom = Long.MAX_VALUE
                        var missingTs = 0
                        for (page in 0 until 40) {
                            progress = "🌍 تریدهای GeckoTerminal: صفحه " + (page + 1) + "/40..."
                            val pg = try { GeckoPrice.api.poolTrades("solana", poolAddr, cursor)?.data } catch (_: Exception) { null } ?: break
                            if (pg.isEmpty()) break
                            allTrades.addAll(pg)
                            var pageMin = Long.MAX_VALUE
                            for (tr in pg) {
                                val t = parseTs(tr.attributes?.block_timestamp)
                                if (t <= 0L) missingTs++ else if (t < pageMin) pageMin = t
                            }
                            if (pageMin == Long.MAX_VALUE) break
                            if (pageMin < gtDepthFrom) gtDepthFrom = pageMin
                            if (pageMin <= fromTs) break
                            val next = (pageMin / 1000) - 1
                            if (next == cursor) break
                            cursor = next
                            delay(250)
                        }
                        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)
                        val buys = mutableMapOf<String, Agg>()
                        val sells = mutableMapOf<String, Double>()
                        var minPx = Double.MAX_VALUE
                        for (t in allTrades) {
                            val a = t.attributes ?: continue
                            val ts = parseTs(a.block_timestamp)
                            if (ts <= 0L || ts < fromTs || ts > toTs) continue
                            val wallet = a.tx_from_address ?: continue
                            val vol = a.volume_in_usd?.toDoubleOrNull() ?: continue
                            val px = a.price_to_in_usd?.toDoubleOrNull() ?: a.price_from_in_usd?.toDoubleOrNull() ?: continue
                            if (px < minPx) minPx = px
                            if ((a.kind ?: "").equals("buy", true)) {
                                if (vol >= thr) {
                                    val ag = buys.getOrPut(wallet) { Agg() }
                                    ag.usd += vol; ag.n++
                                    if (vol > ag.maxSingle) ag.maxSingle = vol
                                    if (ts < ag.first) ag.first = ts
                                }
                            } else {
                                sells[wallet] = (sells[wallet] ?: 0.0) + vol
                            }
                        }
                        val periodLowGt = if (minPx < Double.MAX_VALUE) minPx else 0.0
                        val list = buys.map { (w, a) ->
                            val avg = a.usd / a.n
                            val sold = sells[w] ?: 0.0
                            SusWallet(w, a.usd, avg, if (a.first < Long.MAX_VALUE) sdf.format(Date(a.first)) else "—", a.n, sold,
                                when { sold >= a.usd * 0.5 -> "✅ سود رو گرفته"; sold > 0 -> "⚠️ بخشی رو فروخته"; else -> "💎 هنوز هودل می‌کنه" },
                                if (avg > 0 && currentPrice > 0) currentPrice / avg else 0.0,
                                bottomTag = periodLowGt > 0 && avg > 0 && avg <= periodLowGt * 1.3,
                                maxSingleUsd = a.maxSingle)
                        }.sortedByDescending { it.boughtUsd }.take(10)
                        wallets = list
                        flowLine = flowText(buys.values.sumOf { it.usd }, sells.values.sum())
                        val thrTxt = String.format(Locale.US, "$%,.0f", thr)
                        coverage = "🌍 منبع: تریدهای GeckoTerminal • استخر: " + poolLabel + " • " + allTrades.size + " ترید • آستانه: ≥" + thrTxt
                        if (wallets.isEmpty()) err = "😴 در این بازه کیفی با خرید تکی ≥" + thrTxt + " پیدا نشد"
                    }
                    if (wallets.isEmpty() && sigs.isEmpty() && !rpcBlocked && !coverageGap && err == null) {
                        val notes = windowNotes.joinToString(" | ")
                        err = "😴 در این بازه تراکنشی روی هیچ استخر نامزدی نیست. پنجرهٔ فعالیت: " + notes
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) {
                    err = "⏹ اسکن توسط تو متوقف شد"
                } else {
                    err = if ((t.message ?: "").contains("429"))
                        "⚠️ سهمیه RPC عمومی پر شد (429). بازه را کوتاه‌تر کن."
                    else "⚠️ خطا: " + t.message
                }
            }
            loading = false; progress = ""
        }
    }
    // [PIECE B2 START]
    Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⛓️ موتور ۳: جنایت‌شناسی کامل زنجیره (Solana)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VGreen)
            Text("برای شکار روزانه: یکی از چیپ‌های پنجرهٔ آماده را بزن و اجرا کن. بار اول ممکن است چند دقیقه طول بکشد؛ بار بعد برای همان بازه آنی است. آستانه روی «خرید تکی» هر تراکنش اعمال می‌شود، نه جمع کل.", fontSize = 9.sp, color = VGray, lineHeight = 14.sp)
            TextField(value = symbol, onValueChange = { symbol = it },
                placeholder = { Text("نماد... (CATE)", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField(value = fromText, onValueChange = { fromText = it },
                    placeholder = { Text("از: 2026/09/08", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
                TextField(value = toText, onValueChange = { toText = it },
                    placeholder = { Text("تا: 2026/09/15", fontSize = 10.sp) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(4 to "۴س اخیر", 8 to "۸س اخیر", 12 to "۱۲س اخیر", 24 to "۲۴س اخیر").forEach { (h, label) ->
                    FilterChip(selected = false, onClick = {
                        val sdfP = SimpleDateFormat("yyyy/MM/dd", Locale.US)
                        val nowMs = System.currentTimeMillis()
                        fromText = sdfP.format(Date(nowMs - h * 3600000L))
                        toText = sdfP.format(Date(nowMs + 3600000L))
                    }, label = { Text(label, fontSize = 10.sp) })
                }
            }
            Text("💵 آستانه خرید تکی:", fontSize = 9.sp, color = VGray)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(10_000.0 to "≥۱۰K", 20_000.0 to "≥۲۰K", 50_000.0 to "≥۵۰K", 100_000.0 to "≥۱۰۰K", 200_000.0 to "≥۲۰۰K", 500_000.0 to "≥۵۰۰K").forEach { (v, label) ->
                    FilterChip(selected = threshold == v, onClick = { threshold = v }, label = { Text(label, fontSize = 10.sp) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { run() }, enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = VGreen),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Text("⛓️ نهنگ‌ها را از زنجیره بیرون بکش", fontSize = 12.sp)
                }
                if (loading) {
                    Button(onClick = { job?.cancel() },
                        colors = ButtonDefaults.buttonColors(containerColor = VRed),
                        shape = RoundedCornerShape(8.dp)) {
                        Text("⏹ توقف", fontSize = 11.sp)
                    }
                }
            }
            if (progress.isNotEmpty()) Text(progress, fontSize = 10.sp, color = VBlue)
            if (coverage.isNotEmpty()) Text(coverage, fontSize = 9.sp, color = VGold, lineHeight = 14.sp)
            if (flowLine.isNotEmpty()) Text(flowLine, fontSize = 10.sp, color = VBlue, fontWeight = FontWeight.Bold, lineHeight = 15.sp)
            if (err != null) Text(err ?: "", fontSize = 10.sp, color = VGold)
            Spacer(Modifier.height(8.dp))
            Text("🔑 کلید RPC شخصی:", fontSize = 10.sp, color = VGold, fontWeight = FontWeight.Bold)
            val ksOk = SecureStorage.isKeystoreAvailable()
            val configured = RpcKeyStore.isConfigured(context)
            val masked = RpcKeyStore.mask(context)
            if (!ksOk) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3D1F1F)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("⚠️ Keystore دستگاه در دسترس نیست", fontSize = 9.sp, color = VRed, modifier = Modifier.padding(8.dp))
                }
            } else if (configured) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1F3D2A)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🔐 Configured: " + masked, fontSize = 10.sp, color = VGreen, modifier = Modifier.weight(1f))
                    }
                }
            }
            TextField(value = rpcKey, onValueChange = { rpcKey = it },
                placeholder = { Text(if (configured) "کلید جدید برای چرخش..." else "کلید RPC (اختیاری)", fontSize = 9.sp) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true,
                enabled = ksOk)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val res = if (configured) RpcKeyStore.rotate(context, rpcKey) else RpcKeyStore.set(context, rpcKey)
                        savedMsg = when (res) {
                            is SecureStorage.SecretResult.Saved -> if (configured) "✅ کلید چرخش شد" else "✅ کلید ذخیره شد"
                            is SecureStorage.SecretResult.KeystoreUnavailable -> "⚠️ Keystore خراب"
                            is SecureStorage.SecretResult.CryptoError -> "⚠️ خطای رمزنگاری: " + res.message
                        }
                        if (res is SecureStorage.SecretResult.Saved) rpcKey = ""
                    },
                    enabled = ksOk && rpcKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = VGold),
                    shape = RoundedCornerShape(6.dp)
                ) { Text(if (configured) "🔄 چرخش" else "💾 ذخیره", fontSize = 10.sp) }
                if (configured) {
                    Button(onClick = {
                        RpcKeyStore.clear(context)
                        savedMsg = "✅ کلید حذف شد"
                    }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("🗑 پاک", fontSize = 10.sp) }
                }
            }
            if (savedMsg.isNotEmpty()) Text(savedMsg, fontSize = 9.sp, color = VGreen, fontWeight = FontWeight.Bold)
        }
    }
    if (poolLabel.isNotEmpty() && wallets.isNotEmpty()) {
        Text("🐋 کیف‌های تجمع‌کننده در " + poolLabel + ":", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VGreen)
        wallets.forEach { w ->
            Card(colors = CardDefaults.cardColors(containerColor = VCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐋 " + shortAddrB(w.addr), fontWeight = FontWeight.Black, fontSize = 13.sp, color = VGreen)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1f", w.multiplier) + "x", fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (w.multiplier >= 2) VGreen else VGray)
                    }
                    Text("💵 جمع: " + String.format(Locale.US, "$%,.0f", w.boughtUsd) + " • بزرگ‌ترین خرید تکی: " + String.format(Locale.US, "$%,.0f", w.maxSingleUsd) + " • ورود: " + String.format(Locale.US, "$%.8f", w.avgEntry), fontSize = 10.sp, color = VGray)
                    Text("🕐 اولین: " + w.firstBuyText + " • " + w.txCount + " تراکنش ≥ آستانه • فروش: " + String.format(Locale.US, "$%,.0f", w.soldUsd), fontSize = 9.sp, color = VGray)
                    Text(w.statusText, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (w.statusText.contains("هودل")) VGreen else if (w.statusText.contains("✅")) VGold else VRed)
                    if (w.bottomTag) Text("🎯 کف‌خر سنگین", fontSize = 9.sp, color = VGold, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { onCopy(w.addr) }, colors = ButtonDefaults.buttonColors(containerColor = VCard), shape = RoundedCornerShape(6.dp)) { Text("📋 کپی", fontSize = 9.sp) }
                        Button(onClick = { onInspect(w.addr) }, colors = ButtonDefaults.buttonColors(containerColor = VBlue), shape = RoundedCornerShape(6.dp)) { Text("🔍 بررسی کامل", fontSize = 9.sp) }
                        Button(onClick = { onStar(w.addr, poolLabel, (w.multiplier * 10).toInt(), if (w.bottomTag) "کف‌خر 🎯" else "نهنگ زنجیره", w.boughtUsd, w.maxSingleUsd, w.soldUsd, w.txCount) }, colors = ButtonDefaults.buttonColors(containerColor = VGold), shape = RoundedCornerShape(6.dp)) { Text("❤️", fontSize = 9.sp) }
                    }
                }
            }
        }
    }
}
