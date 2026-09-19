package com.pumpwatch.app.store

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import java.util.concurrent.TimeUnit

/** یک ارز در واچ‌لیست کاربر */
data class WatchItem(
    val id: String,
    val symbol: String,
    val name: String,
    val contract: String? = null,
    val rank: Int? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/** یک هشدار قیمتی روی یک ارز واچ‌لیست */
data class WatchAlert(
    val id: String,
    val coinId: String,
    val symbol: String,
    val name: String,
    val above: Boolean,
    val threshold: Double,
    val createdAt: Long,
    val triggeredAt: Long? = null,
    val triggeredPrice: Double? = null
)

/**
 * 🚀 Sprint 15 (فاز ۲ / Commit 13): واچ‌لیست + هشدارهای قیمتی شخصی
 * - ذخیره در SecureStorage (رمزنگاری‌شده)
 * - evaluate/mergeTriggered pure و قابل‌تست
 * - checkAndFire مسیر مشترک Worker و رفرش UI (هر هشدار فقط یک‌بار فعال می‌شود)
 */
object WatchlistStore {

    private const val KEY_ITEMS = "watch_items_v1"
    private const val KEY_ALERTS = "watch_alerts_v1"
    private const val CHANNEL = "watchlist_alerts"
    const val MAX_ITEMS = 50

    private val gson = Gson()

    private inline fun <reified T> loadList(ctx: Context, key: String): List<T> {
        val json = SecureStorage.getString(ctx, key) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<T>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun items(ctx: Context): List<WatchItem> = loadList(ctx, KEY_ITEMS)
    fun loadAlerts(ctx: Context): List<WatchAlert> = loadList(ctx, KEY_ALERTS)
    fun alerts(ctx: Context): List<WatchAlert> = loadAlerts(ctx).filter { it.triggeredAt == null }
    fun triggered(ctx: Context): List<WatchAlert> = loadAlerts(ctx).filter { it.triggeredAt != null }

    private fun saveItems(ctx: Context, list: List<WatchItem>) =
        SecureStorage.putString(ctx, KEY_ITEMS, gson.toJson(list))

    fun saveAlerts(ctx: Context, list: List<WatchAlert>) =
        SecureStorage.putString(ctx, KEY_ALERTS, gson.toJson(list))

    fun addItem(ctx: Context, item: WatchItem): Boolean {
        val cur = items(ctx).toMutableList()
        if (cur.any { it.id == item.id }) return false
        if (cur.size >= MAX_ITEMS) return false
        cur.add(0, item)
        saveItems(ctx, cur)
        return true
    }

    fun removeItem(ctx: Context, id: String) {
        saveItems(ctx, items(ctx).filterNot { it.id == id })
        saveAlerts(ctx, loadAlerts(ctx).filterNot { it.coinId == id })
    }

    fun addAlert(ctx: Context, a: WatchAlert) {
        saveAlerts(ctx, loadAlerts(ctx) + a)
    }

    fun updateAlert(ctx: Context, id: String, above: Boolean, threshold: Double) {
        saveAlerts(ctx, loadAlerts(ctx).map { if (it.id == id) it.copy(above = above, threshold = threshold) else it })
    }

    fun removeAlert(ctx: Context, id: String) {
        saveAlerts(ctx, loadAlerts(ctx).filterNot { it.id == id })
    }

    // ---------- منطق pure (قابل‌تست) ----------

    internal fun evaluate(active: List<WatchAlert>, priceOf: (String) -> Double?): List<WatchAlert> =
        active.filter { it.triggeredAt == null }.filter { a ->
            val p = priceOf(a.coinId) ?: return@filter false
            if (a.above) p >= a.threshold else p <= a.threshold
        }

    internal fun mergeTriggered(
        all: List<WatchAlert>,
        firedIds: Set<String>,
        priceOf: (String) -> Double?,
        nowMs: Long
    ): List<WatchAlert> =
        all.map { a ->
            if (a.id in firedIds && a.triggeredAt == null)
                a.copy(triggeredAt = nowMs, triggeredPrice = priceOf(a.coinId))
            else a
        }

    // ---------- بررسی مشترک (Worker + رفرش UI) ----------

    suspend fun checkAndFire(ctx: Context): Int {
        val active = alerts(ctx)
        if (active.isEmpty()) return 0
        val coins = try {
            ApiClient.getTop1000Coins()
        } catch (_: Exception) {
            return 0
        }
        val priceMap = coins.associate { it.id to it.current_price }
        val fired = evaluate(active) { priceMap[it] }
        if (fired.isEmpty()) return 0
        val ids = fired.map { it.id }.toSet()
        saveAlerts(ctx, mergeTriggered(loadAlerts(ctx), ids, { priceMap[it] }, System.currentTimeMillis()))
        fired.forEach { notify(ctx, it, priceMap[it.coinId]) }
        return fired.size
    }

    private fun notify(ctx: Context, a: WatchAlert, price: Double?) {
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
                .setContentTitle("🔔 واچ‌لیست: ${a.name} (${a.symbol})")
                .setContentText("هشدار تو فعال شد: قیمت $px به $dir ${String.format(Locale.US, "$%.6f", a.threshold)} رسید")
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), n)
        } catch (_: Exception) { }
    }
}

/** 🚀 Commit 13: بررسی دوره‌ای هشدارهای واچ‌لیست (حداقل بازهٔ مجاز اندروید: ۱۵ دقیقه) */
class WatchlistWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            WatchlistStore.checkAndFire(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object WatchlistScheduler {
    private const val NAME = "WatchlistAlerts"
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
