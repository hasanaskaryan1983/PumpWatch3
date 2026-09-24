package com.pumpwatch.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.GatewayProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

private val FGreen = Color(0xFF00E676)
private val FRed = Color(0xFFFF5252)
private val FGold = Color(0xFFFFC107)
private val FBlue = Color(0xFF40C4FF)
private val FGray = Color(0xFF8B949E)
private val FCard = Color(0xFF1A2230)
private val FG2 = Gson()

private val GW = GatewayProviders()

private const val FAV_WALLET_PARALLELISM = 3
private const val FAV_TOKEN_PARALLELISM = 5
private const val FAV_CHUNK_DELAY_MS = 200L

data class FavWallet(
    val addr: String,
    var note: String,
    var starred: Boolean,
    var addedTs: Long,
    var lastScanTs: Long = 0L,
    var totalUsd: Double = 0.0,
    var unpricedCount: Int = 0,
    var snap: MutableMap<String, Double> = mutableMapOf(),
    var tokenCount: Int = 0,
    var symbols: MutableList<String> = mutableListOf(),
    var role: String = "",
    var huntedAtMs: Long = 0L,
    var boughtUsd: Double = 0.0,
    var maxSingleUsd: Double = 0.0,
    var soldUsd: Double = 0.0,
    var txCount: Int = 0,
    var multiplier: Double = 0.0
)

data class WalletAlert(val addr: String, val ts: Long, val text: String, var read: Boolean = false)

private data class SavedTraderOld(val addr: String, val symbol: String, val score: Int, val note: String)

private val FAV_EVM = listOf(
    "eth" to "https://eth.blockscout.com/",
    "base" to "https://base.blockscout.com/",
    "arbitrum" to "https://arbitrum.blockscout.com/",
    "optimism" to "https://optimism.blockscout.com/",
    "polygon_pos" to "https://polygon.blockscout.com/",
    "gnosis" to "https://gnosis.blockscout.com/",
    "robinhood" to "https://robinhoodchain.blockscout.com/"
)

private data class HoldingsSummary(
    val balances: Map<String, Double>,
    val totalUsd: Double,
    val pricedCount: Int,
    val unpricedCount: Int
)

private data class FavTokenAgg(val sym: String, val amt: Double, val px: Double?)

object FavStore {
    val favs = mutableStateOf(mutableListOf<FavWallet>())
    val trash = mutableStateOf(mutableListOf<FavWallet>())
    val alerts = mutableStateOf(mutableListOf<WalletAlert>())
    var lastScanTs = 0L
    private var loaded = false

