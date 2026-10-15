package com.pumpwatch.app.store

import android.content.Context

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
        val raw = c.getSharedPreferences(PREFS, 0).getString("d_$key", null) ?: return null
        
        // 🚀 P1-2b: مهاجرت نسخه‌دار + قرنطینه
        val migrated = StorageGuard.migrateRaw(c, PREFS, "d_$key", raw)
        return migrated
    }

    fun time(ctx: Context?, key: String): Long {
        val c = ctx ?: return 0L
        return c.getSharedPreferences(PREFS, 0).getLong("t_$key", 0L)
    }
}
