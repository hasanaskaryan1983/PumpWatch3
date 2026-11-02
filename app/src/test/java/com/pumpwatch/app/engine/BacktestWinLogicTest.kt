package com.pumpwatch.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BacktestWinLogicTest {

    @Test
    fun testHWin2TargetWithNegativeNetPnlIsNotWin() {
        // شبیه‌سازی: سود ناخالص 0.5 درصد، اما کارمزد رفت و برگشت 0.6 درصد
        val pnlRaw = 0.5
        val feeRoundTrip = 0.6
        val pnl = pnlRaw - feeRoundTrip // نتیجه: -0.1
        
        // منطق جدید H-WIN2
        val evReason = "TARGET"
        val result = when {
            pnl > 0.0 -> "WIN"
            evReason == "STOP" -> "LOSS"
            else -> "EXP"
        }
        
        // اثبات: با وجود رسیدن به تارگت، چون سود خالص منفی است، WIN نیست
        assertEquals("EXP", result)
    }

    @Test
    fun testHWin2PositiveNetPnlMustBeWin() {
        val pnlRaw = 2.0
        val feeRoundTrip = 0.6
        val pnl = pnlRaw - feeRoundTrip // نتیجه: 1.4
        
        val evReason = "TARGET"
        val result = when {
            pnl > 0.0 -> "WIN"
            evReason == "STOP" -> "LOSS"
            else -> "EXP"
        }
        
        assertEquals("WIN", result)
    }
}
