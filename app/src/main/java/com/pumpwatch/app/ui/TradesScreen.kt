@Composable
private fun JournalContent(allTrades: List<PaperTrade>) {
    val closed = allTrades.filter { it.status == "CLOSED" }
    val wins = closed.filter { it.pnl > 0 }; val losses = closed.filter { it.pnl < 0 }
    val winRate = if (closed.isEmpty()) 0.0 else wins.size * 100.0 / closed.size
    val avgPnl = if (closed.isEmpty()) 0.0 else closed.map { it.pnl }.average()
    val avgWin = if (wins.isEmpty()) 0.0 else wins.map { it.pnl }.average()
    val avgLoss = if (losses.isEmpty()) 0.0 else abs(losses.map { it.pnl }.average())
    val expectancy = (winRate / 100.0 * avgWin) - ((1 - winRate / 100.0) * avgLoss)
    val totalPnlUsd = closed.sumOf { it.pnl * it.sizeUsd / 100.0 }
    val grossProfit = wins.sumOf { it.pnl * it.sizeUsd / 100.0 }
    val grossLoss = abs(losses.sumOf { it.pnl * it.sizeUsd / 100.0 })
    val profitFactor = when { grossLoss > 0 -> grossProfit / grossLoss; grossProfit > 0 -> Double.POSITIVE_INFINITY; else -> 0.0 }
    val equityCurve = buildEquityCurve(closed); val maxDD = computeMaxDrawdown(equityCurve)
    val rMultiples = closed.map { rMultiple(it) }; val avgR = if (rMultiples.isEmpty()) 0.0 else rMultiples.average()
    val bestR = rMultiples.maxOrNull() ?: 0.0; val worstR = rMultiples.minOrNull() ?: 0.0
    val monthly = computeMonthly(closed); val tierStats = computeTierStats(closed)
