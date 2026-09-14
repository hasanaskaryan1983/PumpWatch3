package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson

/**
 * 🚀 Sprint 4 (Walk-Forward): زیرساخت مرکزی خواندن/نوشتن پارامترهای سیگنال.
 *
 * طراحی:
 *  - پارامترها در SharedPreferences تحت کلید "optimized_signal_params" ذخیره می‌شوند
 *  - اگر هیچ پارامتر بهینه‌شده‌ای ذخیره نشده باشد، default های SignalParams برگردانده می‌شود
 *  - WalkForwardOptimizer (Commit بعدی) پس از رسیدن به ۵۰ سیگنال، پارامتر بهینه را
 *    با save() می‌نویسد؛ از آن به بعد همهٔ موتورها همان را استفاده می‌کنند
 *  - clear() برای بازگشت دستی به defaults (مثلاً از تنظیمات آینده)
 *
 * نکتهٔ thread-safety: SharedPreferences خودش thread-safe است؛ Gson هم stateless.
 * پس این object بدون lock ایمن است.
 */
object ParamsStore {

    private const val PREFS_NAME = "pumpwatch_prefs"
    private const val KEY = "optimized_signal_params"
    private val GSON = Gson()

    /**
     * خواندن پارامترهای فعال.
     * اگر ذخیره‌نشده یا خراب باشد → default های SignalParams (همان رفتار فعلی اپ).
     */
    fun load(ctx: Context): SignalParams {
        return try {
            val json = ctx.getSharedPreferences(PREFS_NAME, 0).getString(KEY, "") ?: ""
            if (json.isEmpty()) SignalParams()
            else GSON.fromJson(json, SignalParams::class.java) ?: SignalParams()
        } catch (_: Exception) {
            SignalParams()
        }
    }

    /**
     * ذخیرهٔ پارامترهای بهینه‌شده (توسط WalkForwardOptimizer).
     */
    fun save(ctx: Context, p: SignalParams) {
        ctx.getSharedPreferences(PREFS_NAME, 0).edit()
            .putString(KEY, GSON.toJson(p))
            .apply()
    }

    /**
     * بازگشت به defaults (حذف پارامترهای بهینه‌شده).
     */
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, 0).edit().remove(KEY).apply()
    }

    /**
     * آیا پارامتر بهینه‌شده‌ای ذخیره شده؟ (برای نمایش در داشبورد)
     */
    fun isOptimized(ctx: Context): Boolean {
        return try {
            val json = ctx.getSharedPreferences(PREFS_NAME, 0).getString(KEY, "") ?: ""
            json.isNotEmpty()
        } catch (_: Exception) { false }
    }

    /**
     * نگاشت SignalParams → UnifiedSignalParams
     * (چون UnifiedSignalEngine نوع خودش را می‌گیرد)
     */
    fun toUnified(p: SignalParams): UnifiedSignalParams = UnifiedSignalParams(
        rsiPeriod = p.rsiPeriod,
        adxMin = p.adxMin,
        volumeMin = p.volumeMin,
        breakoutLookback = p.breakoutLookback,
        minScore = p.minScore,
        goldenScore = p.goldenScore,
        atrMult = p.atrMult,
        rr = p.rr
    )
}
