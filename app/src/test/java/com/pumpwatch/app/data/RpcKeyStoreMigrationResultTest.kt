package com.pumpwatch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 🚀 Commit 79: تست‌های pure برای `MigrationResult` types (بدون نیاز به Android).
 *
 * هدف: اثبات اینکه sealed class به‌درستی تعریف شده و هر type منحصر‌به‌فرد است.
 * تست migration واقعی نیاز به Robolectric دارد (در integration test روی دستگاه).
 */
class RpcKeyStoreMigrationResultTest {

    @Test
    fun `MigrationResult types are distinct`() {
        val results = listOf(
            RpcKeyStore.MigrationResult.NothingToMigrate,
            RpcKeyStore.MigrationResult.AlreadySecure,
            RpcKeyStore.MigrationResult.Migrated,
            RpcKeyStore.MigrationResult.KeystoreUnavailable,
            RpcKeyStore.MigrationResult.Failed
        )

        // هر type باید منحصر‌به‌فرد باشد
        assertEquals(5, results.distinct().size)
    }

    @Test
    fun `NothingToMigrate is singleton`() {
        val a = RpcKeyStore.MigrationResult.NothingToMigrate
        val b = RpcKeyStore.MigrationResult.NothingToMigrate
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `AlreadySecure is singleton`() {
        val a = RpcKeyStore.MigrationResult.AlreadySecure
        val b = RpcKeyStore.MigrationResult.AlreadySecure
        assertEquals(a, b)
    }

    @Test
    fun `Migrated is singleton`() {
        val a = RpcKeyStore.MigrationResult.Migrated
        val b = RpcKeyStore.MigrationResult.Migrated
        assertEquals(a, b)
    }

    @Test
    fun `KeystoreUnavailable is singleton`() {
        val a = RpcKeyStore.MigrationResult.KeystoreUnavailable
        val b = RpcKeyStore.MigrationResult.KeystoreUnavailable
        assertEquals(a, b)
    }

    @Test
    fun `Failed is singleton`() {
        val a = RpcKeyStore.MigrationResult.Failed
        val b = RpcKeyStore.MigrationResult.Failed
        assertEquals(a, b)
    }

    @Test
    fun `different types are not equal`() {
        assertNotEquals(
            RpcKeyStore.MigrationResult.NothingToMigrate,
            RpcKeyStore.MigrationResult.Migrated
        )
        assertNotEquals(
            RpcKeyStore.MigrationResult.AlreadySecure,
            RpcKeyStore.MigrationResult.KeystoreUnavailable
        )
        assertNotEquals(
            RpcKeyStore.MigrationResult.Migrated,
            RpcKeyStore.MigrationResult.Failed
        )
    }

    @Test
    fun `when expression covers all types`() {
        val results = listOf(
            RpcKeyStore.MigrationResult.NothingToMigrate,
            RpcKeyStore.MigrationResult.AlreadySecure,
            RpcKeyStore.MigrationResult.Migrated,
            RpcKeyStore.MigrationResult.KeystoreUnavailable,
            RpcKeyStore.MigrationResult.Failed
        )

        results.forEach { result ->
            val label = when (result) {
                RpcKeyStore.MigrationResult.NothingToMigrate -> "nothing"
                RpcKeyStore.MigrationResult.AlreadySecure -> "already"
                RpcKeyStore.MigrationResult.Migrated -> "migrated"
                RpcKeyStore.MigrationResult.KeystoreUnavailable -> "keystore"
                RpcKeyStore.MigrationResult.Failed -> "failed"
            }
            // هر type باید یک branch داشته باشد (compile-time guarantee)
            assert(label.isNotEmpty())
        }
    }
}
