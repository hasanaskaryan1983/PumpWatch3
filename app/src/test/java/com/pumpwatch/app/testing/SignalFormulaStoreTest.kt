package com.pumpwatch.app.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.engine.FormulaSide
import com.pumpwatch.app.engine.RangeMode
import com.pumpwatch.app.engine.SignalFormula
import com.pumpwatch.app.engine.SignalFormulaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 P3-3: تست واحد برای ذخیره‌سازی فرمول سفارشی.
 *
 * مهم‌ترین تضمین: دکمهٔ «بازگشت به بهترین» هیچ‌وقت نمی‌شکند
 * و دادهٔ خراب هرگز باعث crash نمی‌شود (fail-safe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalFormulaStoreTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun clear() {
        ctx.getSharedPreferences("pumpwatch_prefs", 0)
            .edit().remove("signal_formula_v1").apply()
    }

    @Test
    fun `load returns DEFAULT when nothing saved`() {
        clear()
        assertEquals(SignalFormula.DEFAULT, SignalFormulaStore.load(ctx))
    }

    @Test
    fun `save then load returns the same formula`() {
        clear()
        val f = SignalFormula(
            minScore = 60,
            minChange1h = 2.0,
            minVolumeRatio = 0.15,
            rangeMode = RangeMode.NEAR_HIGH,
            side = FormulaSide.PUMP
        )
        SignalFormulaStore.save(ctx, f)
        assertEquals(f, SignalFormulaStore.load(ctx))
    }

    @Test
    fun `isDefault is true initially and false after custom save`() {
        clear()
        assertTrue(SignalFormulaStore.isDefault(ctx))
        SignalFormulaStore.save(ctx, SignalFormula.DEFAULT.copy(minScore = 55))
        assertFalse(SignalFormulaStore.isDefault(ctx))
    }

    @Test
    fun `resetToDefault restores the best settings`() {
        clear()
        SignalFormulaStore.save(
            ctx,
            SignalFormula.DEFAULT.copy(side = FormulaSide.DUMP, minScore = 90)
        )
        assertFalse(SignalFormulaStore.isDefault(ctx))

        SignalFormulaStore.resetToDefault(ctx)

        assertEquals(SignalFormula.DEFAULT, SignalFormulaStore.load(ctx))
        assertTrue(SignalFormulaStore.isDefault(ctx))
    }

    @Test
    fun `corrupted json falls back to DEFAULT without crash`() {
        ctx.getSharedPreferences("pumpwatch_prefs", 0)
            .edit().putString("signal_formula_v1", "{not valid json").apply()
        assertEquals(SignalFormula.DEFAULT, SignalFormulaStore.load(ctx))
    }

    @Test
    fun `enum values survive save-load round trip`() {
        clear()
        val f = SignalFormula.DEFAULT.copy(
            rangeMode = RangeMode.NEAR_LOW,
            side = FormulaSide.DUMP
        )
        SignalFormulaStore.save(ctx, f)
        val loaded = SignalFormulaStore.load(ctx)
        assertEquals(RangeMode.NEAR_LOW, loaded.rangeMode)
        assertEquals(FormulaSide.DUMP, loaded.side)
    }
}
