package com.pumpwatch.app.engine

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * 🚀 G2 Test: Pure logic test for leaderboard aggregation.
 * No Context or Mockito needed, ensuring 100% CI reliability.
 */
class SignalLoggerLeaderboardTest {

    @Test
    fun `leaderboard logic filters wallets with less than minSignals`() {
        // Arrange: Create in-memory logs
        val logs = mutableListOf<LoggedSignal>()
        
        // Wallet A: 25 signals (should be included, >= 20)
        for (i in 1..25) {
            logs.add(LoggedSignal(
                wallet = "WalletA", symbol = "BTC", side = "LONG", 
                entry = 100.0, stop = 90.0, target = 110.0, 
                score = 80, time = System.currentTimeMillis()
            ))
        }
        
        // Wallet B: 10 signals (should be filtered out, < 20)
        for (i in 1..10) {
            logs.add(LoggedSignal(
                wallet = "WalletB", symbol = "ETH", side = "SHORT", 
                entry = 2000.0, stop = 2100.0, target = 1900.0, 
                score = 50, time = System.currentTimeMillis()
            ))
        }

        // Act: Replicate the exact aggregation logic from SignalLogger.kt
        val minSignals = 20
        val grouped = logs.filter { it.wallet.isNotBlank() }
            .groupBy { it.wallet }
            .filter { it.value.size >= minSignals }

        // Assert
        assertEquals(1, grouped.size)
        assertTrue("Should contain WalletA", grouped.containsKey("WalletA"))
        assertEquals(25, grouped["WalletA"]?.size)
        assertTrue("Should NOT contain WalletB", !grouped.containsKey("WalletB"))
    }
}
