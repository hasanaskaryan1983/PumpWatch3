package com.pumpwatch.app.store

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.SecureStorage
import java.util.Locale
import java.util.UUID

data class WatchAlert(
    val id: String,
    val above: Boolean,
    val threshold: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val triggeredAt: Long? = null,
    val triggeredPrice: Double? = null
)

data class WatchCoin(
    val id: String,
    val symbol: String,
    val name: String,
    val contract: String? = null,
    val rank: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val alerts: List<WatchAlert> = emptyList()
)

data class WatchGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val coins: List<WatchCoin> = emptyList()
)

object WatchlistStore {

    private const val PREFS = "pumpwatch_prefs"
    private const val KEY_GROUPS = "watch_groups_v2"
    private const val CHANNEL = "watchlist_alerts"
    const val MAX_GROUPS = 10
    const val MAX_COINS_PER_GROUP = 50
    const val MAX_ALERTS_PER_COIN = 3

    private val gson = Gson()
    private val lock = Any()

    @Volatile
    private var lastLoadFailed = false

    private fun loadGroupsOrNull(ctx: Context): List<WatchGroup>? {
        val json = SecureStorage.getString(ctx, KEY_GROUPS) ?: return emptyList()
        
        // 🚀 P1-2b: مهاجرت نسخه‌دار + قرنطینه
        val migrated = StorageGuard.migrateRaw(ctx, PREFS, KEY_GROUPS, json)
        
        return try {
            gson.fromJson(migrated, object : TypeToken<List<WatchGroup>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("WatchlistStore", "loadGroups FAILED — quarantining", e)
            StorageGuard.quarantine(ctx, PREFS, KEY_GROUPS, migrated)
            SecureStorage.remove(ctx, KEY_GROUPS)
            null
        }
    }

    fun loadGroups(ctx: Context): List<WatchGroup> {
        synchronized(lock) {
            val result = loadGroupsOrNull(ctx)
            lastLoadFailed = (result == null)
            return result ?: emptyList()
        }
    }

    private fun persistGroups(ctx: Context, groups: List<WatchGroup>): Boolean {
        val result = SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        return result == SecureStorage.SecretResult.Saved
    }

    fun saveGroups(ctx: Context, groups: List<WatchGroup>): Boolean {
        synchronized(lock) {
            if (lastLoadFailed && groups.isEmpty()) {
                Log.e("WatchlistStore", "SAVE REJECTED: refusing to overwrite unreadable store with empty list")
                return false
            }
            val ok = persistGroups(ctx, groups)
            if (ok && groups.isNotEmpty()) lastLoadFailed = false
            return ok
        }
    }

    fun addGroup(ctx: Context, name: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.toMutableList()
            if (groups.size >= MAX_GROUPS) return false
            groups.add(0, WatchGroup(name = name))
            return persistGroups(ctx, groups)
        }
    }

