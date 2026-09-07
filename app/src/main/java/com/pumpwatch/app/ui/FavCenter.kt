package com.pumpwatch.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.Blockscout
import com.pumpwatch.app.data.GeckoPrice
import com.pumpwatch.app.data.solanaTyped
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

private val FGreen = Color(0xFF00E676)
private val FRed = Color(0xFFFF5252)
private val FGold = Color(0xFFFFC107)
private val FGray = Color(0xFF8B949E)
private val FCard = Color(0xFF1A2230)
private val FG2 = Gson()

data class FavWallet(
    val addr: String,
    var note: String,
    var starred: Boolean,
    var addedTs: Long,
    var lastScanTs: Long = 0L,
    var totalUsd: Double = 0.0,
    var snap: MutableMap<String, Double> = mutableMapOf()
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

object FavStore {
    val favs = mutableStateOf(mutableListOf<FavWallet>())
    val trash = mutableStateOf(mutableListOf<FavWallet>())
    val alerts = mutableStateOf(mutableListOf<WalletAlert>())
    var lastScanTs = 0L
    private var loaded = false

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
                    olds?.map { FavWallet(it.addr, "${it.symbol} • ${it.note}", false, System.currentTimeMillis()) }?.toMutableList() ?: mutableListOf()
                } else mutableListOf()
            } else FG2.fromJson(j, object : TypeToken<MutableList<FavWallet>>() {}.type) ?: mutableListOf()
        } catch (_: Exception) { mutableListOf() }
        trash.value = try { FG2.fromJson(p.getString("fav_trash", "") ?: "", object : TypeToken<MutableList<FavWallet>>() {}.type) ?: mutableListOf() } catch (_: Exception) { mutableListOf() }
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

    fun addFav(ctx: Context, addr: String, note: String = "", starred: Boolean = false) {
        if (favs.value.any { it.addr == addr } || trash.value.any { it.addr == addr }) return
        favs.value.add(FavWallet(addr, note, starred, System.currentTimeMillis()))
        favs.value = ArrayList(favs.value)
        save(ctx)
    }

    fun moveToTrash(ctx: Context, addr: String) {
        val w = favs.value.firstOrNull { it.addr == addr } ?: return
        favs.value.remove(w); trash.value.add(w)
        favs.value = ArrayList(favs.value); trash.value = ArrayList(trash.value)
        save(ctx)
    }

    fun restore(ctx: Context, addr: String) {
        val w = trash.value.firstOrNull { it.addr == addr } ?: return
        trash.value.remove(w); favs.value.add(w)
        favs.value = ArrayList(favs.value); trash.value = ArrayList(trash.value)
        save(ctx)
    }

    fun deleteForever(ctx: Context, addr: String) {
        trash.value.removeAll { it.addr == addr }
        alerts.value.removeAll { it.addr == addr }
        trash.value = ArrayList(trash.value); alerts.value = ArrayList(alerts.value)
        save(ctx)
    }
}

private suspend fun scanHoldings(addr: String): Pair<Map<String, Double>, Double> {
    val map = mutableMapOf<String, Double>()
    var total = 0.0
    try {
        if (addr.startsWith("0x") && addr.length == 42) {
            for ((gt, host) in FAV_EVM) {
                try {
                    val toks = Blockscout.api(host).tokenList("account", "tokenlist", addr).result ?: continue
                    toks.filter { (it.balance?.toDoubleOrNull() ?: 0.0) > 0 }.take(10).forEach { t ->
                        val dec = t.decimals?.toDoubleOrNull() ?: 18.0
                        val amt = (t.balance?.toDoubleOrNull() ?: 0.0) / 10.0.pow(dec)
                        val sym = t.symbol ?: "?"
                        val c = t.contractAddress ?: return@forEach
                        val px = try { GeckoPrice.api.tokenInfo(gt, c).data?.attributes?.price_usd?.toDoubleOrNull() ?: 0.0 } catch (_: Exception) { 0.0 }
                        map[sym] = (map[sym] ?: 0.0) + amt
                        total += amt * px
                    }
                } catch (_: Exception) { }
            }
        } else if (addr.length in 32..44) {
            val res = solanaTyped(mapOf(
                "jsonrpc" to "2.0", "id" to 1,
                "method" to "getTokenAccountsByOwner",
                "params" to listOf(addr, mapOf("programId" to "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"), mapOf("encoding" to "jsonParsed"))
            ))
            res?.result?.value?.forEach { a ->
                val inf = a.account?.data?.parsed?.info ?: return@forEach
                val amt = inf.tokenAmount?.uiAmountString?.toDoubleOrNull() ?: 0.0
                if (amt <= 0) return@forEach
                val mint = inf.mint ?: return@forEach
                val t = try { GeckoPrice.api.tokenInfo("solana", mint).data?.attributes } catch (_: Exception) { null }
                val sym = t?.symbol ?: mint.take(6)
                val px = t?.price_usd?.toDoubleOrNull() ?: 0.0
                map[sym] = (map[sym] ?: 0.0) + amt
                total += amt * px
            }
        }
    } catch (_: Exception) { }
    return map to total
}

