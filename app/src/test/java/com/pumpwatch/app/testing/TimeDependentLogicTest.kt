package com.pumpwatch.app.testing

import com.pumpwatch.app.engine.AlertRulesStore
import com.pumpwatch.app.engine.NotificationGuard
import com.pumpwatch.app.engine.SignalLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 9: تست‌های زمانی با FakeClock — pure JUnit، بدون Robolectric و بدون dependency جدید.
 */
class TimeDependentLogicTest {

    @Test
    fun `fake clock advances correctly`() {
        val clock = FakeClock()
        val start = clock.now
        clock.advanceHours(6)
        assertEquals(start + 6 * 3_600_000L, clock.now)
    }

    @Test
    fun `rule cooldown expires exactly at six hours`() {
        val clock = FakeClock()
        val rule = TestFixtures.rule(lastTriggeredTs = clock.now)

        clock.advanceHours(5); clock.advanceMinutes(59)
        assertTrue(AlertRulesStore.inCooldown(rule, clock.now))

        clock.advanceMinutes(1) // دقیقاً ۶ ساعت
        assertFalse(AlertRulesStore.inCooldown(rule, clock.now))
    }

    @Test
    fun `open same-direction signal is always duplicate`() {
        val clock = FakeClock()
        val existing = listOf(TestFixtures.signal(status = "OPEN", time = clock.now - 7_200_000L))
        val incoming = TestFixtures.signal(time = clock.now)
        assertTrue(SignalLogger.isDuplicate(existing, incoming, clock.now))
    }

    @Test
    fun `sliding one-hour dedup window expires after 60 minutes`() {
        val clock = FakeClock()
        val candleTs = clock.now
        val existing = listOf(TestFixtures.signal(status = "WIN", time = candleTs))

        clock.advanceMinutes(59)
        assertTrue(SignalLogger.isDuplicate(existing, TestFixtures.signal(status = "WIN", time = candleTs + 1), clock.now))

        clock.advanceMinutes(2) // ۶۱ دقیقه
        assertFalse(SignalLogger.isDuplicate(existing, TestFixtures.signal(status = "WIN", time = candleTs + 1), clock.now))
    }

    @Test
    fun `same candle is duplicate even days later`() {
        val clock = FakeClock()
        val candleTs = clock.now
        val existing = listOf(TestFixtures.signal(status = "WIN", time = candleTs))
        clock.advanceDays(3)
        assertTrue(SignalLogger.isDuplicate(existing, TestFixtures.signal(status = "WIN", time = candleTs), clock.now))
    }

    @Test
    fun `notification suppression window works`() {
        val clock = FakeClock()
        val deliveredAt = clock.now

        clock.advanceHours(1)
        assertTrue(NotificationGuard.isSuppressed(deliveredAt, clock.now, 6 * 3_600_000L))

        clock.advanceHours(5) // جمعاً ۶ ساعت
        assertFalse(NotificationGuard.isSuppressed(deliveredAt, clock.now, 6 * 3_600_000L))
    }

    @Test
    fun `fixtures produce expected defaults`() {
        assertEquals("BTC", TestFixtures.coin().symbol)
        assertEquals("BUY", TestFixtures.signal().side)
        assertEquals("rule-1", TestFixtures.rule().id)
    }
}