    private fun normalizeFavsArray(arr: JsonArray): MutableList<FavWallet> {
        val out = mutableListOf<FavWallet>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val syms: MutableList<String> = when {
                o.has("symbols") && o.get("symbols").isJsonArray ->
                    o.getAsJsonArray("symbols").mapNotNull { if (it.isJsonNull) null else it.asString }.toMutableList()
                o.has("symbol") && !o.get("symbol").isJsonNull && o.get("symbol").asString.isNotEmpty() ->
                    mutableListOf(o.get("symbol").asString)
                else -> mutableListOf()
            }
            o.remove("symbol")
            val na = JsonArray(); syms.forEach { na.add(it) }; o.add("symbols", na)
            fun en(name: String, d: Double) { if (!o.has(name) || o.get(name).isJsonNull) o.addProperty(name, d) }
            fun ei(name: String, d: Int) { if (!o.has(name) || o.get(name).isJsonNull) o.addProperty(name, d) }
            en("boughtUsd", 0.0); en("maxSingleUsd", 0.0); en("soldUsd", 0.0); en("multiplier", 0.0); ei("txCount", 0)
            val fw = FG2.fromJson(o, FavWallet::class.java) ?: continue
            out.add(fw)
        }
        return out
    }

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        favs.value = try {
            val j = p.getString("fav_wallets", "") ?: ""
            if (j.isEmpty()) {
                val old = p.getString("top_traders", "") ?: ""
                if (old.isNotEmpty()) {
                    val olds: MutableList<SavedTraderOld>? = FG2.fromJson(old, object : TypeToken<MutableList<SavedTraderOld>>() {}.type)
                    olds?.map {
                        FavWallet(
                            addr = it.addr, note = it.note, starred = false,
                            addedTs = System.currentTimeMillis(),
                            symbols = mutableListOf(it.symbol), role = "",
                            huntedAtMs = System.currentTimeMillis()
                        )
                    }?.toMutableList() ?: mutableListOf()
                } else mutableListOf()
            } else {
                val arr = FG2.fromJson(j, JsonArray::class.java)
                if (arr != null) normalizeFavsArray(arr) else mutableListOf()
            }
        } catch (_: Exception) { mutableListOf() }
        trash.value = try {
            val tj = p.getString("fav_trash", "") ?: ""
            if (tj.isEmpty()) mutableListOf()
            else { val ta = FG2.fromJson(tj, JsonArray::class.java); if (ta != null) normalizeFavsArray(ta) else mutableListOf() }
        } catch (_: Exception) { mutableListOf() }
        alerts.value = try { FG2.fromJson(p.getString("fav_alerts", "") ?: "", object : TypeToken<MutableList<WalletAlert>>() {}.type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
        lastScanTs = p.getLong("fav_lastscan", 0L)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putString("fav_wallets", FG2.toJson(favs.value))
            .putString("fav_trash", FG2.toJson(trash.value))
            .putString("fav_alerts", FG2.toJson(alerts.value))
            .putLong("fav_lastscan", lastScanTs)
            .apply()
    }

    fun unread(): Int = alerts.value.count { !it.read }

    fun addFav(
        ctx: Context,
        addr: String,
        note: String = "",
        starred: Boolean = false,
        symbol: String = "",
        symbols: List<String> = emptyList(),
        role: String = "",
        huntedAtMs: Long = System.currentTimeMillis(),
        boughtUsd: Double = 0.0,
        maxSingleUsd: Double = 0.0,
        soldUsd: Double = 0.0,
        txCount: Int = 0,
        multiplier: Double = 0.0
    ) {
        load(ctx)
        val allSyms = (symbols + listOf(symbol)).filter { it.isNotEmpty() }.distinct()
        val existing = favs.value.firstOrNull { it.addr == addr }
        if (existing != null) {
            val isNew = allSyms.any { it !in existing.symbols }
            for (s in allSyms) if (s !in existing.symbols) existing.symbols.add(s)
            if (isNew) {
                existing.boughtUsd += boughtUsd
                existing.soldUsd += soldUsd
                existing.txCount += txCount
                existing.maxSingleUsd = maxOf(existing.maxSingleUsd, maxSingleUsd)
                existing.multiplier = maxOf(existing.multiplier, multiplier)
            }
            if (role.isNotEmpty()) existing.role = role
            if (huntedAtMs > 0) existing.huntedAtMs = huntedAtMs
            favs.value = ArrayList(favs.value); save(ctx)
            return
        }
        if (trash.value.any { it.addr == addr }) return
        favs.value.add(
            FavWallet(
                addr = addr, note = note, starred = starred,
                addedTs = System.currentTimeMillis(),
                symbols = allSyms.toMutableList(), role = role, huntedAtMs = huntedAtMs,
                boughtUsd = boughtUsd, maxSingleUsd = maxSingleUsd, soldUsd = soldUsd,
                txCount = txCount, multiplier = multiplier
            )
        )
        favs.value = ArrayList(favs.value); save(ctx)
    }

    fun updateNote(ctx: Context, addr: String, newNote: String) {
        val w = favs.value.firstOrNull { it.addr == addr } ?: return
        w.note = newNote
        favs.value = ArrayList(favs.value); save(ctx)
    }

    fun moveToTrash(ctx: Context, addr: String) {
        val w = favs.value.firstOrNull { it.addr == addr } ?: return
        favs.value.remove(w); trash.value.add(w)
        favs.value = ArrayList(favs.value); trash.value = ArrayList(trash.value); save(ctx)
    }

    fun restore(ctx: Context, addr: String) {
        val w = trash.value.firstOrNull { it.addr == addr } ?: return
        trash.value.remove(w); favs.value.add(w)
        favs.value = ArrayList(favs.value); trash.value = ArrayList(trash.value); save(ctx)
    }

    fun deleteForever(ctx: Context, addr: String) {
        trash.value.removeAll { it.addr == addr }
        alerts.value.removeAll { it.addr == addr }
        trash.value = ArrayList(trash.value); alerts.value = ArrayList(alerts.value); save(ctx)
    }
}

