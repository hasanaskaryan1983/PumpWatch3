package com.pumpwatch.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 🚀 Commit 91 (A5): ذخیرهٔ توکن‌های دنبال‌شده برای Worker پس‌زمینه.
 * ساختار ساده با SharedPreferences.
 */
data class WatchlistEntry(
    val symbol: String,
    val chain: String?,
    val contract: String?,
    val addedAt: Long = System.currentTimeMillis()
)

object WatchlistStore {

    private const val PREFS_NAME = "pumpwatch_prefs"
    private const val KEY_WATCHLIST = "watchlist_v1"
    private val gson = Gson()

    fun load(context: Context): List<WatchlistEntry> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_WATCHLIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<WatchlistEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, entries: List<WatchlistEntry>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(entries)
        prefs.edit().putString(KEY_WATCHLIST, json).apply()
    }

    fun add(context: Context, entry: WatchlistEntry) {
        val current = load(context).toMutableList()
        current.removeAll { it.symbol.equals(entry.symbol, ignoreCase = true) }
        current.add(entry)
        save(context, current)
    }

    fun remove(context: Context, symbol: String) {
        val current = load(context).toMutableList()
        current.removeAll { it.symbol.equals(symbol, ignoreCase = true) }
        save(context, current)
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }
}
