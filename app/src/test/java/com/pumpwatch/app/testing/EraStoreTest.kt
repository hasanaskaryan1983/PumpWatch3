package com.pumpwatch.app.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.engine.EraStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EraStoreTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun clear() = EraStore.clear(ctx)

    @Test
    fun `load returns empty list when nothing saved`() {
        clear()
        assertEquals(0, EraStore.load(ctx).size)
        assertNull(EraStore.lastMark(ctx))
    }

    @Test
    fun `mark always adds a boundary`() {
        clear()
        EraStore.mark(ctx, "الف", nowMs = 1000L)
        EraStore.mark(ctx, "ب", nowMs = 1001L) // فقط ۱ میلی‌ثانیه بعد — بدون debounce
        assertEquals(2, EraStore.load(ctx).size)
        assertEquals("ب", EraStore.lastMark(ctx)?.label)
    }

    @Test
    fun `onConfigChanged debounces same label within 5 minutes`() {
        clear()
        EraStore.onConfigChanged(ctx, "تغییر فرمول", nowMs = 1000L)
        EraStore.onConfigChanged(ctx, "تغییر فرمول", nowMs = 1000L + 4 * 60_000L)
        assertEquals(1, EraStore.load(ctx).size)
    }

    @Test
    fun `onConfigChanged allows same label after 5 minutes`() {
        clear()
        EraStore.onConfigChanged(ctx, "تغییر فرمول", nowMs = 1000L)
        EraStore.onConfigChanged(ctx, "تغییر فرمول", nowMs = 1000L + 6 * 60_000L)
        assertEquals(2, EraStore.load(ctx).size)
    }

    @Test
    fun `onConfigChanged does not debounce different labels`() {
        clear()
        EraStore.onConfigChanged(ctx, "تغییر فرمول", nowMs = 1000L)
        EraStore.onConfigChanged(ctx, "تغییر استراتژی", nowMs = 1001L)
        assertEquals(2, EraStore.load(ctx).size)
    }

    @Test
    fun `clear removes all marks`() {
        EraStore.mark(ctx, "الف")
        EraStore.clear(ctx)
        assertEquals(0, EraStore.load(ctx).size)
    }
}
