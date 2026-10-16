package com.pumpwatch.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 154: گارد رگرسیون برای کلاس‌های حذف‌شده.
 *
 * اگر کسی اشتباهاً این کلاس‌ها را دوباره بسازد، CI قرمز می‌شود.
 * این نگهبان از رجیستری DEPRECATIONS.md پشتیبانی می‌کند و
 * جلوی بازگشت بدهی فنی را می‌گیرد.
 */
class DeprecatedClassesRemovedTest {

    private fun classExists(name: String): Boolean = try {
        Class.forName(name)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    @Test
    fun `WatchlistWorker is permanently removed`() {
        assertFalse(
            "WatchlistWorker must not exist — evaluation lives in MonitorWorker (ADR-0001)",
            classExists("com.pumpwatch.app.store.WatchlistWorker")
        )
    }

    @Test
    fun `WatchlistScheduler is permanently removed`() {
        assertFalse(
            "WatchlistScheduler must not exist — cancel legacy work inline via WorkManager",
            classExists("com.pumpwatch.app.store.WatchlistScheduler")
        )
    }

    @Test
    fun `data WatchlistStore bridge is permanently removed`() {
        assertFalse(
            "data.WatchlistStore bridge must not exist — use store.WatchlistStore",
            classExists("com.pumpwatch.app.data.WatchlistStore")
        )
    }

    @Test
    fun `WatchlistEntry survived the bridge removal in data package`() {
        // WatchlistEntry باید زنده بماند (مهاجرت legacy به آن وابسته است)
        // ولی حالا داخل WatchlistMigration.kt تعریف شده، نه bridge
        assertTrue(
            "WatchlistEntry must exist in data package (now owned by WatchlistMigration)",
            classExists("com.pumpwatch.app.data.WatchlistEntry")
        )
    }
}