private suspend fun scanHoldings(addr: String): HoldingsSummary {
    val balances = mutableMapOf<String, Double>()
    var total = 0.0
    var priced = 0
    var unpriced = 0
    try {
        if (addr.startsWith("0x") && addr.length == 42) {
            val parts = coroutineScope {
                FAV_EVM.map { (gt, host) ->
                    async(Dispatchers.IO) {
                        val out = mutableListOf<FavTokenAgg>()
                        try {
                            val toks = GW.evmTokenList(host, addr).value?.result ?: return@async out
                            toks.filter { (it.balance?.toDoubleOrNull() ?: 0.0) > 0 }.forEach { t ->
                                val dec = t.decimals?.toDoubleOrNull() ?: 18.0
                                val amt = (t.balance?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                                val sym = t.symbol ?: "?"
                                val c = t.contractAddress ?: return@forEach
                                val px = GW.geckoTokenInfo(gt, c).value?.data?.attributes?.price_usd?.toDoubleOrNull()
                                out.add(FavTokenAgg(sym, amt, px))
                            }
                        } catch (_: Exception) { }
                        out
                    }
                }.awaitAll()
            }
            for (part in parts) for (a in part) {
                balances[a.sym] = (balances[a.sym] ?: 0.0) + a.amt
                if (a.px != null && a.px > 0) { total += a.amt * a.px; priced++ } else unpriced++
            }
        } else if (addr.length in 32..44) {
            val res = GW.solanaTypedGateway(
                "tok:$addr",
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1,
                    "method" to "getTokenAccountsByOwner",
                    "params" to listOf(addr, mapOf("programId" to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"), mapOf("encoding" to "jsonParsed"))
                )
            ).value
            val raw = res?.result?.value?.mapNotNull { a ->
                val inf = a.account?.data?.parsed?.info ?: return@mapNotNull null
                val amt = inf.tokenAmount?.uiAmountString?.toDoubleOrNull() ?: 0.0
                if (amt <= 0) return@mapNotNull null
                val mint = inf.mint ?: return@mapNotNull null
                mint to amt
            } ?: emptyList()

            val parts = coroutineScope {
                raw.chunked(FAV_TOKEN_PARALLELISM).flatMap { chunk ->
                    val part = chunk.map { (mint, amt) ->
                        async(Dispatchers.IO) {
                            val t = GW.geckoTokenInfo("solana", mint).value?.data?.attributes
                            FavTokenAgg(t?.symbol ?: mint.take(6), amt, t?.price_usd?.toDoubleOrNull())
                        }
                    }.awaitAll()
                    delay(FAV_CHUNK_DELAY_MS)
                    part
                }
            }
            for (a in parts) {
                balances[a.sym] = (balances[a.sym] ?: 0.0) + a.amt
                if (a.px != null && a.px > 0) { total += a.amt * a.px; priced++ } else unpriced++
            }
        }
    } catch (_: Exception) { }
    return HoldingsSummary(balances, total, priced, unpriced)
}

suspend fun scanStarred(ctx: Context, force: Boolean = false): Int {
    FavStore.load(ctx)
    val now = System.currentTimeMillis()
    if (!force && now - FavStore.lastScanTs < 6L * 3600 * 1000) return 0
    var newAlerts = 0
    val starred = FavStore.favs.value.filter { it.starred }

    val summaries = coroutineScope {
        starred.chunked(FAV_WALLET_PARALLELISM).flatMap { chunk ->
            val part = chunk.map { w ->
                async(Dispatchers.IO) {
                    val s = try { scanHoldings(w.addr) } catch (_: Exception) { null }
                    w to s
                }
            }.awaitAll()
            delay(FAV_CHUNK_DELAY_MS)
            part
        }
    }

    for ((w, summaryOpt) in summaries) {
        try {
            val summary = summaryOpt ?: continue
            val holds = summary.balances
            for ((sym, amt) in holds) {
                val old = w.snap[sym]
                if (old == null) {
                    FavStore.alerts.value.add(0, WalletAlert(w.addr, now, "🟢 توکن جدید در کیف: $sym • مقدار: ${String.format(Locale.US, "%.4f", amt)}"))
                    newAlerts++
                } else if (amt > old * 1.01) {
                    FavStore.alerts.value.add(0, WalletAlert(w.addr, now, "📈 افزایش موجودی: $sym • از ${String.format(Locale.US, "%.4f", old)} به ${String.format(Locale.US, "%.4f", amt)}"))
                    newAlerts++
                }
            }
            w.snap = holds.toMutableMap()
            w.lastScanTs = now
            w.totalUsd = summary.totalUsd
            w.unpricedCount = summary.unpricedCount
            w.tokenCount = summary.pricedCount + summary.unpricedCount
        } catch (_: Exception) { }
    }
    FavStore.lastScanTs = now
    FavStore.favs.value = ArrayList(FavStore.favs.value)
    FavStore.alerts.value = ArrayList(FavStore.alerts.value)
    FavStore.save(ctx)
    return newAlerts
}

private fun shortA(a: String): String = if (a.length > 14) "${a.take(7)}...${a.takeLast(5)}" else a

@Composable
fun FavoritesPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var newAddr by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var editAddr by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    val sdfDate = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    LaunchedEffect(Unit) { FavStore.load(ctx) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("❤️ پروندهٔ نهنگ‌ها", fontWeight = FontWeight.Black, fontSize = 16.sp, color = FRed)
        Text("⭐ زرد = بررسی خودکار هر ۶ ساعت + هشدار در ⚡️ • تکرار نهنگ در چند پامپ خودکار شمرده می‌شود", fontSize = 9.sp, color = FGray)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(value = newAddr, onValueChange = { newAddr = it },
                placeholder = { Text("آدرس کیف مهم...", fontSize = 11.sp) },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), singleLine = true)
            Button(onClick = {
                val a = newAddr.trim().replace(Regex("[^A-Za-z0-9]"), "")
                if (a.isNotEmpty()) { FavStore.addFav(ctx, a); newAddr = ""; msg = "❤️ اضافه شد" }
            }, colors = ButtonDefaults.buttonColors(containerColor = FRed), shape = RoundedCornerShape(8.dp)) { Text("➕", fontSize = 11.sp) }
        }
        Button(onClick = {
            scope.launch {
                scanning = true
                val n = scanStarred(ctx, force = true)
                msg = if (n > 0) "⚡️ $n هشدار جدید ثبت شد!" else "✅ بررسی شد — چیز جدیدی نیومده"
                scanning = false
            }
        }, enabled = !scanning, colors = ButtonDefaults.buttonColors(containerColor = FGold), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (scanning) CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = Color.Black, strokeWidth = 2.dp)
            Text(" 🔄 بررسی فوری کیف‌های ستاره‌دار", fontSize = 12.sp)
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 10.sp, color = FGreen)
        if (FavStore.favs.value.isEmpty()) Text("هنوز نهنگی شکار نکردی — از موتور ۶ ❤️ بزن یا آدرس مهم رو اینجا اضافه کن", fontSize = 10.sp, color = FGray)

        FavStore.favs.value.forEach { w ->
            Card(colors = CardDefaults.cardColors(containerColor = FCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { w.starred = !w.starred; FavStore.favs.value = ArrayList(FavStore.favs.value); FavStore.save(ctx) },
                            colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) {
                            Text(if (w.starred) "⭐" else "☆", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(shortA(w.addr), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FGold)
                            if (w.symbols.isNotEmpty()) {
                                val rep = if (w.symbols.size > 1) " • 🔁 ${w.symbols.size} پامپ" else ""
                                Text("🪙 شکار در: ${w.symbols.joinToString(" / ")} • ${sdfDate.format(Date(if (w.huntedAtMs > 0) w.huntedAtMs else w.addedTs))}$rep",
                                    fontSize = 9.sp, color = if (w.symbols.size > 1) FGold else FBlue, fontWeight = FontWeight.Bold)
                            }
                            if (w.role.isNotEmpty()) {
                                Text("🏷️ نقش: ${w.role}", fontSize = 9.sp,
                                    color = if (w.role.contains("کف‌خر")) FGold else FGray, fontWeight = FontWeight.Bold)
                            }
                            if (w.boughtUsd > 0) {
                                Text("💵 کل خرید: ${String.format(Locale.US, "$%,.0f", w.boughtUsd)} • فروش: ${String.format(Locale.US, "$%,.0f", w.soldUsd)} • ${w.txCount} tx • ${String.format(Locale.US, "%.1f", w.multiplier)}x",
                                    fontSize = 8.sp, color = FGray)
                            }
                        }
                        Button(onClick = { FavStore.moveToTrash(ctx, w.addr) },
                            colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) { Text("🗑", fontSize = 12.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (w.note.isNotEmpty()) "📝 ${w.note}" else "📝 (یادداشت اضافه کن)",
                            fontSize = 9.sp, color = if (w.note.isNotEmpty()) FGray else Color(0xFF555555),
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { editAddr = w.addr; editText = w.note }) { Text("✏️", fontSize = 10.sp) }
                    }
                    if (w.starred && w.lastScanTs > 0) {
                        val pricedCount = w.tokenCount - w.unpricedCount
                        val valueText = when {
                            w.tokenCount == 0 -> "بدون توکن"
                            w.unpricedCount == 0 -> "ارزش: ${String.format(Locale.US, "$%,.0f", w.totalUsd)} • ${w.tokenCount} توکن"
                            pricedCount == 0 -> "❓ ${w.tokenCount} توکن (همه بدون قیمت شناخته‌شده)"
                            else -> "ارزش: ${String.format(Locale.US, "$%,.0f", w.totalUsd)} • ${w.tokenCount} توکن (${w.unpricedCount} بدون قیمت)"
                        }
                        Text("🔄 آخرین بررسی: ${sdf.format(Date(w.lastScanTs))} • $valueText", fontSize = 8.sp, color = FGreen)
                    }
                }
            }
        }
    }

    if (editAddr != null) {
        Dialog(onDismissRequest = { editAddr = null }) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = FCard),
                modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📝 ویرایش یادداشت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FGold)
                    TextField(value = editText, onValueChange = { editText = it },
                        placeholder = { Text("مثلاً: در ۳ پامپ تکرار شد — رانتی قطعی", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editAddr = null }) { Text("انصراف", color = FGray) }
                        TextButton(onClick = { FavStore.updateNote(ctx, editAddr!!, editText); editAddr = null }) { Text("💾 ذخیره", color = FGreen) }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsPage() {
    val ctx = LocalContext.current
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    LaunchedEffect(Unit) {
        FavStore.load(ctx)
        FavStore.alerts.value.forEach { it.read = true }
        FavStore.alerts.value = ArrayList(FavStore.alerts.value); FavStore.save(ctx)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚡️ هشدارهای نهنگ‌ها", fontWeight = FontWeight.Black, fontSize = 16.sp, color = FGold)
        Text("توکن جدید یا افزایش موجودی کیف‌های ستاره‌دار — با باز کردن این صفحه، هشدارها خونده می‌شن", fontSize = 9.sp, color = FGray)
        if (FavStore.alerts.value.isEmpty()) Text("هنوز هشداری نیست — توی ❤️ کیف‌ها رو ستاره‌دار کن", fontSize = 10.sp, color = FGray)
        FavStore.alerts.value.forEach { a ->
            Card(colors = CardDefaults.cardColors(containerColor = FCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(a.text, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = FGreen)
                        Text("${sdf.format(Date(a.ts))} • ${shortA(a.addr)}", fontSize = 8.sp, color = FGray)
                    }
                    Button(onClick = { FavStore.moveToTrash(ctx, a.addr) },
                        colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) { Text("🗑", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun TrashPage() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { FavStore.load(ctx) }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("♻️ سطل بازیافت", fontWeight = FontWeight.Black, fontSize = 16.sp, color = FGray)
        Text("🔁 = برگردون به ❤️ (اگه اشتباهی حذف کردی) • 🗑 = حذف دائم", fontSize = 9.sp, color = FGray)
        if (FavStore.trash.value.isEmpty()) Text("سطل خالیه", fontSize = 10.sp, color = FGray)
        FavStore.trash.value.forEach { w ->
            Card(colors = CardDefaults.cardColors(containerColor = FCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { FavStore.restore(ctx, w.addr) },
                        colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) { Text("🔁", fontSize = 12.sp) }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(shortA(w.addr), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FGray)
                        if (w.note.isNotEmpty()) Text(w.note, fontSize = 9.sp, color = FGray)
                        if (w.symbols.isNotEmpty()) Text("🪙 ${w.symbols.joinToString(" / ")} ${if (w.role.isNotEmpty()) "• ${w.role}" else ""}", fontSize = 8.sp, color = FGray)
                    }
                    Button(onClick = { FavStore.deleteForever(ctx, w.addr) },
                        colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) { Text("🗑", fontSize = 12.sp) }
                }
            }
        }
    }
}
