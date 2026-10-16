package com.pumpwatch.app.engine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CustomStrategy(
    val id: String,
    val name: String,
    val description: String,
    val minScore: Int,
    val minChange1h: Double,
    val minVolumeRatio: Double,
    var isEnabled: Boolean
)

object CustomStrategiesStore {
    private const val PREFS = "pumpwatch_prefs"
    private const val KEY = "custom_strategies"
    private val gson = Gson()

    private val defaultStrategies = listOf(
        CustomStrategy(
            id = "early_pump",
            name = "شکار پامپ زودهنگام",
            description = "امتیاز ≥ ۶۰، تغییر ۱س ≥ ۲٪، حجم بالا",
            minScore = 60,
            minChange1h = 2.0,
            minVolumeRatio = 1.2,
            isEnabled = false
        ),
        CustomStrategy(
            id = "whale_hunter",
            name = "شکار نهنگ",
            description = "تغییر ۱س ≥ ۱٪، نسبت حجم ≥ ۲.۰",
            minScore = 50,
            minChange1h = 1.0,
            minVolumeRatio = 2.0,
            isEnabled = false
        ),
        CustomStrategy(
            id = "bottom_reversal",
            name = "برگشت از کف",
            description = "تغییر ۱س ≤ -۳٪، تغییر ۲۴س ≥ ۰٪",
            minScore = 40,
            minChange1h = -3.0,
            minVolumeRatio = 1.5,
            isEnabled = false
        )
    )

    fun load(ctx: Context): List<CustomStrategy> {
        val p = ctx.getSharedPreferences(PREFS, 0)
        val json = p.getString(KEY, null) ?: return defaultStrategies
        return try {
            val type = object : TypeToken<List<CustomStrategy>>() {}.type
            val loaded = gson.fromJson<List<CustomStrategy>>(json, type)
            // ادغام با مقادیر پیش‌فرض برای اطمینان از سازگاری در آپدیت‌های آینده
            defaultStrategies.map { def ->
                loaded.find { it.id == def.id }?.copy(isEnabled = it.isEnabled) ?: def
            }
        } catch (e: Exception) {
            defaultStrategies
        }
    }

    fun save(ctx: Context, strategies: List<CustomStrategy>) {
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString(KEY, gson.toJson(strategies))
            .apply()
    }

    fun toggle(ctx: Context, id: String) {
        val list = load(ctx).toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isEnabled = !list[idx].isEnabled)
            save(ctx, list)
        }
    }
}
