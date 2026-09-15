package com.pumpwatch.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های واحد برای منطق فیلتر موتور ۵ کیف.
 *
 * تمرکز: تضمین اصل P0-3 که تراکنش با priceUsd=null هرگز
 * به‌بهانهٔ «زیر ۱۰ دلار» حذف نمی‌شود — همان باگی که
 * در تست میدانی مشاهده شد (۱ تراکنش از ۱۳۸ نمایش داده شد).
 */
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
        val input = listOf(tx(amount = 1.0, price = 20.0))  // value = 20
        val (out, summary) = filterAndSummarize(input, 1)
        assertEquals(1, out.size)
        assertTrue(summary.contains("۱ تراکنش بالای"))
    }

    @Test
    fun `priced transaction below 10 USD is dropped`() {
        val input = listOf(tx(amount = 0.1, price = 5.0))  // value = 0.5
        val (out, summary) = filterAndSummarize(input, 1)
        assertEquals("تراکنش زیر ۱۰ دلار با قیمت مشخص باید حذف شود", 0, out.size)
        assertTrue(summary.contains("۰ تراکنش بالای"))
    }

    @Test
    fun `transaction with null price is KEPT regardless of amount — P0-3 invariant`() {
        // این تست دقیقاً جلوی رگرسیون باگ موتور ۵ را می‌گیرد:
        // توکن‌هایی که در کوین‌گکو لیست نشده‌اند نباید حذف شوند.
        val input = listOf(
            tx(sym = "METBOI", amount = 1_000_000.0, price = null),
            tx(sym = "SOL", amount = 0.01, price = null),
            tx(sym = "BTC", amount = 1.0, price = 20.0)
        )
        val (out, summary) = filterAndSummarize(input, 3)
        assertEquals("هر ۳ تراکنش باید بمانند", 3, out.size)
        assertTrue(summary.contains("۲ تراکنش با قیمت نامشخص"))
        assertTrue(summary.contains("۱ تراکنش بالای"))
    }

    @Test
    fun `summary format is always consistent regardless of counts`() {
        val cases = listOf(
            listOf(tx(price = 50.0)),
            listOf(tx(price = null)),
            listOf(tx(price = 50.0), tx(price = null))
        )
        for ((i, input) in cases.withIndex()) {
            val (_, summary) = filterAndSummarize(input, input.size)
            assertTrue("case $i: باید با ✅ شروع شود", summary.startsWith("✅"))
            assertTrue("case $i: باید «از N تراکنش خونده‌شده» داشته باشد", summary.contains("تراکنش خونده‌شده)"))
        }
        val (_, emptySummary) = filterAndSummarize(emptyList(), 0)
        assertEquals("empty input → empty summary", "", emptySummary)
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
        assertEquals("سقف ۶۰", 60, out.size)
    }
}
