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
 * 🚀 Commit 135: integration tests برای SignalNavigator.
 *
 * این تست‌ها roundtrip کامل markPending → observe → consume را با
 * Context واقعی (SharedPreferences) تأیید می‌کنند.
 * StateFlow تضمین می‌کند که حتی اگر subscription بعد از emission
 * اتفاق بیفتد، آخرین value دریافت می‌شود.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalNavigatorRobolectricTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
    }

    // ========== isPending / markPending / consume ==========

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
    fun `consume on unset flag is a no-op`() {
        SignalNavigator.consume(ctx)  // نباید crash کند
        assertFalse(SignalNavigator.isPending(ctx))
    }

    // ========== observe (StateFlow) ==========

    @Test
    fun `observe returns StateFlow`() {
        val flow = SignalNavigator.observe(ctx)
        assertNotNull(flow)
    }

    @Test
    fun `observe initial value is false when no flag set`() {
        val flow = SignalNavigator.observe(ctx)
        assertFalse("Initial value should be false", flow.value)
    }

    @Test
    fun `observe initial value is true when flag already set (fresh start)`() {
        // شبیه‌سازی: worker قبلاً markPending زده، app تازه شروع شده
        ctx.getSharedPreferences("pumpwatch_prefs", 0)
            .edit().putBoolean("pending_open_signals", true).apply()

        val flow = SignalNavigator.observe(ctx)
        assertTrue("StateFlow should sync from persisted value on fresh start", flow.value)
    }

    @Test
    fun `observe reflects markPending`() {
        val flow = SignalNavigator.observe(ctx)
        assertFalse(flow.value)
        SignalNavigator.markPending(ctx)
        assertTrue("StateFlow should reflect markPending", flow.value)
    }

    @Test
    fun `observe reflects consume`() {
        SignalNavigator.markPending(ctx)
        val flow = SignalNavigator.observe(ctx)
        assertTrue(flow.value)
        SignalNavigator.consume(ctx)
        assertFalse("StateFlow should reflect consume", flow.value)
    }

    @Test
    fun `multiple observers see same value`() {
        SignalNavigator.markPending(ctx)
        val flow1 = SignalNavigator.observe(ctx)
        val flow2 = SignalNavigator.observe(ctx)
        assertEquals(flow1.value, flow2.value)
        assertTrue(flow1.value)
        assertTrue(flow2.value)
    }

    // ========== Race condition: subscription after emission ==========

    @Test
    fun `subscription after emission still sees latest value (StateFlow replay=1)`() {
        // این مهم‌ترین خاصیت StateFlow است
        SignalNavigator.markPending(ctx)
        // حالا flow را subscribe می‌کنیم (بعد از emission)
        val flow = SignalNavigator.observe(ctx)
        assertTrue("Late subscriber must see latest value", flow.value)
    }

    @Test
    fun `subscription after consume sees false`() {
        SignalNavigator.markPending(ctx)
        SignalNavigator.consume(ctx)
        val flow = SignalNavigator.observe(ctx)
        assertFalse("Late subscriber must see false after consume", flow.value)
    }
}
