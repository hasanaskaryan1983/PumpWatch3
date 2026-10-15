package com.pumpwatch.app.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.store.StorageGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StorageGuardTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `quarantine preserves corrupt json`() {
        val p = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        p.edit().putString("demo_store", "{broken").apply()

        StorageGuard.quarantine(ctx, "pumpwatch_prefs", "demo_store", p.getString("demo_store", ""))

        assertTrue(StorageGuard.hasQuarantine(ctx, "pumpwatch_prefs", "demo_store"))
        assertEquals("{broken", p.getString("demo_store_quarantine", ""))
        StorageGuard.clearQuarantine(ctx, "pumpwatch_prefs", "demo_store")
        assertFalse(StorageGuard.hasQuarantine(ctx, "pumpwatch_prefs", "demo_store"))
    }

    @Test
    fun `schema version defaults to zero and can be set`() {
        assertEquals(0, StorageGuard.schemaVersion(ctx, "pumpwatch_prefs", "fresh_key"))
        StorageGuard.setSchemaVersion(ctx, "pumpwatch_prefs", "fresh_key", 1)
        assertEquals(1, StorageGuard.schemaVersion(ctx, "pumpwatch_prefs", "fresh_key"))
    }

    @Test
    fun `migrateRaw applies steps in order`() {
        val p = ctx.getSharedPreferences("pumpwatch_prefs", 0)
        p.edit().putString("mig_store", "a").remove("mig_store_schema_version").apply()

        val out = StorageGuard.migrateRaw(
            ctx, "pumpwatch_prefs", "mig_store", "a",
            currentSchema = 2,
            steps = mapOf(0 to { s -> s + "1" }, 1 to { s -> s + "2" })
        )

        assertEquals("a12", out)
        assertEquals(2, StorageGuard.schemaVersion(ctx, "pumpwatch_prefs", "mig_store"))
        assertEquals("a12", p.getString("mig_store", ""))
    }
}
