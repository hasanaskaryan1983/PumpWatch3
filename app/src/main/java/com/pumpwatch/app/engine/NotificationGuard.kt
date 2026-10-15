package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 🚀 P0 Commit 8: لایهٔ dedup در سطح «تحویل نوتیفیکیشن» + گزارش پوشش.
 *
 * تفاوت با dedupهای قبلی:
 *   - SignalLogger.log() جلوی ثبت تکراری «سیگنال» را می‌گیرد
 *   - AlertRulesStore.inCooldown() جلوی تکرار «قانون» را می‌گیرد
 *   - این لایه جلوی تکرار «تحویل نوتیفیکیشن» برای همان رویداد را می‌گیرد
 *     (مثلاً WhaleMemeWorker که قبلاً هر ۱۵ دقیقه یک نوتیفیکیشن تکراری می‌فرستاد)
 *
 * پوشش (Coverage): شمارندهٔ delivered / suppressed برای گزارش شفاف به کاربر.
 */
object NotificationGuard {

    private const val PREFS = "pumpwatch_prefs"
    private const val KEY_LAST_TS = "notif_guard_last_ts"
    private const val KEY_DELIVERED = "notif_delivered_count"
    private const val KEY_SUPPRESSED = "notif_suppressed_count"
    private const val MAX_KEYS = 200
    private val GSON = Gson()

    data class Coverage(val delivered: Int, val suppressed: Int) {
        val total: Int get() = delivered + suppressed
        val deliveryRatePct: Double get() = if (total > 0) delivered * 100.0 / total else 0.0
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    private fun loadMap(ctx: Context): MutableMap<String, Long> = try {
        val json = prefs(ctx).getString(KEY_LAST_TS, "") ?: ""
        if (json.isEmpty()) mutableMapOf()
        else GSON.fromJson(json, object : TypeToken<MutableMap<String, Long>>() {}.type) ?: mutableMapOf()
    } catch (_: Exception) { mutableMapOf() }

    private fun saveMap(ctx: Context, map: Map<String, Long>) {
        prefs(ctx).edit().putString(KEY_LAST_TS, GSON.toJson(map)).apply()
    }

    private fun bump(ctx: Context, key: String) {
        val p = prefs(ctx)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    /**
     * آیا نوتیفیکیشن این رویداد باید تحویل شود؟
     * اگر همان [key] داخل [windowMs] قبلاً تحویل شده → سرکوب (و شمارش suppressed).
     */
    fun shouldDeliver(ctx: Context, key: String, windowMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = loadMap(ctx)[key] ?: 0L
        return if (last > 0L && now - last < windowMs) {
            bump(ctx, KEY_SUPPRESSED)
            false
        } else true
    }

    /** فقط پس از موفقیت ارسال صدا زده شود */
    fun recordDelivered(ctx: Context, key: String) {
        val map = loadMap(ctx)
        map[key] = System.currentTimeMillis()
        if (map.size > MAX_KEYS) {
            val keep = map.entries.sortedByDescending { it.value }.take(MAX_KEYS)
            map.clear()
            keep.forEach { (k, v) -> map[k] = v }
        }
        saveMap(ctx, map)
        bump(ctx, KEY_DELIVERED)
    }

    fun coverage(ctx: Context): Coverage = Coverage(
        delivered = prefs(ctx).getInt(KEY_DELIVERED, 0),
        suppressed = prefs(ctx).getInt(KEY_SUPPRESSED, 0)
    )

    fun resetStats(ctx: Context) {
        prefs(ctx).edit()
            .putInt(KEY_DELIVERED, 0)
            .putInt(KEY_SUPPRESSED, 0)
            .remove(KEY_LAST_TS)
            .apply()
    }
}
