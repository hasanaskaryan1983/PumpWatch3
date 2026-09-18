package com.pumpwatch.app.store

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pumpwatch.app.data.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 🚀 Sprint 14 (مرحله ۳ / Commit 7C-fix): تست migration از نسخه ۱ به ۲
 *
 * درس Commit 7C: Gson constructor کاتلین را صدا نمی‌زند، پس default value ها
 * روی JSON قدیمی اعمال نمی‌شوند. این تست‌ها خودِ آن تله را مستند می‌کنند
 * و ثابت می‌کنند upgradeLegacy آن را خنثی می‌کند.
 */
class TradeStoreMigrationTest {

    private val gson = Gson()

    private fun parseLegacy(json: String): List<Trade> {
        val type = object : TypeToken<List<Trade>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private val LEGACY_OPEN_JSON = """
        [
          {
            "id": "test-1",
            "coinId": "bitcoin",
            "symbol": "BTC",
            "name": "Bitcoin",
            "side": "PUMP",
            "mode": "SPOT",
            "entryPrice": 50000.0,
            "currentPrice": 51000.0,
            "entryTime": 1000000,
            "exitTime": null,
            "exitPrice": null,
            "initialStop": 48000.0,
            "currentStop": 48000.0,
            "target1": 55000.0,
            "target2": 60000.0,
            "exitReason": null,
            "status": "OPEN"
          }
        ]
    """.trimIndent()

    // ---------- ۱) مستندسازی تلهٔ Gson ----------

    @Test
    fun gson_does_NOT_apply_kotlin_defaults_on_legacy_json() {
        // این تست عمداً رفتار خام Gson را قفل می‌کند تا هیچ‌کس دوباره
        // به default value ها برای JSON قدیمی تکیه نکند
        val t = parseLegacy(LEGACY_OPEN_JSON).first()
        assertNull("Gson leaves missing String fields null", t.venue)
        assertNull("Gson leaves missing String fields null (source)", t.source)
        assertEquals("GCM: missing doubles become 0.0", 0.0, t.feePct, 0.0)
        assertEquals("missing ints become 0", 0, t.ledgerVersion)
    }

    // ---------- ۲) upgradeLegacy تله را خنثی می‌کند ----------

    @Test
    fun upgrade_legacy_fills_safe_defaults() {
        val raw = parseLegacy(LEGACY_OPEN_JSON).first()
        val up = TradeStore.upgradeLegacy(raw)

        assertEquals("unknown", up.venue)
        assertEquals("manual", up.source)
        assertEquals("", up.note)
        assertEquals(100.0, up.sizeUsd, 0.0)
        assertEquals(0.1, up.feePct, 0.0)
        assertEquals(0.0, up.slippagePct, 0.0)
        assertNull(up.fillTime)
        assertEquals(2, up.ledgerVersion)
    }

    @Test
    fun upgrade_preserves_exact_old_pnl_formula() {
        // فرمول قدیمی: realized = pct - 0.2 | unrealized = pct - 0.1
        // فرمول جدید با fee=0.1, slip=0.0: realized = pct - 2*0.1 - 0.0 = pct - 0.2 ✓
        val legacyClosed = """
            [
              {
                "id": "t2", "coinId": "bitcoin", "symbol": "BTC", "name": "Bitcoin",
                "side": "PUMP", "mode": "SPOT",
                "entryPrice": 100.0, "currentPrice": 110.0,
                "entryTime": 1, "exitTime": 2, "exitPrice": 110.0,
                "initialStop": 95.0, "currentStop": 95.0,
                "target1": 120.0, "target2": 130.0,
                "exitReason": "TARGET", "status": "CLOSED"
              }
            ]
        """.trimIndent()
        val up = TradeStore.upgradeLegacy(parseLegacy(legacyClosed).first())
        // 10% - 0.2% = 9.8% دقیقاً مثل نسخهٔ قدیمی
        assertEquals(9.8, up.realizedPnl(), 0.0001)
    }

    // ---------- ۳) تریدهای جدید با fee/slippage واقعی ----------

    @Test
    fun new_trade_realized_pnl_uses_actual_fees() {
        val trade = Trade(
            id = "test", coinId = "bitcoin", symbol = "BTC", name = "Bitcoin",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 110.0,
            entryTime = 1000, exitTime = 2000, exitPrice = 110.0,
            initialStop = 95.0, currentStop = 95.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = "TARGET", status = "CLOSED",
            feePct = 0.15, slippagePct = 0.05
        )
        // 10% - 0.30% fee - 0.05% slip = 9.65%
        assertEquals(9.65, trade.realizedPnl(), 0.0001)
    }

    @Test
    fun new_trade_unrealized_pnl_uses_actual_fee() {
        val trade = Trade(
            id = "test", coinId = "bitcoin", symbol = "BTC", name = "Bitcoin",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 105.0,
            entryTime = 1000, exitTime = null, exitPrice = null,
            initialStop = 95.0, currentStop = 95.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = null, status = "OPEN",
            feePct = 0.2
        )
        assertEquals(4.8, trade.unrealizedPnl(), 0.0001)
    }

    @Test
    fun r_multiple_calculation_is_correct() {
        val trade = Trade(
            id = "test", coinId = "bitcoin", symbol = "BTC", name = "Bitcoin",
            side = "PUMP", mode = "SPOT",
            entryPrice = 100.0, currentPrice = 120.0,
            entryTime = 1000, exitTime = 2000, exitPrice = 120.0,
            initialStop = 90.0, currentStop = 90.0,
            target1 = 120.0, target2 = 130.0,
            exitReason = "TARGET", status = "CLOSED"
        )
        val r = trade.rMultiple()
        assertNotNull(r)
        assertEquals(2.0, r!!, 0.0001)
    }

    // ---------- ۴) حفظ فیلدهای موجود قدیمی ----------

    @Test
    fun upgrade_preserves_existing_legacy_fields() {
        val legacyWithExtras = """
            [
              {
                "id": "t3", "coinId": "ethereum", "symbol": "ETH", "name": "Ethereum",
                "side": "DUMP", "mode": "FUT",
                "entryPrice": 3000.0, "currentPrice": 2900.0,
                "entryTime": 1, "exitTime": 2, "exitPrice": 2900.0,
                "initialStop": 3100.0, "currentStop": 3100.0,
                "target1": 2800.0, "target2": 2700.0,
                "exitReason": "STOP", "status": "CLOSED",
                "source": "scanner", "note": "یادداشت من", "sizeUsd": 250.0
              }
            ]
        """.trimIndent()
        val up = TradeStore.upgradeLegacy(parseLegacy(legacyWithExtras).first())
        assertEquals("scanner", up.source)
        assertEquals("یادداشت من", up.note)
        assertEquals(250.0, up.sizeUsd, 0.0)
        assertEquals("DUMP", up.side)
        assertEquals(2, up.ledgerVersion)
    }
}
