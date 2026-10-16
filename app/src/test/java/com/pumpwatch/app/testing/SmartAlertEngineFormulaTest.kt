package com.pumpwatch.app.testing

import com.pumpwatch.app.data.CoinMarket
import com.pumpwatch.app.engine.FormulaSide
import com.pumpwatch.app.engine.RangeMode
import com.pumpwatch.app.engine.SignalFormula
import com.pumpwatch.app.engine.SmartAlertEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🚀 P3-3: تست واحد برای فیلتر فرمول سفارشی (passesFormula).
 *
 * هر تست یک گیت خاص را بررسی می‌کند تا مطمئن شویم
 * فرمول‌ساز دقیقاً همان کاری را می‌کند که کاربر انتظار دارد.
 */
class SmartAlertEngineFormulaTest {

    // ---------- Helperها ----------

    private fun coin(
        id: String = "test-coin",
        price: Double = 150.0,
        high: Double = 200.0,
        low: Double = 100.0,
        change1h: Double? = 2.0,
        marketCap: Double = 1_000_000.0,
        volume: Double = 200_000.0 // turnover = 0.2
    ) = CoinMarket(
        id = id,
        symbol = "TEST",
        name = "Test Coin",
        image = "",
        current_price = price,
        market_cap = marketCap,
        total_volume = volume,
        price_change_percentage_24h = 5.0,
        market_cap_rank = 100,
        change1h = change1h,
        change7d = 10.0,
        change1y = null,
        ath = null,
        atl = null,
        ath_change_percentage = null,
        atl_change_percentage = null,
        high24h = high,
        low24h = low
    )

    private fun eval(
        coin: CoinMarket,
        side: String = "PUMP",
        score: Int = 60
    ) = SmartAlertEngine.AlertEval(
        coin = coin,
        side = side,
        score = score,
        early = false,
        reasons = listOf("تست")
    )

    // ---------- تست‌های DEFAULT ----------

    @Test
    fun `default formula passes a normal signal`() {
        val c = coin()
        val e = eval(c)
        assertTrue(SmartAlertEngine.passesFormula(c, e, SignalFormula.DEFAULT))
    }

    // ---------- گیت امتیاز ----------

    @Test
    fun `minScore gate rejects low score`() {
        val c = coin()
        val e = eval(c, score = 60)
        val f = SignalFormula.DEFAULT.copy(minScore = 70)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `minScore gate accepts equal score`() {
        val c = coin()
        val e = eval(c, score = 70)
        val f = SignalFormula.DEFAULT.copy(minScore = 70)
        assertTrue(SmartAlertEngine.passesFormula(c, e, f))
    }

    // ---------- گیت شتاب ۱ ساعته ----------

    @Test
    fun `minChange1h gate rejects weak momentum`() {
        val c = coin(change1h = 2.0)
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(minChange1h = 3.0)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `minChange1h gate accepts strong negative momentum using abs`() {
        val c = coin(change1h = -4.0)
        val e = eval(c, side = "DUMP")
        val f = SignalFormula.DEFAULT.copy(minChange1h = 3.0)
        assertTrue(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `minChange1h gate rejects null change1h as zero`() {
        val c = coin(change1h = null)
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(minChange1h = 1.0)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    // ---------- گیت نسبت حجم ----------

    @Test
    fun `minVolumeRatio gate rejects low turnover`() {
        val c = coin(volume = 100_000.0) // turnover = 0.1
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(minVolumeRatio = 0.2)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `minVolumeRatio gate accepts high turnover`() {
        val c = coin(volume = 300_000.0) // turnover = 0.3
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(minVolumeRatio = 0.2)
        assertTrue(SmartAlertEngine.passesFormula(c, e, f))
    }

    // ---------- گیت محدودهٔ ۲۴ ساعته ----------

    @Test
    fun `NEAR_HIGH accepts price near 24h high`() {
        val c = coin(price = 190.0) // rangePos = 0.9
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(rangeMode = RangeMode.NEAR_HIGH)
        assertTrue(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `NEAR_HIGH rejects price near 24h low`() {
        val c = coin(price = 110.0) // rangePos = 0.1
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(rangeMode = RangeMode.NEAR_HIGH)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `NEAR_LOW accepts price near 24h low`() {
        val c = coin(price = 110.0)
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(rangeMode = RangeMode.NEAR_LOW)
        assertTrue(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `NEAR_LOW rejects price near 24h high`() {
        val c = coin(price = 190.0)
        val e = eval(c)
        val f = SignalFormula.DEFAULT.copy(rangeMode = RangeMode.NEAR_LOW)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    // ---------- گیت جهت ----------

    @Test
    fun `side PUMP rejects DUMP signals`() {
        val c = coin()
        val e = eval(c, side = "DUMP")
        val f = SignalFormula.DEFAULT.copy(side = FormulaSide.PUMP)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `side DUMP rejects PUMP signals`() {
        val c = coin()
        val e = eval(c, side = "PUMP")
        val f = SignalFormula.DEFAULT.copy(side = FormulaSide.DUMP)
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }

    @Test
    fun `side BOTH accepts both directions`() {
        val c = coin()
        val f = SignalFormula.DEFAULT.copy(side = FormulaSide.BOTH)
        assertTrue(SmartAlertEngine.passesFormula(c, eval(c, side = "PUMP"), f))
        assertTrue(SmartAlertEngine.passesFormula(c, eval(c, side = "DUMP"), f))
    }

    // ---------- ترکیب چند گیت ----------

    @Test
    fun `multiple failing gates reject the signal`() {
        val c = coin(change1h = 0.5, volume = 50_000.0, price = 110.0)
        val e = eval(c, score = 40, side = "DUMP")
        val f = SignalFormula(
            minScore = 50,
            minChange1h = 1.0,
            minVolumeRatio = 0.1,
            rangeMode = RangeMode.NEAR_HIGH,
            side = FormulaSide.PUMP
        )
        assertFalse(SmartAlertEngine.passesFormula(c, e, f))
    }
}
