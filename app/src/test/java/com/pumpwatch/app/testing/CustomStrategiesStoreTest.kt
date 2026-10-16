package com.pumpwatch.app.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pumpwatch.app.engine.CustomStrategiesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 P2-2: تست واحد برای CustomStrategiesStore.
 * اثبات می‌کند که استراتژی‌ها به‌درستی بارگذاری، ذخیره و toggle می‌شوند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomStrategiesStoreTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `load returns default strategies when no data saved`() {
        // پاک کردن داده‌های قبلی
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().remove("custom_strategies").apply()

        val strategies = CustomStrategiesStore.load(ctx)

        assertEquals(3, strategies.size)
        assertEquals("early_pump", strategies[0].id)
        assertEquals("whale_hunter", strategies[1].id)
        assertEquals("bottom_reversal", strategies[2].id)
        assertFalse(strategies.any { it.isEnabled })
    }

    @Test
    fun `toggle changes isEnabled state and persists it`() {
        // پاک کردن داده‌های قبلی
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().remove("custom_strategies").apply()

        // بارگذاری اولیه
        val initial = CustomStrategiesStore.load(ctx)
        assertFalse(initial.first { it.id == "early_pump" }.isEnabled)

        // Toggle کردن
        CustomStrategiesStore.toggle(ctx, "early_pump")

        // بارگذاری مجدد و بررسی
        val afterToggle = CustomStrategiesStore.load(ctx)
        assertTrue(afterToggle.first { it.id == "early_pump" }.isEnabled)

        // Toggle مجدد (بازگشت به حالت اولیه)
        CustomStrategiesStore.toggle(ctx, "early_pump")
        val afterSecondToggle = CustomStrategiesStore.load(ctx)
        assertFalse(afterSecondToggle.first { it.id == "early_pump" }.isEnabled)
    }

    @Test
    fun `toggle does not affect other strategies`() {
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().remove("custom_strategies").apply()

        CustomStrategiesStore.toggle(ctx, "early_pump")

        val strategies = CustomStrategiesStore.load(ctx)
        assertTrue(strategies.first { it.id == "early_pump" }.isEnabled)
        assertFalse(strategies.first { it.id == "whale_hunter" }.isEnabled)
        assertFalse(strategies.first { it.id == "bottom_reversal" }.isEnabled)
    }

    @Test
    fun `load returns non-null list even with corrupted data`() {
        // نوشتن داده خراب
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putString("custom_strategies", "{broken json").apply()

        val strategies = CustomStrategiesStore.load(ctx)

        assertNotNull(strategies)
        assertEquals(3, strategies.size) // بازگشت به مقادیر پیش‌فرض
    }
}
