package com.pumpwatch.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.store.WatchCoin
import com.pumpwatch.app.store.WatchGroup
import java.util.Locale

/**
 * 🚀 Commit 113 (گام ۲ نقشه): مهاجرت یک‌باره از دادهٔ legacy
 * (`pumpwatch_prefs:watchlist_v1`) به `store.WatchlistStore` رمزنگاری‌شده.
 *
 * چرا مستقیم کلید را می‌خوانیم؟
 * چون `data.WatchlistStore` در کامیت ۱۱۳ به bridge تبدیل شد و دیگر از
 * `watchlist_v1` نمی‌خواند — پس مهاجرت باید خود کلید قدیمی را بخواند
 * تا دادهٔ کاربران قدیمی هرگز از دست نرود.
 *
 * ایمنی:
 * - اگر store جدید خراب باشد (decrypt fail)، مهاجرت abort می‌شود
 *   تا دادهٔ قدیمی دست‌نخورده بماند.
 * - flag `watchlist_migrated_v2` از اجرای مجدد جلوگیری می‌کند.
 *
 * 🚀 Commit 154: data class_watchlistEntry از bridge منسوخ به این فایل منتقل شد
 * تا پس از حذف کامل bridge، مهاجرت legacy همچنان کار کند.
 */

// 🚀 Commit 154: این data class قبلاً داخل data/WatchlistStore.kt (bridge) بود.
// مصرف‌کننده‌ها: loadLegacyRaw (همین فایل) + WatchlistMigrationTest (کامیت ۱۲۹).
data class WatchlistEntry(
    val symbol: String,
    val chain: String?,
    val contract: String?,
    val addedAt: Long = System.currentTimeMillis()
)

object WatchlistMigration {

    private const val FLAG_MIGRATED = "watchlist_migrated_v2"
    private const val LEGACY_PREFS = "pumpwatch_prefs"
    private const val LEGACY_KEY = "watchlist_v1"
    private const val LEGACY_GROUP_NAME = "واردشده (Legacy)"
    private const val TAG = "WatchlistMigration"

    private val GSON = Gson()

    /**
     * مهاجرت را اجرا می‌کند اگر:
     * 1. قبلاً اجرا نشده باشد (flag = false)
     * 2. دادهٔ legacy در prefs قدیمی وجود داشته باشد
     * 3. store جدید سالم باشد (load null برنگرداند)
     *
     * @return true اگر مهاجرت انجام شد، false در غیر این صورت
     */
    fun migrateIfNeeded(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

        // ۱) قبلاً مهاجرت شده؟
        if (prefs.getBoolean(FLAG_MIGRATED, false)) {
            return false
        }

        // ۲) دادهٔ legacy وجود دارد؟ — خواندن مستقیم از کلید قدیمی
        val legacyEntries = loadLegacyRaw(ctx)
        if (legacyEntries.isEmpty()) {
            // چیزی برای مهاجرت نیست، فقط flag را بزن
            prefs.edit().putBoolean(FLAG_MIGRATED, true).apply()
            return false
        }

        // ۳) store جدید سالم است؟
        val existingGroups = try {
            com.pumpwatch.app.store.WatchlistStore.loadGroups(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "Unified store unreadable, aborting migration to preserve legacy data", e)
            return false
        }

        // ۴) آیا گروه «واردشده» از قبل وجود دارد؟
        var legacyGroup: WatchGroup? = existingGroups.firstOrNull { it.name == LEGACY_GROUP_NAME }

        // ۵) ساخت یا دریافت گروه legacy
        if (legacyGroup == null) {
            val created = com.pumpwatch.app.store.WatchlistStore.addGroup(ctx, LEGACY_GROUP_NAME)
            if (!created) {
                Log.e(TAG, "Failed to create legacy group (MAX_GROUPS reached?), aborting")
                return false
            }
            val updatedGroups = com.pumpwatch.app.store.WatchlistStore.loadGroups(ctx)
            legacyGroup = updatedGroups.firstOrNull { it.name == LEGACY_GROUP_NAME }
            if (legacyGroup == null) {
                Log.e(TAG, "Legacy group not found after creation, aborting")
                return false
            }
        }

        // ۶) تبدیل WatchlistEntry → WatchCoin و افزودن به گروه
        var imported = 0
        var skipped = 0
        val existingSymbols = legacyGroup.coins
            .map { it.symbol.uppercase(Locale.US) }
            .toSet()

        for (entry in legacyEntries) {
            if (entry.symbol.isBlank()) {
                skipped++
                continue
            }
            val upperSym = entry.symbol.uppercase(Locale.US)
            if (upperSym in existingSymbols) {
                skipped++
                continue
            }

            val coin = WatchCoin(
                // contract اگر موجود بود id یکتا است؛ در غیر این صورت symbol
                id = entry.contract ?: upperSym,
                symbol = upperSym,
                name = upperSym,
                contract = entry.contract,
                rank = null,
                addedAt = entry.addedAt,
                alerts = emptyList()
            )

            val added = com.pumpwatch.app.store.WatchlistStore.addCoin(
                ctx, legacyGroup.id, coin
            )
            if (added) imported++ else skipped++
        }

        Log.i(
            TAG,
            "Migration complete: $imported imported, $skipped skipped (duplicate/empty) / ${legacyEntries.size} total"
        )

        // ۷) پاک کردن کلید legacy (مستقیم، نه از طریق bridge)
        try {
            prefs.edit().remove(LEGACY_KEY).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear legacy key (non-critical)", e)
        }

        // ۸) flag را set کن تا مهاجرت دوباره اجرا نشود
        prefs.edit().putBoolean(FLAG_MIGRATED, true).apply()

        return true
    }

    /**
     * خواندن مستقیم دادهٔ legacy از `pumpwatch_prefs:watchlist_v1`.
     * این تابع به هیچ کلاس دیگری وابسته نیست و مستقیم JSON را parse می‌کند.
     */
    private fun loadLegacyRaw(ctx: Context): List<WatchlistEntry> {
        return try {
            val json = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .getString(LEGACY_KEY, null)
            if (json.isNullOrBlank()) return emptyList()
            GSON.fromJson(json, object : TypeToken<List<WatchlistEntry>>() {}.type)
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse legacy watchlist JSON", e)
            emptyList()
        }
    }

    /**
     * برای توسعه/دیباگ: flag را پاک می‌کند تا مهاجرت دوباره اجرا شود.
     * ⚠️ خطرناک: ممکن است دادهٔ تکراری در گروه legacy ایجاد کند.
     * قبل از فراخوانی، کلید `watchlist_v1` را دستی از prefs برگردانید.
     */
    fun resetMigrationFlag(ctx: Context) {
        ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(FLAG_MIGRATED)
            .apply()
    }
}
