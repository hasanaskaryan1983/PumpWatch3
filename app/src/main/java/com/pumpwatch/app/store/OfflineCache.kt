package com.pumpwatch.app.store

import android.content.Context

// ---------- کش آفلاین (ذخیره روی گوشی) ----------

object OfflineCache {

    private const val PREFS = "pumpdump_offline"

    fun save(ctx: Context?, key: String, json: String) {
        val c = ctx ?: return
        c.getSharedPreferences(PREFS, 0).edit()
            .putString("d_$key", json)
            .putLong("t_$key", System.currentTimeMillis())
            .apply()
    }

    fun load(ctx: Context?, key: String): String? {
        val c = ctx ?: return null
        return c.getSharedPreferences(PREFS, 0).getString("d_$key", null)
    }

    fun time(ctx: Context?, key: String): Long {
        val c = ctx ?: return 0L
        return c.getSharedPreferences(PREFS, 0).getLong("t_$key", 0L)
    }
}
