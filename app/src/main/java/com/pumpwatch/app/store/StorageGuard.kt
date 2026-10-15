package com.pumpwatch.app.store

import android.content.Context

/**
 * 🚀 P1-2: مهاجرت نسخه‌دار + قرنطینهٔ دادهٔ خراب.
 *
 * قانون طلایی: هیچ دادهٔ کاربری هرگز بی‌صدا دور ریخته نمی‌شود.
 *  - هر store یک کلید `<key>_schema_version` دارد
 *  - اگر JSON خراب باشد → به `<key>_quarantine` منتقل می‌شود (قابل بازیابی)
 *  - تغییرات آیندهٔ ساختار → تابع مهاجرت در [migrateRaw] ثبت می‌شود
 */
object StorageGuard {

    const val CURRENT_SCHEMA = 1

    private fun prefs(ctx: Context, name: String) = ctx.getSharedPreferences(name, 0)

    fun schemaVersion(ctx: Context, prefsName: String, storeKey: String): Int =
        prefs(ctx, prefsName).getInt(storeKey + "_schema_version", 0)

    fun setSchemaVersion(ctx: Context, prefsName: String, storeKey: String, version: Int) {
        prefs(ctx, prefsName).edit().putInt(storeKey + "_schema_version", version).apply()
    }

    /**
     * مهاجرت پله‌ای از نسخهٔ ذخیره‌شده تا [currentSchema].
     * [steps]: نگاشت «نسخهٔ قدیمی → تابع تبدیل JSON قدیمی به جدید».
     * اولین اجرا با دادهٔ موجودِ بدون نسخه، همان نسخهٔ جاری فرض می‌شود (سازگار به عقب).
     */
    fun migrateRaw(
        ctx: Context,
        prefsName: String,
        storeKey: String,
        raw: String?,
        currentSchema: Int = CURRENT_SCHEMA,
        steps: Map<Int, (String) -> String> = emptyMap()
    ): String? {
        if (raw.isNullOrEmpty()) return raw
        var v = schemaVersion(ctx, prefsName, storeKey)
        var data = raw
        while (v < currentSchema) {
            val step = steps[v] ?: break
            data = try { step(data) } catch (_: Exception) { data }
            v++
        }
        val stored = schemaVersion(ctx, prefsName, storeKey)
        when {
            v != stored -> {
                prefs(ctx, prefsName).edit().putString(storeKey, data).apply()
                setSchemaVersion(ctx, prefsName, storeKey, currentSchema)
            }
            stored == 0 -> setSchemaVersion(ctx, prefsName, storeKey, currentSchema)
        }
        return data
    }

    /** دادهٔ خراب را به قرنطینه منتقل کن تا قابل بازیابی دستی بماند */
    fun quarantine(ctx: Context, prefsName: String, storeKey: String, raw: String?) {
        if (raw.isNullOrEmpty()) return
        val qKey = storeKey + "_quarantine"
        prefs(ctx, prefsName).edit()
            .putString(qKey, raw)
            .putLong(qKey + "_ts", System.currentTimeMillis())
            .apply()
    }

    fun hasQuarantine(ctx: Context, prefsName: String, storeKey: String): Boolean =
        prefs(ctx, prefsName).contains(storeKey + "_quarantine")

    fun quarantineTs(ctx: Context, prefsName: String, storeKey: String): Long =
        prefs(ctx, prefsName).getLong(storeKey + "_quarantine_ts", 0L)

    fun clearQuarantine(ctx: Context, prefsName: String, storeKey: String) {
        prefs(ctx, prefsName).edit()
            .remove(storeKey + "_quarantine")
            .remove(storeKey + "_quarantine_ts")
            .apply()
    }
}
