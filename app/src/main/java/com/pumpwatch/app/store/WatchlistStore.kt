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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.ApiClient
import com.pumpwatch.app.data.SecureStorage
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 🚀 Sprint 15 (فاز ۲ / Commit 16): واچ‌لیست گروه‌بندی‌شده
 * 🚀 Commit 70 (فاز ۱ — پایداری داده): thread-safety + no-wipe guarantee
 * 🚀 Commit 114: ثبت وضعیت ارزیابی (markEval) برای UI صادقانه
 * 🚀 Commit 115: rearmAlert برای فعال‌سازی مجدد هشدارهای تریگرشده
 */

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

    private const val KEY_GROUPS = "watch_groups_v2"
    private const val CHANNEL = "watchlist_alerts"
    const val MAX_GROUPS = 10
    const val MAX_COINS_PER_GROUP = 50
    const val MAX_ALERTS_PER_COIN = 3

    private val gson = Gson()

    /** قفل برای عملیات Read-Modify-Write (بند ۹) */
    private val lock = Any()

    /**
     * پرچم «آخرین خواندن شکست خورد».
     * تا وقتی true است، هیچ save خالی‌ای پذیرفته نمی‌شود (بند ۸).
     */
    @Volatile
    private var lastLoadFailed = false

    // ---------- خواندن/نوشتن ----------

    private fun loadGroupsOrNull(ctx: Context): List<WatchGroup>? {
        val json = SecureStorage.getString(ctx, KEY_GROUPS) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<WatchGroup>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("WatchlistStore", "loadGroups FAILED — data exists but unreadable; NOT returning empty", e)
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

    fun saveGroups(ctx: Context, groups: List<WatchGroup>) {
        synchronized(lock) {
            if (lastLoadFailed && groups.isEmpty()) {
                Log.e("WatchlistStore", "SAVE REJECTED: refusing to overwrite unreadable store with empty list")
                return
            }
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
            if (groups.isNotEmpty()) lastLoadFailed = false
        }
    }

    // ---------- مدیریت گروه‌ها ----------

    fun addGroup(ctx: Context, name: String): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "addGroup ABORTED: store unreadable")
                return false
            }
            lastLoadFailed = false
            val groups = current.toMutableList()
            if (groups.size >= MAX_GROUPS) return false
            groups.add(0, WatchGroup(name = name))
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
            return true
        }
    }

    fun renameGroup(ctx: Context, groupId: String, newName: String) {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "renameGroup ABORTED: store unreadable")
                return
            }
            lastLoadFailed = false
            val groups = current.map { if (it.id == groupId) it.copy(name = newName) else it }
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        }
    }

    fun removeGroup(ctx: Context, groupId: String) {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "removeGroup ABORTED: store unreadable")
                return
            }
            lastLoadFailed = false
            val groups = current.filterNot { it.id == groupId }
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        }
    }

    // ---------- مدیریت ارزها ----------

    fun addCoin(ctx: Context, groupId: String, coin: WatchCoin): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "addCoin ABORTED: store unreadable")
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
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
            return true
        }
    }

    fun removeCoin(ctx: Context, groupId: String, coinId: String) {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "removeCoin ABORTED: store unreadable")
                return
            }
            lastLoadFailed = false
            val groups = current.map { g ->
                if (g.id == groupId) g.copy(coins = g.coins.filterNot { it.id == coinId }) else g
            }
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        }
    }

    // ---------- مدیریت هشدارها ----------

    fun addAlert(ctx: Context, groupId: String, coinId: String, alert: WatchAlert): Boolean {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "addAlert ABORTED: store unreadable")
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
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
            return true
        }
    }

    fun updateAlert(ctx: Context, groupId: String, coinId: String, alertId: String, above: Boolean, threshold: Double) {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "updateAlert ABORTED: store unreadable")
                return
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
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        }
    }

    fun removeAlert(ctx: Context, groupId: String, coinId: String, alertId: String) {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "removeAlert ABORTED: store unreadable")
                return
            }
            lastLoadFailed = false
            val groups = current.map { g ->
                if (g.id != groupId) return@map g
                g.copy(coins = g.coins.map { c ->
                    if (c.id != coinId) return@map c
                    c.copy(alerts = c.alerts.filterNot { it.id == alertId })
                })
            }
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        }
    }

    /**
     * 🚀 Commit 115: re-arm کردن یک هشدار تریگرشده.
     * triggeredAt و triggeredPrice را null می‌کند تا هشدار دوباره فعال شود.
     * این برای کاربرانی است که می‌خواهند هشدار را بدون حذف مجدد، دوباره فعال کنند.
     */
    fun rearmAlert(ctx: Context, groupId: String, coinId: String, alertId: String) {
        synchronized(lock) {
            val current = loadGroupsOrNull(ctx)
            if (current == null) {
                lastLoadFailed = true
                Log.e("WatchlistStore", "rearmAlert ABORTED: store unreadable")
                return
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
            SecureStorage.putString(ctx, KEY_GROUPS, gson.toJson(groups))
        }
    }

    // ---------- منطق pure (قابل‌تست) ----------

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

    // ---------- بررسی مشترک (MonitorWorker — Commit 114) ----------

    /**
     * 🚀 Commit 114: ثبت وضعیت ارزیابی تا UI بتواند صادقانه بگوید
     * «ارزیابی نشده» (قطع شبکه) در مقابل «بدون هشدار».
     */
    private fun markEval(ctx: Context, ok: Boolean) {
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("watchlist_last_eval_ok", ok)
            .putLong("watchlist_last_eval_ts", System.currentTimeMillis())
            .apply()
    }

    /** وضعیت آخرین ارزیابی: (موفق؟، زمان) — برای نمایش در WatchlistScreen */
    fun lastEvalStatus(ctx: Context): Pair<Boolean, Long> {
        val p = ctx.getSharedPreferences("pumpwatch_prefs", 0)
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
            // 🚀 Commit 114: «ارزیابی نشده» ثبت می‌شود، نه «بدون هشدار»
            markEval(ctx, ok = false)
            return 0
        }
        val priceMap = coins.associate { it.id to it.current_price }
        val fired = evaluate(groups) { priceMap[it] }
        if (fired.isEmpty()) {
            markEval(ctx, ok = true)
            return 0
        }
        saveGroups(ctx, markTriggered(groups, fired, { priceMap[it] }, System.currentTimeMillis()))
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

/** 🚀 Commit 16: بررسی دوره‌ای هشدارهای واچ‌لیست (هر ۱۵ دقیقه) */
@Deprecated("ارزیابی به MonitorWorker منتقل شد (Commit 114)")
class WatchlistWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            WatchlistStore.checkAndFire(applicationContext)
            Result.success()
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isTransient = msg.contains("timeout", true) ||
                msg.contains("429") ||
                msg.contains("503") ||
                msg.contains("502") ||
                msg.contains("504") ||
                e::class.java.simpleName.contains("Timeout")
            if (isTransient) {
                Log.w("WatchlistWorker", "Transient error, will retry", e)
                Result.retry()
            } else {
                Log.e("WatchlistWorker", "Terminal error, failing", e)
                Result.failure()
            }
        }
    }
}

/**
 * 🚀 Commit 114: DEPRECATED — ارزیابی به MonitorWorker منتقل شد.
 * stop() را یک‌بار از MainActivity صدا بزن تا کار دوره‌ای قدیمی
 * روی دستگاه‌هایی که نسخهٔ قبلی را داشته‌اند لغو شود.
 */
@Deprecated("ارزیابی واچ‌لیست حالا داخل MonitorWorker اجرا می‌شود (Commit 114)")
object WatchlistScheduler {
    private const val NAME = "WatchlistAlerts"

    /** لغو کار دوره‌ای قدیمی (ضروری برای دستگاه‌های آپدیت‌شده) */
    fun stop(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }

    fun start(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<WatchlistWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            NAME, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }
}
