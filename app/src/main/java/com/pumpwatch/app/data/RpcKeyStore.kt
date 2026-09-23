package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 🚀 Commit 44 (فاز ۱ برنامهٔ اجرایی): RpcKeyStore با fail-closed روی SecureStorage
 *
 * - هیچ RPC key در SharedPreferences معمولی باقی نمی‌ماند (بعد از migration)
 * - اگر Keystore کار نکند، کلید ذخیره نمی‌شود و UI صادقانه هشدار می‌دهد
 * - rotate() حذف قدیمی + ذخیره جدید به‌صورت اتمی
 * - mask() فقط ۴ کاراکتر اول/آخر برای نمایش (نه خود کلید)
 * - log/exception/clipboard: کلید هرگز وارد نمی‌شود
 */
object RpcKeyStore {

    private const val KEY = "solana_rpc_key"
    private const val LEGACY_PREFS = "pumpwatch_prefs"
    private const val LEGACY_KEY = "rpc_api_key" // کلید احتمالی قدیمی

    /** آیا کلید امن ذخیره شده؟ (بدون رمزگشایی — ارزان) */
    fun isConfigured(ctx: Context): Boolean = SecureStorage.hasSecret(ctx, KEY)

    /** خواندن کلید (رمزگشایی). null اگر نباشد یا Keystore خراب باشد. */
    fun get(ctx: Context): String? = SecureStorage.getSecret(ctx, KEY)

    /**
     * ذخیره با fail-closed.
     * - Saved: موفق
     * - KeystoreUnavailable: دستگاه این قابلیت را ندارد (کلید ذخیره نشد)
     * - CryptoError: خطای دیگر (کلید ذخیره نشد)
     */
    fun set(ctx: Context, key: String): SecureStorage.SecretResult {
        if (key.isBlank()) return clear(ctx).let { SecureStorage.SecretResult.Saved }
        return SecureStorage.putSecret(ctx, KEY, key)
    }

    /** حذف کلید (هم از SecureStorage، هم از SharedPreferences قدیمی اگر باشد). */
    fun clear(ctx: Context) {
        SecureStorage.remove(ctx, KEY)
        // پاک‌کردن ردپای احتمالی از legacy prefs
        try {
            legacyPrefs(ctx).edit().remove(LEGACY_KEY).apply()
        } catch (_: Exception) { }
    }

    /**
     * چرخش کلید: حذف قدیمی و ذخیره جدید اتمی.
     * اگر ذخیره جدید شکست خورد، قدیمی دست‌نخورده باقی می‌ماند (نه حذف).
     */
    fun rotate(ctx: Context, newKey: String): SecureStorage.SecretResult {
        if (newKey.isBlank()) return SecureStorage.SecretResult.CryptoError("empty key")
        val result = SecureStorage.putSecret(ctx, KEY, newKey)
        // putSecret روی همان key overwrite می‌کند؛ اگر Saved بود یعنی موفق
        return result
    }

    /**
     * نمایش ماسک‌شده: "XXXX...YYYY" یا "Configured" یا خالی.
     * برای UI؛ خود کلید هرگز نمایش داده نمی‌شود.
     */
    fun mask(ctx: Context): String {
        val v = get(ctx) ?: return ""
        return if (v.length >= 10) "${v.take(4)}...${v.takeLast(4)}"
        else "•".repeat(v.length)
    }

    /**
     * migration از SharedPreferences قدیمی به SecureStorage.
     * یک‌بار در شروع اپ اجرا می‌شود. اگر migration موفق بود، قدیمی حذف می‌شود.
     * اگر Keystore کار نکند، قدیمی حفظ می‌شود تا کاربر دستی اقدام کند.
     */
    fun migrateFromLegacy(ctx: Context): MigrationResult {
        val legacy = try {
            legacyPrefs(ctx).getString(LEGACY_KEY, null)
        } catch (_: Exception) { null } ?: return MigrationResult.NothingToMigrate
        // اگر قبلاً secure ذخیره شده، فقط قدیمی را پاک کن
        if (isConfigured(ctx)) {
            try { legacyPrefs(ctx).edit().remove(LEGACY_KEY).apply() } catch (_: Exception) { }
            return MigrationResult.AlreadySecure
        }
        return when (val r = set(ctx, legacy)) {
            is SecureStorage.SecretResult.Saved -> {
                try { legacyPrefs(ctx).edit().remove(LEGACY_KEY).apply() } catch (_: Exception) { }
                MigrationResult.Migrated
            }
            is SecureStorage.SecretResult.KeystoreUnavailable -> MigrationResult.KeystoreUnavailable(legacy)
            is SecureStorage.SecretResult.CryptoError -> MigrationResult.Failed(r.message)
        }
    }

    sealed class MigrationResult {
        object NothingToMigrate : MigrationResult()
        object AlreadySecure : MigrationResult()
        object Migrated : MigrationResult()
        data class KeystoreUnavailable(val legacyKey: String) : MigrationResult()
        data class Failed(val reason: String) : MigrationResult()
    }

    private fun legacyPrefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
}
