package com.pumpwatch.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 🚀 Commit 44 (فاز ۱ برنامهٔ اجرایی): RpcKeyStore با fail-closed روی SecureStorage
 * 🚀 Commit 79 (فاز ۲ — observability + safety): synchronized + لاگ migration + flag
 *
 * - هیچ RPC key در SharedPreferences معمولی باقی نمی‌ماند (بعد از migration)
 * - اگر Keystore کار نکند، کلید ذخیره نمی‌شود و UI صادقانه هشدار می‌دهد
 * - rotate() ذخیرهٔ جدید روی همان کلید؛ اگر شکست خورد، قدیمی دست‌نخورده می‌ماند
 * - mask() فقط ۴ کاراکتر اول/آخر برای نمایش (نه خود کلید)
 * - cachedKey() کش حافظه‌ای برای صداهای بدون Context در لایهٔ data
 *   (به‌محض اینکه هر صفحه‌ای یک بار get کند، همهٔ موتورها کلید را می‌بینند)
 * - log/exception/clipboard: کلید هرگز وارد نمی‌شود
 *
 * 🚀 Commit 79:
 * - synchronized(lock) روی migrateFromLegacy برای safety (اگر دو thread هم‌زمان صدا بزنند)
 * - لاگ explicit هر مرحله (Migration started/completed/failed)
 * - flag legacy_migrated در SharedPreferences برای skip کردن migration در future runs
 * - try-catch روی Log calls (در JVM unit test crash نمی‌کند)
 */
object RpcKeyStore {

    private const val TAG = "RpcKeyStore"
    private const val KEY = "solana_rpc_key"

    // نام‌های واقعی دورهٔ قدیمی (Commit 27) برای migration و پاک‌سازی ردپا
    private const val LEGACY_PREFS = "pumpwatch_rpc_prefs"
    private const val LEGACY_KEY = "solana_rpc_api_key"
    private const val FLAG_MIGRATED = "legacy_migrated"

    @Volatile
    private var cached: String? = null

    /** قفل برای migration (safety در برابر race condition) */
    private val lock = Any()

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
     * 🚀 Commit 79: migration از SharedPreferences قدیمی (pumpwatch_rpc_prefs) به SecureStorage.
     *
     * یک‌بار در شروع اپ اجرا می‌شود (flag legacy_migrated چک می‌شود).
     * اگر migration موفق بود، قدیمی حذف می‌شود.
     * اگر Keystore کار نکند، قدیمی حفظ می‌شود تا کاربر دستی اقدام کند (fail-closed).
     *
     * synchronized برای safety در برابر race condition (اگر دو thread هم‌زمان صدا بزنند).
     * لاگ explicit هر مرحله برای observability.
     */
    fun migrateFromLegacy(ctx: Context): MigrationResult {
        synchronized(lock) {
            // 🚀 Commit 79: چک flag — اگر قبلاً migrate شده، skip
            if (legacyPrefs(ctx).getBoolean(FLAG_MIGRATED, false)) {
                logDebug("Migration already completed (flag set)")
                return MigrationResult.AlreadySecure
            }

            val legacy = try {
                legacyPrefs(ctx).getString(LEGACY_KEY, null)?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                logWarning("Failed to read legacy prefs", e)
                null
            }

            if (legacy == null) {
                logDebug("Nothing to migrate (no legacy key found)")
                // flag set کن تا دفعهٔ بعد skip شود
                try {
                    legacyPrefs(ctx).edit().putBoolean(FLAG_MIGRATED, true).apply()
                } catch (e: Exception) {
                    logWarning("Failed to set migration flag", e)
                }
                return MigrationResult.NothingToMigrate
            }

            logInfo("Migration started (legacy key found)")

            // اگر قبلاً secure ذخیره شده، فقط ردپای قدیمی را پاک کن
            if (isConfigured(ctx)) {
                logInfo("Migration: key already secure, cleaning legacy")
                try {
                    legacyPrefs(ctx).edit()
                        .remove(LEGACY_KEY)
                        .putBoolean(FLAG_MIGRATED, true)
                        .apply()
                } catch (e: Exception) {
                    logWarning("Failed to clean legacy after secure check", e)
                }
                return MigrationResult.AlreadySecure
            }

            return when (val result = set(ctx, legacy)) {
                is SecureStorage.SecretResult.Saved -> {
                    logInfo("Migration completed successfully")
                    try {
                        legacyPrefs(ctx).edit()
                            .remove(LEGACY_KEY)
                            .putBoolean(FLAG_MIGRATED, true)
                            .apply()
                    } catch (e: Exception) {
                        logWarning("Migration succeeded but failed to clean legacy", e)
                    }
                    MigrationResult.Migrated
                }
                is SecureStorage.SecretResult.KeystoreUnavailable -> {
                    logWarning("Migration failed: Keystore unavailable")
                    MigrationResult.KeystoreUnavailable
                }
                is SecureStorage.SecretResult.CryptoError -> {
                    logWarning("Migration failed: ${result.message}")
                    MigrationResult.Failed
                }
            }
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

    // 🚀 Commit 79: helper های لاگ با try-catch (در JVM unit test crash نمی‌کند)

    private fun logDebug(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (_: Throwable) {
            println("$TAG: $msg")
        }
    }

    private fun logInfo(msg: String) {
        try {
            Log.i(TAG, msg)
        } catch (_: Throwable) {
            println("$TAG: $msg")
        }
    }

    private fun logWarning(msg: String, e: Throwable? = null) {
        try {
            if (e != null) Log.w(TAG, msg, e) else Log.w(TAG, msg)
        } catch (_: Throwable) {
            println("$TAG: $msg${if (e != null) " (${e.message})" else ""}")
        }
    }
}
