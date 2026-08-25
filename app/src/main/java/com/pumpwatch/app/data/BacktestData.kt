package com.pumpwatch.app.data

data class SimulatedTrade(
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlPercent: Double
)

data class BacktestResult(
    val totalTrades: Int,
    val winRatePercent: Double,
    val netPnlPercent: Double,
    val maxDrawdownPercent: Double,
    val trades: List<SimulatedTrade>
)

object BacktestEngine {

    fun run(
        series: List<Pair<Long, Double>>,
        buyDropPercent: Double,
        sellRisePercent: Double
    ): BacktestResult {
        if (series.size < 2) {
            return BacktestResult(0, 0.0, 0.0, 0.0, emptyList())
        }

        val trades = mutableListOf<SimulatedTrade>()
        var inPosition = false
        var entryPrice = 0.0
        var peak = series.first().second
        var equity = 100.0
        var peakEquity = 100.0
        var maxDrawdown = 0.0

        for ((_, price) in series) {
            if (!inPosition) {
                // دنبال نقطه خرید: ریزش کافی از قله اخیر
                peak = maxOf(peak, price)
                val dropFromPeak = (peak - price) / peak * 100.0
                if (dropFromPeak >= buyDropPercent) {
                    inPosition = true
                    entryPrice = price
                }
            } else {
                // دنبال نقطه فروش: رشد یا افت کافی از قیمت خرید
                val changeFromEntry = (price - entryPrice) / entryPrice * 100.0
                if (changeFromEntry >= sellRisePercent || changeFromEntry <= -sellRisePercent) {
                    inPosition = false
                    trades.add(SimulatedTrade(entryPrice, price, changeFromEntry))
                    equity *= (1.0 + changeFromEntry / 100.0)
                    peakEquity = maxOf(peakEquity, equity)
                    val dd = (peakEquity - equity) / peakEquity * 100.0
                    maxDrawdown = maxOf(maxDrawdown, dd)
                    peak = price
                }
            }
        }

        val total = trades.size
        val wins = trades.count { it.pnlPercent >= 0 }
        val winRate = if (total > 0) wins * 100.0 / total else 0.0
        val netPnl = equity - 100.0

        return BacktestResult(
            totalTrades = total,
            winRatePercent = winRate,
            netPnlPercent = netPnl,
            maxDrawdownPercent = maxDrawdown,
            trades = trades
        )
    }
}
