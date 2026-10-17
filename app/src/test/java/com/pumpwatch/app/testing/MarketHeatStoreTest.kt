package com.pumpwatch.app.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.engine.MarketHeatStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 P3-2: تست واحد برای زمان‌بندی تطبیقی اسکن.
 * تضمین می‌کند که در بازار آرام باتری ذخیره می‌شود
 * و در بازار داغ هیچ فرصتی از دست نمی‌رود.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MarketHeatStoreTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun clear() = MarketHeatStore.reset(ctx)

    @Test
    fun `default state does not skip any run`() {
        clear()
        assertFalse(MarketHeatStore.consumeSkip(ctx))
    }

    @Test
    fun `calm run grants two skip credits`() {
        clear()
        MarketHeatStore.recordRun(ctx, 0)
        assertTrue(MarketHeatStore.consumeSkip(ctx))   // اجرای ۱ رد می‌شود
        assertTrue(MarketHeatStore.consumeSkip(ctx))   // اجرای ۲ رد می‌شود
        assertFalse(MarketHeatStore.consumeSkip(ctx))  // اجرای ۳ اسکن می‌کند
    }

    @Test
    fun `normal run grants one skip credit`() {
        clear()
        MarketHeatStore.recordRun(ctx, 2)
        assertTrue(MarketHeatStore.consumeSkip(ctx))
        assertFalse(MarketHeatStore.consumeSkip(ctx))
    }

    @Test
    fun `hot run grants no skip credits`() {
        clear()
        MarketHeatStore.recordRun(ctx, 5)
        assertFalse(MarketHeatStore.consumeSkip(ctx))
        assertFalse(MarketHeatStore.consumeSkip(ctx))
    }

    @Test
    fun `lastWorthy reflects the most recent recorded heat`() {
        clear()
        MarketHeatStore.recordRun(ctx, 4)
        assertEquals(4, MarketHeatStore.lastWorthy(ctx))
        MarketHeatStore.recordRun(ctx, 0)
        assertEquals(0, MarketHeatStore.lastWorthy(ctx))
    }

    @Test
    fun `negative worthy is coerced to zero`() {
        clear()
        MarketHeatStore.recordRun(ctx, -3)
        assertEquals(0, MarketHeatStore.lastWorthy(ctx))
        assertTrue(MarketHeatStore.consumeSkip(ctx)) // رفتار معادل بازار آرام
    }

    @Test
    fun `reset clears all adaptive state`() {
        MarketHeatStore.recordRun(ctx, 0)
        MarketHeatStore.reset(ctx)
        assertFalse(MarketHeatStore.consumeSkip(ctx))
        assertEquals(0, MarketHeatStore.lastWorthy(ctx))
    }
}
