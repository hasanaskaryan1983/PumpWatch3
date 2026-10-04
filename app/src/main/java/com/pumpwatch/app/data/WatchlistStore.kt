package com.pumpwatch.app.data

import android.content.Context
import android.util.Log

/**
 * 🚀 Commit 113: DEPRECATED — این API قدیمی به store.WatchlistStore هدایت می‌شود.
 *
 * در کامیت ۱۱۴، این فایل کاملاً حذف می‌شود و call site ها باید مستقیماً
 * به com.pumpwatch.app.store.WatchlistStore مراجعه کنند.
 *
 * برای سازگاری عقب‌رو، این توابع همچنان کار می‌کنند ولی:
 * - داده در گروه «واردشده (Legacy)» در store جدید ذخیره می‌شود
 * - همهٔ عملیات رمزنگاری‌شده و thread-safe است
 */
data class WatchlistEntry(
    val symbol: String,
    val chain: String?,
    val contract: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Deprecated(
    message = "Use com.pumpwatch.app.store.WatchlistStore instead",
    replaceWith = ReplaceWith(
        "com.pumpwatch.app.store.WatchlistStore",
        "com.pumpwatch.app.store.WatchlistStore"
    )
)
object WatchlistStore {

    private const val TAG = "WatchlistStore.Legacy"
    private const val LEGACY_GROUP_NAME = "واردشده (Legacy)"

    private fun unified(): com.pumpwatch.app.store.WatchlistStore =
        com.pumpwatch.app.store.WatchlistStore

    private fun findLegacyGroup(ctx: Context): com.pumpwatch.app.store.WatchGroup? {
        val groups = unified().loadGroups(ctx)
        return groups.firstOrNull { it.name == LEGACY_GROUP_NAME }
    }

    private fun ensureLegacyGroup(ctx: Context): com.pumpwatch.app.store.WatchGroup? {
        findLegacyGroup(ctx)?.let { return it }
        unified().addGroup(ctx, LEGACY_GROUP_NAME)
        return findLegacyGroup(ctx)
    }

    fun load(context: Context): List<WatchlistEntry> {
        val group = findLegacyGroup(context) ?: return emptyList()
        return group.coins.map { coin ->
            WatchlistEntry(
                symbol = coin.symbol,
                chain = null, // chain در WatchCoin ذخیره نشده
                contract = coin.contract,
                addedAt = coin.addedAt
            )
        }
    }

    fun save(context: Context, entries: List<WatchlistEntry>) {
        val group = ensureLegacyGroup(context) ?: run {
            Log.e(TAG, "save: cannot create legacy group, aborting")
            return
        }
        // حذف همهٔ ارزهای فعلی گروه
        for (coin in group.coins) {
            unified().removeCoin(context, group.id, coin.id)
        }
        // افزودن entries جدید
        for (entry in entries) {
            val coin = com.pumpwatch.app.store.WatchCoin(
                id = entry.contract ?: entry.symbol,
                symbol = entry.symbol.uppercase(java.util.Locale.US),
                name = entry.symbol.uppercase(java.util.Locale.US),
                contract = entry.contract,
                rank = null,
                addedAt = entry.addedAt,
                alerts = emptyList()
            )
            unified().addCoin(context, group.id, coin)
        }
    }

    fun add(context: Context, entry: WatchlistEntry) {
        val group = ensureLegacyGroup(context) ?: run {
            Log.e(TAG, "add: cannot create legacy group, aborting")
            return
        }
        val coin = com.pumpwatch.app.store.WatchCoin(
            id = entry.contract ?: entry.symbol,
            symbol = entry.symbol.uppercase(java.util.Locale.US),
            name = entry.symbol.uppercase(java.util.Locale.US),
            contract = entry.contract,
            rank = null,
            addedAt = entry.addedAt,
            alerts = emptyList()
        )
        unified().addCoin(context, group.id, coin)
    }

    fun remove(context: Context, symbol: String) {
        val group = findLegacyGroup(context) ?: return
        val upperSym = symbol.uppercase(java.util.Locale.US)
        val coin = group.coins.firstOrNull { it.symbol.equals(upperSym, ignoreCase = true) } ?: return
        unified().removeCoin(context, group.id, coin.id)
    }

    fun clear(context: Context) {
        // فقط prefs قدیمی را پاک می‌کنیم (برای سازگاری عقب‌رو)
        context.getSharedPreferences("pumpwatch_prefs", Context.MODE_PRIVATE)
            .edit().remove("watchlist_v1").apply()
    }
}
