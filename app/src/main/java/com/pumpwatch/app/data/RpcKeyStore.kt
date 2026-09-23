package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 🚀 Commit 44 (فاز ۱ برنامهٔ اجرایی): RpcKeyStore با fail-closed روی SecureStorage
 *
 * - هیچ RPC key در SharedPreferences معمولی باقی نمی‌ماند (بعد از migration)
 * - اگر Keystore کار نکند، کلید ذخیره نمی‌شود و UI صادقانه هشدار می‌دهد
 * - rotate() ذخیرهٔ جدید روی همان کلید؛ اگر شکست خورد، قدیمی دست‌نخورده می‌ماند
 * - mask() فقط ۴ کاراکتر اول/آخر برای نمایش (نه خود کلید)
 * - cachedKey() کش حافظه‌ای برای صداهای بدون Context در لایهٔ data
 *   (به‌محض اینکه هر صفحه‌ای یک بار get کند، همهٔ موتورها کلید را می‌بینند)
 * - log/exception/clipboard: کلید هرگز وارد نمی‌شود
 */
object RpcKeyStore {

    private const val KEY = "solana_rpc_key"

    // نام‌های واقعی دورهٔ قدیمی (Commit 27) برای migration و پاک‌سازی ردپا
    private const val LEGACY_PREFS = "pumpwatch_rpc_prefs"
    private const val LEGACY_KEY = "solana_rpc_api_key"

    @Volatile
    private var cached: String? = null

    /** برای صداهای بدون Context در لایهٔ data (solanaRaw / solanaTyped) */
    fun cachedKey(): String? = cached

    /** آیا کلید امن ذخیره شده؟ (بدون رمزگشایی — ارزان) */
    fun isConfigured(ctx: Context): Boolean = SecureStorage.hasSecret(ctx, KEY)

    /** خواندن کلید (رمزگشایی). null اگر نباشد یا Keystore خراب باشد. کش هم تازه می‌شود. */
    fun get(ctx: Context): String? {
        val v = SecureStorage.getSecret(ctx, KEY)
        cached = v
        return v
    }

    /**
     * ذخیره با fail-closed.
     * - Saved: موفق (کش هم تنظیم می‌شود)
     * - KeystoreUnavailable: دستگاه این قابلیت را ندارد (کلید ذخیره و کش نشد)
     * - CryptoError: خطای دیگر (کلید ذخیره و کش نشد)
     */
    fun set(ctx: Context, key: String): SecureStorage.SecretResult {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clear(ctx)
            return SecureStorage.SecretResult.Saved
        }
        return when (val r = SecureStorage.putSecret(ctx, KEY, trimmed)) {
            is SecureStorage.SecretResult.Saved -> { cached = trimmed; r }
            else -> r
        }
    }

    /** حذف کلید (SecureStorage + کش + ردپای legacy). */
    fun clear(ctx: Context) {
        cached = null
        SecureStorage.remove(ctx, KEY)
        try {
            legacyPrefs(ctx).edit().remove(LEGACY_KEY).apply()
        } catch (_: Exception) { }
    }

    /**
     * چرخش کلید: ذخیرهٔ جدید روی همان کلید.
     * اگر ذخیرهٔ جدید شکست بخورد، قدیمی دست‌نخورده می‌ماند (نه حذف، نه کشِ جدید).
     */
    fun rotate(ctx: Context, newKey: String): SecureStorage.SecretResult = set(ctx, newKey)

    /**
     * نمایش ماسک‌شده: "XXXX...YYYY" یا رشتهٔ نقطه‌ها یا خالی.
     * برای UI؛ خود کلید هرگز نمایش داده نمی‌شود.
     */
    fun mask(ctx: Context): String {
        val v = get(ctx) ?: return ""
        return if (v.length >= 10) "${v.take(4)}...${v.takeLast(4)}"
        else "•".repeat(v.length)
    }

    /**
     * migration از SharedPreferences قدیمی (pumpwatch_rpc_prefs) به SecureStorage.
     * یک‌بار در شروع اپ اجرا می‌شود. اگر migration موفق بود، قدیمی حذف می‌شود.
     * اگر Keystore کار نکند، قدیمی حفظ می‌شود تا کاربر دستی اقدام کند (fail-closed).
     */
    fun migrateFromLegacy(ctx: Context): MigrationResult {
        val legacy = try {
            legacyPrefs(ctx).getString(LEGACY_KEY, null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null } ?: return MigrationResult.NothingToMigrate
        // اگر قبلاً secure ذخیره شده، فقط ردپای قدیمی را پاک کن
        if (isConfigured(ctx)) {
            try { legacyPrefs(ctx).edit().remove(LEGACY_KEY).apply() } catch (_: Exception) { }
            return MigrationResult.AlreadySecure
        }
        return when (set(ctx, legacy)) {
            is SecureStorage.SecretResult.Saved -> {
                try { legacyPrefs(ctx).edit().remove(LEGACY_KEY).apply() } catch (_: Exception) { }
                MigrationResult.Migrated
            }
            is SecureStorage.SecretResult.KeystoreUnavailable -> MigrationResult.KeystoreUnavailable
            is SecureStorage.SecretResult.CryptoError -> MigrationResult.Failed
        }
    }

    sealed class MigrationResult {
        object NothingToMigrate : MigrationResult()
        object AlreadySecure : MigrationResult()
        object Migrated : MigrationResult()
        object KeystoreUnavailable : MigrationResult()
        object Failed : MigrationResult()
    }

    private fun legacyPrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
}
