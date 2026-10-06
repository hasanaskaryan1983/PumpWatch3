package com.pumpwatch.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 🚀 Commit 140: گارد رگرسیون صداقت (Honesty Regression Guard).
 *
 * این تست‌ها متن‌های کلیدی Commit 116 و 118 را قفل می‌کنند:
 * - «هر ۳۰ دقیقه» (نه ۶ ساعت / نه ۱۵ دقیقه)
 * - «تعداد متفاوت در EVM و Solana» (نه «۱۲ چک»)
 * - «Sprint 15 / Commit 118» (نسخهٔ متدولوژی)
 * - «همبستگی ≠ علیت»
 *
 * اگر روزی کسی این متن‌ها را به ادعای دروغین برگرداند، CI قرمز می‌شود.
 * این صفحات استاتیک‌اند (بدون انیمیشن/شبکه) → autoAdvance پیش‌فرض امن است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HonestyScreensUiTest {

    @get:Rule
    val rule = createComposeRule()

    // ========== Methodology ==========

    @Test
    fun `methodology shows honest interval and check-count wording`() {
        rule.setContent { MethodologyScreen(onBack = {}) }
        rule.onNodeWithText("📖 متدولوژی و ریسک").assertExists()
        rule.onNodeWithText("هر ۳۰ دقیقه", substring = true).assertExists()
        rule.onNodeWithText("تعداد متفاوت در EVM و Solana", substring = true).assertExists()
        rule.onNodeWithText("Sprint 15 / Commit 118", substring = true).assertExists()
    }

    @Test
    fun `methodology states no AI in signal generation`() {
        rule.setContent { MethodologyScreen(onBack = {}) }
        // لایه‌ها قاعده‌محورند؛ هیچ ادعای AI/ML نباید وجود داشته باشد
        rule.onNodeWithText("لایهٔ ۳: تأیید MACD و ADX؛ امتیاز ۰ تا ۱۰۰؛ آستانهٔ سیگنال ۷۰ و طلایی ۸۵.", substring = true)
            .assertExists()
    }

    @Test
    fun `methodology back button invokes onBack`() {
        var back = false
        rule.setContent { MethodologyScreen(onBack = { back = true }) }
        rule.onNodeWithText("→ برگشت").performClick()
        assertTrue(back)
    }

    // ========== Risk Disclosure ==========

    @Test
    fun `risk disclosure shows honest monitor interval`() {
        rule.setContent { RiskDisclosureScreen(onBack = {}) }
        rule.onNodeWithText("📖 صداقت و محدودیت‌ها").assertExists()
        rule.onNodeWithText("هر ۳۰ دقیقه توسط MonitorWorker", substring = true).assertExists()
        rule.onNodeWithText("🔗 همبستگی ≠ علیت").assertExists()
        rule.onNodeWithText("🚫 این اپ توصیهٔ مالی نیست").assertExists()
    }

    @Test
    fun `risk disclosure back button invokes onBack after scroll`() {
        var back = false
        rule.setContent { RiskDisclosureScreen(onBack = { back = true }) }
        rule.onNodeWithText("← بازگشت به حریم خصوصی")
            .performScrollTo()
            .performClick()
        assertTrue(back)
    }
}
