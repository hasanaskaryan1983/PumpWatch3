package com.pumpwatch.app.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.engine.SignalParams
import com.pumpwatch.app.engine.SignalResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- یک روز از گلچین‌ها ----------

data class DayPicks(
    val date: String,      // "2026-08-26"
    val mode: String,      // "SPOT" | "FUT"
    val time: Long,
    val picks: List<SignalResult>
)

// ---------- مدیریت ذخیره‌سازی ----------

object PicksStore {

    private const val PREFS = "pumpdump_picks"
    private const val KEY_TODAY = "today_"
    private const val KEY_HISTORY = "history_"
    private const val KEY_PARAMS = "params_"
    private const val KEY_LAST_SCAN = "lastscan_"
    private const val MAX_DAYS = 30

    private val gson = Gson()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ---------- ذخیره با ادغام هوشمند ----------

    fun saveScan(ctx: Context, mode: String, picks: List<SignalResult>) {
        val limit = if (mode == "SPOT") 50 else 20

        // ادغام با سیگنال‌های قبلیِ همان روز (بهترین امتیاز نگه داشته می‌شه)
        val existing = loadToday(ctx, mode)?.picks ?: emptyList()
        val map = linkedMapOf<String, SignalResult>()
        existing.forEach { map[it.coinId + "|" + it.side] = it }
        picks.forEach { n ->
            val k = n.coinId + "|" + n.side
            val o = map[k]
            if (o == null || n.score > o.score) map[k] = n
        }
        val merged = map.values
            .sortedByDescending { it.score }
            .take(limit)
            .toList()

        val day = DayPicks(todayKey(), mode, System.currentTimeMillis(), merged)
        val p = prefs(ctx).edit()
        p.putString(KEY_TODAY + mode, gson.toJson(day))
        p.putLong(KEY_LAST_SCAN + mode, System.currentTimeMillis())

        // به‌روزرسانی تاریخچه (جایگزینی همان روز)
        val history = loadHistory(ctx, mode).toMutableList()
        val idx = history.indexOfFirst { it.date == day.date }
        if (idx >= 0) history[idx] = day else history.add(0, day)
        while (history.size > MAX_DAYS) history.removeAt(history.size - 1)
        p.putString(KEY_HISTORY + mode, gson.toJson(history))
        p.apply()
    }

    // ---------- خواندن ----------

    fun loadToday(ctx: Context, mode: String): DayPicks? {
        val json = prefs(ctx).getString(KEY_TODAY + mode, null) ?: return null
        return try {
            gson.fromJson(json, DayPicks::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun loadHistory(ctx: Context, mode: String): List<DayPicks> {
        val json = prefs(ctx).getString(KEY_HISTORY + mode, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DayPicks>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun lastScan(ctx: Context, mode: String): Long =
        prefs(ctx).getLong(KEY_LAST_SCAN + mode, 0)

    // ---------- حذف ----------

    fun deleteDay(ctx: Context, mode: String, date: String) {
        val p = prefs(ctx).edit()
        val history = loadHistory(ctx, mode).filterNot { it.date == date }
        p.putString(KEY_HISTORY + mode, gson.toJson(history))
        if (loadToday(ctx, mode)?.date == date) p.remove(KEY_TODAY + mode)
        p.apply()
    }

    fun clearAll(ctx: Context, mode: String) {
        prefs(ctx).edit()
            .remove(KEY_HISTORY + mode)
            .remove(KEY_TODAY + mode)
            .remove(KEY_LAST_SCAN + mode)
            .apply()
    }

    // ---------- پارامترهای بهینه‌شده (بک‌تست خودکار) ----------

    fun saveParams(ctx: Context, mode: String, params: SignalParams) {
        prefs(ctx).edit()
            .putString(KEY_PARAMS + mode, gson.toJson(params))
            .apply()
    }

    fun loadParams(ctx: Context, mode: String): SignalParams {
        val json = prefs(ctx).getString(KEY_PARAMS + mode, null)
            ?: return SignalParams()
        return try {
            gson.fromJson(json, SignalParams::class.java) ?: SignalParams()
        } catch (_: Exception) {
            SignalParams()
        }
    }
}
