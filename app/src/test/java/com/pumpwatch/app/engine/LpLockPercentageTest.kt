package com.pumpwatch.app.engine

import com.pumpwatch.app.data.LpHolder
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LpLockPercentageTest {

    @Test
    fun testLockedLpPercentageCalculation() {
        val lpHolders = listOf(
            LpHolder(address = "0x1", tag = "LP", percent = 50.0, is_locked = "1", locked_detail = emptyList()),
            LpHolder(address = "0x2", tag = "User", percent = 30.0, is_locked = "0", locked_detail = emptyList()),
            LpHolder(address = "0x3", tag = "LP", percent = 20.0, is_locked = "1", locked_detail = emptyList())
        )

        val lockedLpPercent = lpHolders.filter { it.is_locked == "1" }.sumOf { it.percent ?: 0.0 }
        assertEquals(70.0, lockedLpPercent, 0.01)
        
        val shouldWarn = lockedLpPercent > 0.0 && lockedLpPercent < 80.0
        assertTrue("Should warn when locked LP is 70 percent", shouldWarn)
    }

    @Test
    fun testNoWarningWhenLockedLpIsHigh() {
        val lpHolders = listOf(
            LpHolder(address = "0x1", tag = "LP", percent = 80.0, is_locked = "1", locked_detail = emptyList()),
            LpHolder(address = "0x2", tag = "User", percent = 20.0, is_locked = "0", locked_detail = emptyList())
        )

        val lockedLpPercent = lpHolders.filter { it.is_locked == "1" }.sumOf { it.percent ?: 0.0 }
        val shouldWarn = lockedLpPercent > 0.0 && lockedLpPercent < 80.0
        
        assertTrue("Should NOT warn when locked LP is 80 percent or more", !shouldWarn)
    }
}
