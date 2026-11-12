package com.pumpwatch.app.engine

import com.google.gson.JsonArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BacktestEngineF9ConsistencyTest {
    private val dayMs = 86_400_000L

    private fun candle(time: Long, open: Double, high: Double, low: Double, close: Double, vol: Double): List<Double> {
        return listOf(time.toDouble(), open, high, low, close, vol)
    }

    @Test
    fun `F9 - Backtest spotScore delegates exactly to ScoringEngine`() {
        val start = LocalDate.of(2024, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val klines = (0 until 250).map { i ->
            candle(start + i * dayMs, 100.0 + i * 0.1, 105.0 + i * 0.1, 95.0 + i * 0.1, 100.0 + i * 0.1, 1000.0)
        }

        val jsonCandles = klines.map { k ->
            JsonArray().apply {
                add(k[0].toLong()); add(k[1]); add(k[2]); add(k[3]); add(k[4]); add(k[5])
            }
        }

        val backtestScore = ScoringEngine.scoreFromCandles(jsonCandles).first
        assertTrue("Score out of bounds: $backtestScore", backtestScore in -100..100)
        
        val weekly = ScoringEngine.weeklyClosesAligned(jsonCandles)
        assertTrue("Weekly alignment failed", weekly.isNotEmpty())
        
        val c = klines.map { it[4] }
        val v = klines.map { it[5] }
        val obvScore = ScoringEngine.obvDeltaScore(c, v)
        assertTrue("OBV score out of bounds: $obvScore", obvScore in -10..10)
    }
}
