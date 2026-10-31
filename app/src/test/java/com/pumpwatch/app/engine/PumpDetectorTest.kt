package com.pumpwatch.app.engine
import org.junit.Assert.*
import org.junit.Test

class PumpDetectorTest {
    @Test fun detectEarlyPump_normalData() {
        val c = gen(30, 1.0, 0.001)
        assertFalse(PumpDetector.detectEarlyPump(c))
    }
    @Test fun detectEarlyPump_spikeData() {
        val c = gen(30, 3.0, 0.02)
        assertTrue(PumpDetector.detectEarlyPump(c))
    }
    @Test fun isAtTop_nearHigh() {
        val c = gen(60, 1.0, 0.0).toMutableList()
        c[c.lastIndex] = c.last().copy(close = 96.0, high = 100.0)
        assertTrue(PumpDetector.isAtTop(c))
    }
    @Test fun analyzePumpPotential_highScore() {
        val c = gen(60, 3.0, 0.02)
        assertTrue(PumpDetector.analyzePumpPotential(c) >= 40)
    }
    private fun gen(n: Int, vm: Double, pc: Double): List<Candle> {
        val l = mutableListOf<Candle>()
        var p = 100.0
        for (i in 0 until n) {
            val isLast = i >= n - 3
            val v = if (isLast) 100.0 * vm else 100.0
            p += if (isLast) pc else 0.0
            l.add(Candle(i.toLong(), p, p + 1.0, p - 1.0, p, v))
        }
        return l
    }
}
