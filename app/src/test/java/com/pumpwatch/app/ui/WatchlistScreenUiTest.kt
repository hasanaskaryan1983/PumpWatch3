package com.pumpwatch.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 151: تست UI بنر هشدار امنیتی fail-closed.
 *
 * این تست دو حالت را پوشش می‌دهد:
 * 1. حالت عادی: بنر امنیتی نمایش داده نمی‌شود
 * 2. حالت fail-closed: بنر قرمز با متن صادقانه نمایش داده می‌شود
 *
 * 🔑 نکته: برای فعال کردن حالت insecure، مستقیم SharedPreferences
 * را دستکاری می‌کنیم (همان کلیدی که SecureStorage.isInsecureFallback می‌خواند).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchlistScreenUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // پاک کردن همهٔ state ها قبل از هر تست
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit().clear().apply()
        ctx.getSharedPreferences("pumpwatch_secure_prefs", 0).edit().clear().apply()
    }

    @Test
    fun `banner hidden when storage is secure`() {
        // حالت عادی: flag insecure ست نشده
        rule.setContent { WatchlistScreen() }
        rule.mainClock.advanceTimeBy(1000)
        
        // بنر قرمز نباید نمایش داده شود
        rule.onNodeWithText("ذخیره‌سازی امن در دسترس نیست", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `banner shown when keystore unavailable`() {
        // فعال کردن حالت insecure با ست کردن flag
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("secure_storage_fell_back", true)
            .apply()

        rule.setContent { WatchlistScreen() }
        rule.mainClock.advanceTimeBy(1000)

        // بنر قرمز باید نمایش داده شود
        rule.onNodeWithText("ذخیره‌سازی امن در دسترس نیست", substring = true)
            .assertExists()
        
        // متن توضیحی صادقانه باید وجود داشته باشد
        rule.onNodeWithText("Keystore دستگاه فعال نیست", substring = true)
            .assertExists()
        rule.onNodeWithText("تغییرات شما در این نشست موقتی‌اند", substring = true)
            .assertExists()
    }

    @Test
    fun `banner contains warning emoji`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("secure_storage_fell_back", true)
            .apply()

        rule.setContent { WatchlistScreen() }
        rule.mainClock.advanceTimeBy(1000)

        // ایموجی هشدار باید وجود داشته باشد
        rule.onNodeWithText("⚠️").assertExists()
    }

    @Test
    fun `eval status card always shown regardless of storage state`() {
        // تست در حالت secure
        rule.setContent { WatchlistScreen() }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("ارزیابی توسط MonitorWorker", substring = true).assertExists()
    }

    @Test
    fun `eval status card shown even when storage insecure`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("pumpwatch_prefs", 0).edit()
            .putBoolean("secure_storage_fell_back", true)
            .apply()

        rule.setContent { WatchlistScreen() }
        rule.mainClock.advanceTimeBy(1000)
        
        // هر دو کارت باید نمایش داده شوند
        rule.onNodeWithText("ارزیابی توسط MonitorWorker", substring = true).assertExists()
        rule.onNodeWithText("ذخیره‌سازی امن در دسترس نیست", substring = true).assertExists()
    }
}
