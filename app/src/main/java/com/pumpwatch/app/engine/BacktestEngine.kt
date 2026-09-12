package com.pumpwatch.app.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object BacktestEngine {

    private const val FEE_RATE = 0.001 // 0.1% per side
    private const val SLIPPAGE_RATE = 0.0005 // 0.05% per side

    data class Trade(
        val symbol: String,
        val entryIndex: Int,
        val exitIndex: Int,
        val side: String,
        val entryPrice: Double,
        val exitPrice: Double,
        val pnl: Double,
        val result: String, // WIN, LOSS, EXP
        val score: Int
    )

    data class BacktestMetrics(
        val totalTrades: Int,
        val wins: Int,
        val losses: Int,
        val expired: Int,
        val winRate: Double,
        val profitFactor: Double,
        val avgPnl: Double,
        val avgWin: Double,
        val avgLoss: Double,
        val expectancy: Double,
        val totalPnl: Double,
        val maxDrawdown: Double,
        val equityCurve: List<Double>,
        val inSampleMetrics: SampleMetrics?,
        val outOfSampleMetrics: SampleMetrics?
    )

    data class SampleMetrics(
        val trades: Int,
        val winRate: Double,
        val totalPnl: Double,
        val avgPnl: Double
    )

    fun runFutures(
        symbol: String,
        klines: List<List<Double>>,
        evalLast: Int,
        hold: Int,
        fundingRate: Double = 0.0,
        signalThreshold: Int = 70
    ): Pair<List<Trade>, BacktestMetrics> {
        if (klines.size < 100) return emptyList<Trade>() to emptyMetrics()

        val allCandles = klines.mapIndexed { index, k -> 
            Candle(time = 0L, open = k[0], high = k[1], low = k[2], close = k[3], volume = k[4])
        }
        val closes = allCandles.map { it.close }
        val highs = allCandles.map { it.high }
        val lows = allCandles.map { it.low }

        val start = max(48, closes.size - evalLast)
        val end = closes.size - hold
        val trades = mutableListOf<Trade>()
        var prevSide = "NONE"

        for (i in start until end) {
            val currentCandles = allCandles.subList(0, i + 1)
            
            val signal = UnifiedSignalEngine.analyze(
                coinId = "TEST", symbol = symbol, name = symbol, 
                candles1h = currentCandles, mode = "FUT", funding = fundingRate,
                params = UnifiedSignalParams(minScore = signalThreshold)
            )

            val side = signal?.side ?: "NONE"
            val freshSignal = side != "NONE" && side != prevSide

            if (freshSignal) {
                val entryPrice = closes[i]
                val isLong = side == "PUMP"
                val score = signal?.score ?: 0

                val entryReal = if (isLong) {
                    entryPrice * (1 + FEE_RATE + SLIPPAGE_RATE)
                } else {
                    entryPrice * (1 - FEE_RATE - SLIPPAGE_RATE)
                }

                val atr = atrAt(closes, i)
                val risk = if (atr > 0) atr * 1.5 else entryPrice * 0.05
                val stop = if (isLong) entryPrice - risk else entryPrice + risk
                val target = if (isLong) entryPrice + risk * 1.5 else entryPrice - risk * 1.5

                var result = "EXP"
                var exitPrice = closes[min(i + hold, closes.size - 1)]
                var exitIndex = min(i + hold, closes.size - 1)

                for (j in (i + 1)..min(i + hold, closes.size - 1)) {
                    val hj = highs[j]
                    val lj = lows[j]
                    if (isLong) {
                        if (lj <= stop) { result = "LOSS"; exitPrice = stop; exitIndex = j; break }
                        if (hj >= target) { result = "WIN"; exitPrice = target; exitIndex = j; break }
                    } else {
                        if (hj >= stop) { result = "LOSS"; exitPrice = stop; exitIndex = j; break }
                        if (lj <= target) { result = "WIN"; exitPrice = target; exitIndex = j; break }
                    }
                }

                val exitReal = if (isLong) {
                    exitPrice * (1 - FEE_RATE - SLIPPAGE_RATE)
                } else {
                    exitPrice * (1 + FEE_RATE + SLIPPAGE_RATE)
                }

                val fundingCost = if (isLong) {
                    -fundingRate * (exitIndex - i) / 8.0 * entryPrice
                } else {
                    fundingRate * (exitIndex - i) / 8.0 * entryPrice
                }

                val pnl = if (isLong) {
                    ((exitReal - entryReal) / entryReal * 100) + (fundingCost / entryPrice * 100)
                } else {
                    ((entryReal - exitReal) / entryReal * 100) + (fundingCost / entryPrice * 100)
                }

                trades.add(Trade(symbol, i, exitIndex, if (isLong) "BUY" else "SELL", entryPrice, exitPrice, pnl, result, score))
                prevSide = side
            }
        }
        return trades to computeMetrics(trades)
    }

    fun runSpot(
        symbol: String,
        klines: List<List<Double>>,
        holdDays: Int,
        scoreThreshold: Int = 70
    ): Pair<List<Trade>, BacktestMetrics> {
        return runFutures(symbol, klines, holdDays, holdDays, 0.0, scoreThreshold)
    }

    private fun computeMetrics(trades: List<Trade>): BacktestMetrics {
        if (trades.isEmpty()) return emptyMetrics()

        val wins = trades.count { it.result == "WIN" }
        val losses = trades.count { it.result == "LOSS" }
        val expired = trades.count { it.result == "EXP" }
        val decided = wins + losses
        val winRate = if (decided > 0) wins * 100.0 / decided else 0.0
        val avgPnl = trades.map { it.pnl }.average()
        val totalPnl = trades.sumOf { it.pnl }

        val winningTrades = trades.filter { it.pnl > 0 }
        val losingTrades = trades.filter { it.pnl < 0 }
        val avgWin = if (winningTrades.isNotEmpty()) winningTrades.map { it.pnl }.average() else 0.0
        val avgLoss = if (losingTrades.isNotEmpty()) abs(losingTrades.map { it.pnl }.average()) else 0.0

        val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)

        val totalWins = winningTrades.sumOf { it.pnl }
        val totalLosses = abs(losingTrades.sumOf { it.pnl })
        val profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) Double.POSITIVE_INFINITY else 0.0

        val equityCurve = mutableListOf(100.0)
        var equity = 100.0
        trades.forEach { t ->
            equity *= (1 + t.pnl / 100.0)
            equityCurve.add(equity)
        }

        var maxDrawdown = 0.0
        var peak = equityCurve[0]
        for (e in equityCurve) {
            if (e > peak) peak = e
            val dd = (peak - e) / peak * 100.0
            if (dd > maxDrawdown) maxDrawdown = dd
        }

        val splitIndex = (trades.size * 0.7).toInt()
        val inSampleTrades = trades.subList(0, max(1, splitIndex))
        val outOfSampleTrades = trades.subList(splitIndex, trades.size)

        val inSampleMetrics = if (inSampleTrades.isNotEmpty()) {
            val inWins = inSampleTrades.count { it.result == "WIN" }
            val inLosses = inSampleTrades.count { it.result == "LOSS" }
            val inDecided = inWins + inLosses
            SampleMetrics(
                trades = inSampleTrades.size,
                winRate = if (inDecided > 0) inWins * 100.0 / inDecided else 0.0,
                totalPnl = inSampleTrades.sumOf { it.pnl },
                avgPnl = inSampleTrades.map { it.pnl }.average()
            )
        } else null

        val outOfSampleMetrics = if (outOfSampleTrades.isNotEmpty()) {
            val outWins = outOfSampleTrades.count { it.result == "WIN" }
            val outLosses = outOfSampleTrades.count { it.result == "LOSS" }
            val outDecided = outWins + outLosses
            SampleMetrics(
                trades = outOfSampleTrades.size,
                winRate = if (outDecided > 0) outWins * 100.0 / outDecided else 0.0,
                totalPnl = outOfSampleTrades.sumOf { it.pnl },
                avgPnl = outOfSampleTrades.map { it.pnl }.average()
            )
        } else null

        return BacktestMetrics(
            totalTrades = trades.size, wins = wins, losses = losses, expired = expired,
            winRate = winRate, profitFactor = profitFactor, avgPnl = avgPnl,
            avgWin = avgWin, avgLoss = avgLoss, expectancy = expectancy, totalPnl = totalPnl,
            maxDrawdown = maxDrawdown, equityCurve = equityCurve,
            inSampleMetrics = inSampleMetrics, outOfSampleMetrics = outOfSampleMetrics
        )
    }

    private fun emptyMetrics() = BacktestMetrics(
        totalTrades = 0, wins = 0, losses = 0, expired = 0,
        winRate = 0.0, profitFactor = 0.0, avgPnl = 0.0,
        avgWin = 0.0, avgLoss = 0.0, expectancy = 0.0, totalPnl = 0.0,
        maxDrawdown = 0.0, equityCurve = listOf(100.0),
        inSampleMetrics = null, outOfSampleMetrics = null
    )

    private fun atrAt(data: List<Double>, index: Int, period: Int = 14): Double {
        if (index < period) return 0.0
        var s = 0.0
        for (k in (index - period + 1)..index) s += abs(data[k] - data[k - 1])
        return s / period
    }
}
