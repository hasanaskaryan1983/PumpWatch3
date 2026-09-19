package com.pumpwatch.app.engine

import android.content.Context
import com.pumpwatch.app.data.BinanceClient
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

        // 🚀 Sprint 14 (Commit 7C): provenance کامل حفظ شد — venue و fillTime واقعی
        val venue = BinanceClient.api.lastSource(sig.symbol)
        val fillTime = System.currentTimeMillis()

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
            status = "OPEN",
            source = "scanner",
            venue = venue,
            fillTime = fillTime,
            slippagePct = 0.1,
            feePct = 0.1,
            ledgerVersion = 2
        )
        TradeStore.upsert(ctx, trade)
    }

    suspend fun checkAndClose(ctx: Context): List<Trade> {
        val trades = TradeStore.load(ctx).filter { it.status == "OPEN" }
        if (trades.isEmpty()) return emptyList()
        val closed = mutableListOf<Trade>()

        for (t in trades) {
            try {
                // 🚀 Commit 10: دو روز کندل ساعتی برای تاریخچهٔ آشکارسازها
                val chart = ScanClient.api.chart(t.coinId, days = 2, interval = "hourly")
                val pts = chart.prices
                val latestPrice = pts.lastOrNull()?.get(1) ?: continue
                // فقط کندل‌های بستهٔ بعد از ورود
                val closes = pts
                    .filter { it[0].toLong() >= t.entryTime && it[0].toLong() < System.currentTimeMillis() - 3_600_000L }
                    .map { it[1] }
                val updated = updateTrade(t, latestPrice, closes)
                if (updated.status == "CLOSED") closed.add(updated)
                TradeStore.upsert(ctx, updated)
            } catch (_: Exception) { }
        }
        return closed
    }

    /**
     * 🚀 Sprint 15 (Commit 10): خروج کاملاً به ExitEngine سپرده شد.
     * ratchet یک‌طرفه: استاپ لانگ فقط بالا، استاپ شورت فقط پایین.
     */
    private fun updateTrade(t: Trade, price: Double, closes: List<Double>): Trade {
        val dec = ExitEngine.decide(
            ExitContext(
                side = t.side,
                entry = t.entryPrice,
                initialStop = t.initialStop,
                currentStop = t.currentStop,
                target = t.target2,
                closes = closes,
                livePrice = price
            )
        )

        var newStop = dec.newStop ?: t.currentStop
        if (t.side == "PUMP") {
            if (newStop < t.currentStop) newStop = t.currentStop
        } else {
            if (t.currentStop > 0.0 && newStop > t.currentStop) newStop = t.currentStop
        }

        return if (dec.action == "CLOSE") {
            t.copy(
                currentPrice = price,
                currentStop = newStop,
                exitTime = System.currentTimeMillis(),
                exitPrice = dec.exitPrice ?: price,
                exitReason = dec.reason ?: "STOP",
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