suspend fun scanStarred(ctx: Context, force: Boolean = false): Int {
    FavStore.load(ctx)
    val now = System.currentTimeMillis()
    if (!force && now - FavStore.lastScanTs < 6L * 3600 * 1000) return 0
    var newAlerts = 0
    for (w in FavStore.favs.value.filter { it.starred }) {
        try {
            val (holds, totalUsd) = scanHoldings(w.addr)
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
            w.totalUsd = totalUsd
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
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
    LaunchedEffect(Unit) { FavStore.load(ctx) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("❤️ کیف پول‌های مورد پسند", fontWeight = FontWeight.Black, fontSize = 16.sp, color = FRed)
        Text("⭐ زرد = بررسی خودکار هر ۶ ساعت + هشدار در ⚡️ •  = انتقال به سطل ♻️", fontSize = 9.sp, color = FGray)
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
        if (FavStore.favs.value.isEmpty()) Text("هنوز کیفی اضافه نکردی — آدرس‌های مهم رو اینجا نگه دار", fontSize = 10.sp, color = FGray)

        FavStore.favs.value.forEach { w ->
            Card(colors = CardDefaults.cardColors(containerColor = FCard), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { w.starred = !w.starred; FavStore.favs.value = ArrayList(FavStore.favs.value); FavStore.save(ctx) },
                        colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) {
                        Text(if (w.starred) "⭐" else "☆", fontSize = 14.sp)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(shortA(w.addr), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = FGold)
                        if (w.note.isNotEmpty()) Text(w.note, fontSize = 9.sp, color = FGray)
                        if (w.starred && w.lastScanTs > 0)
                            Text("آخرین بررسی: ${sdf.format(Date(w.lastScanTs))} • ارزش: ${String.format(Locale.US, "$%,.0f", w.totalUsd)} • ${w.snap.size} توکن", fontSize = 8.sp, color = FGreen)
                    }
                    Button(onClick = { FavStore.moveToTrash(ctx, w.addr) },
                        colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) { Text("🗑", fontSize = 12.sp) }
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
        FavStore.alerts.value = ArrayList(FavStore.alerts.value)
        FavStore.save(ctx)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("⚡️ هشدارهای کیف‌ها", fontWeight = FontWeight.Black, fontSize = 16.sp, color = FGold)
        Text("توکن جدید یا افزایش موجودی کیف‌های ستاره‌دار — با باز کردن این صفحه، هشدارها خونده می‌شن", fontSize = 9.sp, color = FGray)
        if (FavStore.alerts.value.isEmpty()) Text("هنوز هشدارری نیست — توی ❤️ کیف‌ها رو ستاره‌دار کن", fontSize = 10.sp, color = FGray)
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
                    }
                    Button(onClick = { FavStore.deleteForever(ctx, w.addr) },
                        colors = ButtonDefaults.buttonColors(containerColor = FCard), shape = RoundedCornerShape(6.dp)) { Text("🗑", fontSize = 12.sp) }
                }
            }
        }
    }
}
