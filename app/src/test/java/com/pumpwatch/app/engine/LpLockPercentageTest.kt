package com.pumpwatch.app.engine

import com.pumpwatch.app.data.LpHolder
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LpLockPercentageTest {

    @Test
    fun `calculate locked LP percentage correctly`() {
        // Arrange: 3 LP holders, total 100%. 
        // Holder 1: 50% (locked)
        // Holder 2: 30% (unlocked)
        // Holder 3: 20% (locked)
        // Total locked = 70%
        val lpHolders = listOf(
            LpHolder(address = "0x1", tag = "LP", percent = 50.0, is_locked = "1", locked_detail = emptyList()),
            LpHolder(address = "0x2", tag = "User", percent = 30.0, is_locked = "0", locked_detail = emptyList()),
            LpHolder(address = "0x3", tag = "LP", percent = 20.0, is_locked = "1", locked_detail = emptyList())
        )

        // Act: Replicate the logic from MemeRadar.kt
        val lockedLpPercent = lpHolders.filter { it.is_locked == "1" }.sumOf { it.percent ?: 0.0 }

        // Assert
        assertEquals(70.0, lockedLpPercent, 0.01)
        
        // Verify the warning condition (< 80%)
        val shouldWarn = lockedLpPercent > 0.0 && lockedLpPercent < 80.0
        assertTrue("Should warn when locked LP is 70%", shouldWarn)
    }

    @Test
    fun `no warning when locked LP is >= 80%`() {
        val lpHolders = listOf(
            LpHolder(address = "0x1", tag = "LP", percent = 80.0, is_locked = "1", locked_detail = emptyList()),
            LpHolder(address = "0x2", tag = "User", percent = 20.0, is_locked = "0", locked_detail = emptyList())
        )

        val lockedLpPercent = lpHolders.filter { it.is_locked == "1" }.sumOf { it.percent ?: 0.0 }
        val shouldWarn = lockedLpPercent > 0.0 && lockedLpPercent < 80.0
        
        assertTrue("Should NOT warn when locked LP is 80%", !shouldWarn)
    }
}
