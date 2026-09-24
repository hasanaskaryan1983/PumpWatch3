package com.pumpwatch.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10

/**
 * 🚀 Sprint 16 — تب تاپ تریدرها (v0)
 * منبع داده: شکارهای ثبت‌شدهٔ موتور ۶ + افزودن دستی توسط کاربر.
 * صادقانه: این «رتبه‌بندی جهانی» نیست؛ فقط تریدرهایی که خود اپ کشف/ردیابی می‌کند.
 */
data class TraderProfile(
    val addr: String,
    val chain: String,              // "solana" | "evm" | "ton" | "sui"
    val symbol: String,
    val note: String,
    val addedAtMs: Long,
    var lastSeenTs: Long,
    var lastTxId: String,
    var boughtUsd: Double,
    var maxSingleUsd: Double,
    var soldUsd: Double,
    var txCount: Int,
    var multiplier: Double,
    var pumpsCount: Int,
    var alertOn: Boolean
)

data class TraderActivity(val ts: Long, val txId: String)

object TraderStore {

    private const val PREF = "trader_store_v1"
    private const val KEY = "traders"
    private val gson = Gson()
    private val cache = mutableListOf<TraderProfile>()
    private var loaded = false

    fun load(ctx: Context): List<TraderProfile> {
        if (loaded) return cache
        val json = ctx.getSharedPreferences(PREF, 0).getString(KEY, null)
        cache.clear()
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<MutableList<TraderProfile>>() {}.type
                val list: MutableList<TraderProfile>? = gson.fromJson(json, type)
                if (list != null) cache.addAll(list)
            } catch (_: Exception) { }
        }
        loaded = true
        return cache
    }

    private fun persist(ctx: Context) {
        ctx.getSharedPreferences(PREF, 0).edit().putString(KEY, gson.toJson(cache)).apply()
    }

    fun list(ctx: Context): List<TraderProfile> = load(ctx).toList()

    fun ranked(ctx: Context): List<TraderProfile> =
        load(ctx).sortedByDescending { scoreOf(it) }.take(50)

    fun scoreOf(t: TraderProfile): Int {
        val pumpPart = t.pumpsCount * 20
        val multPart = (t.multiplier.coerceIn(0.0, 10.0) * 5).toInt()
        val volPart = if (t.boughtUsd > 0) (log10(t.boughtUsd) * 6).toInt().coerceIn(0, 30) else 0
        return pumpPart + multPart + volPart
    }

    fun upsert(ctx: Context, p: TraderProfile) {
        load(ctx)
        val i = cache.indexOfFirst { it.addr == p.addr }
        if (i >= 0) cache[i] = p else cache.add(0, p)
        persist(ctx)
    }

    fun remove(ctx: Context, addr: String) {
        load(ctx)
        cache.removeAll { it.addr == addr }
        persist(ctx)
    }

    fun setAlert(ctx: Context, addr: String, on: Boolean) {
        load(ctx)
        cache.firstOrNull { it.addr == addr }?.let { it.alertOn = on }
        persist(ctx)
    }

    fun markSeen(ctx: Context, addr: String, ts: Long, txId: String) {
        load(ctx)
        cache.firstOrNull { it.addr == addr }?.let {
            it.lastSeenTs = ts
            it.lastTxId = txId
        }
        persist(ctx)
    }

    fun detectChain(addr: String): String = when {
        addr.startsWith("0x") && addr.length == 42 -> "evm"
        addr.startsWith("0x") && addr.length >= 64 -> "sui"
        addr.startsWith("EQ") || addr.startsWith("UQ") || addr.startsWith("0:") -> "ton"
        else -> "solana"
    }

    /** v0: فقط Solana بررسی فعالیت ارزان و پایدار دارد */
    suspend fun checkNewActivity(ctx: Context, t: TraderProfile): TraderActivity? {
        if (t.chain != "solana") return null
        return try {
            val resp = solanaRaw(
                mapOf(
                    "jsonrpc" to "2.0", "id" to 1,
                    "method" to "getSignaturesForAddress",
                    "params" to listOf(t.addr, mapOf("limit" to 1))
                ),
                ctx = ctx
            )
            val o = resp?.result?.asJsonArray?.get(0)?.asJsonObject ?: return null
            val ts = (o.get("blockTime")?.asLong ?: 0L) * 1000L
            val sig = o.get("signature")?.asString ?: return null
            if (ts > t.lastSeenTs && sig != t.lastTxId) TraderActivity(ts, sig) else null
        } catch (_: Exception) { null }
    }

    fun notify(ctx: Context, t: TraderProfile, a: TraderActivity) {
        val channelId = "trader_alerts"
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "هشدار تاپ تریدرها", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.US)
        val text = "تریدر ${short(t.addr)} روی ${t.symbol} فعالیت جدید داشت (${sdf.format(Date(a.ts))})"
        val n = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🐋 تاپ تریدر فعال شد")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(("trader_" + t.addr).hashCode(), n)
    }

    fun short(a: String): String = if (a.length > 12) "${a.take(6)}...${a.takeLast(4)}" else a
}
