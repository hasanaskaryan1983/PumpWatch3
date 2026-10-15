package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.store.StorageGuard

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

    // 🚀 P1-2: مهاجرت + قرنطینه
    private fun loadMap(ctx: Context): MutableMap<String, Long> {
        val p = prefs(ctx)
        val raw = p.getString(KEY_LAST_TS, "") ?: ""
        if (raw.isEmpty()) return mutableMapOf()
        val migrated = StorageGuard.migrateRaw(ctx, PREFS, KEY_LAST_TS, raw)
        return try {
            GSON.fromJson(migrated, object : TypeToken<MutableMap<String, Long>>() {}.type) ?: mutableMapOf()
        } catch (_: Exception) {
            StorageGuard.quarantine(ctx, PREFS, KEY_LAST_TS, migrated)
            p.edit().remove(KEY_LAST_TS).apply()
            mutableMapOf()
        }
    }

    private fun saveMap(ctx: Context, map: Map<String, Long>) {
        prefs(ctx).edit().putString(KEY_LAST_TS, GSON.toJson(map)).apply()
    }

    private fun bump(ctx: Context, key: String) {
        val p = prefs(ctx)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun isSuppressed(lastTs: Long, nowMs: Long, windowMs: Long): Boolean =
        lastTs > 0L && nowMs - lastTs < windowMs

    fun shouldDeliver(ctx: Context, key: String, windowMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = loadMap(ctx)[key] ?: 0L
        return if (isSuppressed(last, nowMs, windowMs)) {
            bump(ctx, KEY_SUPPRESSED)
            false
        } else true
    }

    fun recordDelivered(ctx: Context, key: String, nowMs: Long = System.currentTimeMillis()) {
        val map = loadMap(ctx)
        map[key] = nowMs
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
