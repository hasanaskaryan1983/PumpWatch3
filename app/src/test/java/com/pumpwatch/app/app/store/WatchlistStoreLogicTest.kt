package com.pumpwatch.app.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 Commit 70: تست‌های منطق pure WatchlistStore (بدون نیاز به Android/Context).
 *
 * هدف: اثبات صحت `evaluate` و `markTriggered` و جلوگیری از regression.
 */
class WatchlistStoreLogicTest {

    private val store = WatchlistStore

    private fun makeGroup(
        groupId: String = "g1",
        coins: List<WatchCoin> = emptyList()
    ) = WatchGroup(id = groupId, name = "Test", coins = coins)

    private fun makeCoin(
        coinId: String = "bitcoin",
        alerts: List<WatchAlert> = emptyList()
    ) = WatchCoin(id = coinId, symbol = "BTC", name = "Bitcoin", alerts = alerts)

    private fun makeAlert(
        id: String = "a1",
        above: Boolean = true,
        threshold: Double = 50000.0,
        triggeredAt: Long? = null
    ) = WatchAlert(id = id, above = above, threshold = threshold, triggeredAt = triggeredAt)

    @Test
    fun `evaluate triggers alert when price crosses threshold above`() {
        val alert = makeAlert(above = true, threshold = 50000.0)
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = store.evaluate(listOf(group)) { 55000.0 }
        assertEquals(1, fired.size)
        assertEquals("a1", fired[0].third.id)
    }

    @Test
    fun `evaluate does not trigger alert when price below threshold`() {
        val alert = makeAlert(above = true, threshold = 50000.0)
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = store.evaluate(listOf(group)) { 45000.0 }
        assertEquals(0, fired.size)
    }

    @Test
    fun `evaluate triggers alert when price crosses threshold below`() {
        val alert = makeAlert(above = false, threshold = 50000.0)
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = store.evaluate(listOf(group)) { 45000.0 }
        assertEquals(1, fired.size)
    }

    @Test
    fun `evaluate skips already triggered alerts`() {
        val alert = makeAlert(triggeredAt = System.currentTimeMillis())
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = store.evaluate(listOf(group)) { 55000.0 }
        assertEquals(0, fired.size)
    }

    @Test
    fun `evaluate handles missing price gracefully`() {
        val alert = makeAlert()
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = store.evaluate(listOf(group)) { null }
        assertEquals(0, fired.size)
    }

    @Test
    fun `markTriggered sets triggeredAt and triggeredPrice`() {
        val alert = makeAlert()
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = listOf(Triple("g1", "bitcoin", alert))
        val now = System.currentTimeMillis()
        val updated = store.markTriggered(listOf(group), fired, { 55000.0 }, now)
        val updatedAlert = updated[0].coins[0].alerts[0]
        assertEquals(now, updatedAlert.triggeredAt)
        assertEquals(55000.0, updatedAlert.triggeredPrice!!, 0.01)
    }

    @Test
    fun `markTriggered does not modify already triggered alerts`() {
        val oldTime = 1000L
        val alert = makeAlert(triggeredAt = oldTime)
        val coin = makeCoin(alerts = listOf(alert))
        val group = makeGroup(coins = listOf(coin))
        val fired = listOf(Triple("g1", "bitcoin", alert))
        val updated = store.markTriggered(listOf(group), fired, { 55000.0 }, System.currentTimeMillis())
        val updatedAlert = updated[0].coins[0].alerts[0]
        assertEquals(oldTime, updatedAlert.triggeredAt)
    }

    @Test
    fun `evaluate handles multiple groups and coins`() {
        val alert1 = makeAlert(id = "a1", threshold = 50000.0)
        val alert2 = makeAlert(id = "a2", threshold = 3000.0, above = false)
        val coin1 = makeCoin(coinId = "bitcoin", alerts = listOf(alert1))
        val coin2 = makeCoin(coinId = "ethereum", alerts = listOf(alert2))
        val group = makeGroup(coins = listOf(coin1, coin2))
        val fired = store.evaluate(listOf(group)) { coinId ->
            when (coinId) {
                "bitcoin" -> 55000.0
                "ethereum" -> 2500.0
                else -> null
            }
        }
        assertEquals(2, fired.size)
        assertTrue(fired.any { it.third.id == "a1" })
        assertTrue(fired.any { it.third.id == "a2" })
    }
}
