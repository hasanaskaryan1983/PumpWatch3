package com.pumpwatch.app.engine

import android.content.Context

/**
 * 🚀 P3-2: ذخیرهٔ «حرارت بازار» برای زمان‌بندی تطبیقی اسکن.
 *
 * منطق:
 *   - بعد از هر اسکن کامل، تعداد سیگنال‌های ارزشمند ثبت می‌شود.
 *   - بر اساس آن، تعداد «اعتبار رد شدن» (skip credits) برای اجراهای بعدی تنظیم می‌شود:
 *       آرام (۰)      → ۲ اعتبار رد شدن  (اسکن مؤثر ~۴۵ دقیقه)
 *       معمولی (۱-۲)  → ۱ اعتبار رد شدن  (اسکن مؤثر ~۳۰ دقیقه)
 *       داغ (≥۳)      → ۰ اعتبار          (اسکن هر اجرا)
 *
 * Fail-safe: هرگونه دادهٔ خراب یا غایب → بدون رد شدن (اسکن عادی).
 */
object MarketHeatStore {

    private const val PREFS = "pumpwatch_prefs"
    private const val KEY_SKIP = "heat_skip_runs"
    private const val KEY_LAST_WORTHY = "heat_last_worthy"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)

    private fun loadSkip(ctx: Context): Int = try {
        prefs(ctx).getInt(KEY_SKIP, 0).coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }

    /** تعداد سیگنال‌های ارزشمند آخرین اسکن کامل */
    fun lastWorthy(ctx: Context): Int = try {
        prefs(ctx).getInt(KEY_LAST_WORTHY, 0).coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }

    /**
     * یک اعتبار رد شدن مصرف می‌کند (اگر وجود داشته باشد).
     *
     * @return true یعنی این اجرا باید اسکن سنگین را رد کند.
     */
    fun consumeSkip(ctx: Context): Boolean {
        val current = loadSkip(ctx)
        return if (current > 0) {
            prefs(ctx).edit().putInt(KEY_SKIP, current - 1).apply()
            true
        } else {
            false
        }
    }

    /**
     * حرارت یک اسکن کامل را ثبت و اعتبارهای رد شدن آینده را تنظیم می‌کند.
     */
    fun recordRun(ctx: Context, worthyCount: Int) {
        val worthy = worthyCount.coerceAtLeast(0)
        val skip = when {
            worthy <= 0 -> 2   // آرام
            worthy <= 2 -> 1   // معمولی
            else -> 0          // داغ
        }
        prefs(ctx).edit()
            .putInt(KEY_LAST_WORTHY, worthy)
            .putInt(KEY_SKIP, skip)
            .apply()
    }

    fun reset(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_SKIP)
            .remove(KEY_LAST_WORTHY)
            .apply()
    }
}
