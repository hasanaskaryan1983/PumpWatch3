package com.pumpwatch.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletHistoryTest {

    private fun tx(
        sym: String = "BTC",
        amount: Double = 1.0,
        price: Double? = 100.0
    ): HistTx = HistTx(
        ts = 0L, dateText = "", chain = "test",
        symbol = sym, amount = amount, incoming = true,
        other = "", priceUsd = price
    )

    @Test
    fun `priced transaction above 10 USD is kept`() {
        val input = listOf(tx(amount = 1.0, price = 20.0))
        val (out, summary) = filterAndSummarize(input, 1)
        assertEquals(1, out.size)
        assertTrue("summary must mention priced transactions", summary.contains("تراکنش بالای"))
    }

    @Test
    fun `priced transaction below 10 USD is dropped`() {
        val input = listOf(tx(amount = 0.1, price = 5.0))
        val (out, summary) = filterAndSummarize(input, 1)
        assertEquals(0, out.size)
        assertTrue(summary.contains("تراکنش بالای"))
    }

    @Test
    fun `transaction with null price is KEPT regardless of amount`() {
        val input = listOf(
            tx(sym = "METBOI", amount = 1_000_000.0, price = null),
            tx(sym = "SOL", amount = 0.01, price = null),
            tx(sym = "BTC", amount = 1.0, price = 20.0)
        )
        val (out, summary) = filterAndSummarize(input, 3)
        assertEquals(3, out.size)
        assertTrue("summary must mention unknown price", summary.contains("تراکنش با قیمت نامشخص"))
        assertTrue(summary.contains("تراکنش بالای"))
    }

    @Test
    fun `summary format is always consistent`() {
        val cases = listOf(
            listOf(tx(price = 50.0)),
            listOf(tx(price = null)),
            listOf(tx(price = 50.0), tx(price = null))
        )
        for ((i, input) in cases.withIndex()) {
            val (_, summary) = filterAndSummarize(input, input.size)
            assertTrue(summary.startsWith("✅"))
            assertTrue(summary.contains("تراکنش خونده‌شده)"))
        }
        val (_, emptySummary) = filterAndSummarize(emptyList(), 0)
        assertEquals("", emptySummary)
    }

    @Test
    fun `output is sorted by ts descending`() {
        val t1 = HistTx(1000L, "", "test", "A", 1.0, true, "", 10.0)
        val t2 = HistTx(3000L, "", "test", "B", 1.0, true, "", 10.0)
        val t3 = HistTx(2000L, "", "test", "C", 1.0, true, "", 10.0)
        val (out, _) = filterAndSummarize(listOf(t1, t2, t3), 3)
        assertEquals(listOf(3000L, 2000L, 1000L), out.map { it.ts })
    }

    @Test
    fun `output is capped at 60 entries`() {
        val input = (1..100).map { i ->
            HistTx(i.toLong(), "", "test", "S$i", 1.0, true, "", 20.0)
        }
        val (out, _) = filterAndSummarize(input, 100)
        assertEquals(60, out.size)
    }
}
