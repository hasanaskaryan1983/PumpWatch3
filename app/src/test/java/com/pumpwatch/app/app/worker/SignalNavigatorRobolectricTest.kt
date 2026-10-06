package com.pumpwatch.app.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 135 (fix): isolation بین تست‌ها.
 * StateFlow داخل object بین تست‌ها زنده می‌ماند؛ پس در setUp
 * هم prefs و هم StateFlow را با consume() ریست می‌کنیم.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalNavigatorRobolectricTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
        // 🚀 fix: ریست StateFlow درون‌حافظه‌ای (نه فقط prefs)
        SignalNavigator.consume(ctx)
    }

    @Test
    fun `isPending is false by default`() {
        assertFalse(SignalNavigator.isPending(ctx))
    }

    @Test
    fun `markPending sets persisted flag`() {
        SignalNavigator.markPending(ctx)
        assertTrue(SignalNavigator.isPending(ctx))
    }

    @Test
    fun `consume clears persisted flag`() {
        SignalNavigator.markPending(ctx)
        SignalNavigator.consume(ctx)
        assertFalse(SignalNavigator.isPending(ctx))
    }

    @Test
    fun `observe initial value is false when no flag set`() {
        val flow = SignalNavigator.observe(ctx)
        assertFalse(flow.value)
    }

    @Test
    fun `observe initial value is true when flag already set (fresh start)`() {
        ctx.getSharedPreferences("pumpwatch_prefs", 0)
            .edit().putBoolean("pending_open_signals", true).apply()
        val flow = SignalNavigator.observe(ctx)
        assertTrue("StateFlow should sync from persisted value on fresh start", flow.value)
    }

    @Test
    fun `observe reflects markPending and consume`() {
        val flow = SignalNavigator.observe(ctx)
        assertFalse(flow.value)
        SignalNavigator.markPending(ctx)
        assertTrue(flow.value)
        SignalNavigator.consume(ctx)
        assertFalse(flow.value)
    }

    @Test
    fun `late subscriber still sees latest value (StateFlow replay=1)`() {
        SignalNavigator.markPending(ctx)
        val flow = SignalNavigator.observe(ctx)
        assertTrue("Late subscriber must see latest value", flow.value)
    }
}
