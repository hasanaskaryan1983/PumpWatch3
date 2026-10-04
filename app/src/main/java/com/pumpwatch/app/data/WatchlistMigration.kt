package com.pumpwatch.app.data

import android.content.Context
import android.util.Log
import com.pumpwatch.app.store.WatchCoin
import com.pumpwatch.app.store.WatchGroup
import com.pumpwatch.app.store.WatchlistStore
import java.util.Locale

/**
 * 🚀 Commit 113 (گام ۲ نقشه): مهاجرت یک‌باره از data.WatchlistStore (legacy)
 * به store.WatchlistStore (unified).
 *
 * - دادهٔ legacy از `pumpwatch_prefs:watchlist_v1` خوانده می‌شود
 * - به یک گروه پیش‌فرض «واردشده (Legacy)» در store جدید منتقل می‌شود
 * - کلید قدیمی پاک می‌شود
 * - flag `watchlist_migrated_v2` در prefs ثبت می‌شود تا دوباره اجرا نشود
 *
 * 🛡️ ایمن: اگر store جدید خراب باشد (decrypt fail)، مهاجرت انجام نمی‌شود
 * تا دادهٔ قدیمی از بین نرود.
 */
object WatchlistMigration {

    private const val FLAG_MIGRATED = "watchlist_migrated_v2"
    private const val LEGACY_GROUP_NAME = "واردشده (Legacy)"
    private const val TAG = "WatchlistMigration"

    /**
     * مهاجرت را اجرا می‌کند اگر:
     * 1. قبلاً اجرا نشده باشد (flag = false)
     * 2. دادهٔ legacy وجود داشته باشد
     * 3. store جدید سالم باشد (load null برنگرداند)
     *
     * @return true اگر مهاجرت انجام شد، false اگر قبلاً شده یا شرایط نبود
     */
    fun migrateIfNeeded(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences("pumpwatch_prefs", Context.MODE_PRIVATE)

        // ۱) قبلاً مهاجرت شده؟
        if (prefs.getBoolean(FLAG_MIGRATED, false)) {
            return false
        }

        // ۲) دادهٔ legacy وجود دارد؟
        val legacyEntries = try {
            com.pumpwatch.app.data.WatchlistStore.load(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load legacy watchlist, skipping migration", e)
            emptyList()
        }

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
        var legacyGroup = existingGroups.firstOrNull { it.name == LEGACY_GROUP_NAME }

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
        val existingSymbols = legacyGroup.coins.map { it.symbol.uppercase(Locale.US) }.toSet()

        for (entry in legacyEntries) {
            if (entry.symbol.uppercase(Locale.US) in existingSymbols) continue

            val coin = WatchCoin(
                id = entry.contract ?: entry.symbol, // contract یا symbol به‌عنوان id یکتا
                symbol = entry.symbol.uppercase(Locale.US),
                name = entry.symbol.uppercase(Locale.US),
                contract = entry.contract,
                rank = null,
                addedAt = entry.addedAt,
                alerts = emptyList()
            )

            val added = com.pumpwatch.app.store.WatchlistStore.addCoin(
                ctx, legacyGroup.id, coin
            )
            if (added) imported++
        }

        Log.i(TAG, "Migration complete: $imported / ${legacyEntries.size} entries migrated")

        // ۷) پاک کردن دادهٔ legacy
        try {
            com.pumpwatch.app.data.WatchlistStore.clear(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear legacy store (non-critical)", e)
        }

        // ۸) flag را set کن
        prefs.edit().putBoolean(FLAG_MIGRATED, true).apply()

        return true
    }

    /**
     * برای توسعه/دیباگ: flag را پاک می‌کند تا مهاجرت دوباره اجرا شود.
     * ⚠️ خطرناک: ممکن است دادهٔ تکراری ایجاد کند.
     */
    fun resetMigrationFlag(ctx: Context) {
        ctx.getSharedPreferences("pumpwatch_prefs", Context.MODE_PRIVATE)
            .edit().remove(FLAG_MIGRATED).apply()
    }
}
