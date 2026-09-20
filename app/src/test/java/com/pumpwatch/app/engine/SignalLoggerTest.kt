package com.pumpwatch.app.engine

import com.pumpwatch.app.ui.marginOfError   // 🚀 Commit 21-fix: تابع pure در فایل UI تعریف شده
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 15 (فاز ۴ / Commit 21-fix): تست‌های pure توابع accuracyStats
 *
 * درس: marginOfError در SignalAccuracyCard.kt (پکیج ui) تعریف شده؛
 * تست در پکیج engine بدون import آن را نمی‌بیند. internal از source set
 * تست قابل دسترسی است (friend module)، پس فقط import لازم بود.
 */
class SignalLoggerTest {

    private fun sig(symbol: String, side: String, status: String, mode: String = "SPOT") =
        LoggedSignal(
            symbol = symbol, side = side, entry = 100.0, stop = 95.0, target = 110.0,
            status = status, mode = mode, time = System.currentTimeMillis()
        )

    @Test
    fun accuracy_stats_empty_logs_returns_zeros() {
        val s = SignalLogger.accuracyStats(emptyList())
        assertEquals(0, s.total)
        assertEquals(0, s.wins)
        assertEquals(0, s.losses)
        assertEquals(0, s.expired)
        assertEquals(0, s.open)
        assertEquals(0, s.totalDecided)
        assertEquals(0.0, s.winRate, 0.0001)
    }

    @Test
    fun accuracy_stats_counts_each_status() {
        val logs = listOf(
            sig("BTC", "BUY", "WIN"),
            sig("ETH", "BUY", "WIN"),
            sig("SOL", "BUY", "LOSS"),
            sig("DOGE", "SELL", "EXP"),
            sig("PEPE", "BUY", "OPEN")
        )
        val s = SignalLogger.accuracyStats(logs)
        assertEquals(5, s.total)
        assertEquals(2, s.wins)
        assertEquals(1, s.losses)
        assertEquals(1, s.expired)
        assertEquals(1, s.open)
        assertEquals(3, s.totalDecided)
    }

    @Test
    fun win_rate_excludes_open_and_expired() {
        val logs = listOf(
            sig("BTC", "BUY", "WIN"),
            sig("ETH", "BUY", "WIN"),
            sig("SOL", "BUY", "LOSS"),
            sig("DOGE", "SELL", "EXP"),
            sig("PEPE", "BUY", "OPEN")
        )
        val s = SignalLogger.accuracyStats(logs)
        assertEquals(66.666, s.winRate, 0.01)
    }

    @Test
    fun win_rate_zero_when_only_open_or_expired() {
        val logs = listOf(
            sig("BTC", "BUY", "OPEN"),
            sig("ETH", "SELL", "EXP")
        )
        val s = SignalLogger.accuracyStats(logs)
        assertEquals(0, s.totalDecided)
        assertEquals(0.0, s.winRate, 0.0001)
    }

    @Test
    fun accuracy_by_mode_groups_correctly() {
        val logs = listOf(
            sig("BTC", "BUY", "WIN", "SPOT"),
            sig("ETH", "BUY", "LOSS", "SPOT"),
            sig("SOL", "SELL", "WIN", "FUT"),
            sig("DOGE", "SELL", "WIN", "FUT"),
            sig("PEPE", "BUY", "LOSS", "FUT")
        )
        val byMode = SignalLogger.accuracyByMode(logs)
        assertEquals(2, byMode.size)

        val spot = byMode["SPOT"]!!
        assertEquals(2, spot.total)
        assertEquals(1, spot.wins)
        assertEquals(1, spot.losses)
        assertEquals(50.0, spot.winRate, 0.01)

        val fut = byMode["FUT"]!!
        assertEquals(3, fut.total)
        assertEquals(2, fut.wins)
        assertEquals(1, fut.losses)
        assertEquals(66.666, fut.winRate, 0.01)
    }

    @Test
    fun accuracy_by_side_groups_correctly() {
        val logs = listOf(
            sig("BTC", "BUY", "WIN"),
            sig("ETH", "BUY", "WIN"),
            sig("SOL", "SELL", "WIN"),
            sig("DOGE", "SELL", "LOSS")
        )
        val bySide = SignalLogger.accuracyBySide(logs)
        assertEquals(2, bySide.size)

        val buy = bySide["BUY"]!!
        assertEquals(100.0, buy.winRate, 0.01)

        val sell = bySide["SELL"]!!
        assertEquals(50.0, sell.winRate, 0.01)
    }

    @Test
    fun margin_of_error_small_for_large_n() {
        val margin = marginOfError(1000, 60.0).toDouble()
        assertTrue("margin should be small for large n: $margin", margin < 5.0)
        assertTrue("margin should be > 1 for n=1000", margin > 1.0)
    }

    @Test
    fun margin_of_error_large_for_small_n() {
        val margin = marginOfError(5, 60.0).toDouble()
        assertTrue("margin should be large for small n: $margin", margin > 20.0)
    }

    @Test
    fun margin_of_error_unknown_for_very_small_n() {
        assertEquals("؟", marginOfError(0, 0.0))
        assertEquals("؟", marginOfError(1, 50.0))
    }
}
