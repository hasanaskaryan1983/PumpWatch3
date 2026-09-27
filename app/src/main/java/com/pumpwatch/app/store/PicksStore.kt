package com.pumpwatch.app.store

import android.content.Context
import android.util.Log
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

/**
 * 🚀 Commit 70 (فاز ۱ — پایداری داده): PicksStore thread-safety + no-wipe guarantee
 *
 * سه باگ بسته شد (CONSTITUTION بندهای ۸، ۹):
 *
 * ۱) Race condition (بند ۹): `saveScan` و `deleteDay` داخل `synchronized(lock)`
 *    → Worker و UI هم‌زمان داده را overwrite نمی‌کنند
 *
 * ۲) load error ≠ save empty (بند ۸): `loadHistoryOrNull` مقدار null
 *    برمی‌گرداند روی شکست parse. هر نوشتنی که بخواهد لیست خالی
 *    را روی store خراب بنویسد، REJECT می‌شود.
 *
 * ۳) add(0) inefficiency: MAX_DAYS=30، O(n) نامرئی → P3 (hand-waved)
 */
object PicksStore {

    private const val PREFS = "pumpdump_picks"
    private const val KEY_TODAY = "today_"
    private const val KEY_HISTORY = "history_"
    private const val KEY_PARAMS = "params_"
    private const val KEY_LAST_SCAN = "lastscan_"
    private const val MAX_DAYS = 30

    private val gson = Gson()

    /** قفل برای عملیات Read-Modify-Write (بند ۹) */
    private val lock = Any()

    /**
     * پرچم «آخرین خواندن شکست خورد» (per mode).
     * تا وقتی true است، هیچ save خالی‌ای پذیرفته نمی‌شود (بند ۸).
     */
    @Volatile
    private var lastLoadFailedSPOT = false
    @Volatile
    private var lastLoadFailedFUT = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ---------- خواندن با no-wipe guarantee ----------

    /**
     * 🚀 Commit 70 (بند ۸): نسخهٔ صادقِ خواندن.
     * - اگر داده‌ای وجود ندارد → emptyList (این «شکست» نیست)
     * - اگر parse شکست بخورد → **null** (یعنی داده هست ولی خوانده نمی‌شود)
     */
    private fun loadHistoryOrNull(ctx: Context, mode: String): List<DayPicks>? {
        val json = prefs(ctx).getString(KEY_HISTORY + mode, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<DayPicks>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("PicksStore", "loadHistory FAILED for $mode — data exists but unreadable; NOT returning empty", e)
            null
        }
    }

    fun loadToday(ctx: Context, mode: String): DayPicks? {
        val json = prefs(ctx).getString(KEY_TODAY + mode, null) ?: return null
        return try {
            gson.fromJson(json, DayPicks::class.java)
        } catch (e: Exception) {
            Log.w("PicksStore", "loadToday parse failed for $mode (returning null)", e)
            null
        }
    }

    fun loadHistory(ctx: Context, mode: String): List<DayPicks> {
        synchronized(lock) {
            val result = loadHistoryOrNull(ctx, mode)
            if (mode == "SPOT") lastLoadFailedSPOT = (result == null)
            else lastLoadFailedFUT = (result == null)
            return result ?: emptyList()
        }
    }

    fun lastScan(ctx: Context, mode: String): Long =
        prefs(ctx).getLong(KEY_LAST_SCAN + mode, 0)

    // ---------- ذخیره با ادغام هوشمند + no-wipe ----------

    fun saveScan(ctx: Context, mode: String, picks: List<SignalResult>) {
        synchronized(lock) {
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
            // 🚀 Commit 70 (بند ۸): اگر loadHistory شکست خورده و لیست خالی است، REJECT
            val currentHistory = loadHistoryOrNull(ctx, mode)
            val lastFailed = if (mode == "SPOT") lastLoadFailedSPOT else lastLoadFailedFUT
            if (currentHistory == null && lastFailed) {
                Log.e("PicksStore", "saveScan ABORTED for $mode: store unreadable — refusing to overwrite")
                // فقط today را save کن، history را دست نزن
                p.apply()
                return
            }
            if (mode == "SPOT") lastLoadFailedSPOT = false else lastLoadFailedFUT = false

            val history = (currentHistory ?: emptyList()).toMutableList()
            val idx = history.indexOfFirst { it.date == day.date }
            if (idx >= 0) history[idx] = day else history.add(0, day)
            while (history.size > MAX_DAYS) history.removeAt(history.size - 1)
            p.putString(KEY_HISTORY + mode, gson.toJson(history))
            p.apply()
        }
    }

    // ---------- حذف ----------

    fun deleteDay(ctx: Context, mode: String, date: String) {
        synchronized(lock) {
            val currentHistory = loadHistoryOrNull(ctx, mode)
            if (currentHistory == null) {
                val lastFailed = if (mode == "SPOT") lastLoadFailedSPOT else lastLoadFailedFUT
                if (lastFailed) {
                    Log.e("PicksStore", "deleteDay ABORTED for $mode: store unreadable")
                    return
                }
            }
            if (mode == "SPOT") lastLoadFailedSPOT = false else lastLoadFailedFUT = false

            val p = prefs(ctx).edit()
            val history = (currentHistory ?: emptyList()).filterNot { it.date == date }
            p.putString(KEY_HISTORY + mode, gson.toJson(history))
            if (loadToday(ctx, mode)?.date == date) p.remove(KEY_TODAY + mode)
            p.apply()
        }
    }

    fun clearAll(ctx: Context, mode: String) {
        synchronized(lock) {
            prefs(ctx).edit()
                .remove(KEY_HISTORY + mode)
                .remove(KEY_TODAY + mode)
                .remove(KEY_LAST_SCAN + mode)
                .apply()
            if (mode == "SPOT") lastLoadFailedSPOT = false else lastLoadFailedFUT = false
        }
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
        } catch (e: Exception) {
            Log.w("PicksStore", "loadParams parse failed for $mode (returning default)", e)
            SignalParams()
        }
    }
}
