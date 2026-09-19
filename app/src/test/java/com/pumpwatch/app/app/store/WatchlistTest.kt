package com.pumpwatch.app.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Sprint 15 (فاز ۲ / Commit 16): تست‌های pure واچ‌لیست v2 (گروه‌بندی‌شده)
 */
class WatchlistTest {

    private fun alert(id: String, above: Boolean, threshold: Double) =
        WatchAlert(id = id, above = above, threshold = threshold, createdAt = System.currentTimeMillis())

    private fun coin(id: String, alerts: List<WatchAlert> = emptyList()) =
        WatchCoin(id = id, symbol = id.uppercase(), name = id, alerts = alerts)

    private fun group(id: String, coins: List<WatchCoin>) =
        WatchGroup(id = id, name = "Test $id", coins = coins)

    @Test
    fun evaluate_triggers_above_when_price_exceeds() {
        val groups = listOf(group("g1", listOf(coin("btc", listOf(alert("a1", true, 50000.0))))))
        val priceOf: (String) -> Double? = { if (it == "btc") 50500.0 else null }
        val fired = WatchlistStore.evaluate(groups, priceOf)
        assertEquals(1, fired.size)
        assertEquals("a1", fired[0].third.id)
    }

    @Test
    fun evaluate_triggers_below_when_price_drops() {
        val groups = listOf(group("g1", listOf(coin("btc", listOf(alert("a1", false, 50000.0))))))
        val priceOf: (String) -> Double? = { if (it == "btc") 49000.0 else null }
        val fired = WatchlistStore.evaluate(groups, priceOf)
        assertEquals(1, fired.size)
        assertEquals("a1", fired[0].third.id)
    }

    @Test
    fun evaluate_skips_already_triggered() {
        val triggered = WatchAlert("a1", true, 50000.0, System.currentTimeMillis(), System.currentTimeMillis() - 1000, 50500.0)
        val groups = listOf(group("g1", listOf(coin("btc", listOf(triggered)))))
        val priceOf: (String) -> Double? = { 51000.0 }
        val fired = WatchlistStore.evaluate(groups, priceOf)
        assertTrue(fired.isEmpty())
    }

    @Test
    fun evaluate_handles_multiple_groups_and_coins() {
        val groups = listOf(
            group("g1", listOf(
                coin("btc", listOf(alert("a1", true, 50000.0))),
                coin("eth", listOf(alert("a2", false, 3000.0)))
            )),
            group("g2", listOf(
                coin("sol", listOf(alert("a3", true, 150.0)))
            ))
        )
        val priceOf: (String) -> Double? = { coin ->
            when (coin) {
                "btc" -> 51000.0
                "eth" -> 2900.0
                "sol" -> 140.0
                else -> null
            }
        }
        val fired = WatchlistStore.evaluate(groups, priceOf)
        assertEquals(2, fired.size)
        assertTrue(fired.any { it.third.id == "a1" })
        assertTrue(fired.any { it.third.id == "a2" })
        assertTrue(fired.none { it.third.id == "a3" })
    }

    @Test
    fun mark_triggered_sets_timestamp_and_price() {
        val groups = listOf(group("g1", listOf(coin("btc", listOf(alert("a1", true, 50000.0))))))
        val fired = listOf(Triple("g1", "btc", alert("a1", true, 50000.0)))
        val priceOf: (String) -> Double? = { 51000.0 }
        val now = 1234567890L
        val updated = WatchlistStore.markTriggered(groups, fired, priceOf, now)

        assertEquals(1, updated.size)
        val coin = updated[0].coins[0]
        val a = coin.alerts[0]
        assertEquals(now, a.triggeredAt)
        assertEquals(51000.0, a.triggeredPrice!!, 0.0)
    }

    @Test
    fun mark_triggered_does_not_overwrite_already_triggered() {
        val old = 1000L
        val triggered = WatchAlert("a1", true, 50000.0, 0L, old, 50500.0)
        val groups = listOf(group("g1", listOf(coin("btc", listOf(triggered)))))
        val fired = listOf(Triple("g1", "btc", triggered))
        val updated = WatchlistStore.markTriggered(groups, fired, { 51000.0 }, 2000L)

        val a = updated[0].coins[0].alerts[0]
        assertEquals(old, a.triggeredAt)
        assertEquals(50500.0, a.triggeredPrice!!, 0.0)
    }
}
