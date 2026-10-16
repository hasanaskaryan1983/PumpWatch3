package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson

/**
 * 🚀 P3-3: ذخیره‌سازی فرمول سیگنال سفارشی کاربر.
 * اگر داده‌ای ذخیره نشده باشد یا خراب باشد، DEFAULT برگردانده می‌شود (fail-safe).
 */
object SignalFormulaStore {
    private const val PREFS = "pumpwatch_prefs"
    private const val KEY = "signal_formula_v1"
    private val GSON = Gson()

    fun load(ctx: Context): SignalFormula {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString(KEY, null)
            ?: return SignalFormula.DEFAULT
        return try {
            GSON.fromJson(raw, SignalFormula::class.java) ?: SignalFormula.DEFAULT
        } catch (_: Exception) {
            SignalFormula.DEFAULT
        }
    }

    fun save(ctx: Context, f: SignalFormula) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, GSON.toJson(f)).apply()
    }

    /** بازگشت به تنظیمات استاندارد و بهینه‌شده */
    fun resetToDefault(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().remove(KEY).apply()
    }

    fun isDefault(ctx: Context): Boolean = load(ctx) == SignalFormula.DEFAULT
}