    fun renameGroup(ctx: Context, groupId: String, newName: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.map { if (it.id == groupId) it.copy(name = newName) else it }
            return persistGroups(ctx, groups)
        }
    }

    fun removeGroup(ctx: Context, groupId: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.filterNot { it.id == groupId }
            return persistGroups(ctx, groups)
        }
    }

    fun addCoin(ctx: Context, groupId: String, coin: WatchCoin): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.toMutableList()
            val gIdx = groups.indexOfFirst { it.id == groupId }
            if (gIdx < 0) return false
            val group = groups[gIdx]
            if (group.coins.size >= MAX_COINS_PER_GROUP) return false
            if (group.coins.any { it.id == coin.id }) return false
            groups[gIdx] = group.copy(coins = group.coins + coin)
            return persistGroups(ctx, groups)
        }
    }

    fun removeCoin(ctx: Context, groupId: String, coinId: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.map { g ->
                if (g.id == groupId) g.copy(coins = g.coins.filterNot { it.id == coinId }) else g
            }
            return persistGroups(ctx, groups)
        }
    }

    fun addAlert(ctx: Context, groupId: String, coinId: String, alert: WatchAlert): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.toMutableList()
            val gIdx = groups.indexOfFirst { it.id == groupId }
            if (gIdx < 0) return false
            val group = groups[gIdx]
            val cIdx = group.coins.indexOfFirst { it.id == coinId }
            if (cIdx < 0) return false
            val coin = group.coins[cIdx]
            if (coin.alerts.size >= MAX_ALERTS_PER_COIN) return false
            val newCoin = coin.copy(alerts = coin.alerts + alert)
            val newCoins = group.coins.toMutableList().apply { set(cIdx, newCoin) }
            groups[gIdx] = group.copy(coins = newCoins)
            return persistGroups(ctx, groups)
        }
    }

    fun updateAlert(ctx: Context, groupId: String, coinId: String, alertId: String, above: Boolean, threshold: Double): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.map { g ->
                if (g.id != groupId) return@map g
                g.copy(coins = g.coins.map { c ->
                    if (c.id != coinId) return@map c
                    c.copy(alerts = c.alerts.map { a ->
                        if (a.id == alertId) a.copy(above = above, threshold = threshold) else a
                    })
                })
            }
            return persistGroups(ctx, groups)
        }
    }

    fun removeAlert(ctx: Context, groupId: String, coinId: String, alertId: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.map { g ->
                if (g.id != groupId) return@map g
                g.copy(coins = g.coins.map { c ->
                    if (c.id != coinId) return@map c
                    c.copy(alerts = c.alerts.filterNot { it.id == alertId })
                })
            }
            return persistGroups(ctx, groups)
        }
    }

    fun rearmAlert(ctx: Context, groupId: String, coinId: String, alertId: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                return false
            }
            lastLoadFailed = false
            val groups = current.map { g ->
                if (g.id != groupId) return@map g
                g.copy(coins = g.coins.map { c ->
                    if (c.id != coinId) return@map c
                    c.copy(alerts = c.alerts.map { a ->
                        if (a.id == alertId) a.copy(triggeredAt = null, triggeredPrice = null) else a
                    })
                })
            }
            return persistGroups(ctx, groups)
        }
    }

    internal fun evaluate(
        groups: List<WatchGroup>,
        priceOf: (String) -> Double?
    ): List<Triple<String, String, WatchAlert>> {
        val fired = mutableListOf<Triple<String, String, WatchAlert>>()
        for (g in groups) {
            for (c in g.coins) {
                val price = priceOf(c.id) ?: continue
                for (a in c.alerts) {
                    if (a.triggeredAt != null) continue
                    val trigger = if (a.above) price >= a.threshold else price <= a.threshold
                    if (trigger) fired.add(Triple(g.id, c.id, a))
                }
            }
        }
        return fired
    }

    internal fun markTriggered(
        groups: List<WatchGroup>,
        fired: List<Triple<String, String, WatchAlert>>,
        priceOf: (String) -> Double?,
        nowMs: Long
    ): List<WatchGroup> {
        val firedMap = fired.groupBy({ it.first to it.second }, { it.third })
        return groups.map { g ->
            g.copy(coins = g.coins.map { c ->
                val firedAlerts = firedMap[g.id to c.id] ?: return@map c
                c.copy(alerts = c.alerts.map { a ->
                    if (firedAlerts.any { it.id == a.id } && a.triggeredAt == null)
                        a.copy(triggeredAt = nowMs, triggeredPrice = priceOf(c.id))
                    else a
                })
            })
        }
    }

    private fun markEval(ctx: Context, ok: Boolean) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putBoolean("watchlist_last_eval_ok", ok)
            .putLong("watchlist_last_eval_ts", System.currentTimeMillis())
            .apply()
    }

    fun lastEvalStatus(ctx: Context): Pair<Boolean, Long> {
        val p = ctx.getSharedPreferences(PREFS, 0)
        return p.getBoolean("watchlist_last_eval_ok", true) to p.getLong("watchlist_last_eval_ts", 0L)
    }

    suspend fun checkAndFire(ctx: Context): Int {
        val groups = loadGroups(ctx)
        if (groups.isEmpty()) {
            markEval(ctx, ok = true)
            return 0
        }
        val coins = try {
            ApiClient.getTop1000Coins()
        } catch (e: Exception) {
            Log.w("WatchlistStore", "checkAndFire: API failed (transient)", e)
            markEval(ctx, ok = false)
            return 0
        }
        val priceMap = coins.associate { it.id to it.current_price }
        val fired = evaluate(groups) { priceMap[it] }
        if (fired.isEmpty()) {
            markEval(ctx, ok = true)
            return 0
        }
        val updated = markTriggered(groups, fired, { priceMap[it] }, System.currentTimeMillis())
        val saved = saveGroups(ctx, updated)
        if (!saved) {
            Log.w("WatchlistStore", "checkAndFire: triggered alerts could not be persisted")
            markEval(ctx, ok = false)
            return 0
        }
        fired.forEach { (groupId, coinId, alert) ->
            val coin = groups.flatMap { it.coins }.find { it.id == coinId }
            if (coin != null) notify(ctx, coin, alert, priceMap[coinId])
        }
        markEval(ctx, ok = true)
        return fired.size
    }

    private fun notify(ctx: Context, coin: WatchCoin, a: WatchAlert, price: Double?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "هشدارهای واچ‌لیست", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            val dir = if (a.above) "بالای" else "زیرِ"
            val px = if (price != null) String.format(Locale.US, "$%.6f", price) else "—"
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🔔 واچ‌لیست: ${coin.name} (${coin.symbol})")
                .setContentText("هشدار فعال شد: قیمت $px به $dir ${String.format(Locale.US, "$%.6f", a.threshold)} رسید")
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), n)
        } catch (e: Exception) {
            Log.w("WatchlistStore", "notify failed (non-critical)", e)
        }
    }
}
