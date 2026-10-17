package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.store.StorageGuard

/**
 * 🚀 P4-1: یک نقطهٔ تغییر در تنظیمات (مرز بین دو دورهٔ عملکرد).
 */
data class EraMark(val ts: Long, val label: String)

/**
 * 🚀 P4-1: ذخیرهٔ نقاط تغییر برای مقایسهٔ قبل/بعد.
 *
 * - [onConfigChanged]: برای تغییرات پرتکرار (مثل فرمول) با debounce پنج‌دقیقه‌ای
 * - [mark]: برای تغییرهای مهم (مثل بازگشت به بهترین) بدون debounce
 *
 * Fail-safe: دادهٔ خراب → لیست خالی (بدون crash).
 */
object EraStore {
    private const val PREFS = "pumpwatch_prefs"
    private const val KEY = "era_marks_v1"
    private const val DEBOUNCE_MS = 5 * 60_000L
    private const val MAX_MARKS = 50
    private val GSON = Gson()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    fun load(ctx: Context): List<EraMark> {
        val p = prefs(ctx)
        val raw = p.getString(KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        val migrated = StorageGuard.migrateRaw(ctx, PREFS, KEY, raw)
        return try {
            GSON.fromJson(migrated, object : TypeToken<List<EraMark>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            StorageGuard.quarantine(ctx, PREFS, KEY, migrated)
            p.edit().remove(KEY).apply()
            emptyList()
        }
    }

    private fun save(ctx: Context, marks: List<EraMark>) {
        prefs(ctx).edit().putString(KEY, GSON.toJson(marks)).apply()
    }

    /**
     * ثبت تغییر تنظیمات با debounce: تغییرهای پشت‌سرهم با همان برچسب
     * در بازهٔ ۵ دقیقه فقط یک مرز ایجاد می‌کنند.
     */
    fun onConfigChanged(ctx: Context, label: String, nowMs: Long = System.currentTimeMillis()) {
        val marks = load(ctx).toMutableList()
        val lastSame = marks.lastOrNull { it.label == label }
        if (lastSame != null && nowMs - lastSame.ts < DEBOUNCE_MS) return
        marks.add(EraMark(nowMs, label))
        while (marks.size > MAX_MARKS) marks.removeAt(0)
        save(ctx, marks)
    }

    /** ثبت بدون debounce (برای رویدادهای مهم مثل بازگشت به بهترین تنظیمات). */
    fun mark(ctx: Context, label: String, nowMs: Long = System.currentTimeMillis()) {
        val marks = load(ctx).toMutableList()
        marks.add(EraMark(nowMs, label))
        while (marks.size > MAX_MARKS) marks.removeAt(0)
        save(ctx, marks)
    }

    fun lastMark(ctx: Context): EraMark? = load(ctx).maxByOrNull { it.ts }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }
}
