package com.pumpwatch.app.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AlertRulesStoreMatchesTest {

    private fun rule(condition: RuleCondition, threshold: Double, symbol: String = "BTC"): AlertRule =
        AlertRule(
            id = UUID.randomUUID().toString(),
            symbol = symbol,
            condition = condition,
            threshold = threshold
        )

    // ========== matches() ==========

    @Test
    fun `PRICE_ABOVE matches when price equals or exceeds threshold`() {
        val r = rule(RuleCondition.PRICE_ABOVE, 50000.0)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 60.0, 1.5))
        assertTrue(AlertRulesStore.matches(r, 60000.0, 70, 0.0, 60.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 49999.0, 70, 0.0, 60.0, 1.5))
    }

    @Test
    fun `PRICE_BELOW matches when price equals or is under threshold`() {
        val r = rule(RuleCondition.PRICE_BELOW, 50000.0)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 60.0, 1.5))
        assertTrue(AlertRulesStore.matches(r, 40000.0, 70, 0.0, 60.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 50001.0, 70, 0.0, 60.0, 1.5))
    }

    @Test
    fun `SCORE_ABOVE matches correctly`() {
        val r = rule(RuleCondition.SCORE_ABOVE, 70.0)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 80, 0.0, 60.0, 1.5))
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 60.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 50000.0, 60, 0.0, 60.0, 1.5))
    }

    @Test
    fun `FUNDING_ABOVE returns false when funding is null`() {
        val r = rule(RuleCondition.FUNDING_ABOVE, 0.0001)
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, null, 60.0, 1.5))
    }

    @Test
    fun `FUNDING_ABOVE matches when funding exceeds threshold`() {
        val r = rule(RuleCondition.FUNDING_ABOVE, 0.0001)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0005, 60.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, 0.00005, 60.0, 1.5))
    }

    @Test
    fun `FUNDING_BELOW matches when funding at or below threshold`() {
        val r = rule(RuleCondition.FUNDING_BELOW, 0.0001)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.00005, 60.0, 1.5))
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0001, 60.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, 0.0002, 60.0, 1.5))
    }

    @Test
    fun `FUNDING_BELOW returns false when funding is null`() {
        val r = rule(RuleCondition.FUNDING_BELOW, 0.0001)
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, null, 60.0, 1.5))
    }

    @Test
    fun `RSI_ABOVE matches correctly`() {
        val r = rule(RuleCondition.RSI_ABOVE, 70.0)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 70.0, 1.5))
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 75.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 69.0, 1.5))
    }

    @Test
    fun `RSI_BELOW matches correctly`() {
        val r = rule(RuleCondition.RSI_BELOW, 30.0)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 29.0, 1.5))
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 30.0, 1.5))
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 31.0, 1.5))
    }

    @Test
    fun `VOLUME_ABOVE matches correctly`() {
        val r = rule(RuleCondition.VOLUME_ABOVE, 2.0)
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 60.0, 2.5))
        assertTrue(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 60.0, 2.0))
        assertFalse(AlertRulesStore.matches(r, 50000.0, 70, 0.0, 60.0, 1.5))
    }

    // ========== symbolMatches() ==========

    @Test
    fun `symbolMatches wildcard matches any symbol`() {
        val r = rule(RuleCondition.PRICE_ABOVE, 50000.0, "*")
        assertTrue(AlertRulesStore.symbolMatches(r, "BTC"))
        assertTrue(AlertRulesStore.symbolMatches(r, "ETH"))
        assertTrue(AlertRulesStore.symbolMatches(r, "DOGE"))
    }

    @Test
    fun `symbolMatches is case-insensitive`() {
        val r = rule(RuleCondition.PRICE_ABOVE, 50000.0, "BTC")
        assertTrue(AlertRulesStore.symbolMatches(r, "btc"))
        assertTrue(AlertRulesStore.symbolMatches(r, "BTC"))
        assertTrue(AlertRulesStore.symbolMatches(r, "Btc"))
        assertFalse(AlertRulesStore.symbolMatches(r, "ETH"))
    }

    // ========== inCooldown() ==========

    @Test
    fun `inCooldown returns true within cooldown window`() {
        val r = rule(RuleCondition.PRICE_ABOVE, 50000.0)
        val now = System.currentTimeMillis()
        val triggered = r.copy(lastTriggeredTs = now - 3_600_000L)  // 1 hour ago
        assertTrue(AlertRulesStore.inCooldown(triggered, now))
    }

    @Test
    fun `inCooldown returns false after cooldown window`() {
        val r = rule(RuleCondition.PRICE_ABOVE, 50000.0)
        val now = System.currentTimeMillis()
        val triggered = r.copy(lastTriggeredTs = now - 7 * 3_600_000L)  // 7 hours ago
        assertFalse(AlertRulesStore.inCooldown(triggered, now))
    }

    @Test
    fun `inCooldown returns false when never triggered`() {
        val r = rule(RuleCondition.PRICE_ABOVE, 50000.0)
        val now = System.currentTimeMillis()
        assertFalse(AlertRulesStore.inCooldown(r, now))
    }
}
