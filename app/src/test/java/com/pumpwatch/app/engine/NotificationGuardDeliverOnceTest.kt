package com.pumpwatch.app.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationGuardDeliverOnceTest {

    private lateinit var ctx: Context
    private val window = 6L * 3_600_000L

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
    }

    @Test
    fun `H-DEDUP - failed delivery does not consume dedup key`() {
        val ok = NotificationGuard.deliverOnce(ctx, "k", window) { false }
        assertFalse(ok)
        assertTrue("next run must retry", NotificationGuard.shouldDeliver(ctx, "k", window))
        assertEquals(0, NotificationGuard.coverage(ctx).delivered)
    }

    @Test
    fun `H-DEDUP - exception during delivery does not consume dedup key`() {
        val ok = NotificationGuard.deliverOnce(ctx, "k", window) { throw RuntimeException("OEM block") }
        assertFalse(ok)
        assertTrue("next run must retry", NotificationGuard.shouldDeliver(ctx, "k", window))
        assertEquals(0, NotificationGuard.coverage(ctx).delivered)
    }

    @Test
    fun `H-DEDUP - successful delivery consumes key and suppresses within window`() {
        val ok = NotificationGuard.deliverOnce(ctx, "k", window) { true }
        assertTrue(ok)
        assertFalse("suppressed inside window", NotificationGuard.shouldDeliver(ctx, "k", window))
        assertEquals(1, NotificationGuard.coverage(ctx).delivered)
    }

    @Test
    fun `H-DEDUP - key expires after window`() {
        NotificationGuard.recordDelivered(ctx, "k", nowMs = 1_000L)
        assertFalse(NotificationGuard.shouldDeliver(ctx, "k", window, nowMs = 1_000L + window - 1))
        assertTrue(NotificationGuard.shouldDeliver(ctx, "k", window, nowMs = 1_000L + window + 1))
    }
}
