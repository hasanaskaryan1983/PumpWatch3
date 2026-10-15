package com.pumpwatch.app.store

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TradeStoreMigrationTest {

    private val gson = Gson()

    private fun parseLegacy(json: String): List<Trade> {
        val type = object : TypeToken<List<Trade>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @Test fun gson_does_NOT_apply_kotlin_defaults_on_legacy_json() {
        val t = parseLegacy("""[{"id":"t1","coinId":"btc","symbol":"BTC","name":"B","side":"PUMP","mode":"SPOT","entryPrice":100.0,"currentPrice":110.0,"entryTime":1,"exitTime":null,"exitPrice":null,"initialStop":90.0,"currentStop":90.0,"target1":120.0,"target2":130.0,"exitReason":null,"status":"OPEN"}]""").first()
        assertNull(t.venue); assertNull(t.source)
        assertEquals(0.0, t.feePct, 0.0); assertEquals(0, t.ledgerVersion)
    }

    @Test fun upgrade_legacy_v1_fills_safe_defaults() {
        val raw = parseLegacy("""[{"id":"t1","coinId":"btc","symbol":"BTC","name":"B","side":"PUMP","mode":"SPOT","entryPrice":100.0,"currentPrice":110.0,"entryTime":1,"exitTime":null,"exitPrice":null,"initialStop":90.0,"currentStop":90.0,"target1":120.0,"target2":130.0,"exitReason":null,"status":"OPEN"}]""").first()
        val up = TradeStore.upgradeLegacy(raw)
        assertEquals("unknown", up.venue); assertEquals("manual", up.source)
        assertEquals(0.1, up.feePct, 0.0); assertEquals(3, up.ledgerVersion)
        assertFalse("partialClose must default to false", up.partialClose)
        assertNull(up.partialClosePrice)
    }

    @Test fun upgrade_legacy_v2_to_v3_keeps_existing_data() {
        // v2 trade با source/scanner ولی بدون partial fields
        val v2 = Trade(
            id = "t2", coinId = "eth", symbol = "ETH", name = "E",
            side = "DUMP", mode = "FUT", entryPrice = 3000.0, currentPrice = 2900.0,
            entryTime = 1, exitTime = 2, exitPrice = 2900.0,
            initialStop = 3100.0, currentStop = 3100.0,
            target1 = 2800.0, target2 = 2700.0,
            exitReason = "STOP", status = "CLOSED",
            source = "scanner", note = "یادداشت من", sizeUsd = 250.0,
            venue = "Bybit", feePct = 0.15, slippagePct = 0.05, ledgerVersion = 2
        )
        val up = TradeStore.upgradeLegacy(v2)
        assertEquals("scanner", up.source); assertEquals("Bybit", up.venue)
        assertEquals(0.15, up.feePct, 0.0); assertEquals(3, up.ledgerVersion)
        assertFalse(up.partialClose)
    }

    @Test fun trade_with_partial_close_preserves_fields() {
        // v3 trade که قبلاً partial شده
        val t = Trade(
            id = "t3", coinId = "btc", symbol = "BTC", name = "B",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 115.0,
            entryTime = 1, exitTime = null, exitPrice = null,
            initialStop = 90.0, currentStop = 100.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = null, status = "OPEN",
            partialClose = true, partialClosePrice = 110.0,
            partialCloseTime = 1000, partialCloseReason = "TP1",
            ledgerVersion = 3
        )
        val up = TradeStore.upgradeLegacy(t)
        assertEquals(true, up.partialClose)
        assertEquals(110.0, up.partialClosePrice!!, 0.0)
        assertEquals("TP1", up.partialCloseReason)
    }

    // ---------- 🚀 Commit 11: توابع PnL جدید ----------

    @Test fun realized_pnl_partial_is_zero_when_not_partialed() {
        val t = Trade(
            id = "t4", coinId = "btc", symbol = "BTC", name = "B",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 110.0,
            entryTime = 1, exitTime = null, exitPrice = null,
            initialStop = 90.0, currentStop = 90.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = null, status = "OPEN",
            partialClose = false
        )
        assertEquals(0.0, t.realizedPnlPartial(), 0.0001)
    }

    @Test fun realized_pnl_partial_is_half_of_pnl_at_partial_price() {
        // entry=100, partialPrice=110, feePct=0.1, slip=0.0
        // full PnL would be 10 - 0.2 - 0 = 9.8%
        // partial (50%) = 4.9%
        val t = Trade(
            id = "t5", coinId = "btc", symbol = "BTC", name = "B",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 115.0,
            entryTime = 1, exitTime = null, exitPrice = null,
            initialStop = 90.0, currentStop = 100.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = null, status = "OPEN",
            partialClose = true, partialClosePrice = 110.0,
            partialCloseTime = 1000, partialCloseReason = "TP1",
            feePct = 0.1, slippagePct = 0.0
        )
        assertEquals(4.9, t.realizedPnlPartial(), 0.0001)
    }

    @Test fun total_realized_pnl_sums_both_halves() {
        // entry=100, partialPrice=110 (TP1), exitPrice=130 (final)
        // partial = 0.5 * (10 - 0.2) = 4.9%
        // final = 0.5 * (30 - 0.2) = 14.9%
        // total = 19.8%
        val t = Trade(
            id = "t6", coinId = "btc", symbol = "BTC", name = "B",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 130.0,
            entryTime = 1, exitTime = 2, exitPrice = 130.0,
            initialStop = 90.0, currentStop = 120.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = "TARGET", status = "CLOSED",
            partialClose = true, partialClosePrice = 110.0,
            partialCloseTime = 1000, partialCloseReason = "TP1",
            feePct = 0.1, slippagePct = 0.0
        )
        assertEquals(4.9, t.realizedPnlPartial(), 0.0001)
        assertEquals(14.9, t.realizedPnlFinal(), 0.0001)
        assertEquals(19.8, t.totalRealizedPnl(), 0.0001)
    }

    @Test fun existing_pnl_formula_preserved_for_non_partialed() {
        // trade بدون partial: همان فرمول قدیمی
        val t = Trade(
            id = "t7", coinId = "btc", symbol = "BTC", name = "B",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 110.0,
            entryTime = 1, exitTime = 2, exitPrice = 110.0,
            initialStop = 90.0, currentStop = 90.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = "TARGET", status = "CLOSED",
            feePct = 0.1, slippagePct = 0.0
        )
        assertEquals(9.8, t.realizedPnl(), 0.0001)
        // partial = 0, final = 4.9 (half) => total = 4.9
        assertEquals(0.0, t.realizedPnlPartial(), 0.0001)
        assertEquals(4.9, t.realizedPnlFinal(), 0.0001)
    }
}
