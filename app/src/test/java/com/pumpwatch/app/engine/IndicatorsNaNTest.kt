package com.pumpwatch.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorsNaNTest {

    @Test
    fun emaLast_returns_NaN_when_data_is_insufficient() {
        val shortData = listOf(10.0, 11.0, 12.0)
        val result = Indicators.emaLast(shortData, 200)
        assertTrue("باید NaN برگرداند", result.isNaN())
    }

    @Test
    fun price_comparison_with_NaN_is_always_false() {
        val e200 = Double.NaN
        val price = 50000.0
        assertFalse("price > NaN باید false باشد", price > e200)
        assertFalse("price < NaN باید false باشد", price < e200)
    }
}
