package com.pumpwatch.app.engine

import android.content.Context
import com.pumpwatch.app.data.ScanClient
import com.pumpwatch.app.data.Trade
import com.pumpwatch.app.store.PicksStore
import com.pumpwatch.app.store.TradeStore
import java.util.UUID

object PaperTradingEngine {

    suspend fun openFromSignal(ctx: Context, sig: SignalResult) {
        if (!TradeStore.isEnabled(ctx)) return

        val openIds = TradeStore.load(ctx)
            .filter { it.status == "OPEN" }
            .map { it.coinId }
            .toSet()
        if (sig.coinId in openIds) return

        val trade = Trade(
            id = UUID.randomUUID().toString(),
            coinId = sig.coinId,
            symbol = sig.symbol,
            name = sig.name,
            side = sig.side,
            mode = sig.mode,
            entryPrice = sig.entry,
            currentPrice = sig.price,
            entryTime = System.currentTimeMillis(),
            exitTime = null,
            exitPrice = null,
            initialStop = sig.stopLoss,
            currentStop = sig.stopLoss,
            target1 = sig.target1,
            target2 = sig.target2,
            exitReason = null,
            status = "OPEN"
        )
        TradeStore.upsert(ctx, trade)
    }

    suspend fun checkAndClose(ctx: Context): List<Trade> {
        val trades = TradeStore.load(ctx).filter { it.status == "OPEN" }
        if (trades.isEmpty()) return emptyList()
        val closed = mutableListOf<Trade>()

        for (t in trades) {
            try {
                val chart = ScanClient.api.chart(t.coinId, days = 1, interval = "hourly")
                val latestPrice = chart.prices.lastOrNull()?.get(1) ?: continue
                val updated = updateTrade(t, latestPrice)
                if (updated.status == "CLOSED") closed.add(updated)
                TradeStore.upsert(ctx, updated)
            } catch (_: Exception) { }
        }
        return closed
    }

    private fun updateTrade(t: Trade, price: Double): Trade {
        val isLong = t.side == "PUMP"
        var newStop = t.currentStop

        if (isLong) {
            if (price > t.entryPrice) {
                val trailStop = price - (t.entryPrice - t.initialStop)
                newStop = maxOf(newStop, trailStop)
                if (price >= t.target1) newStop = maxOf(newStop, t.entryPrice)
            }
        } else {
            if (price < t.entryPrice) {
                val trailStop = price + (t.initialStop - t.entryPrice)
                newStop = if (newStop == 0.0 || newStop > trailStop) trailStop else newStop
                if (price <= t.target1) {
                    newStop = if (newStop == 0.0 || newStop < t.entryPrice) t.entryPrice else newStop
                }
            }
        }

        var exitReason: String? = null
        var exitPrice: Double? = null

        when {
            isLong && price <= newStop -> {
                exitReason = if (newStop >= t.entryPrice) "BE" else "STOP"
                exitPrice = newStop
            }
            !isLong && price >= newStop -> {
                exitReason = if (newStop <= t.entryPrice) "BE" else "STOP"
                exitPrice = newStop
            }
            isLong && price >= t.target2 -> { exitReason = "TARGET"; exitPrice = price }
            !isLong && price <= t.target2 -> { exitReason = "TARGET"; exitPrice = price }
        }

        return if (exitReason != null && exitPrice != null) {
            t.copy(
                currentPrice = price,
                currentStop = newStop,
                exitTime = System.currentTimeMillis(),
                exitPrice = exitPrice,
                exitReason = exitReason,
                status = "CLOSED"
            )
        } else {
            t.copy(currentPrice = price, currentStop = newStop)
        }
    }

    fun closeManual(ctx: Context, id: String, price: Double) {
        val t = TradeStore.load(ctx).find { it.id == id } ?: return
        val closed = t.copy(
            currentPrice = price,
            exitTime = System.currentTimeMillis(),
            exitPrice = price,
            exitReason = "MANUAL",
            status = "CLOSED"
        )
        TradeStore.upsert(ctx, closed)
    }

    suspend fun syncFromToday(ctx: Context) {
        if (!TradeStore.isEnabled(ctx)) return
        val openIds = TradeStore.load(ctx)
            .filter { it.status == "OPEN" }
            .map { it.coinId }
            .toSet()

        for (mode in listOf("SPOT", "FUT")) {
            val today = PicksStore.loadToday(ctx, mode)?.picks ?: emptyList()
            for (sig in today) {
                if (sig.golden && sig.coinId !in openIds) {
                    openFromSignal(ctx, sig)
                }
            }
        }
    }
}
