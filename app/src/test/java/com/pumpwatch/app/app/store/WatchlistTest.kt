package com.pumpwatch.app.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 15 (فاز ۲ / Commit 13): تست‌های pure واچ‌لیست
 * فقط توابع evaluate/mergeTriggered تست می‌شوند (بدون Context)
 */
class WatchlistTest {

    private fun alert(id: String, coinId: String, above: Boolean, threshold: Double) =
        WatchAlert(
            id = id,
            coinId = coinId,
            symbol = coinId.uppercase(),
            name = coinId,
            above = above,
            threshold = threshold,
            createdAt = System.currentTimeMillis()
        )

    @Test
    fun evaluate_triggers_above_when_price_exceeds_threshold() {
        val alerts = listOf(alert("a1", "btc", true, 50000.0))
        val priceOf: (String) -> Double? = { if (it == "btc") 50500.0 else null }
        val fired = WatchlistStore.evaluate(alerts, priceOf)
        assertEquals(1, fired.size)
        assertEquals("a1", fired[0].id)
    }

    @Test
    fun evaluate_triggers_below_when_price_drops_below_threshold() {
        val alerts = listOf(alert("a1", "btc", false, 50000.0))
        val priceOf: (String) -> Double? = { if (it == "btc") 49000.0 else null }
        val fired = WatchlistStore.evaluate(alerts, priceOf)
        assertEquals(1, fired.size)
        assertEquals("a1", fired[0].id)
    }

    @Test
    fun evaluate_does_not_trigger_above_when_price_below() {
        val alerts = listOf(alert("a1", "btc", true, 50000.0))
        val priceOf: (String) -> Double? = { if (it == "btc") 49000.0 else null }
        val fired = WatchlistStore.evaluate(alerts, priceOf)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun evaluate_does_not_trigger_below_when_price_above() {
        val alerts = listOf(alert("a1", "btc", false, 50000.0))
        val priceOf: (String) -> Double? = { if (it == "btc") 51000.0 else null }
        val fired = WatchlistStore.evaluate(alerts, priceOf)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun evaluate_handles_multiple_alerts() {
        val alerts = listOf(
            alert("a1", "btc", true, 50000.0),
            alert("a2", "eth", false, 3000.0),
            alert("a3", "btc", false, 45000.0)
        )
        val priceOf: (String) -> Double? = { coin ->
            when (coin) {
                "btc" -> 51000.0
                "eth" -> 2900.0
                else -> null
            }
        }
        val fired = WatchlistStore.evaluate(alerts, priceOf)
        assertEquals(2, fired.size)
        assertTrue(fired.any { it.id == "a1" })
        assertTrue(fired.any { it.id == "a2" })
        assertFalse(fired.any { it.id == "a3" })
    }

    @Test
    fun evaluate_skips_missing_prices() {
        val alerts = listOf(alert("a1", "btc", true, 50000.0))
        val priceOf: (String) -> Double? = { null }
        val fired = WatchlistStore.evaluate(alerts, priceOf)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun merge_triggered_marks_fired_alerts_with_timestamp() {
        val all = listOf(
            alert("a1", "btc", true, 50000.0),
            alert("a2", "eth", false, 3000.0)
        )
        val firedIds = setOf("a1")
        val priceOf: (String) -> Double? = { if (it == "btc") 51000.0 else null }
        val now = 1234567890L
        val merged = WatchlistStore.mergeTriggered(all, firedIds, priceOf, now)

        assertEquals(2, merged.size)
        val a1 = merged.find { it.id == "a1" }!!
        assertEquals(now, a1.triggeredAt)
        assertEquals(51000.0, a1.triggeredPrice!!, 0.0)
        val a2 = merged.find { it.id == "a2" }!!
        assertEquals(null, a2.triggeredAt)
        assertEquals(null, a2.triggeredPrice)
    }

    @Test
    fun merge_triggered_does_not_overwrite_already_triggered() {
        val old = 1000L
        val a1 = WatchAlert(
            id = "a1", coinId = "btc", symbol = "BTC", name = "B",
            above = true, threshold = 50000.0, createdAt = 0L,
            triggeredAt = old, triggeredPrice = 50500.0
        )
        val merged = WatchlistStore.mergeTriggered(
            listOf(a1), setOf("a1"), { 51000.0 }, 2000L
        )
        assertEquals(old, merged[0].triggeredAt)
        assertEquals(50500.0, merged[0].triggeredPrice!!, 0.0)
    }
}
