package com.pumpwatch.app.store

import org.junit.Assert.*
import org.junit.Test

class WatchlistStoreEvaluateTest {

    private fun makeGroup(id: String = "g1", coins: List<WatchCoin>): WatchGroup =
        WatchGroup(id = id, name = "Test", coins = coins)

    private fun makeCoin(id: String, alerts: List<WatchAlert>): WatchCoin =
        WatchCoin(id = id, symbol = id.uppercase(), name = id.uppercase(), alerts = alerts)

    private fun alert(above: Boolean, threshold: Double, triggered: Boolean = false): WatchAlert =
        WatchAlert(
            id = "a_$threshold",
            above = above,
            threshold = threshold,
            triggeredAt = if (triggered) 1000L else null
        )

    @Test
    fun `evaluate returns empty when no groups`() {
        val fired = WatchlistStore.evaluate(emptyList()) { null }
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `evaluate returns empty when no alerts`() {
        val groups = listOf(makeGroup(coins = listOf(makeCoin("btc", emptyList()))))
        val fired = WatchlistStore.evaluate(groups) { 50000.0 }
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `evaluate returns empty when price unknown`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = true, threshold = 50000.0)))
        )))
        val fired = WatchlistStore.evaluate(groups) { null }
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `ABOVE triggers when price equals threshold`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = true, threshold = 50000.0)))
        )))
        val priceMap = mapOf("btc" to 50000.0)
        val fired = WatchlistStore.evaluate(groups) { priceMap[it] }
        assertEquals(1, fired.size)
        assertEquals("btc", fired[0].second)
    }

    @Test
    fun `ABOVE triggers when price exceeds threshold`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = true, threshold = 50000.0)))
        )))
        val fired = WatchlistStore.evaluate(groups) { 55000.0 }
        assertEquals(1, fired.size)
    }

    @Test
    fun `ABOVE does not trigger below threshold`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = true, threshold = 50000.0)))
        )))
        val fired = WatchlistStore.evaluate(groups) { 49000.0 }
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `BELOW triggers when price at or below threshold`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = false, threshold = 50000.0)))
        )))
        assertEquals(1, WatchlistStore.evaluate(groups) { 50000.0 }.size)
        assertEquals(1, WatchlistStore.evaluate(groups) { 49000.0 }.size)
        assertEquals(0, WatchlistStore.evaluate(groups) { 51000.0 }.size)
    }

    @Test
    fun `evaluate skips already triggered alerts`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = true, threshold = 50000.0, triggered = true)))
        )))
        val fired = WatchlistStore.evaluate(groups) { 55000.0 }
        assertTrue(fired.isEmpty())
    }

    @Test
    fun `evaluate handles multiple coins and groups`() {
        val groups = listOf(
            makeGroup("g1", listOf(
                makeCoin("btc", listOf(alert(above = true, threshold = 50000.0)))
            )),
            makeGroup("g2", listOf(
                makeCoin("eth", listOf(alert(above = false, threshold = 3000.0)))
            ))
        )
        val priceMap = mapOf("btc" to 55000.0, "eth" to 2500.0)
        val fired = WatchlistStore.evaluate(groups) { priceMap[it] }
        assertEquals(2, fired.size)
        assertTrue(fired.any { it.first == "g1" && it.second == "btc" })
        assertTrue(fired.any { it.first == "g2" && it.second == "eth" })
    }

    @Test
    fun `markTriggered sets triggeredAt and triggeredPrice`() {
        val groups = listOf(makeGroup(coins = listOf(
            makeCoin("btc", listOf(alert(above = true, threshold = 50000.0)))
        )))
        val fired = listOf(Triple("g1", "btc", groups[0].coins[0].alerts[0]))
        val updated = WatchlistStore.markTriggered(groups, fired, { 55000.0 }, 12345L)
        val updatedAlert = updated[0].coins[0].alerts[0]
        assertEquals(12345L, updatedAlert.triggeredAt)
        assertEquals(55000.0, updatedAlert.triggeredPrice!!, 0.0001)
    }

    @Test
    fun `markTriggered does not modify non-fired alerts`() {
        val a1 = alert(above = true, threshold = 50000.0)
        val a2 = alert(above = true, threshold = 60000.0)
        val groups = listOf(makeGroup(coins = listOf(makeCoin("btc", listOf(a1, a2)))))
        val fired = listOf(Triple("g1", "btc", a1))
        val updated = WatchlistStore.markTriggered(groups, fired, { 55000.0 }, 12345L)
        assertEquals(12345L, updated[0].coins[0].alerts[0].triggeredAt)
        assertNull(updated[0].coins[0].alerts[1].triggeredAt)
    }
}
